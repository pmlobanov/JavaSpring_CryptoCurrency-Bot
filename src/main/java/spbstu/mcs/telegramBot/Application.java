package spbstu.mcs.telegramBot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.modulith.Modulithic;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import spbstu.mcs.telegramBot.service.KafkaConsumerService;
import spbstu.mcs.telegramBot.service.KafkaProducerService;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.server.ServerApp;
import spbstu.mcs.telegramBot.config.AppConfigurations;
import java.util.List;

@Modulithic
public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            // Start Spring context with our configuration
            ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(AppConfigurations.class);
            logger.info("Spring context initialized successfully");
            
            // Get services from context
            TelegramBotService botService = context.getBean(TelegramBotService.class);
            KafkaConsumerService kafkaConsumer = context.getBean(KafkaConsumerService.class);
            KafkaProducerService kafkaProducer = context.getBean(KafkaProducerService.class);
            
            // Initialize admin accounts and generate API keys
            initializeAdminAccounts(context);
            
            // Initialize and start HTTP server
            initializeAndStartHttpServer(context);
            
            // Initialize TelegramBotsApi
            TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(botService);
            logger.info("Telegram bot registered successfully");
            
            // Start Kafka consumer in a separate thread
            Thread consumerThread = new Thread(kafkaConsumer, "kafka-consumer-thread");
            consumerThread.setDaemon(true);
            consumerThread.start();
            logger.info("Kafka consumer started");

            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, closing application...");
                kafkaProducer.close();
                kafkaConsumer.stop();
                
                // Stop HTTP server
                ServerApp serverApp = context.getBean(ServerApp.class);
                if (serverApp != null) {
                    serverApp.stop();
                    logger.info("HTTP server stopped");
                }
                
                context.close();
                logger.info("Application shutdown complete");
            }));
            
            logger.info("Application started successfully");
            
            // Keep the application running
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.error("Error starting application:", e);
            System.exit(1);
        }
    }
    
    /**
     * Initializes and starts the HTTP server
     * @param context Spring context
     */
    private static void initializeAndStartHttpServer(ConfigurableApplicationContext context) {
        try {
            ServerApp serverApp = context.getBean(ServerApp.class);
            serverApp.start();
            
            // Get server properties from bean
            AppConfigurations.WebConfiguration.ServerProperties serverProperties = 
                context.getBean(AppConfigurations.WebConfiguration.ServerProperties.class);
            String serverUrl = String.format("http://%s:%d", serverProperties.host(), serverProperties.port());
            
            logger.info("HTTP server started on {}", serverUrl);
            logger.info("Health check endpoint available at {}/healthcheck", serverUrl);
            logger.info("Admin API available at {}/admin/...", serverUrl);
        } catch (Exception e) {
            logger.error("Failed to start HTTP server: {}", e.getMessage(), e);
            logger.warn("Application will continue without HTTP server");
        }
    }
    
    /**
     * Initializes admin accounts and logs their API keys
     * @param context Application context
     */
    private static void initializeAdminAccounts(ConfigurableApplicationContext context) {
        try {
            // Get AdminService and additional services from the context
            spbstu.mcs.telegramBot.DB.services.AdminService adminService = 
                context.getBean(spbstu.mcs.telegramBot.DB.services.AdminService.class);
            
            // List of admin usernames to create
            String[] adminUsernames = {"admin1", "admin2", "admin3"};
            
            logger.info("------------------------------------------------------------");
            logger.info("INITIALIZING ADMINISTRATOR ACCOUNTS");
            logger.info("------------------------------------------------------------");
            
            for (String username : adminUsernames) {
                // Check if admin already exists to avoid duplicates
                if (adminService.findByUsername(username).isEmpty()) {
                    // Создаем и сохраняем админа в БД
                    logger.info("Creating new admin: {} in MongoDB collection 'admins'", username);
                    spbstu.mcs.telegramBot.model.Admin admin =
                        adminService.createAdmin(username).block();
                    
                    if (admin != null) {
                        // The plain-text API key is temporarily stored in encryptedApiKey
                        String apiKey = admin.getEncryptedApiKey();
                        logger.info("Admin successfully created with ID: {}", admin.getId());
                        logger.info("Admin username: {}", username);
                        logger.info("API Key: {}", apiKey);
                        logger.info("IMPORTANT: Store this key securely - it will not be retrievable later!");
                        
                        // Проверка наличия админа в БД после создания
                        if (adminService.findByUsername(username).isPresent()) {
                            logger.info("Verified: Admin {} successfully saved to MongoDB", username);
                        } else {
                            logger.error("Failed to verify admin {} in database after creation", username);
                        }
                        
                        logger.info("------------------------------------------------------------");
                    } else {
                        logger.error("Failed to create admin: {}", username);
                    }
                } else {
                    logger.info("Admin already exists in DB: {}", username);
                    // В существующем случае, информируем пользователя о наличии в БД
                    logger.info("Admin record found in MongoDB collection 'admins'");
                }
            }
            
            // Проверка общего количества админов в базе данных
            List<spbstu.mcs.telegramBot.model.Admin> allAdmins = adminService.findAll();
            logger.info("Total admins in database: {}", allAdmins.size());
            logger.info("------------------------------------------------------------");
            
        } catch (Exception e) {
            logger.error("Error initializing admin accounts:", e);
            logger.error("Exception type: {}", e.getClass().getName());
            logger.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                logger.error("Caused by: {}", e.getCause().getMessage());
            }
        }
    }
}