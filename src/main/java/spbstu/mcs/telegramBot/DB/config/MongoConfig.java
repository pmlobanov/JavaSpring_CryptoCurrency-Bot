package spbstu.mcs.telegramBot.DB.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import spbstu.mcs.telegramBot.config.VaultConfig;

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

    @Value("${mongodb.connection-string:}")
    private String connectionString;
    
    @Value("${mongodb.database:}")
    private String databaseName;
    
    @Autowired
    private VaultConfig vaultConfig;

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Возвращает имя базы данных, которая будет использоваться по умолчанию.
     * @return имя базы данных из конфигурации
     */
    @Override
    protected String getDatabaseName() {
        if (databaseName == null || databaseName.isEmpty()) {
            databaseName = vaultConfig.getSecret("secret/data/crypto-bot", "mongodb.database");
            System.out.println("Получено имя базы данных из Vault: " + databaseName);
        } else {
            System.out.println("Получено имя базы данных из PropertySource: " + databaseName);
        }
        return databaseName;
    }

    protected String getMappingBasePackage() {
        return "spbstu.mcs.telegramBot.DB";
    }

    /**
     * Создает и настраивает клиентское подключение к MongoDB.
     * 
     * <p>Подключение устанавливается к MongoDB серверу на основе строки подключения
     * из конфигурационного файла application.properties.</p>
     *
     * @return экземпляр {@link MongoClient} для работы с MongoDB
     * @see MongoClients#create(String)
     */
    @Override
    public MongoClient mongoClient() {
        System.out.println("Создание MongoClient...");
        
        if (connectionString == null || connectionString.isEmpty()) {
            connectionString = vaultConfig.getSecret("secret/data/crypto-bot", "mongodb.connection-string");
            System.out.println("Получена строка подключения из Vault: " + connectionString);
        } else {
            System.out.println("Получена строка подключения из PropertySource: " + connectionString);
        }
        
        if (connectionString == null || !connectionString.startsWith("mongodb://")) {
            System.err.println("ОШИБКА: Неверная строка подключения MongoDB: " + connectionString);
            throw new IllegalArgumentException("Неверная строка подключения MongoDB: " + connectionString);
        }
        
        return MongoClients.create(connectionString);
    }
    
    /**
     * Явное создание бина MongoDatabaseFactory для MongoTemplate
     */
    @Bean
    public MongoDatabaseFactory mongoDbFactory() {
        MongoClient client = mongoClient();
        return new SimpleMongoClientDatabaseFactory(client, getDatabaseName());
    }
    
    /**
     * Явное создание бина MongoTemplate
     */
    @Bean
    public MongoTemplate mongoTemplate() {
        System.out.println("Создание MongoTemplate с базой данных: " + getDatabaseName());
        return new MongoTemplate(mongoDbFactory());
    }
    
    /**
     * Создает коллекцию пользователей для MongoDB.
     * 
     * @return экземпляр {@link MongoCollection<Document>} для работы с пользователями
     */
    @Bean
    public MongoCollection<Document> userCollection() {
        MongoClient client = mongoClient();
        MongoDatabase database = client.getDatabase(getDatabaseName());
        return database.getCollection("users");
    }
}