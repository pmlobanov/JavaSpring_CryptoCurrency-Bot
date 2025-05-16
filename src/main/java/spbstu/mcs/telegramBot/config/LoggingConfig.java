package spbstu.mcs.telegramBot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.FileOutputStream;

/**
 * Конфигурация логгирования для записи логов в файл.
 * Использует SLF4J в качестве фасада и java.util.logging для реализации.
 */
@Configuration
public class LoggingConfig {
    private static final Logger logger = LoggerFactory.getLogger(LoggingConfig.class);
    private static FileHandler fileHandler;
    private static String logFilePath = "logs/application.log"; // Путь по умолчанию
    
    @Value("${logging.file.path:logs}")
    private String configuredLogPath;
    
    @Value("${logging.file.name:application.log}")
    private String configuredLogName;
    
    @PostConstruct
    public void init() {
        synchronized (LoggingConfig.class) {
            logFilePath = configuredLogPath + "/" + configuredLogName;
            File logDir = new File(configuredLogPath);
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (created) {
                    logger.info("Created log directory: {}", configuredLogPath);
                } else {
                    logger.warn("Failed to create log directory: {}", configuredLogPath);
                }
            }
            logger.info("Log file path set to: {}", logFilePath);
            // Инициализация логирования после установки пути
            initializeLogging();
        }
    }
    
    /**
     * Инициализирует логгирование в файл.
     */
    public void initializeLogging() {
        try {
            // Используем уже сконфигурированный путь из Spring
            File logDir = new File(logFilePath).getParentFile();
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs();
            }
            
            // Создаем обработчик файлов с лимитом в 5MB и 3 файла ротации
            fileHandler = new FileHandler(logFilePath, 5 * 1024 * 1024, 3, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.ALL);
            
            // Подключаем обработчик к корневому логгеру
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.addHandler(fileHandler);
            
            // Перенаправляем System.out и System.err в логгер
            System.setOut(createLoggingProxy(System.out, Level.INFO));
            System.setErr(createLoggingProxy(System.err, Level.SEVERE));
            
            logger.info("Logging initialized successfully. Logs are being written to: {}", logFilePath);
        } catch (IOException e) {
            System.err.println("Error initializing logging: " + e.getMessage());
        }
    }
    
    /**
     * Создает прокси для перенаправления вывода в лог файл.
     */
    private static PrintStream createLoggingProxy(final PrintStream realPrintStream, final Level level) {
        return new PrintStream(realPrintStream) {
            private final java.util.logging.Logger logger = java.util.logging.Logger.getLogger("SystemOutput");
            
            @Override
            public void print(String string) {
                realPrintStream.print(string);
                logger.log(level, string);
            }
            
            @Override
            public void println(String string) {
                realPrintStream.println(string);
                logger.log(level, string);
            }
            
            private void logToFile(String timestamp, String level, String message) {
                try {
                    FileOutputStream fos = new FileOutputStream(logFilePath, true);
                    PrintStream ps = new PrintStream(fos);
                    ps.println("[" + timestamp + " " + level + "] " + message);
                    ps.close();
                    fos.close();
                } catch (Exception e) {
                    realPrintStream.println("Error writing to log file: " + e.getMessage());
                }
            }
        };
    }
    
    /**
     * Возвращает путь к файлу логов как бин
     */
    @Bean
    public String logFilePath() {
        return logFilePath;
    }
    
    /**
     * Возвращает обработчик файлов как бин
     */
    @Bean
    public FileHandler fileHandler() {
        return fileHandler;
    }
    
    /**
     * Возвращает путь к файлу логов.
     */
    public static String getLogFilePath() {
        return logFilePath;
    }
} 