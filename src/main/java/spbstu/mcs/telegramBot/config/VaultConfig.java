package spbstu.mcs.telegramBot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.support.VaultResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for Vault integration and secret management.
 * Handles Vault connection, authentication, and provides secret values as Spring beans.
 */
@Configuration
public class VaultConfig implements ApplicationContextAware, BeanPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VaultConfig.class);
    private final VaultTemplate vaultTemplate;
    private static final String VAULT_PROPERTY_SOURCE_NAME = "vaultPropertySource";
    private static final String SECRET_PATH = "secret/data/crypto-bot";
    private ApplicationContext applicationContext;

    public VaultConfig() {
        String vaultUrl = System.getenv().getOrDefault("VAULT_ADDR", "http://vault:8200");
        String token = System.getenv().getOrDefault("VAULT_TOKEN", "root");
        
        logger.info("Initializing Vault connection with URL: {}", vaultUrl);
        
        VaultEndpoint endpoint = new VaultEndpoint();
        
        // Parse the vault URL to get host and port
        try {
            URI uri = URI.create(vaultUrl);
            String scheme = uri.getScheme();
            endpoint.setScheme(scheme);
            endpoint.setHost(uri.getHost());
            
            // Use the port from URI or default to 8200
            int port = uri.getPort() != -1 ? uri.getPort() : 8200;
            endpoint.setPort(port);
            
            logger.info("Connecting to Vault at {}://{}:{} with token: {}", 
                    scheme, endpoint.getHost(), endpoint.getPort(), 
                    token.substring(0, Math.min(4, token.length())) + "***");
        } catch (Exception e) {
            logger.error("Failed to parse Vault URL: {}", vaultUrl, e);
            throw new RuntimeException("Invalid Vault URL: " + vaultUrl, e);
        }
        
        this.vaultTemplate = new VaultTemplate(endpoint, new TokenAuthentication(token));
        
        // Test the connection
        try {
            VaultResponse health = vaultTemplate.read("sys/health");
            if (health != null) {
                logger.info("Successfully connected to Vault. Status: {}", health.getData());
            } else {
                logger.warn("Connected to Vault but health check returned no data.");
            }
        } catch (Exception e) {
            logger.error("Failed to connect to Vault: {}", e.getMessage(), e);
        }
        
        // Загружаем секреты сразу при создании класса
        loadVaultSecrets();
    }

    @Bean
    public VaultTemplate vaultTemplate() {
        return vaultTemplate;
    }

    /**
     * Telegram Bot configuration beans
     */
    @Bean
    public String telegramBotToken() {
        return getSecret(SECRET_PATH, "telegram.bot.token");
    }

    @Bean
    public String telegramBotUsername() {
        return getSecret(SECRET_PATH, "telegram.bot.username");
    }

    /**
     * Kafka configuration beans
     */
    @Bean
    public String kafkaBootstrapServers() {
        return getSecret(SECRET_PATH, "kafka.bootstrap-servers");
    }

    @Bean
    public String kafkaConsumerGroupId() {
        return getSecret(SECRET_PATH, "kafka.consumer.group-id");
    }

    @Bean
    public String kafkaIncomingTopic() {
        return getSecret(SECRET_PATH, "kafka.topics.incoming");
    }

    @Bean
    public String kafkaOutgoingTopic() {
        return getSecret(SECRET_PATH, "kafka.topics.outgoing");
    }

    /**
     * MongoDB configuration beans
     */
    @Bean
    public String mongoDbName() {
        return getSecret(SECRET_PATH, "mongodb.database");
    }

    @Bean
    public String mongoConnectionString() {
        return getSecret(SECRET_PATH, "mongodb.connection-string");
    }

    /**
     * Retrieves a secret from Vault
     */
    public String getSecret(String path, String key) {
        logger.debug("Fetching secret from Vault: {}/{}", path, key);
        try {
            VaultResponse response = vaultTemplate.read(path);
            if (response != null && response.getData() != null) {
                Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
                if (data != null && data.containsKey(key)) {
                    Object value = data.get(key);
                    if (value != null) {
                        String secretValue = value.toString();
                        // Mask the retrieved secret for logging
                        String maskedValue = secretValue.length() > 6 
                            ? secretValue.substring(0, 3) + "***" 
                            : "***";
                        logger.debug("Retrieved secret {}/{}: {}", path, key, maskedValue);
                        return secretValue;
                    }
                }
                logger.warn("Key '{}' not found in Vault at path '{}'", key, path);
            } else {
                logger.warn("No data returned from Vault for path '{}'", path);
            }
            return null;
        } catch (Exception e) {
            logger.error("Error fetching secret {}/{} from Vault: {}", path, key, e.getMessage());
            return null;
        }
    }
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }
    
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Загружаем секреты перед инициализацией любых бинов
        if (applicationContext != null && !isVaultSecretsLoaded) {
            loadVaultSecrets();
        }
        return bean;
    }
    
    private boolean isVaultSecretsLoaded = false;
    
    /**
     * Загружает секреты из Vault и добавляет их в PropertySource Spring
     */
    private void loadVaultSecrets() {
        if (isVaultSecretsLoaded || applicationContext == null) {
            return;
        }
        
        try {
            // Получаем ConfigurableEnvironment из контекста
            if (applicationContext instanceof ConfigurableApplicationContext) {
                ConfigurableEnvironment environment = 
                    ((ConfigurableApplicationContext) applicationContext).getEnvironment();
                MutablePropertySources propertySources = environment.getPropertySources();
                
                // Проверяем, не добавлен ли уже источник свойств из Vault
                if (propertySources.contains(VAULT_PROPERTY_SOURCE_NAME)) {
                    return;
                }
                
                logger.info("Loading secrets from Vault into PropertySource...");
                
                // Получаем все секреты из Vault
                VaultResponse response = vaultTemplate.read(SECRET_PATH);
                if (response != null && response.getData() != null) {
                    Map<String, Object> secrets = new HashMap<>();
                    
                    // Map data format for K/V version 2 (extract from 'data' field)
                    Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
                    if (data != null) {
                        // Маппим секреты в формат property source
                        for (Map.Entry<String, Object> entry : data.entrySet()) {
                            String key = entry.getKey();
                            String value = String.valueOf(entry.getValue());
                            secrets.put(key, value);
                            
                            // Log a masked version of the secret
                            String maskedValue = value.length() > 6 
                                ? value.substring(0, 3) + "***" 
                                : "***";
                            logger.debug("Loaded secret: {} = {}", key, maskedValue);
                        }
                        
                        // Добавляем их как источник свойств с высоким приоритетом
                        propertySources.addFirst(new MapPropertySource(VAULT_PROPERTY_SOURCE_NAME, secrets));
                        isVaultSecretsLoaded = true;
                        logger.info("Successfully loaded {} secrets from Vault", secrets.size());
                    } else {
                        logger.warn("Secret at {} found but 'data' field is missing or empty", SECRET_PATH);
                    }
                } else {
                    logger.error("Failed to load secrets from Vault path: {}", SECRET_PATH);
                }
            }
        } catch (Exception e) {
            logger.error("Error loading properties from Vault: {}", e.getMessage(), e);
        }
    }
} 