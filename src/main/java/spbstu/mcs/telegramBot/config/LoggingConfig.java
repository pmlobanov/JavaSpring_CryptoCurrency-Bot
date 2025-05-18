package spbstu.mcs.telegramBot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Конфигурация логгирования для записи логов в файл.
 * Использует SLF4J в качестве фасада и java.util.logging для реализации.
 */
@Configuration
public class LoggingConfig {
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
    private static final String LOG_FILE_PATH = "./logs/application.log";

    @PostConstruct
    public void init() {
        logger.info("Initializing LoggingConfig...");
        try {
            Path logPath = Paths.get(LOG_FILE_PATH).toAbsolutePath();
            logger.info("Log file path: {}", logPath);
            
            if (!Files.exists(logPath)) {
                logger.info("Log file does not exist, creating directory and file...");
                // Создаем директорию, если она не существует
                Files.createDirectories(logPath.getParent());
                // Создаем пустой файл логов
                Files.createFile(logPath);
                logger.info("Created log file at: {}", logPath);
            } else {
                logger.info("Using existing log file at: {}", logPath);
            }
        } catch (IOException e) {
            logger.error("Failed to create log file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize logging", e);
        }
    }

    @Bean("logFilePath")
    public String logFilePath() {
        return LOG_FILE_PATH;
    }
} 