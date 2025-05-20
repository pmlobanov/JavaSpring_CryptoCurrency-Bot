package spbstu.mcs.telegramBot.cryptoApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Currency.Fiat;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.DB.services.UserService;

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
    private final UserService userService;
    private Portfolio currentPortfolio;
    
    @Autowired
    public CryptoPortfolioManager(ObjectMapper objectMapper,
                             CurrencyConverter currencyConverter,
                             PriceFetcher priceFetcher,
                             PortfolioService portfolioService,
                             UserService userService) {
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.portfolioService = portfolioService;
        this.userService = userService;
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
        return userService.getUserByChatId(currentPortfolio.getChatId())
            .flatMap(user -> {
                Fiat userFiat = Fiat.valueOf(user.getCurrentFiat());
                return currencyConverter.getUsdToFiatRate(userFiat)
                    .flatMap(exchangeRate -> 
                        priceFetcher.getCurrentPrice(crypto)
                            .flatMap(priceJson -> {
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(priceJson);
                                    BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                    long timestamp = jsonNode.get("timestamp").asLong();
                                    
                                    String displaySymbol = crypto.getCode() + "-" + userFiat.getCode();
                                    
                                    currentPortfolio = portfolioService.addCryptoToPortfolio(
                                        currentPortfolio.getId(), crypto, count);
                                    
                                    // Обновляем цену и время в базе данных
                                    currentPortfolio.setLastCryptoPrice(priceInUSDT);
                                    currentPortfolio.setLastCryptoPriceTimestamp(timestamp);
                                    portfolioService.save(currentPortfolio);
                                    
                                    log.info("Added {} {} to portfolio. Stored price in USDT: {}. Total count: {}", 
                                            count, crypto.getCode(), priceInUSDT, 
                                            currentPortfolio.getCount());
                                    
                                    BigDecimal displayPrice;
                                    if (userFiat != Fiat.USD) {
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
            });
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
        
        return userService.getUserByChatId(currentPortfolio.getChatId())
            .flatMap(user -> {
                Fiat userFiat = Fiat.valueOf(user.getCurrentFiat());
                return currencyConverter.getUsdToFiatRate(userFiat)
                    .flatMap(exchangeRate -> 
                        priceFetcher.getCurrentPrice(crypto)
                            .flatMap(priceJson -> Mono.fromCallable(() -> {
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(priceJson);
                                    BigDecimal priceInUSDT = new BigDecimal(jsonNode.get("price").asText());
                                    long timestamp = jsonNode.get("timestamp").asLong();
                                    
                                    currentPortfolio = portfolioService.removeCryptoFromPortfolio(
                                        currentPortfolio.getId(), crypto, count);
                                    
                                    // Обновляем цену и время в базе данных
                                    currentPortfolio.setLastCryptoPrice(priceInUSDT);
                                    currentPortfolio.setLastCryptoPriceTimestamp(timestamp);
                                    portfolioService.save(currentPortfolio);
                                    
                                    log.info("Removed {} {} from portfolio. Remaining: {}, New price: {} USDT", 
                                            count, crypto.getCode(), 
                                            currentPortfolio.getCount(), 
                                            priceInUSDT);
                                    
                                    String displaySymbol = crypto.getCode() + "-" + userFiat.getCode();
                                    
                                    BigDecimal displayPrice;
                                    if (userFiat != Fiat.USD) {
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
            });
    }
    
    /**
     * Получает текущую стоимость портфеля пользователя.
     * Включает информацию о каждом активе и общую стоимость.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> Информация о портфеле
     */
    public Mono<String> getPortfolioInfo(String chatId) {
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Fiat userFiat = Fiat.valueOf(user.getCurrentFiat());
                return Mono.fromCallable(() -> {
                    List<Portfolio> portfolios = portfolioService.getPortfoliosByChatId(chatId);
                    if (portfolios.isEmpty()) {
                        return Mono.just("Ваш портфель пока пуст. Используйте команду /add для добавления криптовалюты.");
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
                                currencyConverter.getUsdToFiatRate(userFiat)
                            ).map(tuple -> {
                                try {
                                    JsonNode priceNode = objectMapper.readTree(tuple.getT1());
                                    BigDecimal priceUSD = new BigDecimal(priceNode.get("price").asText());
                                    BigDecimal conversionRate = tuple.getT2();
                                    
                                    BigDecimal priceInFiat = priceUSD.multiply(conversionRate).setScale(2, RoundingMode.HALF_UP);
                                    BigDecimal valueInFiat = amount.multiply(priceInFiat).setScale(2, RoundingMode.HALF_UP);
                                    
                                    return Map.entry(valueInFiat, 
                                        String.format("• %.6f %s (%.2f %s)\n", 
                                            amount, cryptoCode, valueInFiat, userFiat.getCode()));
                                } catch (Exception e) {
                                    return Map.entry(BigDecimal.ZERO, "");
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
                                totalValue, userFiat.getCode()));
                            
                            return result.toString();
                        });
                }).flatMap(mono -> mono);
            });
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
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Fiat userFiat = Fiat.valueOf(user.getCurrentFiat());
                return Mono.just(portfolioService.getPortfoliosByChatId(chatId))
                    .map(portfolios -> portfolios.stream()
                        .filter(portfolio -> portfolio.getCryptoCurrency() != null)
                        .collect(Collectors.toList()))
                    .flatMap(portfolios -> {
                        if (portfolios.isEmpty()) {
                            return Mono.just("❌ У вас нет активов в портфеле");
                        }

                        return Mono.zip(
                            Flux.fromIterable(portfolios)
                                .flatMap(portfolio -> priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency())
                                    .<Map.Entry<Portfolio, PortfolioPriceInfo>>map(priceJson -> {
                                        try {
                                            JsonNode node = objectMapper.readTree(priceJson);
                                            BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                                            long timestamp = node.get("timestamp").asLong();
                                            
                                            return Map.entry(portfolio, new PortfolioPriceInfo(
                                                portfolio.getCryptoCurrency(),
                                                portfolio.getCount(),
                                                currentPrice,
                                                portfolio.getLastCryptoPrice(),
                                                portfolio.getLastCryptoPriceTimestamp()
                                            ));
                                        } catch (Exception e) {
                                            log.error("Error parsing price JSON", e);
                                            return Map.entry(portfolio, null);
                                        }
                                    }))
                                .collectList(),
                            currencyConverter.getUsdToFiatRate(userFiat)
                        ).map(tuple -> {
                            List<Map.Entry<Portfolio, PortfolioPriceInfo>> portfolioPrices = tuple.getT1();
                            BigDecimal exchangeRate = tuple.getT2();

                            StringBuilder response = new StringBuilder();
                            response.append("💼 Активы в портфеле:\n\n");

                            for (Map.Entry<Portfolio, PortfolioPriceInfo> entry : portfolioPrices) {
                                Portfolio portfolio = entry.getKey();
                                PortfolioPriceInfo priceInfo = entry.getValue();
                                if (priceInfo == null) continue;

                                BigDecimal currentPriceUSD = priceInfo.currentPrice();
                                BigDecimal currentPrice = currentPriceUSD.multiply(exchangeRate)
                                    .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal previousPrice = priceInfo.previousPrice() != null
                                    ? priceInfo.previousPrice().multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP)
                                    : currentPrice;

                                BigDecimal portfolioValue = currentPrice.multiply(priceInfo.count())
                                    .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal previousValue = previousPrice.multiply(priceInfo.count())
                                    .setScale(2, RoundingMode.HALF_UP);

                                BigDecimal change = portfolioValue.subtract(previousValue);
                                BigDecimal changePercent = previousValue.compareTo(BigDecimal.ZERO) != 0
                                    ? change.divide(previousValue, 6, RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal("100"))
                                    : BigDecimal.ZERO;

                                String changeSign = changePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                                String changeEmoji = changePercent.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                                
                                response.append(String.format("💰 %s (%s)\n", 
                                    portfolio.getCryptoCurrency().getCode(), 
                                    portfolio.getCryptoCurrency().getName()));
                                response.append(String.format("   • Сейчас: %.2f %s\n", 
                                    portfolioValue, userFiat.getCode()));
                                response.append(String.format("   • Было: %.2f %s\n", 
                                    previousValue, userFiat.getCode()));
                                response.append(String.format("   %s Изменение: %s%.4f%% (%.2f %s)\n\n",
                                    changeEmoji, changeSign, changePercent, change, userFiat.getCode()));
                            }

                            return response.toString();
                        });
                    });
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
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Fiat userFiat = Fiat.valueOf(user.getCurrentFiat());
                return Mono.just(portfolioService.getPortfoliosByChatId(chatId))
                    .map(portfolios -> portfolios.stream()
                        .filter(portfolio -> portfolio.getCryptoCurrency() != null)
                        .collect(Collectors.toList()))
                    .flatMap(portfolios -> {
                        if (portfolios.isEmpty()) {
                            return Mono.just("❌ У вас нет активов в портфеле");
                        }

                        return Mono.zip(
                            Flux.fromIterable(portfolios)
                                .flatMap(portfolio -> priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency())
                                    .<Map.Entry<Portfolio, PortfolioPriceInfo>>map(priceJson -> {
                                        try {
                                            JsonNode node = objectMapper.readTree(priceJson);
                                            BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                                            long timestamp = node.get("timestamp").asLong();
                                            
                                            return Map.entry(portfolio, new PortfolioPriceInfo(
                                                portfolio.getCryptoCurrency(),
                                                portfolio.getCount(),
                                                currentPrice,
                                                portfolio.getLastCryptoPrice(),
                                                portfolio.getLastCryptoPriceTimestamp()
                                            ));
                                        } catch (Exception e) {
                                            log.error("Error parsing price JSON", e);
                                            return Map.entry(portfolio, null);
                                        }
                                    }))
                                .collectList(),
                            currencyConverter.getUsdToFiatRate(userFiat)
                        ).map(tuple -> {
                            List<Map.Entry<Portfolio, PortfolioPriceInfo>> portfolioPrices = tuple.getT1();
                            BigDecimal exchangeRate = tuple.getT2();

                            BigDecimal totalCurrentPrice = BigDecimal.ZERO;
                            BigDecimal totalPreviousPrice = BigDecimal.ZERO;
                            long latestTimestamp = 0;

                            // Сначала вычисляем общую стоимость
                            for (Map.Entry<Portfolio, PortfolioPriceInfo> entry : portfolioPrices) {
                                Portfolio portfolio = entry.getKey();
                                PortfolioPriceInfo priceInfo = entry.getValue();
                                if (priceInfo == null) continue;

                                BigDecimal currentPriceUSD = priceInfo.currentPrice();
                                BigDecimal currentPrice = currentPriceUSD.multiply(exchangeRate)
                                    .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal previousPrice = priceInfo.previousPrice() != null
                                    ? priceInfo.previousPrice().multiply(exchangeRate)
                                        .setScale(2, RoundingMode.HALF_UP)
                                    : currentPrice;

                                BigDecimal portfolioValue = currentPrice.multiply(priceInfo.count())
                                    .setScale(2, RoundingMode.HALF_UP);
                                BigDecimal previousValue = previousPrice.multiply(priceInfo.count())
                                    .setScale(2, RoundingMode.HALF_UP);

                                totalCurrentPrice = totalCurrentPrice.add(portfolioValue);
                                totalPreviousPrice = totalPreviousPrice.add(previousValue);

                                // Обновляем время последнего обновления
                                if (priceInfo.previousTimestamp() > 0) {
                                    latestTimestamp = Math.max(latestTimestamp, priceInfo.previousTimestamp());
                                }
                            }

                            // Вычисляем общее изменение
                            BigDecimal totalChange = totalCurrentPrice.subtract(totalPreviousPrice);
                            BigDecimal totalChangePercent = totalPreviousPrice.compareTo(BigDecimal.ZERO) != 0
                                ? totalChange.divide(totalPreviousPrice, 6, RoundingMode.HALF_UP)
                                    .multiply(new BigDecimal("100"))
                                : BigDecimal.ZERO;

                            // Форматируем общий процент с 4 знаками после запятой и правильным знаком
                            String changeSign = totalChangePercent.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                            String changeEmoji = totalChangePercent.compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                            
                            StringBuilder response = new StringBuilder();
                            response.append(String.format("💰 Общая стоимость портфеля: %.2f %s\n", 
                                totalCurrentPrice, userFiat.getCode()));
                            response.append(String.format("📊 Было: %.2f %s\n",
                                totalPreviousPrice, userFiat.getCode()));
                            response.append(String.format("%s Изменение: %s%.4f%% (%.2f %s)\n",
                                changeEmoji, changeSign, totalChangePercent, totalChange, userFiat.getCode()));
                            response.append(String.format("⏰ Последнее обновление: %s",
                                formatTimeSinceUpdate(latestTimestamp)));

                            return response.toString();
                        });
                    });
            });
    }

    /**
     * Обновляет цены в базе данных для всех активов пользователя.
     *
     * @param chatId ID чата пользователя
     * @return Mono<Void>
     */
    public Mono<Void> updatePortfolioPrices(String chatId) {
        return Mono.just(portfolioService.getPortfoliosByChatId(chatId))
            .flatMap(portfolios -> Flux.fromIterable(portfolios)
                .filter(portfolio -> portfolio.getCryptoCurrency() != null)
                .flatMap(portfolio -> priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency())
                    .map(priceJson -> {
                        try {
                            JsonNode node = objectMapper.readTree(priceJson);
                            BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                            long timestamp = node.get("timestamp").asLong();
                            
                            portfolio.setLastCryptoPrice(currentPrice);
                            portfolio.setLastCryptoPriceTimestamp(timestamp);
                            portfolioService.save(portfolio);
                            
                            return portfolio;
                        } catch (Exception e) {
                            log.error("Error updating portfolio price", e);
                            return null;
                        }
                    }))
                .then());
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

                return Flux.fromIterable(portfolios)
                    .flatMap(portfolio -> portfolioService.delete(portfolio))
                    .then(Mono.just("✅ Все активы успешно удалены из портфеля"));
            })
            .onErrorResume(e -> {
                log.error("Error deleting all assets", e);
                return Mono.just("❌ Ошибка при удалении активов: " + e.getMessage());
            });
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