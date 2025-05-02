package ru.spbstu.telematics.bitbotx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import reactor.util.retry.Retry;
import java.time.Duration;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Arrays;
import java.math.RoundingMode;
import java.util.ArrayList;
import ru.spbstu.telematics.bitbotx.model.Alert;
import ru.spbstu.telematics.bitbotx.model.AlertType;
import ru.spbstu.telematics.bitbotx.model.Currency;
import ru.spbstu.telematics.bitbotx.model.Currency.Crypto;
import ru.spbstu.telematics.bitbotx.model.Currency.Fiat;
import ru.spbstu.telematics.bitbotx.model.AlertVal;
import ru.spbstu.telematics.bitbotx.model.AlertPerc;
import ru.spbstu.telematics.bitbotx.model.AlertEMA;

/**
 * Сервис для управления ценовыми алертами.
 * Предоставляет функциональность для:
 * - Установки алертов
 * - Мониторинга цен и проверки срабатывания алертов
 */
@Slf4j
@Component
public class AlertsHandling {
    private final PriceFetcher priceFetcher;
    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    private final ConcurrentHashMap<Crypto, AlertVal> priceAlerts;
    private final ConcurrentHashMap<Crypto, AlertPerc> percentAlerts;
    private final ConcurrentHashMap<Crypto, AlertEMA> emaAlerts;
    private static final int EMA_PERIOD = 20; // Период для EMA
    
    public AlertsHandling(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        this.priceFetcher = priceFetcher;
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceAlerts = new ConcurrentHashMap<>();
        this.percentAlerts = new ConcurrentHashMap<>();
        this.emaAlerts = new ConcurrentHashMap<>();
    }
    
