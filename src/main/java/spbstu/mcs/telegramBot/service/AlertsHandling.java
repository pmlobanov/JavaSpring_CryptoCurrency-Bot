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
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Notification;
import spbstu.mcs.telegramBot.model.User;

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
    private static final int EMA_PERIOD = 20; // Период для EMA

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
                     //     NotificationRepository notificationRepository,
                          UserService userService) {
        log.info("Инициализация сервиса AlertsHandling...");
        this.objectMapper = objectMapper;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.telegramBotService = telegramBotService;
        this.notificationService = notificationService;
      //  this.notificationRepository = notificationRepository;
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
        return Mono.zip(
                priceFetcher.getCurrentPrice(cryptoCurrency),
                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
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
                BigDecimal maxPriceInFiat = maxPrice.setScale(2, RoundingMode.HALF_UP);
                BigDecimal minPriceInFiat = minPrice.setScale(2, RoundingMode.HALF_UP);

                log.info("Установка VALUE алерта для {}: текущая цена={} USD ({} {})",
                        cryptoCurrency, currentPriceUSD, currentPrice, Currency.Fiat.getCurrentFiat().getCode());

                Notification notification = new Notification(
                        null,
                        cryptoCurrency,
                        Currency.Fiat.getCurrentFiat(),
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
                                currentPrice, Currency.Fiat.getCurrentFiat().getCode(),
                                maxPriceInFiat, Currency.Fiat.getCurrentFiat().getCode(),
                                minPriceInFiat, Currency.Fiat.getCurrentFiat().getCode())));
            } catch (Exception e) {
                log.error("Ошибка при установке VALUE алерта: {}", e.getMessage());
                return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
            }
        });
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
        return Mono.zip(
                priceFetcher.getCurrentPrice(cryptoCurrency),
                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
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

                // Рассчитываем границы в целевой валюте
                BigDecimal upperBoundary = currentPrice.multiply(
                                BigDecimal.ONE.add(upPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal lowerBoundary = currentPrice.multiply(
                                BigDecimal.ONE.subtract(downPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                        .setScale(2, RoundingMode.HALF_UP);

                log.info("Установка PERCENT алерта для {}: текущая цена={} USD ({} {})",
                        cryptoCurrency, currentPriceUSD, currentPrice, Currency.Fiat.getCurrentFiat().getCode());
                log.info("Границы: верхняя={} {} (+{}%), нижняя={} {} (-{}%)",
                        upperBoundary, Currency.Fiat.getCurrentFiat().getCode(), upPercent,
                        lowerBoundary, Currency.Fiat.getCurrentFiat().getCode(), downPercent);

                Notification notification = new Notification(
                        null,
                        cryptoCurrency,
                        Currency.Fiat.getCurrentFiat(),
                        Notification.ThresholdType.PERCENT,
                        true,
                        chatId,
                        upperBoundary.doubleValue(),
                        lowerBoundary.doubleValue(),
                        currentPrice.doubleValue()
                );

                notification.setUpPercent(upPercent.doubleValue());
                notification.setDownPercent(downPercent.doubleValue());
                notification.setStartTimestamp(timestamp);

                return notificationService.createUserNotification(notification)
                        .then(Mono.just(String.format("✅ Алерт установлен для %s\n" +
                                        "💰 Текущая цена: %.2f %s\n" +
                                        "📈 Рост: +%.2f%%\n" +
                                        "📉 Падение: -%.2f%%",
                                cryptoCurrency.getCode(),
                                currentPrice, Currency.Fiat.getCurrentFiat().getCode(),
                                upPercent, downPercent)));
            } catch (Exception e) {
                log.error("Ошибка при установке PERCENT алерта: {}", e.getMessage());
                return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
            }
        });
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
        return Mono.zip(
                priceFetcher.getCurrentPrice(cryptoCurrency),
                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
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
                                    Currency.Fiat.getCurrentFiat(),
                                    Notification.ThresholdType.EMA,
                                    false, // isActive не используется
                                    chatId,
                                    null, // upperBoundary не используется
                                    null, // lowerBoundary не используется
                                    currentPriceUSD.doubleValue()
                            );
                            notification.setStartEMA(sma.doubleValue());
                            notification.setCurrentEMA(sma.doubleValue());
                            notification.setStartTimestamp(startTimestamp);

                            return notificationService.createUserNotification(notification)
                                    .then(Mono.just(String.format("✅ Алерт EMA установлен для %s\n" +
                                                    "💰 Текущая цена: %.2f %s\n" +
                                                    "📈 Начальное EMA (SMA 20): %.2f %s",
                                            cryptoCurrency.getCode(),
                                            currentPriceFiat, Currency.Fiat.getCurrentFiat().getCode(),
                                            smaFiat, Currency.Fiat.getCurrentFiat().getCode())));
                        });
            } catch (Exception e) {
                log.error("Ошибка при установке EMA алерта: {}", e.getMessage());
                return Mono.just("❌ Ошибка при установке алерта: " + e.getMessage());
            }
        });
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

            return notificationService.getActiveAlerts(cryptoCurrency)
                    .filter(alert -> alert.getThresholdType().toString().equals(type))
                    .filter(alert -> alert.getChatId() != null && alert.getChatId().equals(chatId))
                    .next()
                    .flatMap(alert -> notificationService.delete(alert)
                            .then(Mono.just("Alert deleted successfully")))
                    .switchIfEmpty(Mono.just("Alert not found"));
        } catch (Exception e) {
            log.error("Error deleting alert: {}", e.getMessage());
            return Mono.just("Error deleting alert: " + e.getMessage());
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

    /**
     * Возвращает список всех установленных алертов для указанного пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<SendMessage> Сообщение со списком алертов
     */
    private Mono<SendMessage> getAllActiveAlerts(String chatId) {
        return notificationService.getAllActiveAlerts(chatId)
                .collectList()
                .map(notifications -> {
                    if (notifications.isEmpty()) {
                        return new SendMessage(chatId, "У вас нет активных алертов.");
                    }

                    StringBuilder message = new StringBuilder("Ваши активные алерты:\n");
                    for (Notification notification : notifications) {
                        message.append(formatNotification(notification)).append("\n");
                    }

                    return new SendMessage(chatId, message.toString());
                });
    }

    private Currency.Crypto findCryptoByCode(String code) {
        for (Currency.Crypto c : Currency.Crypto.values()) {
            if (c.getCode().equalsIgnoreCase(code)) {
                return c;
            }
        }
        return null;
    }

    private Mono<SendMessage> sendAlertMessage(Notification notification, String message) {
        return Mono.just(new SendMessage(notification.getChatId(), message))
                .doOnSuccess(sendMessage -> {
                    notification.setIsActive(false);
                    notificationService.save(notification)
                            .subscribe(saved -> {
                                telegramBotService.sendResponseAsync(notification.getChatId(), message)
                                        .subscribe(
                                                null,
                                                error -> log.error("Error sending alert message: {}", error.getMessage())
                                        );
                            });
                });
    }

    private String formatNotification(Notification notification) {
        return String.format("%s: %s порог на уровне %s",
                notification.getCryptoCurrency(),
                notification.getThresholdType(),
                notification.getActiveThreshold());
    }

    private Mono<Void> handleValueThreshold(User user, Currency.Crypto cryptoCurrency, Double threshold) {
        return priceFetcher.getCurrentPrice(cryptoCurrency)
                .map(currentPrice -> Double.parseDouble(currentPrice))
                .defaultIfEmpty(0.0)
                .flatMap(price -> {
                    Notification notification = new Notification(
                            null,
                            cryptoCurrency,
                            Currency.Fiat.getCurrentFiat(),
                            Notification.ThresholdType.VALUE,
                            true,
                            user.getChatId(),
                            threshold,
                            threshold,
                            price
                    );

                    return notificationService.createUserNotification(notification)
                            .then(telegramBotService.sendResponseAsync(user.getChatId(),
                                    String.format("Алерт установлен для %s на уровне %s %s",
                                            cryptoCurrency, threshold, Currency.Fiat.getCurrentFiat().getCode())))
                            .then();
                });
    }

    private Mono<Void> handlePercentThreshold(User user, Currency.Crypto cryptoCurrency, Double threshold) {
        return priceFetcher.getCurrentPrice(cryptoCurrency)
                .map(currentPrice -> Double.parseDouble(currentPrice))
                .defaultIfEmpty(0.0)
                .flatMap(price -> {
                    Notification notification = new Notification(
                            null,
                            cryptoCurrency,
                            Currency.Fiat.getCurrentFiat(),
                            Notification.ThresholdType.PERCENT,
                            true,
                            user.getChatId(),
                            threshold,
                            threshold,
                            price
                    );

                    return notificationService.createUserNotification(notification)
                            .then(telegramBotService.sendResponseAsync(user.getChatId(),
                                    String.format("Алерт установлен для %s на уровне %s%%", cryptoCurrency, threshold)))
                            .then();
                });
    }

    private Mono<Void> handleEmaThreshold(User user, Currency.Crypto cryptoCurrency, Double threshold) {
        return priceFetcher.getCurrentPrice(cryptoCurrency)
                .map(currentPrice -> Double.parseDouble(currentPrice))
                .defaultIfEmpty(0.0)
                .flatMap(price -> {
                    Notification notification = new Notification(
                            null,
                            cryptoCurrency,
                            Currency.Fiat.getCurrentFiat(),
                            Notification.ThresholdType.EMA,
                            false, // isActive не используется
                            user.getChatId(),
                            null, // upperBoundary не используется
                            null, // lowerBoundary не используется
                            price
                    );

                    return notificationService.createUserNotification(notification)
                            .then(telegramBotService.sendResponseAsync(user.getChatId(),
                                    String.format("Алерт установлен для %s на уровне EMA %s", cryptoCurrency, threshold)))
                            .then();
                });
    }

    private String formatDuration(long timestamp) {
        long currentTime = System.currentTimeMillis() / 1000; // текущее время в секундах
        long duration = currentTime - timestamp;

        long days = duration / (24 * 3600);
        long hours = (duration % (24 * 3600)) / 3600;
        long minutes = (duration % 3600) / 60;

        StringBuilder durationStr = new StringBuilder();
        if (days > 0) {
            durationStr.append(days).append(" дн. ");
        }
        if (hours > 0) {
            durationStr.append(hours).append(" ч. ");
        }
        if (minutes > 0) {
            durationStr.append(minutes).append(" мин.");
        }
        return durationStr.toString().trim();
    }

    // Конвертируем текущую цену в целевую валюту
    private Mono<BigDecimal> convertPriceToTargetCurrency(BigDecimal priceUSD, Currency.Fiat targetCurrency) {
        return currencyConverter.getUsdToFiatRate(targetCurrency)
                .map(rate -> priceUSD.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    // Конвертируем границы в целевую валюту
    private Mono<BigDecimal> convertBoundaryToTargetCurrency(BigDecimal boundaryUSD, Currency.Fiat targetCurrency) {
        return currencyConverter.getUsdToFiatRate(targetCurrency)
                .map(rate -> boundaryUSD.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    // Рассчитываем границы в целевой валюте
    private Mono<BigDecimal> calculateBoundaryInTargetCurrency(BigDecimal currentPrice, BigDecimal percentChange, Currency.Fiat targetCurrency) {
        BigDecimal boundaryUSD = currentPrice.multiply(BigDecimal.ONE.add(percentChange.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)));
        return convertBoundaryToTargetCurrency(boundaryUSD, targetCurrency);
    }

    // Рассчитываем начальное EMA
    private BigDecimal calculateInitialEMA(List<BigDecimal> prices) {
        if (prices.size() < EMA_PERIOD) {
            return prices.get(prices.size() - 1);
        }
        return prices.subList(0, EMA_PERIOD).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(new BigDecimal(EMA_PERIOD), 2, RoundingMode.HALF_UP);
    }

    // Не используется для EMA
    private static final double[] WEIGHTS = {
            0.0,  // Не используется для EMA
            0.0,  // Не используется для EMA
    };

    // Удаляем все нечисловые символы, кроме точки
    private String cleanNumericString(String input) {
        return input.replaceAll("[^0-9.]", "");
    }

    // Получаем начальную цену в целевой валюте
    private BigDecimal getInitialPriceInTargetCurrency(Notification alert) {
        return BigDecimal.valueOf(alert.getStartPrice());
    }

    // Получаем границы в целевой валюте
    private BigDecimal getBoundaryInTargetCurrency(Notification alert) {
        return BigDecimal.valueOf(alert.getUpperBoundary());
    }

    // Обновляем EMA
    private void updateEMA(Notification alert, BigDecimal currentPrice) {
        BigDecimal currentEMA = BigDecimal.valueOf(alert.getCurrentEMA());
        BigDecimal newEMA = currentEMA.multiply(new BigDecimal("0.95"))
                .add(currentPrice.multiply(new BigDecimal("0.05")));

        // Сохраняем обновленное значение EMA
        alert.setCurrentEMA(newEMA.doubleValue());
        notificationService.save(alert);

        // Проверяем пересечение EMA
        checkEMACrossing(alert, currentPrice);
    }

    // Проверяем пересечение EMA
    private void checkEMACrossing(Notification alert, BigDecimal currentPrice) {
        BigDecimal ema = BigDecimal.valueOf(alert.getCurrentEMA());
        boolean wasAbove = alert.isActive(); // используем старое значение для определения пересечения
        boolean isAbove = currentPrice.compareTo(ema) > 0;

        // Если произошло пересечение (изменение состояния)
        if (wasAbove != isAbove) {
            alert.setIsActive(isAbove); // сохраняем новое состояние
            String trendMessage = isAbove ?
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

            // Отправляем уведомление о смене тренда
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