package spbstu.mcs.telegramBot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Notification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис для управления ценовыми алертами.
 * Предоставляет функциональность для:
 * - Установки алертов
 * - Мониторинга цен и проверки срабатывания алертов
 */
@Service
public class AlertsHandling {
    private static final Logger log = LoggerFactory.getLogger(AlertsHandling.class);

    private final ObjectMapper objectMapper;
    private final CurrencyConverter currencyConverter;
    private final PriceFetcher priceFetcher;
    private final TelegramBotService telegramBotService;
    private final NotificationService notificationService;
   // private final NotificationRepository notificationRepository;
    private final UserService userService;

    @Autowired
    public AlertsHandling(ObjectMapper objectMapper,
                          CurrencyConverter currencyConverter,
                          PriceFetcher priceFetcher,
                          TelegramBotService telegramBotService,
                          NotificationService notificationService,
                          UserService userService) {
        log.info("Инициализация сервиса AlertsHandling...");
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.telegramBotService = telegramBotService;
        this.notificationService = notificationService;
        this.userService = userService;
        log.info("Сервис AlertsHandling успешно инициализирован");
    }

    /**
     * Устанавливает алерт на основе минимального и максимального значений цены.
     * Если для данной криптовалюты уже существует алерт по ценам, он будет перезаписан.
     *
     * @param cryptoCurrency Символ криптовалюты
     * @param maxPrice Максимальная цена для срабатывания алерта
     * @param minPrice Минимальная цена для срабатывания алерта
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<String> Сообщение о результате установки алерта
     */
    public Mono<String> setAlertVal(Crypto cryptoCurrency, BigDecimal maxPrice, BigDecimal minPrice, String chatId) {
        // Проверяем количество знаков после запятой
        if (maxPrice.scale() > 2 || minPrice.scale() > 2) {
            return Mono.just("❌ Цена может содержать не более 2 знаков после запятой");
        }

        // Проверяем, что цены не равны нулю
        if (maxPrice.compareTo(BigDecimal.ZERO) <= 0 || minPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.just("❌ Цены должны быть больше нуля");
        }

        // Проверяем максимальную границу цены (1 миллиард)
        BigDecimal MAX_PRICE = new BigDecimal("1000000000");
        if (maxPrice.compareTo(MAX_PRICE) > 0 || minPrice.compareTo(MAX_PRICE) > 0) {
            return Mono.just("❌ Цена не может превышать 1 000 000 000");
        }

        // Получаем все VALUE алерты пользователя для данной криптовалюты
        return notificationService.getAllUserAlerts(chatId)
                .filter(alert -> alert.getThresholdType() == Notification.ThresholdType.VALUE 
                        && alert.getCryptoCurrency() == cryptoCurrency)
                .next()
                .flatMap(existingAlert -> notificationService.delete(existingAlert))
                .then(userService.getUserByChatId(chatId))
                .flatMap(user -> Mono.zip(
                        priceFetcher.getCurrentPrice(cryptoCurrency),
                        currencyConverter.getUsdToFiatRate(user.getFiatCurrency())
                ).flatMap(tuple -> {
                    try {
                        String priceJson = tuple.getT1();
                        BigDecimal conversionRate = tuple.getT2();

                        JsonNode jsonNode = objectMapper.readTree(priceJson);
                        BigDecimal currentPriceUSD = new BigDecimal(jsonNode.get("price").asText());
                        long timestamp = jsonNode.get("timestamp").asLong();

                        // Конвертируем текущую цену в целевую валюту
                        BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                                .setScale(2, RoundingMode.HALF_UP);

                        // Используем введенные значения напрямую, так как они уже в целевой валюте
                        BigDecimal maxPriceInFiat = maxPrice;
                        BigDecimal minPriceInFiat = minPrice;

                        log.info("Установка VALUE алерта для {}: текущая цена={} USD ({} {})",
                                cryptoCurrency, currentPriceUSD, currentPrice, user.getFiatCurrency().getCode());

                        Notification notification = new Notification(
                                null,
                                cryptoCurrency,
                                user.getFiatCurrency(),
                                Notification.ThresholdType.VALUE,
                                true,
                                chatId,
                                maxPriceInFiat.doubleValue(),
                                minPriceInFiat.doubleValue(),
                                currentPrice.doubleValue()
                        );

                        notification.setStartTimestamp(timestamp);

                        return notificationService.createUserNotification(notification)
                                .then(Mono.just(String.format("✅ Алерт установлен для %s\n" +
                                                "💰 Текущая цена: %.2f %s\n" +
                                                "📈 Верхняя граница: %.2f %s\n" +
                                                "📉 Нижняя граница: %.2f %s",
                                        cryptoCurrency.getCode(),
                                        currentPrice, notification.getFiatCurrency().getCode(),
                                        maxPriceInFiat, notification.getFiatCurrency().getCode(),
                                        minPriceInFiat, notification.getFiatCurrency().getCode())));
                    } catch (Exception e) {
                        log.error("Ошибка при установке VALUE алерта: {}", e.getMessage());
                        return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
                    }
                }));
    }