    /**
     * Устанавливает алерт на основе минимального и максимального значений цены.
     * Если для данной криптовалюты уже существует алерт по ценам, он будет перезаписан.
     *
     * @param crypto Криптовалюта
     * @param minPrice Минимальная цена для срабатывания алерта
     * @param maxPrice Максимальная цена для срабатывания алерта
     * @return Mono<String> JSON-строка с текущей ценой и timestamp установки
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_MANAGER')")
    public Mono<String> setAlertVal(Crypto crypto, BigDecimal minPrice, BigDecimal maxPrice) {
        String symbol = crypto.getCode() + "-" + Fiat.getCurrentFiat().getCode();
        Fiat currentFiat = Fiat.getCurrentFiat();
        
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> {
                    // Конвертируем границы алерта в USD
                    BigDecimal minPriceUsd = minPrice.divide(exchangeRate, 2, RoundingMode.HALF_UP);
                    BigDecimal maxPriceUsd = maxPrice.divide(exchangeRate, 2, RoundingMode.HALF_UP);
                    
                    return priceFetcher.getCurrentPrice(crypto)
                            .flatMap(currentJson -> {
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(currentJson);
                                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                                    long startTimestamp = jsonNode.get("timestamp").asLong();
                                    
                                    AlertVal alert = new AlertVal(symbol, currentPriceUsd, startTimestamp, minPriceUsd, maxPriceUsd);
                                    AlertVal existingAlert = priceAlerts.put(crypto, alert);
                    if (existingAlert != null) {
                                        log.debug("Overwritten existing price alert for {}", symbol);
                                    }
                                    
                                    // Конвертируем цены для отображения в текущую фиатную валюту
                                    BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                    
                                    log.info("Set price alert for {}: min={}, max={}, current={}", 
                                            symbol, minPrice, maxPrice, currentPriceFiat);
                                    
                                    return Mono.fromCallable(() -> {
                                        ObjectNode result = objectMapper.createObjectNode();
                                        result.put("symbol", symbol);
                                        result.put("startPrice", currentPriceFiat.toString());
                                        result.put("startTimestamp", startTimestamp);
                                        String json = objectMapper.writeValueAsString(result);
                                        log.debug("Returning JSON for {} price alert: startPrice={}, startTimestamp={}", 
                                                symbol, currentPriceFiat, startTimestamp);
                                        return json;
                                    });
                                } catch (Exception e) {
                                    return Mono.error(new RuntimeException("Error processing price alert: " + e.getMessage()));
                                }
                            });
                });
    }

    /**
     * Устанавливает алерт на основе процентного отклонения от текущей цены.
     * Если для данной криптовалюты уже существует алерт по процентам, он будет перезаписан.
     *
     * @param crypto Криптовалюта
     * @param downPercent Процент отклонения вниз от текущей цены
     * @param upPercent Процент отклонения вверх от текущей цены
     * @return Mono<String> JSON-строка с текущей ценой и timestamp установки
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_MANAGER')")
    public Mono<String> setAlertPerc(Crypto crypto, BigDecimal downPercent, BigDecimal upPercent) {
        String symbol = crypto.getCode() + "-" + Fiat.getCurrentFiat().getCode();
        Fiat currentFiat = Fiat.getCurrentFiat();
        
        return currencyConverter.getUsdToFiatRate(currentFiat)
                .flatMap(exchangeRate -> 
                    priceFetcher.getCurrentPrice(crypto)
                            .flatMap(currentJson -> {
                                try {
                                    JsonNode jsonNode = objectMapper.readTree(currentJson);
                                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                                    long startTimestamp = jsonNode.get("timestamp").asLong();
                                    
                                    AlertPerc alert = new AlertPerc(symbol, currentPriceUsd, startTimestamp, downPercent, upPercent);
                                    AlertPerc existingAlert = percentAlerts.put(crypto, alert);
                    if (existingAlert != null) {
                                        log.debug("Overwritten existing percent alert for {}", symbol);
                                    }
                                    
                                    // Конвертируем цены для отображения в текущую фиатную валюту
                                    BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                    BigDecimal minPriceFiat = alert.minPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                    BigDecimal maxPriceFiat = alert.maxPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                                    
                                    log.info("Set percent alert for {}: down={}%, up={}%, current={}", 
                                            symbol, downPercent, upPercent, currentPriceFiat);
                                    
                                    return Mono.fromCallable(() -> {
                                        ObjectNode result = objectMapper.createObjectNode();
                                        result.put("symbol", symbol);
                                        result.put("startPrice", currentPriceFiat.toString());
                                        result.put("upperBoundary", maxPriceFiat.toString());
                                        result.put("lowerBoundary", minPriceFiat.toString());
                                        result.put("startTimestamp", startTimestamp);
                                        String json = objectMapper.writeValueAsString(result);
                                        log.debug("Returning JSON for {} percent alert: startPrice={}, upperBoundary={}, lowerBoundary={}, startTimestamp={}", 
                                                symbol, currentPriceFiat, maxPriceFiat, minPriceFiat, startTimestamp);
                                        return json;
                                    });
                                } catch (Exception e) {
                                    return Mono.error(new RuntimeException("Error processing percent alert: " + e.getMessage()));
                                }
                            })
                );
    }
    
    /**
     * Устанавливает алерт на основе EMA (Exponential Moving Average).
     * При установке вычисляет начальное SMA за 3 недели и сохраняет его как EMA.
     * Затем каждые 5 минут обновляет EMA по формуле.
     * Использует параллельные запросы для ускорения получения исторических данных.
     *
     * @param crypto Криптовалюта
     * @return Mono<String> JSON-строка с текущей ценой и timestamp установки
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_MANAGER')")
    public Mono<String> setAlertEMA(Crypto crypto) {
        String symbol = crypto.getCode() + "-" + Fiat.getCurrentFiat().getCode();
        Fiat currentFiat = Fiat.getCurrentFiat();
        
        // Получаем курс обмена и текущую цену одновременно
        Mono<BigDecimal> exchangeRateMono = currencyConverter.getUsdToFiatRate(currentFiat);
        Mono<String> currentPriceMono = priceFetcher.getCurrentPrice(crypto);
        
        return Mono.zip(exchangeRateMono, currentPriceMono)
            .flatMap(tuple -> {
                BigDecimal exchangeRate = tuple.getT1();
                String currentPriceJson = tuple.getT2();
                
                try {
                    // Парсим текущую цену
                    JsonNode jsonNode = objectMapper.readTree(currentPriceJson);
                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                    long startTimestamp = jsonNode.get("timestamp").asLong();
                    
                    // Генерируем временные метки для исторических запросов
                    long[] timestamps = new long[EMA_PERIOD];
                    for (int i = 0; i < EMA_PERIOD; i++) {
                        timestamps[i] = startTimestamp - (i * 24 * 60 * 60);
                    }
                    
                    // Создаем список запросов для исторических цен
                    List<Mono<BigDecimal>> priceRequests = new ArrayList<>();
                    for (long timestamp : timestamps) {
                        Mono<BigDecimal> priceMono = priceFetcher.getSymbolPriceByTime(crypto, timestamp)
                            .flatMap(this::parsePrice)
                            .retryWhen(Retry.fixedDelay(2, Duration.ofMillis(10))
                                .doBeforeRetry(retrySignal -> {
                                    Duration delay = retrySignal.totalRetries() == 1 ? 
                                        Duration.ofMillis(20) : Duration.ofMillis(10);
                                    try {
                                        Thread.sleep(delay.toMillis());
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    log.info("Retrying price request for timestamp {} (attempt: {})", 
                                        timestamp, retrySignal.totalRetries());
                                }))
                            .onErrorResume(e -> {
                                log.error("Error getting price for timestamp {} after retries: {}", timestamp, e.getMessage());
                                return Mono.just(BigDecimal.ZERO);
                            });
                        priceRequests.add(priceMono);
                    }
                    
                    // Выполняем запросы параллельно
                    return Flux.merge(priceRequests)
                        .collectList()
                        .map(prices -> {
                            // Рассчитываем SMA
                            BigDecimal sum = prices.stream()
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal sma = sum.divide(new BigDecimal(prices.size()), 2, RoundingMode.HALF_UP);
                            boolean isUpper = sma.compareTo(currentPriceUsd) > 0;
                            
                            // Конвертируем цены
                            BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate)
                                .setScale(2, RoundingMode.HALF_UP);
                            BigDecimal smaFiat = sma.multiply(exchangeRate)
                                .setScale(2, RoundingMode.HALF_UP);
                            
                            log.info("For {}: Initial SMA = {}, current price = {}, SMA is {} price", 
                                symbol, smaFiat, currentPriceFiat, isUpper ? "above" : "below");
                            
                            // Создаем и сохраняем алерт
                            final AlertEMA alert = new AlertEMA(symbol, currentPriceUsd, startTimestamp, sma);
                            alert.setIsUpper(isUpper);
                            AlertEMA existingAlert = emaAlerts.put(crypto, alert);
                            if (existingAlert != null) {
                                log.debug("Overwritten existing EMA alert for {}", symbol);
                            }
                            log.info("Set EMA alert for {}. Initial value: {}", symbol, smaFiat);
                            
                            // Формируем ответ
                            ObjectNode result = objectMapper.createObjectNode();
                            result.put("symbol", symbol);
                            result.put("startPrice", currentPriceFiat.toString());
                            result.put("startEMA", smaFiat.toString());
                            result.put("startTimestamp", startTimestamp);
                            
                            try {
                                return objectMapper.writeValueAsString(result);
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException("Error creating JSON response", e);
                            }
                        });
                } catch (Exception e) {
                    return Mono.error(new RuntimeException("Error processing EMA alert: " + e.getMessage(), e));
                }
            });
    }
    
    @Scheduled(cron = "0 */5 * * * *") // Каждые 5 минут
    @Transactional
    @Async
    public void checkAlerts() {
        int totalAlerts = priceAlerts.size() + percentAlerts.size() + emaAlerts.size();
        log.info("Starting alerts check. Alert count: {}", totalAlerts);
        
        if (totalAlerts == 0) {
            log.debug("No alerts to check");
            return;
        }
        
        // Получаем курс обмена для отображения в логах
        Fiat currentFiat = Fiat.getCurrentFiat();
        BigDecimal exchangeRate = currencyConverter.getUsdToFiatRate(currentFiat).block();
        if (exchangeRate == null) {
            log.error("Failed to get exchange rate for {}", currentFiat.getCode());
            return;
        }
        
        // Checking price alerts
        priceAlerts.forEach((crypto, alert) -> {
            if (alert.isTriggered()) {
                log.debug("Price alert for {} already triggered at {}", 
                        alert.symbol(), alert.triggerTimestamp());
                return;
            }
            
            // Конвертируем границы в фиат для логирования
            BigDecimal minPriceFiat = alert.minPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal maxPriceFiat = alert.maxPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            
            log.debug("Checking price alert for {}. Range: {} - {} {}", 
                    alert.symbol(), minPriceFiat, maxPriceFiat, currentFiat.getCode());
            
            priceFetcher.getCurrentPrice(crypto)
                .flatMap(this::parsePrice)
                .doOnError(e -> log.error("Error checking alert for {}: {}", alert.symbol(), e.getMessage()))
                .subscribe(price -> {
                    // Цена для сравнения в USD
                    boolean triggered = alert.checkTrigger(price);
                    
                    // Конвертируем цену в фиат для отображения
                    BigDecimal priceFiat = price.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    if (triggered) {
                        log.warn("Alert triggered: {} (range: {} - {} {}, price: {} {})", 
                                alert.getDescription(),
                                minPriceFiat, maxPriceFiat, currentFiat.getCode(),
                                priceFiat, currentFiat.getCode());
                        // Обновляем алерт, устанавливая triggered = true и сохраняя timestamp
                        AlertVal triggeredAlert = alert.withTrigger();
                        priceAlerts.put(crypto, triggeredAlert);
                    } else {
                        log.warn("Alert not triggered: {} (range: {} - {} {}, price: {} {})", 
                                alert.getDescription(),
                                minPriceFiat, maxPriceFiat, currentFiat.getCode(),
                                priceFiat, currentFiat.getCode());
                    }
                });
        });
        
        // Checking percent alerts
        percentAlerts.forEach((crypto, alert) -> {
            if (alert.isTriggered()) {
                log.debug("Percent alert for {} already triggered at {}", 
                        alert.symbol(), alert.triggerTimestamp());
                return;
            }
            
            // Конвертируем границы в фиат для логирования
            BigDecimal minPriceFiat = alert.minPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal maxPriceFiat = alert.maxPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            
            log.debug("Checking percent alert for {}. Range: {} - {} {}, limits: -{}%, +{}%", 
                    alert.symbol(), minPriceFiat, maxPriceFiat, currentFiat.getCode(),
                    alert.downPercent(), alert.upPercent());
            
            priceFetcher.getCurrentPrice(crypto)
                .flatMap(this::parsePrice)
                .doOnError(e -> log.error("Error checking alert for {}: {}", alert.symbol(), e.getMessage()))
                .subscribe(price -> {
                    // Цена для сравнения в USD
                    boolean triggered = alert.checkTrigger(price);
                    
                    // Конвертируем цену в фиат для отображения
                    BigDecimal priceFiat = price.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    if (triggered) {
                        log.warn("Alert triggered: {} (limits: -{}%, +{}%, price: {} {})", 
                                alert.getDescription(),
                                alert.downPercent(), alert.upPercent(),
                                priceFiat, currentFiat.getCode());
                        // Обновляем алерт, устанавливая triggered = true и сохраняя timestamp
                        AlertPerc triggeredAlert = alert.withTrigger();
                        percentAlerts.put(crypto, triggeredAlert);
                    } else {
                        log.warn("Alert not triggered: {} (limits: -{}%, +{}%, price: {} {})", 
                                alert.getDescription(),
                                alert.downPercent(), alert.upPercent(),
                                priceFiat, currentFiat.getCode());
                    }
                });
        });
        
        // Checking and updating EMA alerts
        emaAlerts.forEach((crypto, alert) -> {
            // Конвертируем EMA в фиат для логирования
            BigDecimal emaValueFiat = alert.getEmaValue().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            
            log.debug("Checking EMA alert for {}. Current EMA value: {} {}", 
                    alert.getSymbol(), emaValueFiat, currentFiat.getCode());
            
            priceFetcher.getCurrentPrice(crypto)
                .flatMap(this::parsePrice)
                .doOnError(e -> log.error("Error checking EMA alert for {}: {}", alert.getSymbol(), e.getMessage()))
                .subscribe(currentPrice -> {
                    currentPrice = currentPrice.setScale(2, RoundingMode.HALF_UP);
                    
                    BigDecimal multiplier = new BigDecimal("2").divide(
                            new BigDecimal(EMA_PERIOD + 1), 2, RoundingMode.HALF_UP);
                    BigDecimal newEMA = currentPrice.multiply(multiplier)
                            .add(alert.getEmaValue().multiply(BigDecimal.ONE.subtract(multiplier)))
                            .setScale(2, RoundingMode.HALF_UP);
                    
                    alert.setEmaValue(newEMA);
                    emaAlerts.put(crypto, alert);
                    
                    // Конвертируем цены в фиат для отображения
                    BigDecimal currentPriceFiat = currentPrice.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newEMAFiat = newEMA.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    String direction = alert.isUpper() ? "above" : "below";
                    if (alert.checkTrigger(currentPrice)) {
                        log.warn("Alert triggered: {} (EMA {} {}: {} {}, price: {} {})", 
                                alert.getDescription(),
                                direction, currentFiat.getCode(), newEMAFiat, currentFiat.getCode(), 
                                currentPriceFiat, currentFiat.getCode());
                    } else {
                        log.warn("Alert not triggered: {} (EMA {} {}: {} {}, price: {} {})", 
                                alert.getDescription(),
                                direction, currentFiat.getCode(), newEMAFiat, currentFiat.getCode(), 
                                currentPriceFiat, currentFiat.getCode());
                    }
                });
        });
        
        log.debug("Completed alerts check");
    }
    
