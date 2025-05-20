package spbstu.mcs.telegramBot.cryptoApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Currency.Fiat;
import spbstu.mcs.telegramBot.DB.services.UserService;

/**
 * Сервис для выполнения финансовых расчетов и мониторинга цен.
 * Предоставляет функциональность для:
 * - Сравнения валютных пар
 * - Получения истории цен
 */
@Service
@Slf4j
public class CryptoInformation {
    private static final int PRICE_SCALE = 8;
    private static final int PERCENT_SCALE = 2;
    
    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    private final PriceFetcher priceFetcher;
    private final UserService userService;

    @Autowired
    public CryptoInformation(ObjectMapper objectMapper,
                           CurrencyConverter currencyConverter,
                           PriceFetcher priceFetcher,
                           UserService userService) {
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.userService = userService;
    }
    
    /**
     * Сравнивает две криптовалюты за указанный период.
     * Возвращает JSON с текущими и историческими ценами, а также их соотношением.
     * Цены конвертируются в текущую фиатную валюту из базы данных.
     *
     * @param crypto1 Первая криптовалюта
     * @param crypto2 Вторая криптовалюта
     * @param period Период сравнения (например, "3d", "6h", "30m")
     * @param chatId ID чата пользователя
     * @return Mono<String> JSON-строка с результатами сравнения
     */
    public Mono<String> compareCurrencies(Crypto crypto1, Crypto crypto2, String period, String chatId) {
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Fiat currentFiat = Fiat.valueOf(user.getCurrentFiat());
                return currencyConverter.getUsdToFiatRate(currentFiat)
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
                                    String symbol1 = crypto1.getCode() + "-" + currentFiat.getCode();
                                    String symbol2 = crypto2.getCode() + "-" + currentFiat.getCode();
                                    
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
                                    
                                    // Format ratios based on their values
                                    String formattedCurrentRatio = formatRatio(currentRatio);
                                    String formattedHistoricRatio = formatRatio(historicRatio);
                                    
                                    ObjectNode ratioNode = objectMapper.createObjectNode();
                                    ratioNode.put("currentRatio", formattedCurrentRatio);
                                    ratioNode.put("historicRatio", formattedHistoricRatio);
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
            });
    }
    
    /**
     * Получает историю цен для пары криптовалюта-фиат за указанный период.
     * Использует значения криптовалюты и фиатной валюты из базы данных.
     * Цены конвертируются в текущую фиатную валюту.
     *
     * @param period Период для получения истории цен
     * @param chatId ID чата пользователя
     * @return Mono<String> с историей цен
     */
    public Mono<String> showPriceHistory(String period, String chatId) {
        var intervalPoints = calculateIntervalAndPoints(period);
        long intervalMillis = intervalPoints[0];
        int points = (int) intervalPoints[1];
        
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Crypto currentCrypto = Crypto.valueOf(user.getCurrentCrypto());
                Fiat currentFiat = Fiat.valueOf(user.getCurrentFiat());
                
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
                                
                                // Генерируем все точки, включая текущую и самую старую
                                Flux<Long> timestamps = Flux.range(0, points + 1)
                                        .map(i -> currentTimestamp - (i * intervalMillis))
                                .map(ts -> ts / 1000);
                        
                                // Получаем исторические цены с повторными попытками
                                return timestamps.flatMap(ts -> 
                                    priceFetcher.getSymbolPriceByTime(currentCrypto, ts)
                                        .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
                                            .doBeforeRetry(signal -> 
                                                log.warn("Retrying after error: {}", signal.failure().getMessage())
                                            )
                                        )
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
                                        // Сортируем точки по времени (от старых к новым)
                                        historyNodes.sort((a, b) -> 
                                            Long.compare(
                                                a.get("timestamp").asLong(),
                                                b.get("timestamp").asLong()
                                            )
                                        );
                                        
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
                                        
                                        // Находим первую и последнюю цены
                                        BigDecimal firstPrice = new BigDecimal(historyNodes.get(0).get("price").asText());
                                        BigDecimal lastPrice = new BigDecimal(historyNodes.get(historyNodes.size() - 1).get("price").asText());
                                        
                                        // Находим минимальную и максимальную цены
                                        BigDecimal minPrice = historyNodes.stream()
                                                .map(node -> new BigDecimal(node.get("price").asText()))
                                                .min(BigDecimal::compareTo)
                                                .orElse(BigDecimal.ZERO);
                                                
                                        BigDecimal maxPrice = historyNodes.stream()
                                                .map(node -> new BigDecimal(node.get("price").asText()))
                                                .max(BigDecimal::compareTo)
                                                .orElse(BigDecimal.ZERO);
                                        
                                        // Рассчитываем изменение в процентах
                                        BigDecimal percentChange = lastPrice.subtract(firstPrice)
                                                .divide(firstPrice, 4, RoundingMode.HALF_UP)
                                                .multiply(new BigDecimal("100"))
                                                .setScale(2, RoundingMode.HALF_UP);
                                        
                                        // Добавляем новые метрики
                                        result.put("firstPrice", firstPrice.toString());
                                        result.put("lastPrice", lastPrice.toString());
                                        result.put("percentChange", percentChange.toString());
                                        result.put("minPrice", minPrice.toString());
                                        result.put("maxPrice", maxPrice.toString());
                                        
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
            });
    }
    
    /**
     * Возвращает текущую цену для текущей пары криптовалюта-фиат.
     * Цена конвертируется в текущую фиатную валюту.
     *
     * @param chatId ID чата пользователя
     * @return Mono<String> JSON-строка с текущей ценой
     */
    public Mono<String> showCurrentPrice(String chatId) {
        return userService.getUserByChatId(chatId)
            .flatMap(user -> {
                Crypto currentCrypto = Crypto.valueOf(user.getCurrentCrypto());
                Fiat currentFiat = Fiat.valueOf(user.getCurrentFiat());
                
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
                                    
                                    // Формируем новый JSON с ценой в текущей фиатной валюте
                                    ObjectNode result = objectMapper.createObjectNode();
                                    result.put("symbol", currentCrypto.getCode() + "-" + currentFiat.getCode());
                                    result.put("price", priceInFiat.toString());
                                    result.put("timestamp", timestamp);
                                    
                                    String resultJson = objectMapper.writeValueAsString(result);
                                    log.info("Current price: {}", resultJson);
                                    return Mono.just(resultJson);
                                } catch (Exception e) {
                                    log.error("Error processing current price: {}", e.getMessage());
                                    return Mono.error(e);
                                }
                            })
                    );
            });
    }
    
    private Mono<JsonNode> getPriceData(Crypto crypto, String period) {
        return priceFetcher.getCurrentPrice(crypto)
                .flatMap(currentJson -> {
                    try {
                        JsonNode currentNode = objectMapper.readTree(currentJson);
                        long currentTimestamp = currentNode.get("timestamp").asLong() * 1000;
                        long historicTimestamp = currentTimestamp - periodToMillis(period);
                        
                        return priceFetcher.getSymbolPriceByTime(crypto, historicTimestamp)
                                .flatMap(historicJson -> {
                                    try {
                                        JsonNode historicNode = objectMapper.readTree(historicJson);
                                        
                                        ObjectNode result = objectMapper.createObjectNode();
                                        result.put("currentPrice", currentNode.get("price").asText());
                                        result.put("currentTimestamp", currentTimestamp);
                                        result.put("historicPrice", historicNode.get("price").asText());
                                        result.put("historicTimestamp", historicTimestamp);
                                        
                                        return Mono.just(result);
                                    } catch (Exception e) {
                                        log.error("Error processing historic price data: {}", e.getMessage());
                                        return Mono.error(e);
                                    }
                                });
                    } catch (Exception e) {
                        log.error("Error processing current price data: {}", e.getMessage());
                        return Mono.error(e);
                    }
                });
    }
    
    private String calculateChange(BigDecimal current, BigDecimal historic) {
        if (historic.compareTo(BigDecimal.ZERO) == 0) {
            return "0.00";
        }
        BigDecimal change = current.subtract(historic)
                .divide(historic, PERCENT_SCALE + 2, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
        return change.toString();
    }
    
    private String formatRatio(BigDecimal ratio) {
        if (ratio.compareTo(BigDecimal.ONE) >= 0) {
            // If ratio >= 1, show 2 decimal places
            return ratio.setScale(4, RoundingMode.HALF_UP).toString();
        } else {
            // If ratio < 1, show up to 2 significant digits after decimal point
            String ratioStr = ratio.toString();
            int decimalIndex = ratioStr.indexOf('.');
            if (decimalIndex == -1) return ratioStr;
            
            // Count leading zeros after decimal point
            int leadingZeros = 0;
            for (int i = decimalIndex + 1; i < ratioStr.length(); i++) {
                if (ratioStr.charAt(i) == '0') {
                    leadingZeros++;
                } else {
                    break;
                }
            }
            
            // Show 2 significant digits after the last leading zero
            int totalDigits = leadingZeros + 4;
            return ratio.setScale(totalDigits, RoundingMode.HALF_UP).toString();
        }
    }
    
    private long periodToMillis(String period) {
        String unit = period.substring(period.length() - 1);
        int value = Integer.parseInt(period.substring(0, period.length() - 1));
        
        return switch (unit) {
            case "m" -> value * 60L * 1000; // minutes
            case "h" -> value * 60L * 60L * 1000; // hours
            case "d" -> value * 24L * 60L * 60L * 1000; // days
            case "M" -> value * 30L * 24L * 60L * 60L * 1000; // months (30 days)
            default -> throw new IllegalArgumentException("Invalid period unit: " + unit);
        };
    }
    
    private long[] calculateIntervalAndPoints(String period) {
        String unit = period.substring(period.length() - 1);
        int value = Integer.parseInt(period.substring(0, period.length() - 1));
        
        return switch (unit) {
            case "h", "H" -> {
                // Для часов: интервал 1 час, количество точек = значение периода
                yield new long[] { 3600000, value };
            }
            case "d", "D" -> {
                // Для дней: интервал 1 день, количество точек = значение периода
                yield new long[] { 86400000, value };
            }
            case "M" -> {
                // Для месяцев: интервал 1 день, количество точек = количество дней в месяце
                yield new long[] { 86400000, value * 30 };
            }
            default -> throw new IllegalArgumentException("Invalid period unit. Use h/H (hours), d/D (days) or M (months)");
        };
    }
} 