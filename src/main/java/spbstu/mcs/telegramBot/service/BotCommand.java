package spbstu.mcs.telegramBot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.cryptoApi.CryptoInformation;
import spbstu.mcs.telegramBot.service.AlertsHandling;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.model.Notification;
import java.util.List;
import java.util.ArrayList;
import reactor.core.publisher.Flux;
import java.math.RoundingMode;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.Map;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.model.Portfolio;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import spbstu.mcs.telegramBot.cryptoApi.CryptoPortfolioManager;
import spbstu.mcs.telegramBot.DB.services.AdminService;

/**
 * Класс для обработки команд Telegram бота.
 * Предоставляет функционал для:
 * - Обработки команд пользователя
 * - Управления портфелем криптовалют
 * - Настройки оповещений
 * - Получения информации о ценах
 */
@Component
@Service
@Slf4j
public class BotCommand {
    private final CryptoInformation cryptoInformation;
    private final ObjectMapper objectMapper;
    private final AlertsHandling alertsHandling;
    private final TelegramBotService telegramBotService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final CurrencyConverter currencyConverter;
    private final PriceFetcher priceFetcher;
    private final PortfolioService portfolioService;
    private final CryptoPortfolioManager cryptoPortfolioManager;
    private final AdminService adminService;

    @Autowired
    public BotCommand(CryptoInformation cryptoInformation, 
                     ObjectMapper objectMapper,
                     AlertsHandling alertsHandling,
                     TelegramBotService telegramBotService,
                     UserService userService,
                     NotificationService notificationService,
                     CurrencyConverter currencyConverter,
                     PriceFetcher priceFetcher,
                     PortfolioService portfolioService,
                     CryptoPortfolioManager cryptoPortfolioManager,
                     AdminService adminService) {
        this.cryptoInformation = cryptoInformation;
        this.objectMapper = objectMapper;
        this.alertsHandling = alertsHandling;
        this.telegramBotService = telegramBotService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.currencyConverter = currencyConverter;
        this.priceFetcher = priceFetcher;
        this.portfolioService = portfolioService;
        this.cryptoPortfolioManager = cryptoPortfolioManager;
        this.adminService = adminService;
    }

    /**
     * Обрабатывает аргументы команды
     * @param args Строка аргументов
     * @param expectedCount Ожидаемое количество аргументов
     * @return Массив строк с аргументами, если валидны, иначе null
     */
    private String[] processArguments(String args, int expectedCount) {
        log.info("Processing arguments: '{}', expected count: {}", args, expectedCount);
        
        if (args == null || args.trim().isEmpty()) {
            log.info("Args are null or empty, returning {}", expectedCount == 0 ? "empty array" : "null");
            return expectedCount == 0 ? new String[0] : null;
        }
        
        String[] splitArgs = args.trim().split("\\s+");
        log.info("Split arguments: {}", String.join(", ", splitArgs));
        
        if (splitArgs.length != expectedCount) {
            log.info("Argument count mismatch: got {}, expected {}", splitArgs.length, expectedCount);
            return null;
        }
        
        log.info("Returning valid arguments");
        return splitArgs;
    }

    /**
     * Обрабатывает команду /start
     * @return Приветственное сообщение
     */
    public String handlerStart() {
        return "🚀 Добро пожаловать в BitBotX — вашего надежного помощника в мире криптовалют!\n" +
                "С моей помощью вы сможете:\n" +
                "🔹 Узнавать актуальные цены на BTC, ETH и другие криптовалюты.\n" +
                "🔹 Анализировать историю цен за выбранный период.\n" +
                "🔹 Сравнивать курсы разных активов.\n" +
                "🔹 Управлять портфелем: добавлять криптовалюты, отслеживать прибыль и убытки.\n" +
                "🔹 Настраивать оповещения о важных изменениях цен.\n" +
                "❓ Нужна помощь? Напишите /help, и я расскажу, как работать с моими функциями!\n" +
                "📈 Давайте начнем! Просто выберите нужную команду или введите интересующий вас запрос.";
    }

    public String handlerStart(String args) {
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return "\u274C Команда /start не принимает аргументов";
        }
        
