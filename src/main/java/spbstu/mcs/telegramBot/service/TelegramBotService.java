package spbstu.mcs.telegramBot.service;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.DB.collections.User;
import org.telegram.telegrambots.meta.api.objects.Chat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import reactor.core.publisher.Mono;

/**
 * Сервис для работы с Telegram ботом.
 * Обрабатывает входящие сообщения и команды, отправляет ответы пользователям.
 */
@Service
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    private final String botToken;
    private final String botUsername;
    private final KafkaProducerService kafkaProducer;
    private final BotCommand botCommand;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final UserService userService;

    @Autowired
    public TelegramBotService(@Qualifier("botToken") String botToken, 
                            @Qualifier("botUsername") String botUsername, 
                            KafkaProducerService kafkaProducer,
                            @Lazy BotCommand botCommand,
                            UserService userService) {
        super(botToken);
        logger.info("Initializing TelegramBotService with username: {}", botUsername);
        try {
            this.botToken = botToken;
            this.botUsername = botUsername;
            this.kafkaProducer = kafkaProducer;
            this.botCommand = botCommand;
            this.userService = userService;
            logger.info("TelegramBotService initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize TelegramBotService: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Обрабатывает входящее сообщение.
     * Определяет тип сообщения и вызывает соответствующий обработчик.
     *
     * @param update Объект обновления от Telegram
     */
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            Message message = update.getMessage();
            String messageText = message.getText();
            Long chatIdLong = message.getChatId();
            String chatId = chatIdLong != null ? chatIdLong.toString() : null;
            String userTgName = message.getFrom().getUserName();

            if (chatId == null) {
                logger.error("Failed to get chatId from message");
                return;
            }

            logger.info("Processing message from chatId: {}, userTgName: {}", chatId, userTgName);

            if (messageText.startsWith("/")) {
                processCommand(message)
                    .subscribe(
                        null,
                        error -> logger.error("Error processing command: {}", error.getMessage())
                    );
            } else {
            // Для всех остальных сообщений отправляем в Kafka
            kafkaProducer.sendIncomingMessageAsync(chatId, messageText);
        }
    }
    }

    /**
     * Обрабатывает текстовое сообщение.
     * Проверяет, является ли сообщение командой, и обрабатывает его соответственно.
     *
     * @param message Текстовое сообщение
     * @return Mono<Void>
     */
    private Mono<Void> handleTextMessage(Message message) {
        String text = message.getText();
        String chatId = message.getChatId().toString();
        
        if (text.startsWith("/")) {
            return processCommand(message);
        }
        
        return Mono.just(botCommand.handlerQ())
            .flatMap(response -> sendResponseAsync(chatId, response))
            .then();
    }

    /**
     * Обрабатывает команду.
     * Извлекает команду и аргументы, передает их в BotCommand для обработки.
     *
     * @param message Сообщение с командой
     * @return Mono<Void>
     */
    private Mono<Void> processCommand(Message message) {
        String text = message.getText();
        String chatId = message.getChatId().toString();
            String[] parts = text.split(" ", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1].trim() : "";
            
        Mono<Void> result;
            switch (command) {
                case "/start":
                result = userService.getUserByChatId(chatId)
                        .flatMap(user -> {
                        if (user.isHasStarted()) {
                            return Mono.just("Вы уже начали работу с ботом. Используйте /help для просмотра доступных команд.");
                        }
                            user.setHasStarted(true);
                            return userService.save(user)
                                .then(Mono.just(botCommand.handlerStart(args)));
                        })
                    .switchIfEmpty(Mono.defer(() -> {
                            User newUser = new User(null, chatId);
                            newUser.setHasStarted(true);
                            return userService.save(newUser)
                                .then(Mono.just(botCommand.handlerStart(args)));
                    }))
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/help":
                result = Mono.just(botCommand.handlerHelp(args))
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/set_crypto":
                result = Mono.just(botCommand.handlerSetCrypto(args))
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/set_fiat":
                result = Mono.just(botCommand.handlerSetFiat(args))
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/show_current_price":
                result = botCommand.handlerShowCurrentPrice(args)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/show_price_history":
                result = botCommand.handlerShowPriceHistory(args)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/compare_currency":
                result = botCommand.handlerCompareCurrency(args)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/portfolio":
                result = botCommand.handlerPortfolio(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/get_portfolio_price":
                result = botCommand.handlerGetPortfolioPrice(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/balance":
                result = botCommand.handlerBalance(chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/set_alert_val":
                    if (chatId == null) {
                        logger.error("ChatId is null in set_alert_val command");
                    result = sendErrorAsync(chatId, "Error: Could not determine chat ID");
                } else {
                    logger.info("Processing set_alert_val command for chatId: {}", chatId);
                    result = botCommand.handlerSetAlertVal(args, chatId)
                        .flatMap(response -> sendResponseAsync(chatId, response))
                        .then();
                }
                break;
                
                case "/set_alert_perc":
                    if (chatId == null) {
                        logger.error("ChatId is null in set_alert_perc command");
                    result = sendErrorAsync(chatId, "Error: Could not determine chat ID");
                } else {
                    logger.info("Processing set_alert_perc command for chatId: {}", chatId);
                    result = botCommand.handlerSetAlertPerc(args, chatId)
                        .flatMap(response -> sendResponseAsync(chatId, response))
                        .then();
                }
                break;
                
                case "/set_alert_ema":
                    if (chatId == null) {
                        logger.error("ChatId is null in set_alert_ema command");
                    result = sendErrorAsync(chatId, "Error: Could not determine chat ID");
                } else {
                    logger.info("Processing set_alert_ema command for chatId: {}", chatId);
                    result = botCommand.handlerSetAlertEMA(args, chatId)
                        .flatMap(response -> sendResponseAsync(chatId, response))
                        .then();
                }
                break;
                
                case "/my_alerts":
                result = botCommand.handlerMyAlerts(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/delete_alert":
                result = botCommand.handlerDeleteAlert(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                case "/delete_all_alerts":
                result = botCommand.handlerDeleteAllAlerts(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/add":
                if (chatId == null) {
                    logger.error("ChatId is null in add command");
                    result = sendErrorAsync(chatId, "Error: Could not determine chat ID");
                } else {
                    logger.info("Processing add command for chatId: {}", chatId);
                    result = botCommand.handlerAdd(args.split("\\s+"), chatId)
                        .flatMap(response -> sendResponseAsync(chatId, response))
                        .then();
                }
                break;
                
            case "/remove":
                if (chatId == null) {
                    logger.error("ChatId is null in remove command");
                    result = sendErrorAsync(chatId, "Error: Could not determine chat ID");
                } else {
                    logger.info("Processing remove command for chatId: {}", chatId);
                    result = botCommand.handlerRemove(args.split("\\s+"), chatId)
                        .flatMap(response -> sendResponseAsync(chatId, response))
                        .then();
                }
                break;
                
            case "/delete_asset":
                result = botCommand.handlerDeleteAsset(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/delete_all_assets":
                result = botCommand.handlerDeleteAllAssets(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/get_assets_price":
                result = botCommand.handlerGetPortfolioAssets(args, chatId)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
            case "/authors":
                result = botCommand.handlerAuthors(args)
                    .flatMap(response -> sendResponseAsync(chatId, response))
                    .then();
                break;
                
                default:
                result = sendResponseAsync(chatId, "Неизвестная команда. Для просмотра списка команд, используйте команду /help.")
                    .then();
                break;
        }
        return result;
            }

    /**
     * Отправляет ответ пользователю.
     * Форматирует сообщение и отправляет его через Telegram API.
     *
     * @param chatId ID чата
     * @param text Текст сообщения
     * @return Mono<Void>
     */
    public Mono<Void> sendResponseAsync(String chatId, String text) {
        logger.info("Sending response to chat {}: {}", chatId, text);
        return Mono.fromRunnable(() -> {
            try {
                kafkaProducer.sendOutgoingMessageAsync(chatId, text);
                logger.info("Message sent to Kafka successfully");
            } catch (Exception e) {
                logger.error("Error sending message to Kafka: {}", e.getMessage());
            }
        });
    }

    /**
     * Отправляет сообщение об ошибке пользователю.
     * Форматирует сообщение об ошибке и отправляет его через Telegram API.
     *
     * @param chatId ID чата
     * @param errorMessage Текст сообщения об ошибке
     * @return Mono<Void>
     */
    private Mono<Void> sendErrorAsync(String chatId, String errorMessage) {
        return sendResponseAsync(chatId, "❌ " + errorMessage);
    }

    public Mono<String> processKafkaMessage(String jsonMessage) {
        try {
            // Extract the message text and chatId from JSON
            String text = extractMessageFromJson(jsonMessage);
            String chatId = extractChatIdFromJson(jsonMessage);
            
            if (text == null || chatId == null) {
                logger.error("Failed to extract message text or chatId from JSON: {}", jsonMessage);
                return Mono.just("Error: Invalid message format");
            }
            
            logger.info("Processing Kafka message from chatId: {}", chatId);
            
            // Проверяем, начал ли пользователь работу с ботом
            return userService.getUserByChatId(chatId)
                .<String>flatMap(user -> {
                    // Если это не команда /start и пользователь не начал работу с ботом
                    if (!text.equals("/start") && !user.isHasStarted()) {
                        return sendResponseAsync(chatId, "❌ Пожалуйста, начните работу с ботом командой /start")
                            .then(Mono.just("❌ Пожалуйста, начните работу с ботом командой /start"));
                    }
                    
                    // Обрабатываем команды или сообщения
                    if (text.startsWith("/")) {
                        return processCommand(text, chatId)
                            .then(Mono.just("Команда обработана"));
                    }
                    
                    return Mono.just(botCommand.handlerQ())
                        .flatMap(response -> {
                            return sendResponseAsync(chatId, response)
                                .then(Mono.just(response));
                        });
                })
                .switchIfEmpty(Mono.defer(() -> 
                    sendResponseAsync(chatId, "❌ Пожалуйста, начните работу с ботом командой /start")
                        .then(Mono.just("❌ Пожалуйста, начните работу с ботом командой /start"))
                ));
        } catch (Exception e) {
            logger.error("Error processing Kafka message: {}", e.getMessage());
            return Mono.just("Произошла ошибка при обработке сообщения.");
        }
    }
    
    private String extractMessageFromJson(String messageJson) {
        try {
            int start = messageJson.indexOf("\"message\":\"") + 11;
            if (start < 11) return null;
            int end = messageJson.indexOf("\"", start);
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Error extracting message from JSON: {}", messageJson, e);
            return null;
        }
    }
    
    private String extractChatIdFromJson(String messageJson) {
        try {
            // Сначала пытаемся найти chatId в кавычках
            int start = messageJson.indexOf("\"chatId\":\"") + 10;
            if (start < 10) {
                // Если не найдено в кавычках, пробуем без кавычек
                start = messageJson.indexOf("\"chatId\":") + 9;
                if (start < 9) {
                    logger.error("Не удалось найти chatId в сообщении: {}", messageJson);
                    return null;
                }
                // Находим конец числа
                int end = messageJson.indexOf(",", start);
                if (end == -1) {
                    end = messageJson.indexOf("}", start);
                }
                if (end == -1) {
                    logger.error("Не удалось найти конец chatId в сообщении: {}", messageJson);
                    return null;
                }
                return messageJson.substring(start, end).trim();
            }
            // Если найдено в кавычках, находим закрывающую кавычку
            int end = messageJson.indexOf("\"", start);
            if (end == -1) {
                logger.error("Не удалось найти конец chatId в кавычках: {}", messageJson);
                return null;
            }
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Ошибка при извлечении chatId: {}", e.getMessage());
            return null;
        }
    }

    private String extractMessageTextFromJson(String messageJson) {
        try {
            // Извлекаем текст сообщения и chatId из JSON
            int start = messageJson.indexOf("\"text\":\"") + 8;
            if (start < 8) {
                logger.error("Не удалось найти текст сообщения: {}", messageJson);
                return null;
            }
            int end = messageJson.indexOf("\"", start);
            if (end == -1) {
                logger.error("Не удалось найти конец текста сообщения: {}", messageJson);
                return null;
            }
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Ошибка при извлечении текста сообщения: {}", e.getMessage());
            return null;
        }
    }

    private String extractUserIdFromJson(String messageJson) {
        try {
            int start = messageJson.indexOf("\"userId\":\"") + 10;
            if (start < 10) return null;
            int end = messageJson.indexOf("\"", start);
            return messageJson.substring(start, end);
        } catch (Exception e) {
            logger.error("Error extracting userId from JSON: {}", messageJson, e);
            return null;
        }
    }

    private Mono<Void> processCommand(String text, String chatId) {
        Message message = new Message();
        message.setText(text);
        Chat chat = new Chat();
        chat.setId(Long.parseLong(chatId));
        message.setChat(chat);
        return processCommand(message);
    }
}
