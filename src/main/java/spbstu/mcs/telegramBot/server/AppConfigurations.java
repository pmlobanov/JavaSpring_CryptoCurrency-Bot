package spbstu.mcs.telegramBot.server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import java.util.Map;
import java.util.HashMap;
import org.springframework.scheduling.annotation.EnableScheduling;

import spbstu.mcs.telegramBot.config.VaultConfig;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.service.AlertsHandling;
import spbstu.mcs.telegramBot.security.EncryptionService;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import spbstu.mcs.telegramBot.DB.services.AdminService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.DB.services.ApiKeyService;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import spbstu.mcs.telegramBot.cryptoApi.CryptoInformation;

/**
 * Unified configuration file that organizes multiple configurations into logical sections.
 * This is the main configuration class for the application.
 */
@Configuration
@ComponentScan(
    basePackages = "spbstu.mcs.telegramBot",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "spbstu.mcs.telegramBot.security.SecurityConfig")
    }
)
@PropertySource("classpath:application.properties")
@EnableMongoRepositories(basePackages = "spbstu.mcs.telegramBot.DB.repositories")
@Slf4j
public class AppConfigurations {

    @Autowired
    private VaultConfig vaultConfig;

    /**
     * Enables @Value annotations for resolving placeholders in Spring configuration classes
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        
        // Explicitly set the properties resource
        Resource resource = new ClassPathResource("application.properties");
        configurer.setLocation(resource);
        return configurer;
    }

    @Bean
    public MongoClient mongoClient() {
        String mongoUri = vaultConfig.getSecret("secret/data/crypto-bot", "mongodb.connection-string");
        return MongoClients.create(mongoUri);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        String database = vaultConfig.getSecret("secret/data/crypto-bot", "mongodb.database");
        return new MongoTemplate(new SimpleMongoClientDatabaseFactory(mongoClient(), database));
    }

    @Bean
    public MongoCollection<Document> userCollection(MongoTemplate mongoTemplate) {
        return mongoTemplate.getCollection("users");
    }

    @Bean
    public EncryptionService encryptionService() {
        return new EncryptionService();
    }
    
    /**
     * Web configuration section including server properties and routing
     */
    @Configuration
    public static class WebConfiguration {
        
        @Value("${server.host}")
        private String serverHost;
        
        @Value("${server.port}")
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

        @Bean
        public RouterFunction<ServerResponse> routerFunction() {
            return RouterFunctions.route(RequestPredicates.GET("/healthcheck"),
                _ -> ServerResponse.ok().bodyValue("Server is running"));
        }

        /**
         * Creates ServerApp bean for handling HTTP requests
         */
        @Bean
        public ServerApp serverApp(
                ServerProperties serverProperties,
                RouterFunction<ServerResponse> routes,
                AdminService adminService,
                UserService userService,
                EncryptionService encryptionService,
                ApiKeyService apiKeyService,
                PriceFetcher priceFetcher,
                @Value("${logging.file.path}") String logFilePath,
                @Value("${spring.kafka.bootstrap-servers}") String kafkaBootstrapServers,
                @Value("${spring.kafka.topics.incoming}") String kafkaIncomingTopic,
                @Value("${spring.kafka.topics.outgoing}") String kafkaOutgoingTopic) {
            return new ServerApp(
                serverProperties, routes, adminService, userService,
                encryptionService, apiKeyService, priceFetcher,
                logFilePath, kafkaBootstrapServers, kafkaIncomingTopic, kafkaOutgoingTopic
            );
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

        @Bean
        public String kafkaIncomingTopic() {
            return vaultConfig.getSecret("secret/data/crypto-bot", "kafka.topics.incoming");
        }

        @Bean
        public String kafkaOutgoingTopic() {
            return vaultConfig.getSecret("secret/data/crypto-bot", "kafka.topics.outgoing");
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
        private EncryptionService encryptionService;
        
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
        public CryptoInformation cryptoInformation(ObjectMapper objectMapper, CurrencyConverter currencyConverter, PriceFetcher priceFetcher, UserService userService) {
            return new CryptoInformation(objectMapper, currencyConverter, priceFetcher, userService);
        }
        
        @Bean
        public AlertsHandling alertsHandling(ObjectMapper objectMapper, 
                                            CurrencyConverter currencyConverter,
                                            PriceFetcher priceFetcher,
                                            TelegramBotService telegramBotService,
                                            NotificationService notificationService,
                                            UserService userService) {
            return new AlertsHandling(objectMapper, currencyConverter, priceFetcher, 
                                    telegramBotService, notificationService, userService);
        }
    }
} 