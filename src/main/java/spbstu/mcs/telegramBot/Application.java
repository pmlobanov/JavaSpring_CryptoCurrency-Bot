package spbstu.mcs.telegramBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import spbstu.mcs.telegramBot.DB.ApplicationContextManager;
import spbstu.mcs.telegramBot.config.Config;
import spbstu.mcs.telegramBot.service.KafkaConsumerService;
import spbstu.mcs.telegramBot.service.KafkaProducerService;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.service.BotCommand;
import spbstu.mcs.telegramBot.DB.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.CountDownLatch;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private static ApplicationContextManager contextManager;

    public static void main(String[] args) {
        try {
            // Инициализация MongoDB через Spring контекст
            contextManager = ApplicationContextManager.create();
            logger.info("MongoDB connection initialized");
            
            // Initialize components
            KafkaProducerService producer = new KafkaProducerService();
            BotCommand botCommand = contextManager.getBotCommand();
            UserService userService = contextManager.getUserService();
            TelegramBotService bot = new TelegramBotService(
                    Config.getTelegramBotToken(),
                    Config.getTelegramBotUsername(),
                    producer,
                    botCommand,
                    userService
            );

            // Start Kafka consumer for both incoming and outgoing messages
            KafkaConsumerService consumer = new KafkaConsumerService(bot);
            Thread consumerThread = new Thread(consumer, "kafka-consumer-thread");
            consumerThread.setDaemon(true);
            consumerThread.start();

            // Register bot
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(bot);

            logger.info("Bot successfully started!");
            System.out.println("Bot successfully started!");

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received...");
                System.out.println("Shutdown signal received...");
                producer.close();
                consumer.stop();
                if (contextManager != null) {
                    contextManager.close();
                    logger.info("MongoDB connection closed");
                }
                shutdownLatch.countDown();
            }));

            // Wait for shutdown signal
            shutdownLatch.await();
        } catch (Exception e) {
            logger.error("Error starting application:", e);
            System.err.println("Error starting application:");
            e.printStackTrace();
            System.exit(1);
        }
    }
}