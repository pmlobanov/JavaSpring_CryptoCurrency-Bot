package ru.spbstu.telematics.bitbotx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;

import ru.spbstu.telematics.bitbotx.model.Currency.Crypto;
import ru.spbstu.telematics.bitbotx.model.Currency.Fiat;
import ru.spbstu.telematics.bitbotx.model.Asset;

/**
 * Сервис для управления портфелем пользователя.
 * Позволяет добавлять, удалять и отслеживать активы.
 */
@Slf4j
@Component
public class PortfolioManagement {
    private final PriceFetcher priceFetcher;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    private final ConcurrentHashMap<Crypto, Asset> portfolio = new ConcurrentHashMap<>();
    
    // Добавляем переменные для отслеживания изменения цены портфеля
    private BigDecimal lastPortfolioPrice = null;
    private Long lastPortfolioPriceTimestamp = null;
    
    public PortfolioManagement(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        this.priceFetcher = priceFetcher;
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
    }
    
    /**
     * Добавляет указанное количество криптовалюты в портфель.
     * Если криптовалюта уже есть в портфеле, количество увеличивается.
     * Цены хранятся в USD, но возвращаются в текущей фиатной валюте.
     *
     * @param crypto Криптовалюта для добавления
     * @param count Количество для добавления
     * @return Mono<String> JSON-строка с обновленной информацией по активу
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PORTFOLIO_MANAGER')")
    public Mono<String> add(Crypto crypto, BigDecimal count) {
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(crypto)
                        .flatMap(priceJson -> {
                            try {
                                // Парсим ответ от биржи (цена всегда в USDT)
                                JsonNode jsonNode = objectMapper.readTree(priceJson);
                                BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                long timestamp = jsonNode.get("timestamp").asLong();
                                
                                // Формируем символы для хранения и отображения
                                String displaySymbol = crypto.getCode() + "-" + currentFiat.getCode();
                                String storageSymbol = crypto.getCode() + "-USD";
                                
                                // Если актив уже есть, увеличиваем количество, иначе создаем новый
                                Asset asset = portfolio.compute(crypto, (key, existingAsset) -> {
                                    if (existingAsset != null) {
                                        BigDecimal newCount = existingAsset.getCount().add(count);
                                        existingAsset.setCount(newCount);
                                        existingAsset.setPrice(priceInUSDT, timestamp);
                                        return existingAsset;
                                    } else {
                                        return new Asset(storageSymbol, count, priceInUSDT, timestamp);
                                    }
                                });
                                
                                log.info("Added {} {} to portfolio. Stored price in USDT: {}. Total count: {}", 
                                        count, crypto.getCode(), priceInUSDT, asset.getCount());
                                
                                // Если текущая фиатная валюта не USD, конвертируем цену для отображения
                                BigDecimal displayPrice;
                                if (currentFiat != Fiat.USD) {
                                    // Конвертируем из USD в текущий фиат
                                    displayPrice = priceInUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                } else {
                                    displayPrice = priceInUSDT;
                                }
                                
                                // Рассчитываем стоимость в текущем фиате
                                BigDecimal assetValue = asset.getCount().multiply(displayPrice).setScale(2, RoundingMode.HALF_UP);
                                
                                ObjectNode result = objectMapper.createObjectNode();
                                result.put("symbol", displaySymbol);
                                result.put("count", asset.getCount().toString());
                                result.put("price", displayPrice.toString());
                                result.put("value", assetValue.toString());
                                result.put("timestamp", asset.getLastPriceTimestamp());
                                
                                return Mono.just(objectMapper.writeValueAsString(result));
                            } catch (Exception e) {
                                return Mono.error(new RuntimeException("Error processing portfolio addition: " + e.getMessage()));
                            }
                        })
                );
    }
    
    /**
     * Удаляет указанное количество криптовалюты из портфеля.
     * Если после удаления количество меньше или равно 0, актив полностью удаляется из портфеля.
     * Цены хранятся в USD, но возвращаются в текущей фиатной валюте.
     *
     * @param crypto Криптовалюта для удаления
     * @param count Количество для удаления
     * @return Mono<String> JSON-строка с обновленной информацией или статусом удаления
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PORTFOLIO_MANAGER')")
    public Mono<String> remove(Crypto crypto, BigDecimal count) {
        Asset asset = portfolio.get(crypto);
        if (asset == null) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "not_found");
            result.put("message", "Asset " + crypto.getCode() + " not found in portfolio");
            try {
                return Mono.just(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Error processing portfolio removal: " + e.getMessage()));
            }
        }
        
        // Проверяем, если пользователь пытается удалить больше, чем есть
        if (count.compareTo(asset.getCount()) > 0) {
            return Mono.fromCallable(() -> {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("status", "insufficient_amount");
                result.put("availableCount", asset.getCount().toString());
                return objectMapper.writeValueAsString(result);
            });
        }
        
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    // Получаем свежую цену с биржи (цена всегда в USDT)
                    priceFetcher.getCurrentPrice(crypto)
                        .flatMap(priceJson -> Mono.fromCallable(() -> {
                            try {
                                // Парсим ответ от биржи (цена всегда в USDT)
                                JsonNode jsonNode = objectMapper.readTree(priceJson);
                                BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                long timestamp = jsonNode.get("timestamp").asLong();
                                
                                // Храним цену как есть (уже в USD)
                                asset.setPrice(priceInUSDT, timestamp);
                                
                                // Вычитаем количество
                                BigDecimal newCount = asset.getCount().subtract(count);
                                asset.setCount(newCount);
                                
                                log.info("Removed {} {} from portfolio. Remaining: {}, New price: {} USDT", 
                                        count, crypto.getCode(), newCount, priceInUSDT);
                                
                                // Формируем символ 
                                String displaySymbol = crypto.getCode() + "-" + currentFiat.getCode();
                                
                                // Если текущая фиатная валюта не USD, конвертируем цену для отображения
                                BigDecimal displayPrice;
                                if (currentFiat != Fiat.USD) {
                                    // Конвертируем из USD в текущий фиат
                                    displayPrice = priceInUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                } else {
                                    displayPrice = priceInUSDT;
                                }
                                
                                // Рассчитываем стоимость в текущем фиате
                                BigDecimal assetValue = newCount.multiply(displayPrice).setScale(2, RoundingMode.HALF_UP);
                                
                                // Формируем результат
                                ObjectNode result = objectMapper.createObjectNode();
                                result.put("status", "success");
                                result.put("symbol", displaySymbol);
                                result.put("count", newCount.toString());
                                result.put("price", displayPrice.toString());
                                result.put("value", assetValue.toString());
                                result.put("timestamp", timestamp);
                                
                                return objectMapper.writeValueAsString(result);
                            } catch (Exception e) {
                                throw new RuntimeException("Error processing portfolio removal: " + e.getMessage());
                            }
                        }))
                );
    }
    
    /**
     * Возвращает текущую стоимость портфеля пользователя.
     * Обновляет цены всех активов и возвращает общую стоимость.
     * Если есть предыдущая цена портфеля, рассчитывает изменение в сумме и процентах.
     *
     * @return Mono<String> JSON-строка с информацией о стоимости портфеля
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PORTFOLIO_READER')")
    public Mono<String> getPortfolioPrice() {
        if (portfolio.isEmpty()) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "empty");
            try {
                return Mono.just(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Error processing portfolio request: " + e.getMessage()));
            }
        }
        
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> Mono.fromCallable(() -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    BigDecimal totalValue = BigDecimal.ZERO;
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    
                    for (var entry : portfolio.entrySet()) {
                        Crypto crypto = entry.getKey();
                        Asset asset = entry.getValue();
                        
                        try {
                            // Получаем актуальную цену в USDT
                            String priceJson = priceFetcher.getCurrentPrice(crypto).block();
                            JsonNode jsonNode = objectMapper.readTree(priceJson);
                            BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                            long timestamp = jsonNode.get("timestamp").asLong();
                            
                            // Обновляем цену в USDT
                            asset.setPrice(priceInUSDT, timestamp);
                            
                            // Рассчитываем стоимость в USDT и добавляем к общей сумме
                            BigDecimal assetValueUSDT = asset.getCount().multiply(priceInUSDT).setScale(2, RoundingMode.HALF_UP);
                            totalValue = totalValue.add(assetValueUSDT);
                        } catch (Exception e) {
                            log.error("Error updating price for {}: {}", crypto.getCode(), e.getMessage());
                        }
                    }
                    
                    // Конвертируем общую стоимость в фиатную валюту
                    BigDecimal totalValueFiat = totalValue.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    result.put("status", "success");
                    result.put("totalValue", totalValueFiat.toString());
                    result.put("currency", currentFiat.getCode());
                    result.put("timestamp", currentTimestamp);
                    
                    // Проверяем, есть ли информация о предыдущей цене портфеля
                    if (lastPortfolioPrice != null && lastPortfolioPriceTimestamp != null) {
                        // Рассчитываем изменение цены в сумме
                        BigDecimal priceChange = totalValueFiat.subtract(lastPortfolioPrice);
                        
                        // Рассчитываем изменение в процентах
                        BigDecimal percentChange = BigDecimal.ZERO;
                        if (lastPortfolioPrice.compareTo(BigDecimal.ZERO) > 0) {
                            percentChange = priceChange.divide(lastPortfolioPrice, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                        }
                        
                        // Используем простой "-" знак вместо direction и emoji
                        String changePrefix = priceChange.compareTo(BigDecimal.ZERO) >= 0 ? "" : "-";
                        
                        // Добавляем информацию об изменении цены
                        result.put("priceChange", changePrefix + priceChange.abs().setScale(2, RoundingMode.HALF_UP).toString());
                        result.put("percentChange", changePrefix + percentChange.abs().toString());
                        result.put("lastPriceTimestamp", lastPortfolioPriceTimestamp);
                    }
                    
                    // Обновляем информацию о последней цене портфеля
                    lastPortfolioPrice = totalValueFiat;
                    lastPortfolioPriceTimestamp = currentTimestamp;
                    
                    return objectMapper.writeValueAsString(result);
                }));
    }
    
    /**
     * Возвращает текущую стоимость всех активов пользователя с информацией об изменении цены.
     * Обновляет цены всех активов, вычисляет изменение цены для каждого актива
     * и возвращает общую информацию о портфеле.
     *
     * @return Mono<String> JSON-строка с информацией о всех активах и их стоимости
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PORTFOLIO_READER')")
    public Mono<String> getAssetsPrice() {
        if (portfolio.isEmpty()) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "empty");
            try {
                return Mono.just(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Error processing assets request: " + e.getMessage()));
            }
        }
        
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> Mono.fromCallable(() -> {
                    ObjectNode result = objectMapper.createObjectNode();
                    ArrayNode assetsArray = objectMapper.createArrayNode();
                    BigDecimal totalValue = BigDecimal.ZERO;
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    
                    // Сохраняем предыдущие цены активов для расчета изменений
                    ConcurrentHashMap<Crypto, BigDecimal> previousAssetPrices = new ConcurrentHashMap<>();
                    for (var entry : portfolio.entrySet()) {
                        previousAssetPrices.put(entry.getKey(), entry.getValue().getLastPrice());
                    }
                    
                    for (var entry : portfolio.entrySet()) {
                        Crypto crypto = entry.getKey();
                        Asset asset = entry.getValue();
                        
                        try {
                            // Получаем актуальную цену в USDT
                            String priceJson = priceFetcher.getCurrentPrice(crypto).block();
                            JsonNode jsonNode = objectMapper.readTree(priceJson);
                            BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                            long timestamp = jsonNode.get("timestamp").asLong();
                            
                            // Сохраняем предыдущую цену и стоимость актива
                            BigDecimal previousPriceUSDT = previousAssetPrices.get(crypto);
                            BigDecimal previousValueUSDT = previousPriceUSDT != null ? 
                                asset.getCount().multiply(previousPriceUSDT).setScale(2, RoundingMode.HALF_UP) : null;
                            
                            // Обновляем текущую цену в USDT
                            asset.setPrice(priceInUSDT, timestamp);
                            
                            // Рассчитываем текущую стоимость в USDT
                            BigDecimal assetValueUSDT = asset.getCount().multiply(priceInUSDT).setScale(2, RoundingMode.HALF_UP);
                            totalValue = totalValue.add(assetValueUSDT);
                            
                            // Конвертируем в фиатную валюту для отображения
                            BigDecimal assetValueFiat = assetValueUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                            BigDecimal priceFiat = priceInUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                            
                            ObjectNode assetNode = objectMapper.createObjectNode();
                            assetNode.put("symbol", crypto.getCode());
                            assetNode.put("count", asset.getCount().toString());
                            assetNode.put("price", priceFiat.toString());
                            assetNode.put("value", assetValueFiat.toString());
                            assetNode.put("timestamp", timestamp);
                            
                            // Если есть предыдущая цена, рассчитываем изменения
                            if (previousPriceUSDT != null && previousValueUSDT != null) {
                                // Конвертируем предыдущую стоимость в фиатную валюту
                                BigDecimal previousValueFiat = previousValueUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                
                                // Рассчитываем изменение в сумме
                                BigDecimal valueChange = assetValueFiat.subtract(previousValueFiat);
                                
                                // Рассчитываем изменение в процентах
                                BigDecimal percentChange = BigDecimal.ZERO;
                                if (previousValueFiat.compareTo(BigDecimal.ZERO) > 0) {
                                    percentChange = valueChange.divide(previousValueFiat, 4, RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                                }
                                
                                // Используем простой "-" знак вместо direction
                                String changePrefix = valueChange.compareTo(BigDecimal.ZERO) >= 0 ? "" : "-";
                                
                                // Добавляем информацию об изменении цены
                                assetNode.put("valueChange", changePrefix + valueChange.abs().setScale(2, RoundingMode.HALF_UP).toString());
                                assetNode.put("percentChange", changePrefix + percentChange.abs().toString());
                            }
                            
                            assetsArray.add(assetNode);
                        } catch (Exception e) {
                            log.error("Error updating price for {}: {}", crypto.getCode(), e.getMessage());
                        }
                    }
                    
                    // Конвертируем общую стоимость в фиатную валюту
                    BigDecimal totalValueFiat = totalValue.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    result.put("status", "success");
                    result.put("totalValue", totalValueFiat.toString());
                    result.put("currency", currentFiat.getCode());
                    result.put("timestamp", currentTimestamp);
                    result.set("assets", assetsArray);
                    
                    // Проверяем, есть ли информация о предыдущей цене портфеля
                    if (lastPortfolioPrice != null && lastPortfolioPriceTimestamp != null) {
                        // Рассчитываем изменение цены в сумме
                        BigDecimal priceChange = totalValueFiat.subtract(lastPortfolioPrice);
                        
                        // Рассчитываем изменение в процентах
                        BigDecimal percentChange = BigDecimal.ZERO;
                        if (lastPortfolioPrice.compareTo(BigDecimal.ZERO) > 0) {
                            percentChange = priceChange.divide(lastPortfolioPrice, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                        }
                        
                        // Используем простой "-" знак вместо direction
                        String changePrefix = priceChange.compareTo(BigDecimal.ZERO) >= 0 ? "" : "-";
                        
                        // Добавляем информацию об изменении цены
                        result.put("priceChange", changePrefix + priceChange.abs().setScale(2, RoundingMode.HALF_UP).toString());
                        result.put("percentChange", changePrefix + percentChange.abs().toString());
                        result.put("lastPriceTimestamp", lastPortfolioPriceTimestamp);
                    }
                    
                    // Обновляем информацию о последней цене портфеля
                    lastPortfolioPrice = totalValueFiat;
                    lastPortfolioPriceTimestamp = currentTimestamp;
                    
                    return objectMapper.writeValueAsString(result);
                }));
    }
} 