    /**
     * Устанавливает алерт на основе процентного отклонения от текущей цены.
     * Если для данной криптовалюты уже существует алерт по процентам, он будет перезаписан.
     *
     * @param cryptoCurrency Символ криптовалюты
     * @param upPercent Процент роста от текущей цены
     * @param downPercent Процент падения от текущей цены
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<String> Сообщение о результате установки алерта
     */
    public Mono<String> setAlertPerc(Crypto cryptoCurrency, BigDecimal upPercent, BigDecimal downPercent, String chatId) {
        // Проверяем количество знаков после запятой
        if (upPercent.scale() > 2 || downPercent.scale() > 2) {
            return Mono.just("❌ Процент может содержать не более 2 знаков после запятой");
        }

        // Проверяем, что проценты положительные и не равны нулю
        if (upPercent.compareTo(BigDecimal.ZERO) <= 0 || downPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.just("❌ Проценты должны быть больше нуля");
        }

        // Проверяем максимальную границу процента (100%)
        BigDecimal MAX_PERCENT = new BigDecimal("100");
        if (upPercent.compareTo(MAX_PERCENT) > 0 || downPercent.compareTo(MAX_PERCENT) > 0) {
            return Mono.just("❌ Процент не может превышать 100%");
        }

        // Получаем все PERCENT алерты пользователя для данной криптовалюты
        return notificationService.getAllUserAlerts(chatId)
                .filter(alert -> alert.getThresholdType() == Notification.ThresholdType.PERCENT 
                        && alert.getCryptoCurrency() == cryptoCurrency)
                .next()
                .flatMap(existingAlert -> notificationService.delete(existingAlert))
                .then(userService.getUserByChatId(chatId))
                .flatMap(user -> Mono.zip(
                        priceFetcher.getCurrentPrice(cryptoCurrency),
                        currencyConverter.getUsdToFiatRate(user.getFiatCurrency())
                ).flatMap(tuple -> {
                    try {
                        String priceJson = tuple.getT1();
                        BigDecimal conversionRate = tuple.getT2();

                        JsonNode jsonNode = objectMapper.readTree(priceJson);
                        BigDecimal currentPriceUSD = new BigDecimal(jsonNode.get("price").asText());
                        long timestamp = jsonNode.get("timestamp").asLong();

                        // Конвертируем текущую цену в целевую валюту
                        BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                                .setScale(2, RoundingMode.HALF_UP);

                        // Рассчитываем границы на основе процентов
                        BigDecimal maxPriceInFiat = currentPrice.multiply(BigDecimal.ONE.add(upPercent.divide(new BigDecimal("100"))))
                                .setScale(2, RoundingMode.HALF_UP);
                        BigDecimal minPriceInFiat = currentPrice.multiply(BigDecimal.ONE.subtract(downPercent.divide(new BigDecimal("100"))))
                                .setScale(2, RoundingMode.HALF_UP);

                        log.info("Установка PERCENT алерта для {}: текущая цена={} USD ({} {})",
                                cryptoCurrency, currentPriceUSD, currentPrice, user.getFiatCurrency().getCode());

                        Notification notification = new Notification(
                                null,
                                cryptoCurrency,
                                user.getFiatCurrency(),
                                Notification.ThresholdType.PERCENT,
                                true,
                                chatId,
                                maxPriceInFiat.doubleValue(),
                                minPriceInFiat.doubleValue(),
                                currentPrice.doubleValue()
                        );

                        // Сохраняем проценты в уведомлении
                        notification.setUpPercent(upPercent.doubleValue());
                        notification.setDownPercent(downPercent.doubleValue());
                        notification.setStartTimestamp(timestamp);

                        return notificationService.createUserNotification(notification)
                                .then(Mono.just(String.format("✅ Алерт установлен для %s\n" +
                                                "💰 Текущая цена: %.2f %s\n" +
                                                "📈 Рост: +%.2f%%\n" +
                                                "📉 Падение: -%.2f%%\n" +
                                                "📊 Верхняя граница: %.2f %s\n" +
                                                "📊 Нижняя граница: %.2f %s",
                                        cryptoCurrency.getCode(),
                                        currentPrice, notification.getFiatCurrency().getCode(),
                                        upPercent, downPercent,
                                        maxPriceInFiat, notification.getFiatCurrency().getCode(),
                                        minPriceInFiat, notification.getFiatCurrency().getCode())));
                    } catch (Exception e) {
                        log.error("Ошибка при установке PERCENT алерта: {}", e.getMessage());
                        return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
                    }
                }));
    }