        return handlerStart();
    }

    /**
     * Обрабатывает команду /help
     * @return Справка по командам
     */
    public String handlerHelp() {
        // Эта команда ожидает 0 аргументов
        return "\uD83D\uDCDA BitBotX Help Center\n" +
                "Ваш гид по возможностям крипто-бота\n" +
                "\uD83D\uDD39 Основные команды:\n" +
                "▸ /start - Начало работы с ботом\n" +
                "▸ /help - Это справочное меню\n" +
                "▸ /authors - список авторов\n" +
                "▸ /set_fiat <валюта> - Настройка фиатной валюты по умолчанию\n" +
                "▸ /set_crypto <криптовалюта> - Настройка криптовалюты по умолчанию\n" +
                "\uD83D\uDCCA Мониторинг цен:\n" +
                "▸ /show_current_price - Текущий курс валюты по умолчанию\n" +
                "▸ /show_price_history <период> - История цены (3d/7d/1M)\n" +
                "▸ /compare_currency <валюта 1> <валюта 2> <период> - Сравнение криптовалют (3h/12h/24h - часы, 3d/7d/30d - дни)\n" +
                "\uD83D\uDCBC Портфель:\n" +
                "▸ /add <количество> <валюта> - Добавить актив\n" +
                "▸ /remove <количество> <валюта> - Удалить актив\n" +
                "▸ /portfolio - Просмотр портфеля\n" +
                "▸ /get_portfolio_price - Стоимость портфеля\n" +
                "▸ /get_assets_price - Цены активов\n" +
                "▸ /balance - Баланс портфеля\n" +
                "▸ /delete_asset <валюта> - Удалить актив\n" +
                "▸ /delete_all_assets - Удалить все активы\n" +
                "\uD83D\uDD14 Оповещения:\n" +
                "▸ /set_alert_ema <валюта> - По индикатору\n" +
                "▸ /set_alert_val <валюта> <максимальная цена> <минимальная цена> - По цене\n" +
                "▸ /set_alert_perc <валюта> <максимальный прирост> <максимальный убыток> - По процентам\n" +
                "▸ /my_alerts - Активные оповещения\n" +
                "▸ /delete_alert <тип> <валюта> - Удалить оповещение\n" +
                "▸ /delete_all_alerts - Удаление всех оповещений";
    }

    public String handlerHelp(String args) {
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return "\u274C Команда /help не принимает аргументов";
        }
        
        return handlerHelp();
    }

    /**
     * Обрабатывает команду /set_crypto
     * @return Сообщение об ошибке формата
     */
    private String handlerSetCrypto() {
        return "\u274C Пожалуйста, укажите одну из следующих криптовалют: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC. Например: /set_crypto BTC";
    }

    public String handlerSetCrypto(String args) {
        // Process arguments - we expect exactly 1 argument
        String[] processedArgs = processArguments(args, 1);
        if (processedArgs == null) {
            return handlerSetCrypto();
        }
        
        String cryptoCode = processedArgs[0];
        
        try {
            // Try to find matching crypto currency
            Currency.Crypto crypto = null;
            for (Currency.Crypto c : Currency.Crypto.values()) {
                if (c.getCode().equalsIgnoreCase(cryptoCode)) {
                    crypto = c;
                    break;
                }
            }
            
            if (crypto == null) {
                return "\u274C Криптовалюта с кодом " + cryptoCode + " не используется. Выберите один из следующих кодов: " +
                       "BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC. Например: /set_crypto BTC";
            }
            
            // Check if this crypto is already set as current
            if (crypto == Currency.Crypto.getCurrentCrypto()) {
                return "\u2139️ Криптовалюта " + crypto.getCode() + " уже установлена в качестве текущей.";
            }
            
            Currency.Crypto.setCurrentCrypto(crypto);
            return "💱 Криптовалюта по умолчанию обновлена! Теперь базовой криптовалютой для аналитики и сравнений выбран " + crypto.getCode();
        } catch (Exception e) {
            return "Произошла ошибка при установке криптовалюты: " + e.getMessage();
        }
    }

    /**
     * Обрабатывает команду /set_fiat
     * @return Сообщение об ошибке формата
     */
    private String handlerSetFiat() {
        return "\u274C Пожалуйста, укажите одну из следующих фиатных валют: USD, EUR, JPY, GBP, RUB, CNY. Например: /set_fiat USD";
    }

    public String handlerSetFiat(String args) {
        // Process arguments - we expect exactly 1 argument
        String[] processedArgs = processArguments(args, 1);
        if (processedArgs == null) {
            return handlerSetFiat();
        }
        
        String fiatCode = processedArgs[0];
        
        try {
            // Try to find matching fiat currency
            Currency.Fiat fiat = null;
            for (Currency.Fiat f : Currency.Fiat.values()) {
                if (f.getCode().equalsIgnoreCase(fiatCode)) {
                    fiat = f;
                    break;
                }
            }
            
            if (fiat == null) {
                return "\u274C Фиатная валюта с кодом " + fiatCode + " не используется. Выберите один из следующих кодов: " +
                       "USD, EUR, JPY, GBP, RUB, CNY. Например: /set_fiat USD";
            }
            
            if (fiat == Currency.Fiat.getCurrentFiat()) {
                return "\u2139️ Фиатная валюта " + fiat.getCode() + " уже установлена в качестве текущей.";
            }
            
            Currency.Fiat.setCurrentFiat(fiat);
            return "💱 Фиатная валюта обновлена! Теперь все цены будут отображаться в " + fiat.getCode();
        } catch (Exception e) {
            return "Произошла ошибка при установке фиатной валюты: " + e.getMessage();
        }
    }
    
    /**
     * Обрабатывает команду /show_current_price
     * @return Текущая цена криптовалюты
     */
    public Mono<String> handlerShowCurrentPrice(String args) {
        log.info("Processing /show_current_price command with args: {}", args);
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            log.info("Invalid arguments for /show_current_price command");
            return Mono.just("\u274C Команда /show_current_price не принимает аргументов");
        }
        
        log.info("Calling handlerShowCurrentPrice()");
        return handlerShowCurrentPrice()
            .doOnNext(response -> log.info("Got response from handlerShowCurrentPrice: {}", response))
            .doOnError(error -> log.error("Error in handlerShowCurrentPrice: {}", error.getMessage()));
    }
    
    private Mono<String> handlerShowCurrentPrice() {
        log.info("Getting current price from cryptoInformation");
        return cryptoInformation.showCurrentPrice()
            .doOnNext(jsonPrice -> log.info("Received price data: {}", jsonPrice))
            .map(jsonPrice -> {
                try {
                    JsonNode node = objectMapper.readTree(jsonPrice);
                    String symbol = node.get("symbol").asText();
                    String price = node.get("price").asText();
                    long timestamp = node.get("timestamp").asLong();
                    
                    log.info("Processing price data - symbol: {}, price: {}, timestamp: {}", symbol, price, timestamp);
                    
                    // Format the date in UTC+3
                    java.time.ZonedDateTime dateTime = java.time.Instant.ofEpochSecond(timestamp)
                        .atZone(java.time.ZoneId.of("Europe/Moscow"));
                    String formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
                    
                    // Get crypto name
                    String cryptoName = "";
                    for (Currency.Crypto crypto : Currency.Crypto.values()) {
                        if (symbol.startsWith(crypto.getCode())) {
                            cryptoName = crypto.getName();
                            break;
                        }
                    }
                    
                    String response = String.format("📊 Текущий курс криптовалюты\n" +
                                       "➡️ %s (%s):\n" +
                                       "💰 %s (по курсу %s)\n" +
                                       "🔄 Обновлено: Сегодня, %s (UTC+3)",
                                       symbol.split("-")[0], cryptoName, price, 
                                       Currency.Fiat.getCurrentFiat().getCode(), formattedDate);
                    
                    log.info("Formatted response: {}", response);
                    return response;
                } catch (Exception e) {
                    log.error("Error processing price data: {}", e.getMessage(), e);
                    return "❌ Ошибка при обработке данных: " + e.getMessage();
                }
            })
            .onErrorResume(throwable -> {
                log.error("Error getting current price: {}", throwable.getMessage(), throwable);
                return Mono.just("❌ Ошибка при получении текущей цены: " + throwable.getMessage());
            });
    }

    /**
     * Форматирует историю цен
     * @param period Период времени
     * @return Отформатированная история цен
     */
    private Mono<String> formatPriceHistory(String period) {
        return cryptoInformation.showPriceHistory(period)
            .map(jsonPrice -> {
                try {
                    JsonNode node = objectMapper.readTree(jsonPrice);
                    String symbol = node.get("symbol").asText();
                    String firstPrice = node.get("firstPrice").asText();
                    String lastPrice = node.get("lastPrice").asText();
                    String percentChange = node.get("percentChange").asText();
                    String minPrice = node.get("minPrice").asText();
                    String maxPrice = node.get("maxPrice").asText();
                    long currentTimestamp = node.get("currentTimestamp").asLong();
    
                    // Format the current time in UTC+3
                    java.time.ZonedDateTime dateTime = java.time.Instant.ofEpochSecond(currentTimestamp)
                        .atZone(java.time.ZoneId.of("Europe/Moscow"));
                    String formattedTime = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
    
                    // Get crypto name
                    String cryptoName = "";
                    for (Currency.Crypto crypto : Currency.Crypto.values()) {
                        if (symbol.startsWith(crypto.getCode())) {
                            cryptoName = crypto.getName();
                            break;
                        }
                    }
    
                    // Format the history data
                    StringBuilder historyTable = new StringBuilder();
                    // Шапка таблицы с выравниванием
                    historyTable.append(String.format("%-17s | %12s\n", "📅 Дата и время", "Цена (" + Currency.Fiat.getCurrentFiat().getCode() + ")"));
                    historyTable.append("—————————|———————\n");
    
                    JsonNode history = node.get("history");
                    for (JsonNode priceNode : history) {
                        long timestamp = priceNode.get("timestamp").asLong();
                        String price = priceNode.get("price").asText();
    
                        java.time.ZonedDateTime priceDateTime = java.time.Instant.ofEpochSecond(timestamp)
                            .atZone(java.time.ZoneId.of("Europe/Moscow"));
                        String formattedDate = priceDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    
                        // Выравнивание по ширине
                        historyTable.append(String.format("%-19s | %12s\n", formattedDate, price));
                    }
    
                    // Format the change percentage with sign
                    String changeSign = Double.parseDouble(percentChange) >= 0 ? "+" : "";
    
                    return String.format("%s\n\n📊 Изменение за период:\n%s%s%% (с %s до %s)\n" +
                                       "📌 Минимум: %s ∣ Максимум: %s\n" +
                                       "🔍 Данные обновлены: Сегодня, %s",
                                       historyTable.toString(),
                                       changeSign, percentChange, firstPrice, lastPrice,
                                       minPrice, maxPrice, formattedTime);
                } catch (Exception e) {
                    return "Ошибка при обработке данных: " + e.getMessage();
                }
            })
            .onErrorResume(throwable -> Mono.just("Ошибка при получении истории цен: " + throwable.getMessage()));
    }
    
    
    public Mono<String> handlerShowPriceHistory(String args) {
        // This command expects 1 argument (period)
        String[] processedArgs = processArguments(args, 1);
        if (processedArgs == null) {
            return Mono.just("❌ Пожалуйста, укажите период. Например: /show_price_history 7d");
        }
        
        String period = processedArgs[0];
        // Validate period format
        if (!period.matches("^(3h|12h|24h|3d|7d|1M)$")) {
            return Mono.just("❌ Неверный формат периода. Доступные периоды:\n" +
                           "▸ 3h, 12h, 24h - часы\n" +
                           "▸ 3d, 7d, 1M - дни");
        }
        
        return formatPriceHistory(period);
    }

    public String handlerQ() {
        return "Неизвестная команда. Для просмотра списка команд, используйте команду /help.";
    }

    /**
     * Обрабатывает команду /compare_currency
     * @param args Аргументы команды
     * @return Сравнение валют
     */
    public Mono<String> handlerCompareCurrency(String args) {
        // Process arguments - we expect exactly 3 arguments
        String[] processedArgs = processArguments(args, 3);
        if (processedArgs == null) {
            return Mono.just("❌ Пожалуйста, укажите две криптовалюты и период. Например: /compare_currency BTC SOL 7d");
        }
        
        String crypto1Code = processedArgs[0];
        String crypto2Code = processedArgs[1];
        String period = processedArgs[2];
        
        // Validate period format
        if (!period.matches("^(3h|12h|24h|3d|7d|1M)$")) {
            return Mono.just("❌ Неверный формат периода. Доступные периоды:\n" +
                           "▸ 3h, 12h, 24h - часы\n" +
                           "▸ 3d, 7d, 1M - дни");
        }
        
        try {
            // Try to find matching crypto currencies
            final Currency.Crypto crypto1 = findCryptoByCode(crypto1Code);
            final Currency.Crypto crypto2 = findCryptoByCode(crypto2Code);
            
            if (crypto1 == null || crypto2 == null) {
                return Mono.just("❌ Одна или обе криптовалюты не найдены. Используйте следующие коды: " +
                               "BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
            }
            
            return cryptoInformation.compareCurrencies(crypto1, crypto2, period)
                .map(json -> {
                    try {
                        JsonNode node = objectMapper.readTree(json);
                        String symbol1 = node.get("symbol1").get("symbol").asText();
                        String symbol2 = node.get("symbol2").get("symbol").asText();
                        
                        String currentPrice1 = node.get("symbol1").get("currentPrice").asText();
                        String historicPrice1 = node.get("symbol1").get("historicPrice").asText();
                        String change1 = node.get("symbol1").get("change").asText();
                        
                        String currentPrice2 = node.get("symbol2").get("currentPrice").asText();
                        String historicPrice2 = node.get("symbol2").get("historicPrice").asText();
                        String change2 = node.get("symbol2").get("change").asText();
                        
                        String currentRatio = node.get("ratio").get("currentRatio").asText();
                        String historicRatio = node.get("ratio").get("historicRatio").asText();
                        String ratioChange = node.get("ratio").get("change").asText();
                        
                        // Get crypto names
                        String crypto1Name = crypto1.getName();
                        String crypto2Name = crypto2.getName();
                        
                        // Format the output
                        StringBuilder output = new StringBuilder();
                        output.append(String.format("💱  СРАВНЕНИЕ КУРСОВ (%s)\n\n", period));

                        String currentFiatCode = Currency.Fiat.getCurrentFiat().getCode(); 
                        
                        // First crypto
                        output.append(String.format("💰 %s (%s)\n", crypto1Code, crypto1Name));
                        output.append(String.format("📈 Цена сейчас: %s (%s)\n", currentPrice1, currentFiatCode));
                        output.append(String.format("📈 Цена тогда: %s (%s)\n\n", historicPrice1, currentFiatCode));
                        
                        output.append("🆚 \n\n");                       
                        
                        // Second crypto
                        output.append(String.format("💰 %s (%s)\n", crypto2Code, crypto2Name));
                        output.append(String.format("📈 Цена сейчас: %s (%s)\n", currentPrice2, currentFiatCode));
                        output.append(String.format("📈 Цена тогда: %s (%s)\n\n", historicPrice2, currentFiatCode));
                        
                        // Changes
                        output.append("📅 Изменение за период:\n");
                        output.append(String.format("%s: %s%s%% \n", crypto1Code, 
                            Double.parseDouble(change1) >= 0 ? "▲ +" : "▼ ", change1));
                        output.append(String.format("%s: %s%s%% \n\n", crypto2Code,
                            Double.parseDouble(change2) >= 0 ? "▲ +" : "▼ ", change2));
                        
                        // Ratio
                        output.append("💡 Соотношение " + crypto1Code + "/" + crypto2Code + ": \n");
                        output.append(String.format("Сейчас 1 %s = %s %s\n", crypto1Code, currentRatio, crypto2Code));
                        output.append(String.format("Тогда: 1 %s = %s %s\n", crypto1Code, historicRatio, crypto2Code));
                        output.append(String.format("Изменение: %s%s%%", 
                            Double.parseDouble(ratioChange) >= 0 ? "▲ +" : "▼ ", ratioChange));
                        
                        return output.toString();
                    } catch (Exception e) {
                        return "Ошибка при сравнении валют: " + e.getMessage();
                    }
                })
                .onErrorResume(throwable -> Mono.just("Ошибка при сравнении валют: " + throwable.getMessage()));
        } catch (Exception e) {
            return Mono.just("Ошибка при сравнении валют: " + e.getMessage());
        }
    }
    
    /**
     * Находит криптовалюту по коду
     * @param code Код криптовалюты
     * @return Найденная криптовалюта или null
     */
    private Currency.Crypto findCryptoByCode(String code) {
        for (Currency.Crypto c : Currency.Crypto.values()) {
            if (c.getCode().equalsIgnoreCase(code)) {
                return c;
            }
        }
        return null;
    }

    /**
     * Обрабатывает команду /set_alert_val
     * @return Сообщение об ошибке формата
     */
    private String handlerSetAlertVal() {
        return "\u274C Пожалуйста, укажите криптовалюту, максимальную и минимальную цены. Например: /set_alert_VAL BTC 65000 63000";
    }

    public Mono<String> handlerSetAlertVal(String args, String chatId) {
        // Process arguments - we expect exactly 3 arguments
        String[] processedArgs = processArguments(args, 3);
        if (processedArgs == null) {
            return Mono.just(handlerSetAlertVal());
        }
        
        String cryptoCode = processedArgs[0];
        String maxPriceStr = processedArgs[1];
        String minPriceStr = processedArgs[2];
        
        try {
            // Validate crypto code
            Currency.Crypto crypto = null;
            for (Currency.Crypto c : Currency.Crypto.values()) {
                if (c.getCode().equalsIgnoreCase(cryptoCode)) {
                    crypto = c;
                    break;
                }
            }
            
            if (crypto == null) {
                return Mono.just("\u274C Криптовалюта с кодом " + cryptoCode + " не используется. Выберите один из следующих кодов: " +
                       "BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
            }
            
            // Проверяем формат чисел (только положительные числа без знака плюс)
            if (!maxPriceStr.matches("^\\d+(\\.\\d+)?$") || !minPriceStr.matches("^\\d+(\\.\\d+)?$")) {
                return Mono.just("\u274C Некорректный формат цены. Используйте только положительные числа без знака плюс");
            }
            
            // Parse prices
            BigDecimal maxPrice = new BigDecimal(maxPriceStr);
            BigDecimal minPrice = new BigDecimal(minPriceStr);
            
            if (maxPrice.compareTo(BigDecimal.ZERO) <= 0 || minPrice.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.just("\u274C Цены должны быть положительными числами");
            }
            
            if (maxPrice.compareTo(minPrice) <= 0) {
                return Mono.just("\u274C Максимальная цена должна быть больше минимальной");
            }
            
            // Set the alert
            return alertsHandling.setAlertVal(crypto, maxPrice, minPrice, chatId);
            
        } catch (NumberFormatException e) {
            return Mono.just("\u274C Некорректный формат цены. Пожалуйста, используйте числа");
        } catch (Exception e) {
            return Mono.just("Произошла ошибка при установке алерта: " + e.getMessage());
        }
    }

    /**
     * Обрабатывает команду /set_alert_perc
     * @return Сообщение об ошибке формата
     */
    private String handlerSetAlertPerc() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /set_alert_perc <криптовалюта> <процент роста> <процент падения>\n" +
               "Пример: /set_alert_perc BTC 5.25 3.75\n" +
               "Проценты могут быть указаны с точностью до 2 знаков после запятой";
    }

    public Mono<String> handlerSetAlertPerc(String args, String chatId) {
        String[] processedArgs = processArguments(args, 3);
        if (processedArgs == null) {
            return Mono.just(handlerSetAlertPerc());
        }

        String cryptoCode = processedArgs[0].toUpperCase();
        try {
            // Проверяем формат процентов (только положительные числа без знака плюс)
            if (!processedArgs[1].matches("^\\d+(\\.\\d{1,2})?$") || !processedArgs[2].matches("^\\d+(\\.\\d{1,2})?$")) {
                return Mono.just("❌ Неверный формат процента! Используйте только положительные числа без знака плюс, максимум 2 знака после запятой");
            }

            BigDecimal upPercent = new BigDecimal(processedArgs[1]);
            BigDecimal downPercent = new BigDecimal(processedArgs[2]);
            
            if (upPercent.compareTo(BigDecimal.ZERO) <= 0 || downPercent.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.just("❌ Проценты должны быть положительными числами");
            }

            // Проверяем существование криптовалюты
            Currency.Crypto crypto = findCryptoByCode(cryptoCode);
            if (crypto == null) {
                return Mono.just("❌ Неверный код криптовалюты! Используйте: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
            }

            return alertsHandling.setAlertPerc(crypto, upPercent, downPercent, chatId)
                .onErrorResume(e -> Mono.just("❌ Ошибка при установке процентного алерта: " + e.getMessage()));
        } catch (NumberFormatException e) {
            return Mono.just("❌ Неверный формат процента! Используйте только положительные числа без знака плюс");
        }
    }

    /**
     * Обрабатывает команду /set_alert_ema
     * @return Сообщение об ошибке формата
     */
    private String handlerSetAlertEMA() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /set_alert_ema <криптовалюта>\n" +
               "Пример: /set_alert_ema BTC";
    }

    public Mono<String> handlerSetAlertEMA(String args, String chatId) {
        String[] processedArgs = processArguments(args, 1);
        if (processedArgs == null) {
            return Mono.just(handlerSetAlertEMA());
        }

        String cryptoCode = processedArgs[0].toUpperCase();
        // Проверяем существование криптовалюты
        Currency.Crypto crypto = findCryptoByCode(cryptoCode);
        if (crypto == null) {
            return Mono.just("❌ Неверный код криптовалюты! Используйте: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }

        return alertsHandling.setAlertEMA(crypto, chatId)
            .onErrorResume(e -> Mono.just("❌ Ошибка при установке EMA алерта: " + e.getMessage()));
    }

    /**
     * Обрабатывает команду /my_alerts
     * @return Сообщение об ошибке формата
     */
    private String handlerMyAlerts() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /my_alerts\n" +
               "Эта команда покажет все ваши алерты.";
    }

    private static class AlertPricePair {
        private final Notification notification;
        private final String price;

        public AlertPricePair(Notification notification, String price) {
            this.notification = notification;
            this.price = price;
        }

        public Notification getNotification() {
            return notification;
        }

        public String getPrice() {
            return price;
        }
    }

    public Mono<String> handlerMyAlerts(String args, String chatId) {
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just(handlerMyAlerts());
        }

        return notificationService.getAllUserAlerts(chatId)
            .collectList()
            .flatMap(notifications -> {
                if (notifications.isEmpty()) {
                    return Mono.just("📭 У вас нет алертов.");
                }

                StringBuilder message = new StringBuilder("🔔 Ваши алерты:\n\n");
                
                // Создаем список пар (алерт, текущая цена)
                List<Mono<AlertPricePair>> alertPriceMonos = new ArrayList<>();
                
                for (Notification notification : notifications) {
                    String type = notification.getThresholdType().toString();
                    String symbol = notification.getCryptoCurrency().toString();
                    String fiat = notification.getFiatCurrency().getCode();
                    
                    String typeEmoji = switch (type) {
                        case "VALUE" -> "💰";
                        case "PERCENT" -> "📊";
                        case "EMA" -> "📈";
                        default -> "❓";
                    };
                    
                    message.append(String.format("%s %s (%s)\n", typeEmoji, symbol, type));
                    
                    // Получаем текущую цену в USD и конвертируем в валюту из БД
                    alertPriceMonos.add(Mono.zip(
                        priceFetcher.getCurrentPrice(notification.getCryptoCurrency()),
                        currencyConverter.getUsdToFiatRate(notification.getFiatCurrency())
                    ).flatMap(tuple -> {
                        try {
                            JsonNode node = objectMapper.readTree(tuple.getT1());
                            String priceStr = node.get("price").asText();
                            BigDecimal currentPriceUSD = new BigDecimal(priceStr);
                            BigDecimal conversionRate = tuple.getT2();
                            
                            // Конвертируем цену в выбранную пользователем валюту
                            BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                                .setScale(2, RoundingMode.HALF_UP);
                            
                            return Mono.just(new AlertPricePair(notification, 
                                String.format("   Текущая цена: %.2f %s\n", currentPrice, fiat)));
                        } catch (Exception e) {
                            return Mono.just(new AlertPricePair(notification, "   Текущая цена: недоступна\n"));
                        }
                    }));
                    
                    // Показываем всю информацию об алерте
                    switch (type) {
                        case "VALUE" -> {
                            message.append(String.format("   Верхняя граница: %.2f %s\n", 
                                notification.getUpperBoundary(), fiat));
                            message.append(String.format("   Нижняя граница: %.2f %s\n", 
                                notification.getLowerBoundary(), fiat));
                            message.append(String.format("   Начальная цена: %.2f %s\n", 
                                notification.getStartPrice(), fiat));
                        }
                        case "PERCENT" -> {
                            message.append(String.format("   Рост: +%.2f%%\n", 
                                notification.getUpPercent()));
                            message.append(String.format("   Падение: -%.2f%%\n", 
                                notification.getDownPercent()));
                            message.append(String.format("   Верхняя граница: %.2f %s\n", 
                                notification.getUpperBoundary(), fiat));
                            message.append(String.format("   Нижняя граница: %.2f %s\n", 
                                notification.getLowerBoundary(), fiat));
                            message.append(String.format("   Начальная цена: %.2f %s\n", 
                                notification.getStartPrice(), fiat));
                        }
                        case "EMA" -> {
                            message.append(String.format("   Начальное EMA: %.2f %s\n", 
                                notification.getStartEMA(), fiat));
                            message.append(String.format("   Текущее EMA: %.2f %s\n", 
                                notification.getCurrentEMA(), fiat));
                            message.append(String.format("   Начальная цена: %.2f %s\n", 
                                notification.getStartPrice(), fiat));
                        }
                    }
                    
                    // Показываем статус только для VALUE и PERCENT алертов
                    if (type.equals("VALUE") || type.equals("PERCENT")) {
                        String statusEmoji = notification.isActive() ? "✅" : "❌";
                        message.append(String.format("   Статус: %s\n", statusEmoji));
                        if (!notification.isActive() && notification.getTriggerTimestamp() != null) {
                            message.append(String.format("   Время срабатывания: %s\n", 
                                formatDuration(notification.getTriggerTimestamp())));
                        } else {
                            message.append(String.format("   Время работы: %s\n", 
                                formatDuration(notification.getStartTimestamp())));
                        }
                    } else {
                        // Для EMA алертов всегда показываем начало работы
                        message.append(String.format("   Начало работы: %s\n", 
                            formatDuration(notification.getStartTimestamp())));
                    }
                    
                    message.append("\n");
                }
                
                // Ждем получения всех текущих цен
                return Flux.fromIterable(alertPriceMonos)
                    .flatMap(mono -> mono)
                    .collectList()
                    .map(pairs -> {
                        // Сортируем пары по порядку алертов в исходном сообщении
                        pairs.sort((p1, p2) -> {
                            int index1 = notifications.indexOf(p1.getNotification());
                            int index2 = notifications.indexOf(p2.getNotification());
                            return Integer.compare(index1, index2);
                        });
                        
                        // Вставляем цены в сообщение
                        String[] lines = message.toString().split("\n");
                        StringBuilder finalMessage = new StringBuilder();
                        int priceIndex = 0;
                        
                        for (int i = 0; i < lines.length; i++) {
                            String line = lines[i];
                            finalMessage.append(line).append("\n");
                            // Если это строка с типом алерта (содержит эмодзи), добавляем текущую цену
                            if (line.contains("💰") || line.contains("📊") || line.contains("📈")) {
                                finalMessage.append(pairs.get(priceIndex).getPrice());
                                priceIndex++;
                            }
                        }
                        
                        return finalMessage.toString();
                    });
            })
            .onErrorResume(e -> Mono.just("❌ Ошибка при получении списка алертов: " + e.getMessage()));
    }

    /**
     * Форматирует длительность
     * @param timestamp Временная метка
     * @return Отформатированная длительность
     */
    private String formatDuration(long timestamp) {
        java.time.ZonedDateTime dateTime = java.time.Instant.ofEpochSecond(timestamp)
            .atZone(java.time.ZoneId.of("Europe/Moscow"));
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
    }

    /**
     * Обрабатывает команду /delete_alert
     * @return Сообщение об ошибке формата
     */
    private String handlerDeleteAlert() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /delete_alert <тип> <криптовалюта>\n" +
               "Пример: /delete_alert VALUE BTC\n" +
               "Типы алертов: VALUE, PERCENT, EMA";
    }

    public Mono<String> handlerDeleteAlert(String args, String chatId) {
        String[] processedArgs = processArguments(args, 2);
        if (processedArgs == null) {
            return Mono.just(handlerDeleteAlert());
        }

        String type = processedArgs[0].toUpperCase();
        String symbol = processedArgs[1].toUpperCase();

        // Проверяем тип алерта и преобразуем сокращения в полные названия
        String fullType = switch (type) {
            case "VAL" -> "VALUE";
            case "PERC" -> "PERCENT";
            case "EMA" -> "EMA";
            default -> type;
        };

        // Проверяем тип алерта
        if (!fullType.matches("^(VALUE|PERCENT|EMA)$")) {
            return Mono.just("❌ Неверный тип алерта! Используйте: VAL, PERC или EMA");
        }

        // Проверяем существование криптовалюты
        Currency.Crypto crypto = findCryptoByCode(symbol);
        if (crypto == null) {
            return Mono.just("❌ Неверный код криптовалюты! Используйте: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }

        return notificationService.getAllUserAlerts(chatId)
            .filter(alert -> alert.getThresholdType().toString().equals(fullType))
            .filter(alert -> alert.getCryptoCurrency().equals(crypto))
            .next()
            .flatMap(alert -> notificationService.delete(alert)
                .then(Mono.just(String.format("✅ Алерт %s для %s успешно удален!", type, symbol))))
            .switchIfEmpty(Mono.just(String.format("❌ Алерт %s для %s не найден", type, symbol)))
            .onErrorResume(e -> Mono.just("❌ Ошибка при удалении алерта: " + e.getMessage()));
    }

    /**
     * Обрабатывает команду /delete_all_alerts
     * @return Сообщение об ошибке формата
     */
    private String handlerDeleteAllAlerts() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /delete_all_alerts\n" +
               "Эта команда удалит все ваши активные алерты.";
    }

    public Mono<String> handlerDeleteAllAlerts(String args, String chatId) {
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just(handlerDeleteAllAlerts());
        }

        return notificationService.getAllUserAlerts(chatId)
            .collectList()
            .flatMap(alerts -> {
                if (alerts.isEmpty()) {
                    return Mono.just("📭 У вас нет алертов для удаления.");
                }

                return Flux.fromIterable(alerts)
                    .flatMap(alert -> notificationService.delete(alert))
                    .then(Mono.just(String.format("✅ Успешно удалено %d алертов!", alerts.size())));
            })
            .onErrorResume(e -> Mono.just("❌ Ошибка при удалении алертов: " + e.getMessage()));
    }

    /**
     * Проверяет, начал ли пользователь работу с ботом
     * @param chatId ID чата пользователя
     * @return Mono с сообщением об ошибке или пустой Mono
     */
    private Mono<String> checkUserStarted(String chatId) {
        return userService.getUserByChatId(chatId)
            .map(user -> user.isHasStarted())
            .defaultIfEmpty(false)
            .flatMap(hasStarted -> {
                if (!hasStarted) {
                    return Mono.just("❌ Пожалуйста, начните работу с ботом командой /start");
                }
                return Mono.empty();
            });
    }

    /**
     * Обрабатывает команду
     * @param command Команда
     * @param args Аргументы команды
     * @param chatId ID чата пользователя
     * @return Mono<Void>
     */
    public Mono<Void> processCommand(String command, String[] args, String chatId) {
        log.info("Received command: '{}' with args: {}", command, args != null ? String.join(" ", args) : "none");
        
        // Если это не команда /start, проверяем, начал ли пользователь работу с ботом
        if (!command.equals("/start")) {
            return checkUserStarted(chatId)
                .flatMap(errorMessage -> {
                    if (errorMessage != null) {
                        log.info("User has not started: {}", errorMessage);
                        return telegramBotService.sendResponseAsync(chatId, errorMessage);
                    }
                    log.info("User has started, processing command: {}", command);
                    return processCommandInternal(command, args, chatId);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("User has started (empty check), processing command: {}", command);
                    return processCommandInternal(command, args, chatId);
                }));
        }
        log.info("Processing /start command");
        return processCommandInternal(command, args, chatId);
    }

    /**
     * Внутренняя обработка команды
     * @param command Команда
     * @param args Аргументы команды
     * @param chatId ID чата пользователя
     * @return Mono<Void>
     */
    private Mono<Void> processCommandInternal(String command, String[] args, String chatId) {
        String argsStr = args != null ? String.join(" ", args) : "";
        String normalizedCommand = command.trim().toLowerCase();
        
        return switch (normalizedCommand) {
            case "/start" -> {
                yield userService.getUserByChatId(chatId)
                    .flatMap(user -> {
                        // Проверяем, нужно ли обновлять статус
                        if (!user.isHasStarted()) {
                            user.setHasStarted(true);
                            log.info("Setting hasStarted=true for existing user with chatId: {}", chatId);
                            return userService.save(user)
                                .doOnSuccess(u -> log.info("Successfully updated user hasStarted status for chatId: {}", chatId))
                                .doOnError(e -> log.error("Error updating user status: {}", e.getMessage()))
                                .then(telegramBotService.sendResponseAsync(chatId, handlerStart(argsStr)));
                        } else {
                            // Даже если пользователь уже активирован, отправляем стандартное приветствие
                            // вместо сообщения "Вы уже начали работу с ботом"
                            log.info("User already active, sending normal welcome message for chatId: {}", chatId);
                            return telegramBotService.sendResponseAsync(chatId, handlerStart(argsStr));
                        }
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        log.info("Creating brand new user with chatId: {}", chatId);
                        User newUser = new User(null, chatId);
                        newUser.setHasStarted(true);
                        return userService.save(newUser)
                            .doOnSuccess(u -> log.info("Successfully created new user with hasStarted=true"))
                            .doOnError(e -> log.error("Error creating new user: {}", e.getMessage()))
                            .then(telegramBotService.sendResponseAsync(chatId, handlerStart(argsStr)));
                    }));
            }
            case "/help" -> telegramBotService.sendResponseAsync(chatId, handlerHelp(argsStr));
            case "/set_crypto" -> telegramBotService.sendResponseAsync(chatId, handlerSetCrypto(argsStr));
            case "/set_fiat" -> telegramBotService.sendResponseAsync(chatId, handlerSetFiat(argsStr));
            case "/show_current_price" -> handlerShowCurrentPrice(argsStr)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/show_price_history" -> handlerShowPriceHistory(argsStr)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/compare_currency" -> handlerCompareCurrency(argsStr)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/portfolio" -> handlerPortfolio(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/get_portfolio_price" -> handlerGetPortfolioPrice(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/get_assets_price" -> handlerGetPortfolioAssets(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/balance" -> handlerBalance(chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/set_alert_val" -> handlerSetAlertVal(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/set_alert_perc" -> handlerSetAlertPerc(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/set_alert_ema" -> handlerSetAlertEMA(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/my_alerts" -> handlerMyAlerts(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/delete_alert" -> handlerDeleteAlert(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/delete_all_alerts" -> handlerDeleteAllAlerts(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/add" -> handlerAdd(args, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/remove" -> handlerRemove(args, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/delete_asset" -> handlerDeleteAsset(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/delete_all_assets" -> handlerDeleteAllAssets(argsStr, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/authors" -> handlerAuthors(argsStr)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/admin_create" -> handlerAdminCreate(args, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/admin_refresh_key" -> handlerAdminRefreshKey(args, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            case "/admin_deactivate" -> handlerAdminDeactivate(args, chatId)
                .flatMap(response -> telegramBotService.sendResponseAsync(chatId, response));
            default -> telegramBotService.sendResponseAsync(chatId, 
                "❌ Неизвестная команда. Используйте /help для просмотра доступных команд.");
        };
    }

    /**
     * Обрабатывает команду /add
     * @return Сообщение об ошибке формата
     */
    private String handlerAdd() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /add <количество> <криптовалюта>\n" +
               "Пример: /add 0.42 ETH\n" +
               "Доступные криптовалюты: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC";
    }

    public Mono<String> handlerAdd(String[] args, String chatId) {
        if (args.length != 2) {
            return Mono.just("❌ Неверный формат команды!\n" +
                    "Используйте: /add <количество> <криптовалюта>\n" +
                    "Пример: /add 0.42 ETH\n" +
                    "Доступные криптовалюты: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }

        try {
            BigDecimal amount = new BigDecimal(args[0]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.just("❌ Нельзя вводить отрицательное или нулевое значение!");
            }
            String cryptoCode = args[1].toUpperCase();
            Currency.Crypto crypto = Currency.Crypto.valueOf(cryptoCode);

            // Получаем портфель пользователя для конкретной криптовалюты
            List<Portfolio> portfolios = portfolioService.getPortfoliosByChatId(chatId);
            final Portfolio portfolio;
            
            if (portfolios.stream()
                    .filter(p -> p.getCryptoCurrency() != null && p.getCryptoCurrency().equals(crypto))
                    .findFirst()
                    .orElse(null) == null) {
                // Если портфеля для этой криптовалюты нет, создаем новый
                return userService.getUserByChatId(chatId)
                    .flatMap(user -> {
                        if (user == null) {
                            return Mono.just("❌ Пожалуйста, начните с команды /start");
                        }
                        return portfolioService.createPortfolio(chatId)
                            .flatMap(newPortfolio -> {
                                newPortfolio.setCryptoCurrency(crypto);
                                final Portfolio currentPortfolio = newPortfolio;
                                return priceFetcher.getCurrentPrice(crypto)
                                    .flatMap(priceJson -> {
                                        try {
                                            JsonNode node = objectMapper.readTree(priceJson);
                                            BigDecimal currentPrice = new BigDecimal(node.get("price").asText());
                                            BigDecimal totalValue = amount.multiply(currentPrice);
                                            
                                            // Добавляем криптовалюту в портфель
                                            Portfolio updatedPortfolio = portfolioService.addCryptoToPortfolio(currentPortfolio.getId(), crypto, amount);
                                            
                                            // Обновляем последнюю известную цену
                                            updatedPortfolio.setLastCryptoPrice(currentPrice);
                                            updatedPortfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                                            portfolioService.save(updatedPortfolio);
                                            
                                            return Mono.just(String.format("✅ Добавлено %.6f %s\n" +
                                                    "💰 Текущая цена: %.2f %s\n" +
                                                    "💵 Стоимость: %.2f %s\n\n" +
                                                    "📊 Всего в портфеле: %.6f %s\n" +
                                                    "💎 Общая стоимость актива: %.2f %s",
                                                    amount.setScale(6, RoundingMode.FLOOR), crypto.getCode(),
                                                    currentPrice, Currency.Fiat.getCurrentFiat().getCode(),
                                                    totalValue, Currency.Fiat.getCurrentFiat().getCode(),
                                                    amount.setScale(6, RoundingMode.HALF_UP), crypto.getCode(),
                                                    totalValue, Currency.Fiat.getCurrentFiat().getCode()));
                                        } catch (Exception e) {
                                            return Mono.just("❌ Ошибка при обработке цены: " + e.getMessage());
                                        }
                                    });
                            });
                    });
            } else {
                portfolio = portfolios.stream()
                    .filter(p -> p.getCryptoCurrency() != null && p.getCryptoCurrency().equals(crypto))
                    .findFirst()
                    .get();
            }

            // Получаем текущую цену криптовалюты
            return Mono.zip(
                priceFetcher.getCurrentPrice(crypto),
                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
            ).flatMap(tuple -> {
                try {
                    JsonNode node = objectMapper.readTree(tuple.getT1());
                    BigDecimal currentPriceUSD = new BigDecimal(node.get("price").asText());
                    BigDecimal conversionRate = tuple.getT2();
                    
                    // Конвертируем цену в выбранную пользователем валюту
                    BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    // Добавляем криптовалюту в портфель
                    Portfolio updatedPortfolio = portfolioService.addCryptoToPortfolio(portfolio.getId(), crypto, amount);
                    
                    // Обновляем последнюю известную цену (храним в USD)
                    updatedPortfolio.setLastCryptoPrice(currentPriceUSD);
                    updatedPortfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                    portfolioService.save(updatedPortfolio);
                    
                    // Рассчитываем общую стоимость в выбранной валюте
                    BigDecimal totalValue = amount.multiply(currentPrice);
                    BigDecimal totalAmount = updatedPortfolio.getCount();
                    BigDecimal totalPortfolioValue = totalAmount.multiply(currentPrice);
                    
                    return Mono.just(String.format("✅ Добавлено %.6f %s\n" +
                            "💰 Текущая цена: %.2f %s\n" +
                            "💵 Стоимость: %.2f %s\n\n" +
                            "📊 Всего в портфеле: %.6f %s\n" +
                            "💎 Общая стоимость актива: %.2f %s",
                            amount.setScale(6, RoundingMode.HALF_UP), crypto.getCode(),
                            currentPrice, Currency.Fiat.getCurrentFiat().getCode(),
                            totalValue, Currency.Fiat.getCurrentFiat().getCode(),
                            totalAmount.setScale(6, RoundingMode.HALF_UP), crypto.getCode(),
                            totalPortfolioValue, Currency.Fiat.getCurrentFiat().getCode()));
                } catch (Exception e) {
                    log.error("Ошибка при обработке данных о цене", e);
                    return Mono.just("❌ Ошибка при получении цены криптовалюты");
                }
            });
        } catch (NumberFormatException e) {
            return Mono.just("❌ Неверный формат числа");
        } catch (IllegalArgumentException e) {
            return Mono.just("❌ Неизвестная криптовалюта. Доступные: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }
    }

    public Mono<String> handlerBalance(String chatId) {
        List<Portfolio> portfolios = portfolioService.getPortfoliosByChatId(chatId);
        if (portfolios.isEmpty()) {
            return Mono.just("❌ Ваш портфель пуст. Используйте команду /add для создания портфеля и добавления криптовалюты.");
        }

        final Portfolio portfolio = portfolios.get(0);
        if (portfolio.getCryptoCurrency() == null) {
            return Mono.just("Ваш портфель пуст. Используйте команду /add для добавления криптовалюты.");
        }

        return Mono.zip(
            priceFetcher.getCurrentPrice(portfolio.getCryptoCurrency()),
            currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
        ).flatMap(tuple -> {
            try {
                JsonNode node = objectMapper.readTree(tuple.getT1());
                BigDecimal currentPriceUSD = new BigDecimal(node.get("price").asText());
                BigDecimal conversionRate = tuple.getT2();
                
                // Конвертируем цену в выбранную пользователем валюту
                BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                    .setScale(2, RoundingMode.HALF_UP);
                
                // Рассчитываем общую стоимость в выбранной валюте
                BigDecimal totalValue = portfolio.getCount().multiply(currentPrice);
                
                // Обновляем последнюю известную цену (храним в USD)
                Portfolio updatedPortfolio = portfolioService.save(portfolio);
                updatedPortfolio.setLastCryptoPrice(currentPriceUSD);
                updatedPortfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                portfolioService.save(updatedPortfolio);
                
                return Mono.just(String.format("💰 Баланс портфеля:\n\n" +
                        "Криптовалюта: %s\n" +
                        "Количество: %.6f\n" +
                        "Текущая цена: %.2f %s\n" +
                        "Общая стоимость: %.2f %s",
                        portfolio.getCryptoCurrency().getCode(),
                        portfolio.getCount().setScale(6, RoundingMode.HALF_UP),
                        currentPrice, Currency.Fiat.getCurrentFiat().getCode(),
                        totalValue, Currency.Fiat.getCurrentFiat().getCode()));
            } catch (Exception e) {
                log.error("Ошибка при обработке данных о цене", e);
                return Mono.just("❌ Ошибка при получении цены криптовалюты");
            }
        });
    }

    /**
     * Обрабатывает команду /remove
     * @return Сообщение об ошибке формата
     */
    private String handlerRemove() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /remove <количество> <криптовалюта>\n" +
               "Пример: /remove 0.42 ETH\n" +
               "Доступные криптовалюты: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC";
    }

    public Mono<String> handlerRemove(String[] args, String chatId) {
        if (args.length != 2) {
            return Mono.just(handlerRemove());
        }

        try {
            BigDecimal amount = new BigDecimal(args[0]);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return Mono.just("❌ Нельзя вводить отрицательное или нулевое значение!");
            }
            String cryptoCode = args[1].toUpperCase();
            Currency.Crypto crypto = Currency.Crypto.valueOf(cryptoCode);

            // Получаем портфель пользователя для конкретной криптовалюты
            List<Portfolio> portfolios = portfolioService.getPortfoliosByChatId(chatId);
            final Portfolio portfolio = portfolios.stream()
                .filter(p -> p.getCryptoCurrency() != null && p.getCryptoCurrency().equals(crypto))
                .findFirst()
                .orElse(null);

            if (portfolio == null) {
                return Mono.just(String.format("❌ В вашем портфеле нет криптовалюты %s", crypto.getCode()));
            }

            // Проверяем, достаточно ли средств для удаления
            if (portfolio.getCount().compareTo(amount) < 0) {
                return Mono.just(String.format("❌ Недостаточно средств. В портфеле: %.6f %s", 
                    portfolio.getCount().setScale(6, RoundingMode.HALF_UP), crypto.getCode()));
            }

            // Получаем текущую цену криптовалюты
            return Mono.zip(
                priceFetcher.getCurrentPrice(crypto),
                currencyConverter.getUsdToFiatRate(Currency.Fiat.getCurrentFiat())
            ).flatMap(tuple -> {
                try {
                    JsonNode node = objectMapper.readTree(tuple.getT1());
                    BigDecimal currentPriceUSD = new BigDecimal(node.get("price").asText());
                    BigDecimal conversionRate = tuple.getT2();
                    
                    // Конвертируем цену в выбранную пользователем валюту
                    BigDecimal currentPrice = currentPriceUSD.multiply(conversionRate)
                        .setScale(2, RoundingMode.HALF_UP);
                    
                    // Уменьшаем количество криптовалюты в портфеле
                    Portfolio updatedPortfolio = portfolioService.removeCryptoFromPortfolio(portfolio.getId(), crypto, amount);
                    
                    // Обновляем последнюю известную цену (храним в USD)
                    updatedPortfolio.setLastCryptoPrice(currentPriceUSD);
                    updatedPortfolio.setLastCryptoPriceTimestamp(System.currentTimeMillis() / 1000);
                    portfolioService.save(updatedPortfolio);
                    
                    // Рассчитываем общую стоимость в выбранной валюте
                    BigDecimal totalValue = amount.multiply(currentPrice);
                    BigDecimal totalAmount = updatedPortfolio.getCount();
                    BigDecimal totalPortfolioValue = totalAmount.multiply(currentPrice);
                    
                    return Mono.just(String.format("✅ Удалено %.6f %s\n\n" +
                            "📊 Всего в портфеле: %.6f %s\n" +
                            "💎 Общая стоимость актива: %.2f %s",
                            amount.setScale(6, RoundingMode.HALF_UP), crypto.getCode(),
                            totalAmount.setScale(6, RoundingMode.HALF_UP), crypto.getCode(),
                            totalPortfolioValue, Currency.Fiat.getCurrentFiat().getCode()));
                } catch (Exception e) {
                    log.error("Ошибка при обработке данных о цене", e);
                    return Mono.just("❌ Ошибка при получении цены криптовалюты");
                }
            });
        } catch (NumberFormatException e) {
            return Mono.just("❌ Неверный формат числа");
        } catch (IllegalArgumentException e) {
            return Mono.just("❌ Неизвестная криптовалюта. Доступные: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }
    }

    /**
     * Обрабатывает команду /portfolio
     * @param args Аргументы команды
     * @param chatId ID чата пользователя
     * @return Информация о портфеле
     */
    public Mono<String> handlerPortfolio(String args, String chatId) {
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just("❌ Команда /portfolio не принимает аргументов");
        }
        return cryptoPortfolioManager.getPortfolioInfo(chatId);
    }

    /**
     * Обрабатывает команду /get_portfolio_price
     * @param args Аргументы команды
     * @param chatId ID чата пользователя
     * @return Информация о стоимости портфеля
     */
    public Mono<String> handlerGetPortfolioPrice(String args, String chatId) {
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just("❌ Команда /get_portfolio_price не принимает аргументов");
        }
        return cryptoPortfolioManager.getPortfolioPriceInfo(chatId);
    }

    /**
     * Получает информацию об авторах
     * @return Информация об авторах
     */
    private String getAuthorsInfo() {
        return "Вывод списка авторов\n" +
               "• Лобанов Павел (@pmlobanov)\n" +
               "• Черепнов Максим (@aofitasotr)\n" +
               "• Чурова Софья (@SunnyChur)\n" +
               "                          - Студенты 3 курса СПБПУ Петра Великого направления \n " +
               "                          - Математика и компьютерные науки";
    }

    /**
     * Обрабатывает команду /authors
     * @param args Аргументы команды
     * @return Информация об авторах
     */
    public Mono<String> handlerAuthors(String args) {
        log.info("Processing arguments: '{}', expected count: 0", args);
        if (args == null || args.trim().isEmpty()) {
            log.info("Args are null or empty, returning empty array");
            return Mono.just(getAuthorsInfo());
        }
        return Mono.just("❌ Команда /authors не принимает аргументов");
    }

    /**
     * Обрабатывает команду /delete_asset
     * @return Сообщение об ошибке формата
     */
    private String handlerDeleteAsset() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /delete_asset <криптовалюта>\n" +
               "Пример: /delete_asset ETH\n" +
               "Доступные криптовалюты: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC";
    }

    public Mono<String> handlerDeleteAsset(String args, String chatId) {
        String[] processedArgs = processArguments(args, 1);
        if (processedArgs == null) {
            return Mono.just(handlerDeleteAsset());
        }

        try {
            String cryptoCode = processedArgs[0].toUpperCase();
            Currency.Crypto crypto = Currency.Crypto.valueOf(cryptoCode);
            return cryptoPortfolioManager.deleteAsset(chatId, crypto);
        } catch (IllegalArgumentException e) {
            return Mono.just("❌ Неизвестная криптовалюта. Доступные: BTC, ETH, SOL, XRP, ADA, DOGE, AVAX, NEAR, LTC");
        }
    }

    /**
     * Обрабатывает команду /delete_all_assets
     * @return Сообщение об ошибке формата
     */
    private String handlerDeleteAllAssets() {
        return "❌ Неверный формат команды!\n" +
               "Используйте: /delete_all_assets\n" +
               "Эта команда удалит все активы из вашего портфеля.";
    }

    public Mono<String> handlerDeleteAllAssets(String args, String chatId) {
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just(handlerDeleteAllAssets());
        }

        return cryptoPortfolioManager.deleteAllAssets(chatId);
    }

    public Mono<String> handlerGetPortfolioAssets(String args, String chatId) {
        // This command expects 0 arguments
        String[] processedArgs = processArguments(args, 0);
        if (processedArgs == null) {
            return Mono.just("❌ Команда /get_assets_price не принимает аргументов");
        }
        return cryptoPortfolioManager.getAssetsPrice(chatId);
    }

    /**
     * Обрабатывает команду /admin_create
     * Создает нового администратора
     */
    public Mono<String> handlerAdminCreate(String[] args, String chatId) {
        if (args.length != 1) {
            return Mono.just("❌ Неверный формат команды!\n" +
                           "Используйте: /admin_create <username>");
        }

        String username = args[0];
        return adminService.createAdmin(username)
            .map(admin -> "✅ Администратор " + username + " успешно создан.\n" +
                         "API ключ будет отправлен в личном сообщении.")
            .onErrorResume(e -> Mono.just("❌ Ошибка при создании администратора: " + e.getMessage()));
    }

    /**
     * Обрабатывает команду /admin_refresh_key
     * Обновляет API ключ администратора
     */
    public Mono<String> handlerAdminRefreshKey(String[] args, String chatId) {
        if (args.length != 1) {
            return Mono.just("❌ Неверный формат команды!\n" +
                           "Используйте: /admin_refresh_key <username>");
        }

        String username = args[0];
        return adminService.refreshApiKey(username)
            .map(admin -> "✅ API ключ для администратора " + username + " успешно обновлен.\n" +
                         "Новый ключ будет отправлен в личном сообщении.")
            .switchIfEmpty(Mono.just("❌ Администратор не найден: " + username))
            .onErrorResume(e -> Mono.just("❌ Ошибка при обновлении API ключа: " + e.getMessage()));
    }

    /**
     * Обрабатывает команду /admin_deactivate
     * Деактивирует администратора
     */
    public Mono<String> handlerAdminDeactivate(String[] args, String chatId) {
        if (args.length != 1) {
            return Mono.just("❌ Неверный формат команды!\n" +
                           "Используйте: /admin_deactivate <username>");
        }

        String username = args[0];
        return adminService.deactivateAdmin(username)
            .map(success -> success ? 
                "✅ Администратор " + username + " успешно деактивирован." :
                "❌ Администратор не найден: " + username)
            .onErrorResume(e -> Mono.just("❌ Ошибка при деактивации администратора: " + e.getMessage()));
    }
}
