package spbstu.mcs.telegramBot.cryptoApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.List;
import java.util.AbstractMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.time.Duration;

import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Currency.Fiat;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.model.Currency;

/**
 * Сервис для управления криптовалютным портфелем пользователя.
 * Предоставляет функционал для работы с портфелем криптовалют, включая:
 * - Добавление и удаление активов
 * - Получение информации о портфеле
 * - Расчет стоимости портфеля
 * - Отслеживание изменений цен
 */
@Service
@Slf4j
public class CryptoPortfolioManager {
    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    private final PriceFetcher priceFetcher;
    private final PortfolioService portfolioService;
    private Portfolio currentPortfolio;
    
    @Autowired
    public CryptoPortfolioManager(ObjectMapper objectMapper,
                             CurrencyConverter currencyConverter,
                             PriceFetcher priceFetcher,
                             PortfolioService portfolioService) {
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.portfolioService = portfolioService;
    }
    
    private record PortfolioPriceInfo(
        Currency.Crypto crypto,
        BigDecimal count,
        BigDecimal currentPrice,
        BigDecimal previousPrice,
        long previousTimestamp
    ) {}
    
    /**
     * Добавляет криптовалюту в портфель пользователя.
     * Обновляет количество и сохраняет текущую цену.
     *
     * @param crypto Криптовалюта для добавления
     * @param count Количество для добавления
     * @return Mono<String> JSON с информацией об обновленном активе
     */
    public Mono<String> add(Crypto crypto, BigDecimal count) {
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(crypto)
                        .flatMap(priceJson -> {
                            try {
                                JsonNode jsonNode = objectMapper.readTree(priceJson);
                                BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                long timestamp = jsonNode.get("timestamp").asLong();
                                
                                String displaySymbol = crypto.getCode() + "-" + currentFiat.getCode();
                                
                                currentPortfolio = portfolioService.addCryptoToPortfolio(
                                    currentPortfolio.getId(), crypto, count);
                                
                                log.info("Added {} {} to portfolio. Stored price in USDT: {}. Total count: {}", 
                                        count, crypto.getCode(), priceInUSDT, 
                                        currentPortfolio.getCount());
                                
                                BigDecimal displayPrice;
                                if (currentFiat != Fiat.USD) {
                                    displayPrice = priceInUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                } else {
                                    displayPrice = priceInUSDT;
                                }
                                
                                BigDecimal assetValue = count.multiply(displayPrice).setScale(2, RoundingMode.HALF_UP);
                                
                                ObjectNode result = objectMapper.createObjectNode();
                                result.put("symbol", displaySymbol);
                                result.put("count", count.toString());
                                result.put("price", displayPrice.toString());
                                result.put("value", assetValue.toString());
                                result.put("timestamp", timestamp);
                                
                                return Mono.just(objectMapper.writeValueAsString(result));
                            } catch (Exception e) {
                                return Mono.error(new RuntimeException("Error processing portfolio addition: " + e.getMessage()));
                            }
                        })
                );
    }
    
    /**
     * Удаляет криптовалюту из портфеля пользователя.
     * Обновляет количество и сохраняет текущую цену.
     *
     * @param crypto Криптовалюта для удаления
     * @param count Количество для удаления
     * @return Mono<String> JSON с информацией об обновленном активе
     */
    public Mono<String> remove(Crypto crypto, BigDecimal count) {
        if (currentPortfolio == null || currentPortfolio.getCryptoCurrency() != crypto) {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", "not_found");
            result.put("message", "Asset " + crypto.getCode() + " not found in portfolio");
            try {
                return Mono.just(objectMapper.writeValueAsString(result));
            } catch (Exception e) {
                return Mono.error(new RuntimeException("Error processing portfolio removal: " + e.getMessage()));
            }
        }
        
        BigDecimal currentCount = currentPortfolio.getCount();
        if (count.compareTo(currentCount) > 0) {
            return Mono.fromCallable(() -> {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("status", "insufficient_amount");
                result.put("availableCount", currentCount.toString());
                return objectMapper.writeValueAsString(result);
            });
        }
        
        Fiat currentFiat = Fiat.getCurrentFiat();
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(crypto)
                        .flatMap(priceJson -> Mono.fromCallable(() -> {
                            try {
                                JsonNode jsonNode = objectMapper.readTree(priceJson);
                                BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                long timestamp = jsonNode.get("timestamp").asLong();
                                
                                currentPortfolio = portfolioService.removeCryptoFromPortfolio(
                                    currentPortfolio.getId(), crypto, count);
                                
                                log.info("Removed {} {} from portfolio. Remaining: {}, New price: {} USDT", 
                                        count, crypto.getCode(), 
                                        currentPortfolio.getCount(), 
                                        priceInUSDT);
                                
                                String displaySymbol = crypto.getCode() + "-" + currentFiat.getCode();
                                
                                BigDecimal displayPrice;
                                if (currentFiat != Fiat.USD) {
                                    displayPrice = priceInUSDT.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                } else {
                                    displayPrice = priceInUSDT;
                                }
                                
                                BigDecimal newCount = currentPortfolio.getCount();
                                BigDecimal assetValue = newCount.multiply(displayPrice).setScale(2, RoundingMode.HALF_UP);
                                
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
     * Получает текущую стоимость портфеля пользователя.
     * Включает информацию о каждом активе и общую стоимость.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> Информация о портфеле
     */
    public Mono<String> getPortfolioInfo(String chatId) {
        return Mono.fromCallable(() -> {
            List<Portfolio> portfolios = portfolioService.getPortfoliosByChatId(chatId);
            if (portfolios.isEmpty()) {
                return Mono.just("У вас пока нет портфеля. Используйте команду /add для создания портфеля и добавления криптовалюты.");
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("👜 Портфель (%d активов):\n", portfolios.size()));
            
            return Flux.fromIterable(portfolios)
                .filter(portfolio -> portfolio.getCryptoCurrency() != null && portfolio.getCount().compareTo(BigDecimal.ZERO) > 0)
                .flatMap(portfolio -> {
                    String cryptoCode = portfolio.getCryptoCurrency().getCode();
                    BigDecimal amount = portfolio.getCount().setScale(6, RoundingMode.FLOOR);
                    
                    return Mono.zip(
                        priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency()),
                        currencyConverter.getUsdToFiatRate(spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat())
                    ).map(tuple -> {
                        try {
                            JsonNode priceNode = objectMapper.readTree(tuple.getT1());
                            BigDecimal priceUSD = new BigDecimal(priceNode.get("price").asText());
                            BigDecimal conversionRate = tuple.getT2();
                            
                            BigDecimal priceInFiat = priceUSD.multiply(conversionRate).setScale(2, RoundingMode.HALF_UP);
                            BigDecimal valueInFiat = amount.multiply(priceInFiat).setScale(2, RoundingMode.HALF_UP);
                            
                            return new AbstractMap.SimpleEntry<>(valueInFiat, 
                                String.format("• %.6f %s (%.2f %s)\n", 
                                    amount, cryptoCode, valueInFiat, 
                                    spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat().getCode()));
                        } catch (Exception e) {
                            return new AbstractMap.SimpleEntry<>(BigDecimal.ZERO, "");
                        }
                    });
                })
                .collectList()
                .map(portfolioEntries -> {
                    BigDecimal totalValue = BigDecimal.ZERO;
                    for (Map.Entry<BigDecimal, String> entry : portfolioEntries) {
                        totalValue = totalValue.add(entry.getKey());
                        result.append(entry.getValue());
                    }
                    
                    result.append(String.format("\n💼 Итого: %.2f %s", 
                        totalValue, spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat().getCode()));
                    
                    return result.toString();
                });
        }).flatMap(mono -> mono);
    }

    private String formatTimeSinceUpdate(long timestamp) {
        if (timestamp <= 0) {
            return "нет";
        }
        
        long currentTimestamp = System.currentTimeMillis() / 1000;
        long diffSeconds = currentTimestamp - timestamp;
        
        long days = diffSeconds / (24 * 3600);
        long hours = (diffSeconds % (24 * 3600)) / 3600;
        long minutes = (diffSeconds % 3600) / 60;
        long seconds = diffSeconds % 60;
        
        StringBuilder timeBuilder = new StringBuilder();
        if (days > 0) timeBuilder.append(days).append(" дн. ");
        if (hours > 0) timeBuilder.append(hours).append(" ч. ");
        if (minutes > 0) timeBuilder.append(minutes).append(" мин. ");
        if (seconds > 0 || timeBuilder.length() == 0) timeBuilder.append(seconds).append(" сек.");
        
        return timeBuilder.toString();
    }

    /**
     * Получает информацию о ценах активов в портфеле.
     * Включает текущие цены и динамику изменений.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> Информация о ценах активов
     */
    public Mono<String> getAssetsPrice(String chatId) {
        return Mono.just(portfolioService.getPortfoliosByChatId(chatId))
            .map(portfolios -> portfolios.stream()
                .filter(portfolio -> portfolio.getCryptoCurrency() != null)
                .collect(Collectors.toList()))
            .flatMap(portfolios -> {
                if (portfolios.isEmpty()) {
                    return Mono.just("❌ У вас нет активов в портфеле. Используйте команду /add для добавления криптовалюты.");
                }

                return Mono.zip(
                    Flux.fromIterable(portfolios)
                        .flatMap(portfolio -> {
                            Currency.Crypto crypto = portfolio.getCryptoCurrency();
                            BigDecimal previousPriceUSD = portfolio.getLastCryptoPrice();
                            long previousTimestamp = portfolio.getLastCryptoPriceTimestamp();
                            
                            // Проверяем наличие предыдущей цены
                            if (previousPriceUSD == null || previousPriceUSD.compareTo(BigDecimal.ZERO) <= 0) {
                                log.warn("No previous price found for {} in portfolio", crypto.getCode());
                            }
                            
                            return Mono.zip(
                                priceFetcher.getCurrentPrice(crypto),
                                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
                            ).flatMap(tuple -> {
                                try {
                                    JsonNode node = objectMapper.readTree(tuple.getT1());
                                    BigDecimal currentPriceUSD = new BigDecimal(node.get("price").asText());
                                    BigDecimal conversionRate = tuple.getT2();
                                    
                                    // Проверяем корректность текущей цены
                                    if (currentPriceUSD.compareTo(BigDecimal.ZERO) <= 0) {
                                        log.error("Invalid current price for {}: {}", crypto.getCode(), currentPriceUSD);
                                        return Mono.error(new RuntimeException("Invalid price received from API"));
                                    }
                                    
                                    // Конвертируем цены в текущую фиатную валюту
                                    BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                                        .setScale(2, RoundingMode.HALF_UP);
                                    BigDecimal previousPrice = previousPriceUSD != null && previousPriceUSD.compareTo(BigDecimal.ZERO) > 0
                                        ? previousPriceUSD.multiply(conversionRate).setScale(2, RoundingMode.HALF_UP)
                                        : currentPrice;
                                    
                                    // Рассчитываем изменение цены
                                    BigDecimal priceChange = currentPrice.subtract(previousPrice);
                                    BigDecimal priceChangePercent = previousPrice.compareTo(BigDecimal.ZERO) > 0
                                        ? priceChange.divide(previousPrice, 4, RoundingMode.HALF_UP)
                                            .multiply(new BigDecimal("100"))
                                            .setScale(2, RoundingMode.HALF_UP)
                                        : BigDecimal.ZERO;
                                    
                                    // Создаем объект с информацией о ценах
                                    PortfolioPriceInfo priceInfo = new PortfolioPriceInfo(
                                        crypto,
                                        portfolio.getCount(),
                                        currentPrice,
                                        previousPrice,
                                        previousTimestamp
                                    );
                                    
                                    // Обновляем последнюю цену в USD после всех расчетов
                                    portfolio.setLastCryptoPrice(currentPriceUSD);
                                    portfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                                    portfolioService.save(portfolio);
                                    
                                    return Mono.just(priceInfo);
                                } catch (Exception e) {
                                    log.error("Error processing price data for {}: {}", crypto.getCode(), e.getMessage());
                                    return Mono.error(e);
                                }
                            });
                        })
                        .collectList(),
                    currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
                ).map(tuple -> {
                    List<PortfolioPriceInfo> priceInfos = tuple.getT1();
                    BigDecimal conversionRate = tuple.getT2();
                    String fiatCode = Currency.Fiat.getCurrentFiat().getCode();
                    
                    // Рассчитываем общую стоимость портфеля
                    BigDecimal totalCurrentValue = priceInfos.stream()
                        .map(info -> info.currentPrice().multiply(info.count()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    BigDecimal totalPreviousValue = priceInfos.stream()
                        .map(info -> info.previousPrice().multiply(info.count()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    // Рассчитываем общее изменение
                    BigDecimal totalChange = totalCurrentValue.subtract(totalPreviousValue);
                    BigDecimal totalChangePercent = totalPreviousValue.compareTo(BigDecimal.ZERO) > 0
                        ? totalChange.divide(totalPreviousValue, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                    
                    // Форматируем сообщение
                    StringBuilder message = new StringBuilder();
                    message.append(String.format("💼 Цена вашего портфеля: %.2f %s\n", 
                        totalCurrentValue, fiatCode));
                    message.append(String.format("📊 Предыдущая цена портфеля: %.2f %s\n", 
                        totalPreviousValue, fiatCode));
                    
                    String changeSign = totalChangePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                    message.append(String.format("📈 Динамика изменения: %s%.2f%% (%s%.2f %s)\n\n", 
                        changeSign, totalChangePercent,
                        totalChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                        totalChange, fiatCode));
                    
                    message.append("Активы в портфеле:\n");
                    
                    for (PortfolioPriceInfo info : priceInfos) {
                        BigDecimal assetCurrentValue = info.currentPrice().multiply(info.count())
                            .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal assetPreviousValue = info.previousPrice().multiply(info.count())
                            .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal assetChange = assetCurrentValue.subtract(assetPreviousValue);
                        BigDecimal assetChangePercent = assetPreviousValue.compareTo(BigDecimal.ZERO) > 0
                            ? assetChange.divide(assetPreviousValue, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                        
                        String assetChangeSign = assetChangePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        
                        message.append(String.format("• %.6f %s (%.2f %s): 📈 %s%.2f%% (%s%.2f %s)\n",
                            info.count().setScale(6, RoundingMode.FLOOR), info.crypto().getCode(),
                            assetCurrentValue, fiatCode,
                            assetChangeSign, assetChangePercent,
                            assetChange.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "",
                            assetChange, fiatCode));
                        
                        message.append(String.format("  Предыдущая цена: %.2f %s\n",
                            info.previousPrice().multiply(info.count()), fiatCode));
                        
                        if (info.previousTimestamp() > 0) {
                            long currentTime = System.currentTimeMillis() / 1000;
                            long duration = currentTime - info.previousTimestamp();
                            long minutes = duration / 60;
                            long seconds = duration % 60;
                            message.append(String.format("  С последнего обновления: %d мин. %d сек.\n",
                                minutes, seconds));
                        }
                        message.append("\n");
                    }
                    
                    // Добавляем текущее время
                    java.time.ZonedDateTime now = java.time.ZonedDateTime.now(
                        java.time.ZoneId.of("Europe/Moscow"));
                    message.append(String.format("⏰ Текущее время: %s\n",
                        now.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))));
                    
                    return message.toString();
                });
            })
            .onErrorResume(e -> {
                log.error("Error getting assets price", e);
                return Mono.just("❌ Ошибка при получении цен активов: " + e.getMessage());
            });
    }

    /**
     * Получает информацию о стоимости портфеля.
     * Включает общую стоимость и динамику изменений.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> Информация о стоимости портфеля
     */
    public Mono<String> getPortfolioPriceInfo(String chatId) {
        return Mono.fromCallable(() -> portfolioService.getPortfoliosByChatId(chatId))
            .flatMap(portfolios -> {
                if (portfolios.isEmpty()) {
                    return Mono.just("❌ У вас нет активов в портфеле. Используйте команду /add для добавления криптовалюты.");
                }

                return Flux.fromIterable(portfolios)
                    .filter(portfolio -> portfolio.getCryptoCurrency() != null && portfolio.getCount().compareTo(BigDecimal.ZERO) > 0)
                    .flatMap(portfolio -> 
                        Mono.zip(
                            priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency()),
                            currencyConverter.getUsdToFiatRate(spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat())
                                .<BigDecimal>onErrorResume(e -> {
                                    // Если текущая валюта USD, возвращаем 1.0
                                    if (spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat() == spbstu.mcs.telegramBot.model.Currency.Fiat.USD) {
                                        return Mono.just(BigDecimal.ONE);
                                    }
                                    return Mono.error(e);
                                })
                        ).map(tuple -> {
                            try {
                                JsonNode priceNode = objectMapper.readTree(tuple.getT1());
                                BigDecimal currentPriceUSD = new BigDecimal(priceNode.get("price").asText());
                                BigDecimal conversionRate = tuple.getT2();
                                
                                // Проверяем корректность текущей цены
                                if (currentPriceUSD.compareTo(BigDecimal.ZERO) <= 0) {
                                    log.error("Invalid current price for {}: {}", 
                                        portfolio.getCryptoCurrency().getCode(), currentPriceUSD);
                                    throw new RuntimeException("Invalid price received from API");
                                }
                                
                                // Сохраняем предыдущую цену перед обновлением
                                BigDecimal previousPrice = portfolio.getLastCryptoPrice();
                                long previousTimestamp = portfolio.getLastCryptoPriceTimestamp();
                                
                                // Проверяем наличие предыдущей цены
                                if (previousPrice == null || previousPrice.compareTo(BigDecimal.ZERO) <= 0) {
                                    log.warn("No previous price found for {} in portfolio", 
                                        portfolio.getCryptoCurrency().getCode());
                                }
                                
                                // Рассчитываем текущую стоимость
                                BigDecimal currentPriceInFiat = currentPriceUSD.multiply(conversionRate);
                                BigDecimal currentValue = portfolio.getCount().multiply(currentPriceInFiat);
                                
                                // Рассчитываем стоимость по предыдущей цене
                                BigDecimal previousValue = BigDecimal.ZERO;
                                if (previousPrice != null && previousPrice.compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal previousPriceInFiat = previousPrice.multiply(conversionRate);
                                    previousValue = portfolio.getCount().multiply(previousPriceInFiat);
                                }
                                
                                // Обновляем цену в портфеле после всех расчетов
                                portfolio.setLastCryptoPrice(currentPriceUSD);
                                portfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                                portfolioService.save(portfolio);
                                
                                return new AbstractMap.SimpleEntry<BigDecimal, AbstractMap.SimpleEntry<BigDecimal, Long>>(
                                    currentValue, 
                                    new AbstractMap.SimpleEntry<>(previousValue, previousTimestamp)
                                );
                            } catch (Exception e) {
                                log.error("Error processing price data for {}: {}", 
                                    portfolio.getCryptoCurrency().getCode(), e.getMessage());
                                return new AbstractMap.SimpleEntry<BigDecimal, AbstractMap.SimpleEntry<BigDecimal, Long>>(
                                    BigDecimal.ZERO,
                                    new AbstractMap.SimpleEntry<>(BigDecimal.ZERO, 0L)
                                );
                            }
                        })
                    )
                    .collectList()
                    .map(portfolioEntries -> {
                        StringBuilder result = new StringBuilder();
                        BigDecimal totalCurrentValue = BigDecimal.ZERO;
                        BigDecimal totalPreviousValue = BigDecimal.ZERO;
                        long latestTimestamp = 0;
                        
                        for (Map.Entry<BigDecimal, AbstractMap.SimpleEntry<BigDecimal, Long>> entry : portfolioEntries) {
                            totalCurrentValue = totalCurrentValue.add(entry.getKey());
                            totalPreviousValue = totalPreviousValue.add(entry.getValue().getKey());
                            latestTimestamp = Math.max(latestTimestamp, entry.getValue().getValue());
                        }
                        
                        // Рассчитываем общее изменение
                        BigDecimal changeValue = totalCurrentValue.subtract(totalPreviousValue);
                        BigDecimal changePercent = totalPreviousValue.compareTo(BigDecimal.ZERO) > 0
                            ? changeValue.divide(totalPreviousValue, 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"))
                                .setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                        
                        String fiatCode = spbstu.mcs.telegramBot.model.Currency.Fiat.getCurrentFiat().getCode();
                        String changeSign = changePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        String valueChangeSign = changeValue.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                        String changeEmoji = changePercent.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                        
                        // Форматируем текущее время
                        java.time.ZonedDateTime now = java.time.ZonedDateTime.now(
                            java.time.ZoneId.of("Europe/Moscow"));
                        String currentTime = now.format(
                            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
                        
                        // Форматируем время с последнего обновления
                        String timeSinceUpdate = formatTimeSinceUpdate(latestTimestamp);
                        
                        return String.format("💼 Цена вашего портфеля: %.2f %s\n" +
                                          "📊 Предыдущая цена портфеля: %.2f %s\n" +
                                          "%s Динамика изменения: %s%.2f%% (%s%.2f %s)\n\n" +
                                          "⏰ Текущее время: %s\n" +
                                          "🕒 С последнего обновления цены прошло: %s",
                                          totalCurrentValue.doubleValue(), fiatCode,
                                          totalPreviousValue.doubleValue(), fiatCode,
                                          changeEmoji, changeSign, 
                                          changePercent.doubleValue(),
                                          valueChangeSign, 
                                          changeValue.abs().doubleValue(), fiatCode,
                                          currentTime, timeSinceUpdate);
                    });
            });
    }

    /**
     * Удаляет конкретный актив из портфеля пользователя.
     *
     * @param chatId ID чата пользователя
     * @param crypto Криптовалюта для удаления
     * @return Mono<String> Результат операции
     */
    public Mono<String> deleteAsset(String chatId, Crypto crypto) {
        return Mono.fromCallable(() -> portfolioService.getPortfoliosByChatId(chatId))
            .flatMap(portfolios -> {
                if (portfolios.isEmpty()) {
                    return Mono.just("❌ У вас нет портфеля. Используйте команду /add для создания портфеля.");
                }

                Portfolio portfolio = portfolios.stream()
                    .filter(p -> p.getCryptoCurrency() != null && p.getCryptoCurrency().equals(crypto))
                    .findFirst()
                    .orElse(null);

                if (portfolio == null) {
                    return Mono.just(String.format("❌ В вашем портфеле нет криптовалюты %s", crypto.getCode()));
                }

                portfolioService.delete(portfolio);
                return Mono.just(String.format("✅ Криптовалюта %s успешно удалена из портфеля", crypto.getCode()));
            })
            .onErrorResume(e -> Mono.just("❌ Ошибка при удалении актива: " + e.getMessage()));
    }

    /**
     * Удаляет все активы из портфеля пользователя.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> Результат операции
     */
    public Mono<String> deleteAllAssets(String chatId) {
        return Mono.fromCallable(() -> portfolioService.getPortfoliosByChatId(chatId))
            .flatMap(portfolios -> {
                if (portfolios.isEmpty()) {
                    return Mono.just("❌ У вас нет портфеля. Используйте команду /add для создания портфеля.");
                }

                portfolios.forEach(portfolioService::delete);
                return Mono.just("✅ Все активы успешно удалены из портфеля");
            })
            .onErrorResume(e -> Mono.just("❌ Ошибка при удалении активов: " + e.getMessage()));
    }

    /**
     * Устанавливает текущий портфель.
     *
     * @param portfolio портфель для установки
     */
    public void setCurrentPortfolio(Portfolio portfolio) {
        this.currentPortfolio = portfolio;
    }

    /**
     * Возвращает текущий портфель.
     *
     * @return текущий портфель
     */
    public Portfolio getCurrentPortfolio() {
        return currentPortfolio;
    }
} 