    /**
     * Устанавливает алерт на основе EMA (Exponential Moving Average).
     * При установке вычисляет начальное SMA за 3 недели и сохраняет его как EMA.
     * Затем каждые 5 минут обновляет EMA по формуле.
     * Использует параллельные запросы для ускорения получения исторических данных.
     *
     * @param cryptoCurrency Символ криптовалюты
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<String> JSON-строка с текущей ценой и timestamp установки
     */
    public Mono<String> setAlertEMA(Crypto cryptoCurrency, String chatId) {
        // Получаем все EMA алерты пользователя
        return notificationService.getAllUserAlerts(chatId)
                .filter(alert -> alert.getThresholdType() == Notification.ThresholdType.EMA 
                        && alert.getCryptoCurrency() == cryptoCurrency)
                .next()
                .flatMap(existingAlert -> notificationService.delete(existingAlert))
                .then(userService.getUserByChatId(chatId))
                .flatMap(user -> Mono.zip(
                        priceFetcher.getCurrentPrice(cryptoCurrency),
                        currencyConverter.getUsdToFiatRate(user.getFiatCurrency())
                ).flatMap(tuple -> {
                    try {
                        String priceJson = tuple.getT1();
                        BigDecimal conversionRate = tuple.getT2();

                        JsonNode jsonNode = objectMapper.readTree(priceJson);
                        BigDecimal currentPriceUSD = new BigDecimal(jsonNode.get("price").asText());
                        long startTimestamp = jsonNode.get("timestamp").asLong();

                        // Получаем 20 исторических цен (одна на каждый из последних 20 дней)
                        int EMA_PERIOD = 20;
                        List<Mono<BigDecimal>> priceRequests = new ArrayList<>();
                        for (int i = 0; i < EMA_PERIOD; i++) {
                            long ts = startTimestamp - (i * 24 * 60 * 60);
                            Mono<BigDecimal> priceMono = priceFetcher.getSymbolPriceByTime(cryptoCurrency, ts)
                                    .flatMap(this::parsePrice)
                                    .retryWhen(Retry.fixedDelay(2, Duration.ofSeconds(1))
                                        .doBeforeRetry(signal -> 
                                            log.warn("Retrying database operation after error: {}", signal.failure().getMessage())
                                        ))
                                    .onErrorResume(e -> Mono.just(BigDecimal.ZERO));
                            priceRequests.add(priceMono);
                        }

                        return Flux.merge(priceRequests)
                                .collectList()
                                .flatMap(prices -> {
                                    // Рассчитываем SMA
                                    BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal sma = sum.divide(new BigDecimal(prices.size()), 2, RoundingMode.HALF_UP);

                                    // Конвертируем цены
                                    BigDecimal currentPriceFiat = currentPriceUSD.multiply(conversionRate).setScale(2, RoundingMode.HALF_UP);
                                    BigDecimal smaFiat = sma.multiply(conversionRate).setScale(2, RoundingMode.HALF_UP);

                                    Notification notification = new Notification(
                                            null,
                                            cryptoCurrency,
                                            user.getFiatCurrency(),
                                            Notification.ThresholdType.EMA,
                                            false, // isActive не используется
                                            chatId,
                                            null, // upperBoundary не используется
                                            null, // lowerBoundary не используется
                                            currentPriceFiat.doubleValue() // Сохраняем цену в фиате
                                    );
                                    notification.setStartEMA(smaFiat.doubleValue());
                                    notification.setCurrentEMA(smaFiat.doubleValue());
                                    notification.setStartTimestamp(startTimestamp);
                                    // Устанавливаем начальное значение isAbove
                                    notification.setIsAbove(currentPriceFiat.compareTo(smaFiat) > 0);

                                    return notificationService.createUserNotification(notification)
                                            .then(Mono.just(String.format("✅ Алерт EMA установлен для %s\n" +
                                                            "💰 Текущая цена: %.2f %s\n" +
                                                            "📈 Начальное EMA (SMA 20): %.2f %s",
                                                    cryptoCurrency.getCode(),
                                                    currentPriceFiat, notification.getFiatCurrency().getCode(),
                                                    smaFiat, notification.getFiatCurrency().getCode())));
                                });
                    } catch (Exception e) {
                        log.error("Ошибка при установке EMA алерта: {}", e.getMessage());
                        return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
                    }
                }));
    }

