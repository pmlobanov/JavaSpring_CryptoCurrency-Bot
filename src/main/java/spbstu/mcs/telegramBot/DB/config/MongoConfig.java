package spbstu.mcs.telegramBot.DB.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import spbstu.mcs.telegramBot.config.Config;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Конфигурационный класс для подключения к MongoDB.
 * Наследует {@link AbstractMongoClientConfiguration} для базовой конфигурации Spring Data MongoDB.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Определяет имя базы данных</li>
 *   <li>Настраивает подключение к MongoDB серверу</li>
 * </ul>
 */
@Configuration
@EnableMongoRepositories(basePackages = "spbstu.mcs.telegramBot.DB.repositories")
@ComponentScan({
    "spbstu.mcs.telegramBot.DB", 
    "spbstu.mcs.telegramBot.service",
    "spbstu.mcs.telegramBot.cryptoApi"
})
@EnableScheduling
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Возвращает имя базы данных, которая будет использоваться по умолчанию.
     * @return имя базы данных из конфигурации
     */
    @Override
    public String getDatabaseName() {
        return Config.getMongoDbName();
    }

    protected String getMappingBasePackage() {
        return "spbstu.mcs.telegramBot.DB";
    }


    /**
     * Создает и настраивает клиентское подключение к MongoDB.
     * 
     * <p>Подключение устанавливается к MongoDB серверу на основе строки подключения
     * из конфигурационного файла application.yml.</p>
     *
     * @return экземпляр {@link MongoClient} для работы с MongoDB
     * @see MongoClients#create(String)
     */
    @Bean
    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(Config.getMongoConnectionString());
    }
}