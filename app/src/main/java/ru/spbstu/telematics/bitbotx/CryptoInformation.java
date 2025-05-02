package ru.spbstu.telematics.bitbotx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.stream.Collectors;

import ru.spbstu.telematics.bitbotx.model.Currency.Crypto;
import ru.spbstu.telematics.bitbotx.model.Currency.Fiat;
import ru.spbstu.telematics.bitbotx.model.Currency;

/**
 * Сервис для выполнения финансовых расчетов и мониторинга цен.
 * Предоставляет функциональность для:
 * - Сравнения валютных пар
 * - Получения истории цен
 */
@Slf4j
@Component
public class CryptoInformation {
    private static final int PRICE_SCALE = 8;
    private static final int PERCENT_SCALE = 2;
    
    private final PriceFetcher priceFetcher;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    
    public CryptoInformation(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        this.priceFetcher = priceFetcher;
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
    }
    
    /**
     * Сравнивает две криптовалюты за указанный период.
     * Возвращает JSON с текущими и историческими ценами, а также их соотношением.
     * Цены конвертируются в текущую фиатную валюту.
     *
     * @param crypto1 Первая криптовалюта
     * @param crypto2 Вторая криптовалюта
     * @param period Период сравнения (например, "3d", "6h", "30m")
     * @return Mono<String> JSON-строка с результатами сравнения
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PRICE_READER')")
    public Mono<String> compareCurrencies(Crypto crypto1, Crypto crypto2, String period) {
        return currencyConverter.getUsdToFiatRate(Fiat.getCurrentFiat())
                .flatMap(exchangeRate -> 
                    Mono.zip(
                        getPriceData(crypto1, period),
                        getPriceData(crypto2, period),
                        (data1, data2) -> {
                            // Конвертируем цены из USD в текущую фиатную валюту
                            try {
                                // Создаем результирующий JSON
                                ObjectNode result = objectMapper.createObjectNode();
                                result.put("period", period);
                                result.put("currentTimestamp", data1.get("currentTimestamp").asLong() / 1000);
                                result.put("historicTimestamp", data1.get("historicTimestamp").asLong() / 1000);
                                
                                // Форматируем символы и конвертируем цены
                                String symbol1 = crypto1.getCode() + "-" + Fiat.getCurrentFiat().getCode();
                                String symbol2 = crypto2.getCode() + "-" + Fiat.getCurrentFiat().getCode();
                                
                                BigDecimal currentPrice1 = new BigDecimal(data1.get("currentPrice").asText())
                                        .multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal historicPrice1 = new BigDecimal(data1.get("historicPrice").asText())
                                        .multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                        
                                BigDecimal currentPrice2 = new BigDecimal(data2.get("currentPrice").asText())
                                        .multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal historicPrice2 = new BigDecimal(data2.get("historicPrice").asText())
                                        .multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                
                                // Символ 1
                                ObjectNode symbol1Node = objectMapper.createObjectNode();
                                symbol1Node.put("symbol", symbol1);
                                symbol1Node.put("currentPrice", currentPrice1.toString());
                                symbol1Node.put("historicPrice", historicPrice1.toString());
                                symbol1Node.put("change", calculateChange(currentPrice1, historicPrice1));
                                result.set("symbol1", symbol1Node);
                                
                                // Символ 2
                                ObjectNode symbol2Node = objectMapper.createObjectNode();
                                symbol2Node.put("symbol", symbol2);
                                symbol2Node.put("currentPrice", currentPrice2.toString());
                                symbol2Node.put("historicPrice", historicPrice2.toString());
                                symbol2Node.put("change", calculateChange(currentPrice2, historicPrice2));
                                result.set("symbol2", symbol2Node);
                                
                                // Соотношение
                                BigDecimal currentRatio = currentPrice1.divide(currentPrice2, PRICE_SCALE, RoundingMode.HALF_UP);
                                BigDecimal historicRatio = historicPrice1.divide(historicPrice2, PRICE_SCALE, RoundingMode.HALF_UP);
                                
                                ObjectNode ratioNode = objectMapper.createObjectNode();
                                ratioNode.put("currentRatio", currentRatio.setScale(PERCENT_SCALE, RoundingMode.HALF_UP).toString());
                                ratioNode.put("historicRatio", historicRatio.setScale(PERCENT_SCALE, RoundingMode.HALF_UP).toString());
                                ratioNode.put("change", calculateChange(currentRatio, historicRatio));
                                result.set("ratio", ratioNode);
                                
                                String json = objectMapper.writeValueAsString(result);
                                log.info("Comparison result: {}", json);
                                return json;
                            } catch (Exception e) {
                                log.error("Error creating comparison result: {}", e.getMessage());
                                throw new RuntimeException("Error creating comparison result", e);
                            }
                        }
                    )
                );
    }
    
    /**
     * Получает историю цен для пары криптовалюта-фиат за указанный период.
     * Использует текущие значения криптовалюты и фиатной валюты.
     * Цены конвертируются в текущую фиатную валюту.
     *
     * @param period Период для получения истории цен
     * @return Mono<String> с историей цен
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PRICE_READER')")
    public Mono<String> showPriceHistory(String period) {
        var intervalPoints = calculateIntervalAndPoints(period);
        long intervalMillis = intervalPoints[0];
        int points = (int) intervalPoints[1];
        Crypto currentCrypto = Crypto.getCurrentCrypto();
        Fiat currentFiat = Fiat.getCurrentFiat();
        
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(currentCrypto)
                        .flatMap(currentJson -> Mono.fromCallable(() -> objectMapper.readTree(currentJson))
                        .flatMap(currentNode -> {
                            BigDecimal currentPrice = new BigDecimal(currentNode.get("price").asText());
                            long currentTimestamp = currentNode.get("timestamp").asLong() * 1000;
                            
                            // Конвертируем текущую цену
                            BigDecimal currentPriceInFiat = currentPrice
                                    .multiply(exchangeRate)
                                    .setScale(2, RoundingMode.HALF_UP);
                            
                    Flux<Long> timestamps = Flux.range(1, points - 1)
                                    .map(i -> currentTimestamp - (i * intervalMillis))
                            .map(ts -> ts / 1000);
                    
                            // Получаем исторические цены
                            return timestamps.flatMap(ts -> 
                                priceFetcher.getSymbolPriceByTime(currentCrypto, ts)
                                    .flatMap(json -> Mono.fromCallable(() -> {
                                        JsonNode node = objectMapper.readTree(json);
                                        BigDecimal price = new BigDecimal(node.get("price").asText());
                                        long timestamp = node.get("timestamp").asLong() * 1000;
                                        
                                        // Конвертируем историческую цену
                                        BigDecimal priceInFiat = price
                                                .multiply(exchangeRate)
                                                .setScale(2, RoundingMode.HALF_UP);
                                        
                                        // Создаем JSON-узел
                                        ObjectNode priceNode = objectMapper.createObjectNode();
                                        priceNode.put("price", priceInFiat.toString());
                                        priceNode.put("timestamp", timestamp / 1000);
                                        return priceNode;
                                    }))
                            )
                            .collectList()
                            .map(historyNodes -> {
                                try {
                                    // Формируем результат
                                    ObjectNode result = objectMapper.createObjectNode();
                                    result.put("symbol", currentCrypto.getCode() + "-" + currentFiat.getCode());
                                    result.put("period", period);
                                    result.put("currentTimestamp", currentTimestamp / 1000);
                                    result.put("currentPrice", currentPriceInFiat.toString());
                                    
                                    // Добавляем историю
                                    ArrayNode historyArray = objectMapper.createArrayNode();
                                    historyNodes.forEach(historyArray::add);
                                    result.set("history", historyArray);
                                    
                                    String json = objectMapper.writeValueAsString(result);
                                    log.info("Price history: {}", json);
                                    return json;
                                } catch (Exception e) {
                                    log.error("Error creating price history: {}", e.getMessage());
                                    throw new RuntimeException("Error creating price history", e);
                                }
                            });
                        }))
                );
    }
    
    /**
     * Возвращает текущую цену для текущей пары криптовалюта-фиат.
     * Цена конвертируется в текущую фиатную валюту.
     *
     * @return Mono<String> JSON-строка с текущей ценой
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PRICE_READER')")
    public Mono<String> showCurrentPrice() {
        Crypto currentCrypto = Crypto.getCurrentCrypto();
        Fiat currentFiat = Fiat.getCurrentFiat();
        
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(currentCrypto)
                        .flatMap(currentJson -> {
                            try {
                                JsonNode originalNode = objectMapper.readTree(currentJson);
                                String symbol = originalNode.get("symbol").asText();
                                BigDecimal priceUSD = new BigDecimal(originalNode.get("price").asText());
                                long timestamp = originalNode.get("timestamp").asLong();
                                
                                // Конвертируем цену в текущую фиатную валюту
                                BigDecimal priceInFiat = priceUSD
                                        .multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                
                                ObjectNode result = objectMapper.createObjectNode();
                                result.put("symbol", symbol.replace("USDT", currentFiat.getCode()));
                                result.put("price", priceInFiat.toString());
                                result.put("timestamp", timestamp);
                                
                                String json = objectMapper.writeValueAsString(result);
                                log.info("Current price: {}", json);
                                return Mono.just(json);
                            } catch (Exception e) {
                                log.error("Error processing current price: {}", e.getMessage());
                                return Mono.error(e);
                            }
                        })
                );
    }
    
    private Mono<JsonNode> getPriceData(Crypto crypto, String period) {
        long periodMillis = periodToMillis(period);
        
        return priceFetcher.getCurrentPrice(crypto)
                .flatMap(currentJson -> Mono.fromCallable(() -> objectMapper.readTree(currentJson))
                .flatMap(currentNode -> {
                    BigDecimal currentPrice = new BigDecimal(currentNode.get("price").asText());
                    long currentTimestamp = currentNode.get("timestamp").asLong() * 1000;
                    
                    return priceFetcher.getSymbolPriceByTime(crypto, (currentTimestamp - periodMillis) / 1000)
                        .flatMap(historicJson -> Mono.fromCallable(() -> {
                            JsonNode historicNode = objectMapper.readTree(historicJson);
                            BigDecimal historicPrice = new BigDecimal(historicNode.get("price").asText());
                            long historicTimestamp = historicNode.get("timestamp").asLong() * 1000;
                            
                            // Создаем JSON-объект с данными
                            ObjectNode data = objectMapper.createObjectNode();
                            data.put("symbol", crypto.getCode());
                            data.put("currentPrice", currentPrice.toString());
                            data.put("historicPrice", historicPrice.toString());
                            data.put("currentTimestamp", currentTimestamp);
                            data.put("historicTimestamp", historicTimestamp);
                            
                            return data;
                        }));
                }));
    }
    
    private String calculateChange(BigDecimal current, BigDecimal historic) {
        if (historic.compareTo(BigDecimal.ZERO) == 0) {
            return "0.00";
        }
        return current.subtract(historic)
                .divide(historic, PRICE_SCALE, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP)
                .toString();
    }
    
    private long periodToMillis(String period) {
        if (period == null || period.isEmpty()) {
            throw new IllegalArgumentException("Period cannot be null or empty");
        }

        char unit = period.charAt(period.length() - 1);
        int value = Integer.parseInt(period.substring(0, period.length() - 1));

        return switch (unit) {
            case 'm' -> value * 60L * 1000L;
            case 'h' -> value * 60L * 60L * 1000L;
            case 'd' -> value * 24L * 60L * 60L * 1000L;
            case 'w' -> value * 7L * 24L * 60L * 60L * 1000L;
            case 'M' -> value * 30L * 24L * 60L * 60L * 1000L;
            default -> throw new IllegalArgumentException("Unknown period unit: " + unit);
        };
    }

    private long[] calculateIntervalAndPoints(String period) {
        return switch (period.charAt(period.length() - 1)) {
            case 'm' -> new long[]{60L * 1000L, Integer.parseInt(period.substring(0, period.length() - 1)) + 1};
            case 'h' -> new long[]{60L * 60L * 1000L, Integer.parseInt(period.substring(0, period.length() - 1)) + 1};
            case 'd' -> new long[]{24L * 60L * 60L * 1000L, Integer.parseInt(period.substring(0, period.length() - 1)) + 1};
            case 'w' -> new long[]{7L * 24L * 60L * 60L * 1000L, Integer.parseInt(period.substring(0, period.length() - 1)) + 1};
            case 'M' -> new long[]{30L * 24L * 60L * 60L * 1000L, Integer.parseInt(period.substring(0, period.length() - 1)) + 1};
            default -> throw new IllegalArgumentException("Invalid period format: " + period);
        };
    }
} 