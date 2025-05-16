package spbstu.mcs.telegramBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;
import org.springframework.scheduling.annotation.EnableScheduling;

import spbstu.mcs.telegramBot.controller.AdminController;
import spbstu.mcs.telegramBot.security.AdminAuthMiddleware;
import spbstu.mcs.telegramBot.DB.services.AdminService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.cryptoApi.*;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.repositories.NotificationRepository;

/**
 * Unified configuration file that organizes multiple configurations into logical sections.
 * This reduces the number of configuration files in the codebase by grouping them by functionality.
 */
@Configuration
@Slf4j
public class AppConfigurations {

    @Autowired
    private VaultConfig vaultConfig;
    
    /**
     * Web configuration section including server properties and routing
     */
    @Configuration
    public class WebConfiguration {
        
        @Value("${server.host:localhost}")
        private String serverHost;
        
        @Value("${server.port:8080}")
        private int serverPort;
        
        /**
         * Server properties class that provides host and port configuration
         */
        public static class ServerProperties {
            private final String host;
            private final int port;

            public ServerProperties(String host, int port) {
                this.host = host;
                this.port = port;
            }

            public String host() {
                return host;
            }

            public int port() {
                return port;
            }
            
            @Override
            public String toString() {
                return String.format("ServerProperties[host=%s, port=%d]", host, port);
            }
        }
        
        /**
         * Creates a ServerProperties bean with configured host and port
         */
        @Bean
        public ServerProperties serverProperties() {
            return new ServerProperties(serverHost, serverPort);
        }

        /**
         * Creates a router function for handling HTTP endpoints
         */
        @Bean
        public RouterFunction<ServerResponse> routerFunction(AdminController adminController) {
            return RouterFunctions
                .route()
                // Health check endpoint
                .GET("/api/health", request -> 
                    ServerResponse.ok().bodyValue(Map.of("status", "Server is running", "version", "1.0.0")))
                // Admin routes
                .GET("/api/admin/users", adminController::getUsers)
                .build();
        }
    }
    
    /**
     * Web API and client configuration
     */
    @Configuration
    public class ApiConfiguration {
        
        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
    
    /**
     * Kafka configuration for message streaming
     */
    @Configuration
    @EnableKafka
    public class KafkaConfiguration {
        
        @Bean
        public ProducerFactory<String, String> producerFactory() {
            Map<String, Object> props = new HashMap<>();
            String bootstrapServers = vaultConfig.getSecret("secret/data/crypto-bot", "kafka.bootstrap-servers");
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.RETRIES_CONFIG, 3);
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
            props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
            props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
            props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
            
            return new DefaultKafkaProducerFactory<>(props);
        }

        @Bean
        public KafkaTemplate<String, String> kafkaTemplate() {
            return new KafkaTemplate<>(producerFactory());
        }

        @Bean
        public ConsumerFactory<String, String> consumerFactory() {
            Map<String, Object> props = new HashMap<>();
            String bootstrapServers = vaultConfig.getSecret("secret/data/crypto-bot", "kafka.bootstrap-servers");
            String groupId = vaultConfig.getSecret("secret/data/crypto-bot", "kafka.consumer.group-id");
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
            props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);
            props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
            props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
            props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
            props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            
            return new DefaultKafkaConsumerFactory<>(props);
        }

