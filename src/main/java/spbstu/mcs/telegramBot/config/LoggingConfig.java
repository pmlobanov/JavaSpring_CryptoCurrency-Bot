package spbstu.mcs.telegramBot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Конфигурация логгирования для записи логов в файл.
 * Использует SLF4J в качестве фасада и java.util.logging для реализации.
 */
@Configuration
public class LoggingConfig {
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
    private static final String LOG_DIR = "/app/logs";
    private static final String LOG_FILE = "application.log";
    private static final String LOG_FILE_PATH = LOG_DIR + "/" + LOG_FILE;

    @PostConstruct
    public void init() {
        logger.info("Initializing LoggingConfig...");
        try {
            // Создаем директорию для логов, если она не существует
            Path logDir = Paths.get(LOG_DIR).toAbsolutePath();
            if (!Files.exists(logDir)) {
                logger.info("Creating log directory at: {}", logDir);
                Files.createDirectories(logDir);
            }
            
            // Проверяем/создаем файл логов
            Path logPath = logDir.resolve(LOG_FILE);
            logger.info("Log file path: {}", logPath);
            
            if (!Files.exists(logPath)) {
                logger.info("Creating log file at: {}", logPath);
                Files.createFile(logPath);
            } else {
                logger.info("Clearing existing log file at: {}", logPath);
                Files.write(logPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("Failed to create/clear log file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize logging", e);
        }
    }

    @Bean("logFilePath")
    public String logFilePath() {
        return LOG_FILE_PATH;
    }
} 