    /**
     * Проверяет все установленные алерты каждые 5 минут.
     * Для каждого алерта получает текущую цену и проверяет условия срабатывания.
     */
    @Scheduled(fixedRate = 300000) // Проверка каждые 5 минут
    public void checkAlerts() {
        log.info("Начало проверки алертов...");
        notificationService.getAllActiveAlerts()
                .flatMap(notification -> {
                    log.info("Проверка алерта для {} (тип: {})",
                            notification.getCryptoCurrency(), notification.getThresholdType());

                    return priceFetcher.getCurrentPrice(notification.getCryptoCurrency())
                            .flatMap(priceJson -> {
                                try {
                                    JsonNode node = objectMapper.readTree(priceJson);
                                    BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                                    long timestamp = node.get("timestamp").asLong();

                                    // Конвертируем цену в целевую валюту из уведомления
                                    return currencyConverter.getUsdToFiatRate(notification.getFiatCurrency())
                                            .flatMap(conversionRate -> {
                                                BigDecimal priceInTargetCurrency = currentPrice.multiply(conversionRate)
                                                        .setScale(2, RoundingMode.HALF_UP);

                                                // Проверяем условия срабатывания алерта
                                                boolean isTriggered = false;
                                                String message = "";

                                                switch (notification.getThresholdType()) {
                                                    case VALUE -> {
                                                        if (priceInTargetCurrency.compareTo(BigDecimal.valueOf(notification.getUpperBoundary())) >= 0) {
                                                            isTriggered = true;
                                                            message = String.format("🚨 Цена %s превысила верхнюю границу, сейчас она составляет: %.2f %s",
                                                                    notification.getCryptoCurrency().getCode(),
                                                                    priceInTargetCurrency, notification.getFiatCurrency().getCode());
                                                        } else if (priceInTargetCurrency.compareTo(BigDecimal.valueOf(notification.getLowerBoundary())) <= 0) {
                                                            isTriggered = true;
                                                            message = String.format("🚨 Цена %s опустилась ниже нижней границы, сейчас она составляет: %.2f %s",
                                                                    notification.getCryptoCurrency().getCode(),
                                                                    priceInTargetCurrency, notification.getFiatCurrency().getCode());
                                                        }
                                                    }
                                                    case PERCENT -> {
                                                        BigDecimal startPrice = BigDecimal.valueOf(notification.getStartPrice());
                                                        BigDecimal percentChange = priceInTargetCurrency.subtract(startPrice)
                                                                .divide(startPrice, 4, RoundingMode.HALF_UP)
                                                                .multiply(new BigDecimal("100"));

                                                        if (percentChange.compareTo(BigDecimal.valueOf(notification.getUpPercent())) >= 0) {
                                                            isTriggered = true;
                                                            message = String.format("🚨 Цена %s выросла на %.2f%% (до %.2f %s)",
                                                                    notification.getCryptoCurrency().getCode(),
                                                                    percentChange, priceInTargetCurrency, notification.getFiatCurrency().getCode());
                                                        } else if (percentChange.compareTo(BigDecimal.valueOf(-notification.getDownPercent())) <= 0) {
                                                            isTriggered = true;
                                                            message = String.format("🚨 Цена %s упала на %.2f%% (до %.2f %s)",
                                                                    notification.getCryptoCurrency().getCode(),
                                                                    percentChange.abs(), priceInTargetCurrency, notification.getFiatCurrency().getCode());
                                                        }
                                                    }
                                                    case EMA -> {
                                                        // Обновляем EMA
                                                        updateEMA(notification, priceInTargetCurrency);
                                                    }
                                                }

                                                if (isTriggered) {
                                                    notification.setIsActive(false);
                                                    notification.setTriggerTimestamp(timestamp);
                                                    return notificationService.save(notification)
                                                            .then(telegramBotService.sendResponseAsync(notification.getChatId(), message))
                                                            .doOnSuccess(v -> log.info("Уведомление успешно отправлено для алерта {} (тип: {})",
                                                                    notification.getCryptoCurrency(), notification.getThresholdType()))
                                                            .doOnError(e -> log.error("Ошибка при отправке уведомления для алерта {} (тип: {}): {}",
                                                                    notification.getCryptoCurrency(), notification.getThresholdType(), e.getMessage()));
                                                }

                                                return Mono.empty();
                                            });
                                } catch (Exception e) {
                                    log.error("Ошибка при проверке алерта: {}", e.getMessage());
                                    return Mono.empty();
                                }
                            });
                })
                .subscribe(
                        null,
                        error -> log.error("Ошибка при проверке алертов: {}", error.getMessage()),
                        () -> log.info("Проверка алертов завершена")
                );
    }