        @Bean
        public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
            ConcurrentKafkaListenerContainerFactory<String, String> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory());
            return factory;
        }
    }
    
    /**
     * Конфигурация Telegram бота
     */
    @Configuration
    public class TelegramConfiguration {
        
        @Bean
        public TelegramBotsApi telegramBotsApi() throws Exception {
            return new TelegramBotsApi(DefaultBotSession.class);
        }

        @Bean
        public String botToken() {
            return vaultConfig.getSecret("secret/data/crypto-bot", "telegram.bot.token");
        }
        
        @Bean
        public String botUsername() {
            return vaultConfig.getSecret("secret/data/crypto-bot", "telegram.bot.username");
        }
    }
    
    /**
     * Конфигурация безопасности и API криптовалют
     */
    @Configuration
    @EnableScheduling
    public class SecurityConfiguration {
        
        @Autowired
        private UserService userService;
        
        @Autowired
        private AdminService adminService;
        
        @Autowired
        @Lazy
        private AdminAuthMiddleware adminAuthMiddleware;
        
        // ====================== CRYPTO API BEANS ======================
        
        @Bean
        public PriceFetcher priceFetcher(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
            String apiKey = vaultConfig.getSecret("secret/data/crypto-bot", "bingx.api.key");
            String apiSecret = vaultConfig.getSecret("secret/data/crypto-bot", "bingx.api.secret");
            String bingxApiUrl = vaultConfig.getSecret("secret/data/crypto-bot", "bingx.api.url");
            return new PriceFetcher(webClientBuilder, objectMapper, apiKey, apiSecret, bingxApiUrl);
        }
        
        @Bean
        public CurrencyConverter currencyConverter(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
            String currencyApiUrl = vaultConfig.getSecret("secret/data/crypto-bot", "currency.api.url");
            return new CurrencyConverter(webClientBuilder, objectMapper, currencyApiUrl);
        }

        @Bean
        public CryptoInformation cryptoInformation(ObjectMapper objectMapper, CurrencyConverter currencyConverter, PriceFetcher priceFetcher) {
            return new CryptoInformation(objectMapper, currencyConverter, priceFetcher);
        }

        @Bean
        public AlertsHandling alertsHandling(ObjectMapper objectMapper, 
                                            CurrencyConverter currencyConverter,
                                            PriceFetcher priceFetcher,
                                            TelegramBotService telegramBotService,
                                            NotificationService notificationService,
                                            NotificationRepository notificationRepository,
                                            UserService userService) {
            return new AlertsHandling(objectMapper, currencyConverter, priceFetcher, telegramBotService, 
                                    notificationService, notificationRepository, userService);
        }
        
        // ====================== SECURITY BEANS ======================
        
        @Bean
        public CommandSecurityHandler commandSecurityHandler() {
            return new CommandSecurityHandler(adminAuthMiddleware, userService, adminService);
        }

        /**
         * Обработчик для защищенных команд
         */
        public class CommandSecurityHandler {
            private final AdminAuthMiddleware adminAuthMiddleware;
            private final UserService userService;
            private final AdminService adminService;

            public CommandSecurityHandler(AdminAuthMiddleware adminAuthMiddleware, 
                                        UserService userService,
                                        AdminService adminService) {
                this.adminAuthMiddleware = adminAuthMiddleware;
                this.userService = userService;
                this.adminService = adminService;
            }

            /**
             * Проверяет доступ к команде /users
             * @param command команда для проверки
             * @param authHeader заголовок авторизации
             * @return Mono<Boolean> true, если доступ разрешен
             */
            public Mono<Boolean> canAccessUsersCommand(String command, String authHeader) {
                if (!"/users".equals(command)) {
                    return Mono.just(false);
                }

                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.warn("Invalid authorization header format: {}", authHeader);
                    return Mono.just(false);
                }
                
                String apiKey = authHeader.substring("Bearer ".length()).trim();
                log.info("Extracted API key from header: {}", apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4));
                
                return adminService.validateApiKey(apiKey)
                    .map(admin -> {
                        log.info("Admin validated successfully: {}", admin.getUsername());
                        return true;
                    })
                    .defaultIfEmpty(false)
                    .doOnError(error -> log.error("Error validating API key: {}", error.getMessage()));
            }

            /**
             * Получает список всех пользователей (только для администраторов)
             * @return Mono<String> текстовое представление списка пользователей
             */
            public Mono<String> getUsersList() {
                return userService.getAllUsers()
                    .collectList()
                    .map(users -> {
                        StringBuilder sb = new StringBuilder("Список пользователей:\n\n");
                        users.forEach(user -> 
                            sb.append(String.format("ID: %s\nUsername: %s\nСтатус: %s\n\n", 
                                user.getId(), 
                                user.getUserTgName(), 
                                user.isHasStarted() ? "Активен" : "Неактивен"))
                        );
                        return sb.toString();
                    })
                    .doOnError(error -> log.error("Error getting users list: {}", error.getMessage()));
            }
        }
    }
} 