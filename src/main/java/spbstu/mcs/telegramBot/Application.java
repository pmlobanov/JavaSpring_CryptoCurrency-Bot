package spbstu.mcs.telegramBot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import spbstu.mcs.telegramBot.config.Config;
import spbstu.mcs.telegramBot.service.KafkaConsumerService;
import spbstu.mcs.telegramBot.service.KafkaProducerService;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.DB.ApplicationContextManager;

import java.util.concurrent.CountDownLatch;

public class Application {
    private static final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public static void main(String[] args) {
        try (ApplicationContextManager contextManager = ApplicationContextManager.create()) {
            // 1. Инициализация сервисов
            KafkaProducerService producer = new KafkaProducerService();

            // 2. Создание экземпляра бота с передачей зависимостей
            TelegramBotService bot = createBotInstance(producer, contextManager);

            // 3. Запуск Kafka Consumer в отдельном потоке
            startKafkaConsumer(bot);

            // 4. Регистрация бота
            registerBot(bot);

            // 5. Обработка завершения работы
            addShutdownHook(producer, bot);

            System.out.println("Bot successfully started!");

            // Ожидание сигнала завершения
            shutdownLatch.await();
        } catch (Exception e) {
            handleStartupError(e);
        }
    }

    private static TelegramBotService createBotInstance(KafkaProducerService producer,
                                                        ApplicationContextManager contextManager) {
        return new TelegramBotService(
                Config.getTelegramBotToken(),
                Config.getTelegramBotUsername(),
                producer,
                contextManager.getUserService(),
                contextManager.getPortfolioService(),
                contextManager.getNotificationService(),
                contextManager.getTrackedCryptoService()
        );
    }

    private static void startKafkaConsumer(TelegramBotService bot) {
        KafkaConsumerService consumer = new KafkaConsumerService(bot);
        Thread consumerThread = new Thread(consumer, "kafka-consumer-thread");
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    private static void registerBot(TelegramBotService bot) throws Exception {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);
    }

    private static void addShutdownHook(KafkaProducerService producer, TelegramBotService bot) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received...");
            producer.close();
            bot.stop();
            shutdownLatch.countDown();
        }));
    }

    private static void handleStartupError(Exception e) {
        System.err.println("Error starting application:");
        e.printStackTrace();
        System.exit(1);
    }
}