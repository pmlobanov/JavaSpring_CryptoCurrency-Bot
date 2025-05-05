package spbstu.mcs.telegramBot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import spbstu.mcs.telegramBot.DB.ApplicationContextManager;
import spbstu.mcs.telegramBot.DB.collections.User;
import spbstu.mcs.telegramBot.DB.currencies.CryptoCurrency;
import spbstu.mcs.telegramBot.DB.currencies.FiatCurrency;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.DB.services.TrackedCryptoService;
import spbstu.mcs.telegramBot.DB.services.UserService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TelegramBotService extends TelegramLongPollingBot {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    private final String botToken;
    private final String botUsername;
    private final KafkaProducerService kafkaProducer;
    private final MessageProcessorService messageProcessor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private UserService userService;
    private final PortfolioService portfolioService;
    private final NotificationService notificationService;
    private final TrackedCryptoService trackedCryptoService;
    private volatile boolean running = true;

    public TelegramBotService(String botToken,
                              String botUsername,
                              KafkaProducerService kafkaProducer,
                              UserService userService,
                              PortfolioService portfolioService,
                              NotificationService notificationService,
                              TrackedCryptoService trackedCryptoService) {
        super(botToken);
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.kafkaProducer = kafkaProducer;
        this.userService = userService;
        this.portfolioService = portfolioService;
        this.notificationService = notificationService;
        this.trackedCryptoService = trackedCryptoService;
        this.messageProcessor = new MessageProcessorService();
    }

    public void stop() {
        running = false;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {

        CompletableFuture.runAsync(() -> {
            if (update.hasMessage() && update.getMessage().hasText()) {
                String text = update.getMessage().getText();
                String chatId = update.getMessage().getChatId().toString();
                String userId = update.getMessage().getFrom().getId().toString();

                // Аутентификация пользователя
                User user = authenticateUser(userId, update.getMessage().getFrom().getUserName());


                kafkaProducer.sendMessageAsync(chatId, userId, text);
                String response = processCommand(text);

            }
        }, executor);
    }

    private User authenticateUser(String userId, String username) {
        User user;
        try {
            user = userService.findByUserTgName(userId);
        }
        catch (Exception e) {
                logger.error("Error acurred: "+e.getMessage());
                logger.info("Creating new user: {}", userId);
                user = userService.createUser(
                        userId,
                        FiatCurrency.USD,
                        CryptoCurrency.BTC
                );
        }
            return user;
        /* catch (Exception e) {
            logger.error("Authentication error for user {}", userId, e);
            throw new RuntimeException("User authentication failed");
        }*/
    }

    public String processKafkaMessage(String jsonMessage) {
        return messageProcessor.processMessage(jsonMessage);
    }

    private String processCommand(String text) {
        if (text.startsWith("/")) {
            String command = text.split(" ")[0].toLowerCase();
            switch (command) {
                case "/start":
                    return BotCommand.handlerStart();
                case "/help":
                    return BotCommand.handlerHelp();
                default:
                    return BotCommand.handlerQ();
            }
        }
        return BotCommand.handlerQ();
    }

    void sendResponseAsync(String chatId, String text) {
        CompletableFuture.runAsync(() -> {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);

            try {
                execute(message);
            } catch (TelegramApiException e) {
                logger.error("Failed to send message to chat {}: {}", chatId, e.getMessage());
            }
        }, executor);
    }
}