package spbstu.mcs.telegramBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Основной класс конфигурации Spring, который загружает свойства и настраивает сканирование компонентов
 */
@Configuration
@ComponentScan(
    basePackages = "spbstu.mcs.telegramBot",
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.REGEX, pattern = "spbstu.mcs.telegramBot.security.SecurityConfig")
    }
)
@PropertySource("classpath:application.properties")
@Import({
    AppConfigurations.class,
    LoggingConfig.class,
    SchedulingConfig.class,
    VaultConfig.class
})
public class ApplicationConfig {
    
    @Autowired
    private VaultConfig vaultConfig;
    
    /**
     * Включает аннотации @Value для разрешения плейсхолдеров в классах конфигурации Spring
     */
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setIgnoreUnresolvablePlaceholders(true);
        
        // Явно устанавливаем ресурс свойств
        Resource resource = new ClassPathResource("application.properties");
        configurer.setLocation(resource);
        return configurer;
    }
    
    /**
     * Создает бин ServerApp для обработки HTTP-запросов
     */
    @Bean
    public spbstu.mcs.telegramBot.server.ServerApp serverApp(
            spbstu.mcs.telegramBot.DB.services.AdminService adminService,
            spbstu.mcs.telegramBot.DB.services.UserService userService,
            spbstu.mcs.telegramBot.security.AdminAuthMiddleware adminAuthMiddleware,
            spbstu.mcs.telegramBot.security.EncryptionService encryptionService,
            spbstu.mcs.telegramBot.DB.repositories.AdminRepository adminRepository,
            spbstu.mcs.telegramBot.DB.services.ApiKeyService apiKeyService,
            spbstu.mcs.telegramBot.cryptoApi.PriceFetcher priceFetcher,
            AppConfigurations.WebConfiguration.ServerProperties serverProperties,
            RouterFunction<ServerResponse> routerFunction,
            @Qualifier("logFilePath") String logFilePath,
            @Qualifier("kafkaBootstrapServers") String kafkaBootstrapServers,
            @Qualifier("kafkaIncomingTopic") String kafkaIncomingTopic,
            @Qualifier("kafkaOutgoingTopic") String kafkaOutgoingTopic) {
        
        return new spbstu.mcs.telegramBot.server.ServerApp(
            serverProperties, routerFunction, adminService, userService, 
            adminAuthMiddleware, encryptionService, adminRepository, apiKeyService, priceFetcher,
            logFilePath, kafkaBootstrapServers, kafkaIncomingTopic, kafkaOutgoingTopic);
    }
} 