    private Mono<BigDecimal> parsePrice(String priceJson) {
        return Mono.fromCallable(() -> {
            if (priceJson == null) {
                throw new IllegalArgumentException("API response cannot be null");
            }
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode node = objectMapper.readTree(priceJson);
            JsonNode priceNode = node.get("price");
            if (priceNode == null || priceNode.isNull()) {
                throw new IllegalArgumentException("'price' field is missing or null in API response");
            }
            return new BigDecimal(priceNode.asText());
        });
    }

    /**
     * Возвращает информацию о всех активных алертах в формате JSON.
     * Для каждого алерта возвращает:
     * - символ
     * - timestamp установки
     * - цену при установке
     * - дополнительные параметры в зависимости от типа алерта
     *
     * @return Mono<String> JSON-строка с информацией об алертах
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_READER')")
    public Mono<String> showAlerts() {
        return Mono.fromCallable(() -> {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode result = objectMapper.createObjectNode();
            
            Fiat currentFiat = Fiat.getCurrentFiat();
            BigDecimal exchangeRate = currencyConverter.getUsdToFiatRate(currentFiat).block();
            if (exchangeRate == null) {
                log.error("Failed to get exchange rate for {}", currentFiat.getCode());
                throw new RuntimeException("Failed to get exchange rate");
            }
            
            // Добавляем алерты по ценам
            ArrayNode priceAlertsArray = objectMapper.createArrayNode();
            priceAlerts.forEach((crypto, alert) -> {
                try {
                    // Получаем текущую цену и timestamp
                    String currentPriceJson = priceFetcher.getCurrentPrice(crypto).block();
                    JsonNode jsonNode = objectMapper.readTree(currentPriceJson);
                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                    long currentTimestamp = jsonNode.get("timestamp").asLong();
                    
                    // Конвертируем цены в текущую фиатную валюту
                    BigDecimal startPriceFiat = alert.startPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal minPriceFiat = alert.minPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal maxPriceFiat = alert.maxPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    String symbol = crypto.getCode() + "-" + currentFiat.getCode();
                    
                ObjectNode alertNode = objectMapper.createObjectNode();
                    alertNode.put("symbol", symbol);
                    alertNode.put("startPrice", startPriceFiat.toString());
                    alertNode.put("currentPrice", currentPriceFiat.toString());
                    alertNode.put("upperBoundary", maxPriceFiat.toString());
                    alertNode.put("lowerBoundary", minPriceFiat.toString());
                    alertNode.put("startTimestamp", alert.startTimestamp());
                    alertNode.put("currentTimestamp", currentTimestamp);
                    alertNode.put("isTriggered", alert.triggered());
                    if (alert.triggered()) {
                        alertNode.put("triggerTimestamp", alert.triggerTimestamp());
                    }
                priceAlertsArray.add(alertNode);
                } catch (Exception e) {
                    log.error("Error processing price alert for {}: {}", alert.symbol(), e.getMessage());
                }
            });
            result.set("priceAlerts", priceAlertsArray);
            
            // Добавляем алерты по процентам
            ArrayNode percentAlertsArray = objectMapper.createArrayNode();
            percentAlerts.forEach((crypto, alert) -> {
                try {
                    // Получаем текущую цену и timestamp
                    String currentPriceJson = priceFetcher.getCurrentPrice(crypto).block();
                    JsonNode jsonNode = objectMapper.readTree(currentPriceJson);
                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                    long currentTimestamp = jsonNode.get("timestamp").asLong();
                    
                    // Конвертируем цены в текущую фиатную валюту
                    BigDecimal startPriceFiat = alert.startPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal minPriceFiat = alert.minPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal maxPriceFiat = alert.maxPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    String symbol = crypto.getCode() + "-" + currentFiat.getCode();
                    
                ObjectNode alertNode = objectMapper.createObjectNode();
                    alertNode.put("symbol", symbol);
                    alertNode.put("startPrice", startPriceFiat.toString());
                    alertNode.put("currentPrice", currentPriceFiat.toString());
                    alertNode.put("upperBoundary", maxPriceFiat.toString());
                    alertNode.put("lowerBoundary", minPriceFiat.toString());
                    alertNode.put("startTimestamp", alert.startTimestamp());
                    alertNode.put("currentTimestamp", currentTimestamp);
                    alertNode.put("isTriggered", alert.triggered());
                    if (alert.triggered()) {
                        alertNode.put("triggerTimestamp", alert.triggerTimestamp());
                    }
                    alertNode.put("downPercent", alert.downPercent().toString());
                    alertNode.put("upPercent", alert.upPercent().toString());
                percentAlertsArray.add(alertNode);
                } catch (Exception e) {
                    log.error("Error processing percent alert for {}: {}", alert.symbol(), e.getMessage());
                }
            });
            result.set("percentAlerts", percentAlertsArray);
            
            // Добавляем EMA алерты
            ArrayNode emaAlertsArray = objectMapper.createArrayNode();
            emaAlerts.forEach((crypto, alert) -> {
                try {
                    // Получаем текущую цену и timestamp
                    String currentPriceJson = priceFetcher.getCurrentPrice(crypto).block();
                    JsonNode jsonNode = objectMapper.readTree(currentPriceJson);
                    BigDecimal currentPriceUsd = new BigDecimal(jsonNode.get("price").asText());
                    long currentTimestamp = jsonNode.get("timestamp").asLong();
                    
                    // Конвертируем цены в текущую фиатную валюту
                    BigDecimal startPriceFiat = alert.getStartPrice().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal currentPriceFiat = currentPriceUsd.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal startEMAFiat = alert.getStartEMA().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal currentEMAFiat = alert.getEmaValue().multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
                    
                    String symbol = crypto.getCode() + "-" + currentFiat.getCode();
                    
                ObjectNode alertNode = objectMapper.createObjectNode();
                    alertNode.put("symbol", symbol);
                    alertNode.put("startPrice", startPriceFiat != null ? startPriceFiat.toString() : "null");
                    alertNode.put("currentPrice", currentPriceFiat.toString());
                    alertNode.put("startEMA", startEMAFiat.toString());
                    alertNode.put("currentEMA", currentEMAFiat.toString());
                    alertNode.put("startTimestamp", alert.getStartTimestamp());
                emaAlertsArray.add(alertNode);
                } catch (Exception e) {
                    log.error("Error processing EMA alert for {}: {}", alert.getSymbol(), e.getMessage());
                }
            });
            result.set("emaAlerts", emaAlertsArray);
            
            String json = objectMapper.writeValueAsString(result);
            log.debug("Active alerts: {}", json);
            return json;
        });
    }

    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_MANAGER')")
    public Mono<String> deleteAlert(AlertType type, Crypto crypto) {
        return Mono.fromCallable(() -> {
            boolean alertExists = false;
            
            switch (type) {
                case PRICE -> alertExists = priceAlerts.remove(crypto) != null;
                case PERCENT -> alertExists = percentAlerts.remove(crypto) != null;
                case EMA -> alertExists = emaAlerts.remove(crypto) != null;
            }
            
            String status = alertExists ? "success" : "not_found";
            log.info("Delete {} alert for {}: {}", type, crypto.getCode(), status);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", status);
            return objectMapper.writeValueAsString(result);
        });
    }

    /**
     * Удаляет все активные алерты.
     * Требует роли ALERT_MANAGER.
     *
     * @return Mono<String> JSON-строка со статусом операции и количеством удаленных алертов
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('ALERT_MANAGER')")
    public Mono<String> deleteAllAlerts() {
        return Mono.fromCallable(() -> {
            int priceCount = priceAlerts.size();
            int percentCount = percentAlerts.size();
            int emaCount = emaAlerts.size();
            int totalCount = priceCount + percentCount + emaCount;
            
            priceAlerts.clear();
            percentAlerts.clear();
            emaAlerts.clear();
            
            String status = totalCount > 0 ? "success" : "not_found";
            
            log.info("Deleted all alerts: {} price alerts, {} percent alerts, {} EMA alerts, status: {}", 
                    priceCount, percentCount, emaCount, status);
            
            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", status);
            return objectMapper.writeValueAsString(result);
        });
    }
} 