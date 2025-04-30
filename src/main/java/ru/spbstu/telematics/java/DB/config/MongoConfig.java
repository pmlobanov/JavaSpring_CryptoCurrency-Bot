package ru.spbstu.telematics.java.DB.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

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
@EnableMongoRepositories(basePackages = "ru.spbstu.telematics.java.DB.repositories")
@ComponentScan("ru.spbstu.telematics.java.DB") // Добавьте эту строку
public class MongoConfig extends AbstractMongoClientConfiguration {

    /**
     * Возвращает имя базы данных, которая будет использоваться по умолчанию.
     * @return имя базы данных ("BitBotDB")
     */
    @Override
    public String getDatabaseName() {
        return "BitBotDB";
    }

    protected String getMappingBasePackage() {
        return "ru.spbstu.telematics.java.DB";
    }


    /**
     * Создает и настраивает клиентское подключение к MongoDB.
     *
     * <p>Подключение устанавливается к локальному MongoDB серверу:</p>
     * <ul>
     *   <li>Хост: localhost</li>
     *   <li>Порт: 27017 (стандартный порт MongoDB)</li>
     * </ul>
     *
     * <p>Пример URI подключения: "mongodb://localhost:27017"</p>
     *
     * @return экземпляр {@link MongoClient} для работы с MongoDB
     * @see MongoClients#create(String)
     */
    @Bean
    @Override
    public MongoClient mongoClient() {
        return MongoClients.create("mongodb://localhost:27017");
    }
}