    private Mono<BigDecimal> parsePrice(String priceJson) {
        return Mono.fromCallable(() -> {
            JsonNode jsonNode = objectMapper.readTree(priceJson);
            String priceStr = jsonNode.get("price").asText();
            // Удаляем все нечисловые символы, кроме точки
            priceStr = priceStr.replaceAll("[^0-9.]", "");
            return new BigDecimal(priceStr);
        });
    }

    /**
     * Возвращает список всех установленных алертов.
     *
     * @return Mono<String> JSON-строка со списком алертов
     */
    public Mono<String> showAlerts() {
        return notificationService.getAllActiveAlerts()
                .collectList()
                .flatMap(activeAlerts -> {
                    try {
                        ObjectNode result = objectMapper.createObjectNode();
                        ArrayNode alertsArray = objectMapper.createArrayNode();

                        for (Notification notification : activeAlerts) {
                            ObjectNode alertNode = objectMapper.createObjectNode();
                            alertNode.put("type", notification.getThresholdType().toString());
                            alertNode.put("symbol", notification.getCryptoCurrency().toString());
                            alertNode.put("fiat", notification.getFiatCurrency().getCode());
                            alertNode.put("threshold", notification.getActiveThreshold());
                            alertNode.put("isActive", notification.isActive());
                            alertsArray.add(alertNode);
                        }
                        result.set("alerts", alertsArray);

                        return Mono.just(objectMapper.writeValueAsString(result));
                    } catch (Exception e) {
                        log.error("Error showing alerts: {}", e.getMessage());
                        return Mono.error(new RuntimeException("Error showing alerts: " + e.getMessage()));
                    }
                });
    }

    /**
     * Удаляет алерт по символу и типу.
     *
     * @param symbol Символ криптовалюты
     * @param type Тип алерта
     * @param chatId ID чата пользователя
     * @return Mono<String> Сообщение о результате операции
     */
    public Mono<String> deleteAlert(String symbol, String type, String chatId) {
        try {
            String cryptoCode = symbol.split("-")[0];
            Crypto cryptoCurrency = Crypto.valueOf(cryptoCode);

            return notificationService.getAllUserAlerts(chatId)
                    .filter(alert -> alert.getThresholdType().toString().equals(type))
                    .filter(alert -> alert.getCryptoCurrency().equals(cryptoCurrency))
                    .next()
                    .flatMap(alert -> notificationService.delete(alert)
                            .then(Mono.just("✅ Алерт успешно удален")))
                    .switchIfEmpty(Mono.just("❌ Алерт не найден"));
        } catch (Exception e) {
            log.error("Error deleting alert: {}", e.getMessage());
            return Mono.just("❌ Ошибка при удалении алерта: " + e.getMessage());
        }
    }

    /**
     * Удаляет все установленные алерты для указанного пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<Void> Сообщение о результате операции
     */
    public Mono<Void> deleteAllAlerts(String chatId) {
        return notificationService.deleteAllAlerts(chatId)
                .then(telegramBotService.sendResponseAsync(chatId, "Все алерты были удалены."))
                .then();
    }

    // Обновляем EMA
    private void updateEMA(Notification alert, BigDecimal currentPrice) {
        BigDecimal currentEMA = BigDecimal.valueOf(alert.getCurrentEMA());
        BigDecimal newEMA = currentEMA.multiply(new BigDecimal("0.95"))
                .add(currentPrice.multiply(new BigDecimal("0.05")));

        // Сохраняем обновленное значение EMA
        alert.setCurrentEMA(newEMA.doubleValue());

        // Проверяем пересечение EMA
        checkEMACrossing(alert, currentPrice);
    }

    // Проверяем пересечение EMA
    private void checkEMACrossing(Notification alert, BigDecimal currentPrice) {
        BigDecimal ema = BigDecimal.valueOf(alert.getCurrentEMA());
        boolean isCurrentlyAbove = currentPrice.compareTo(ema) > 0;
        
        // Если isAbove еще не установлен (первая проверка), устанавливаем его
        if (alert.getIsAbove() == null) {
            alert.setIsAbove(isCurrentlyAbove);
            notificationService.save(alert);
            return;
        }

        // Проверяем, произошло ли пересечение
        if (alert.getIsAbove() != isCurrentlyAbove) {
            alert.setIsAbove(isCurrentlyAbove);
            String trendMessage = isCurrentlyAbove ?
                    String.format("🚨 Обнаружен восходящий тренд для %s\n" +
                                    "💰 Текущая цена: %.2f %s\n" +
                                    "📈 EMA: %.2f %s",
                            alert.getCryptoCurrency().getCode(),
                            currentPrice, alert.getFiatCurrency().getCode(),
                            ema, alert.getFiatCurrency().getCode()) :
                    String.format("🚨 Обнаружен нисходящий тренд для %s\n" +
                                    "💰 Текущая цена: %.2f %s\n" +
                                    "📉 EMA: %.2f %s",
                            alert.getCryptoCurrency().getCode(),
                            currentPrice, alert.getFiatCurrency().getCode(),
                            ema, alert.getFiatCurrency().getCode());

            // Отправляем уведомление только при пересечении
            telegramBotService.sendResponseAsync(alert.getChatId(), trendMessage)
                    .subscribe(
                            null,
                            error -> log.error("Ошибка при отправке уведомления о тренде: {}", error.getMessage())
                    );
        }

        notificationService.save(alert);
    }

    // Текущее время в секундах
    long currentTime = System.currentTimeMillis() / 1000;
} 