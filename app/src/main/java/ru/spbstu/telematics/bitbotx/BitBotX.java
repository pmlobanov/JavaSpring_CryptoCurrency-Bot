package ru.spbstu.telematics.bitbotx;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.spbstu.telematics.bitbotx.config.SecurityConfig;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.Duration;
import ru.spbstu.telematics.bitbotx.model.Currency;
import ru.spbstu.telematics.bitbotx.model.Currency.Crypto;
import ru.spbstu.telematics.bitbotx.model.Currency.Fiat;
import ru.spbstu.telematics.bitbotx.model.AlertType;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Главный класс приложения BitBotX.
 * Запускает контекст Spring и инициализирует все необходимые компоненты.
 */
@Slf4j
@Configuration
public class BitBotX {
    @Autowired
    private PortfolioManagement portfolioManagement;
    
    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        configurer.setLocation(new ClassPathResource("application.properties"));
        return configurer;
    }

    public static void main(String[] args) {
        try (var context = new AnnotationConfigApplicationContext()) {
            log.info("Starting BitBotX application...");
            context.register(SecurityConfig.class);
            context.register(BitBotX.class);
            context.scan("ru.spbstu.telematics.bitbotx");
            log.info("Refreshing Spring context...");
            context.refresh();
            log.info("Spring context initialized successfully");
            
            log.info("Loading application components...");
            CryptoInformation cryptoInformation = context.getBean(CryptoInformation.class);
            AlertsHandling alertsHandling = context.getBean(AlertsHandling.class);
            PortfolioManagement portfolioManagement = context.getBean(PortfolioManagement.class);
            CurrencyConverter currencyConverter = context.getBean(CurrencyConverter.class);
            log.info("All components loaded successfully");
            
            // Устанавливаем валюты по умолчанию
            log.info("Setting default currencies: BTC and EUR");
            Crypto.setCurrentCrypto(Crypto.BTC);
            Fiat.setCurrentFiat(Fiat.EUR);

            log.info("Getting current price for {}-{}...", Crypto.getCurrentCrypto().getCode(), Fiat.getCurrentFiat().getCode());
            cryptoInformation.showCurrentPrice()
            .subscribe(price -> log.info("Current price: {}", price));
    
    // Пример сравнения валют
    log.info("Comparing currencies {} and {} over period of {}...", Crypto.BTC.getCode(), Crypto.ETH.getCode(), "3d");
    cryptoInformation.compareCurrencies(Crypto.BTC, Crypto.ETH, "3d")
            .subscribe(result -> log.info("Currency comparison result: {}", result));
    
    // Пример получения истории цен
    log.info("Getting price history for {} over period of {}...", Crypto.getCurrentCrypto().getCode(), "12h");
    cryptoInformation.showPriceHistory("12h")
            .subscribe(history -> log.info("Price history: {}", history));
            
            // Пример установки алертов
            log.info("Setting up alerts...");
            
            // Устанавливаем алерт по цене для BTC
            String priceAlertResult = alertsHandling.setAlertVal(Crypto.BTC, new BigDecimal("82000"), new BigDecimal("83500"))
                .block(Duration.ofSeconds(2));
            log.info("Price alert set: {}", priceAlertResult);

            log.info("Waiting 5 seconds before new alert...");
             try {
                 Thread.sleep(5000);
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 log.error("Sleep interrupted: {}", e.getMessage());
             }
            
            // Устанавливаем алерт по процентам для ETH
            String percentAlertResult = alertsHandling.setAlertPerc(Crypto.ETH, new BigDecimal("0.01"), new BigDecimal("0.01"))
                .block(Duration.ofSeconds(2));
            log.info("Percent alert set: {}", percentAlertResult);

            log.info("Waiting 5 seconds before new alert...");
             try {
                 Thread.sleep(5000);
             } catch (InterruptedException e) {
                 Thread.currentThread().interrupt();
                 log.error("Sleep interrupted: {}", e.getMessage());
             }
            
            // Устанавливаем EMA алерт для BTC
            String emaAlertResult = alertsHandling.setAlertEMA(Crypto.BTC)
                    .block(Duration.ofSeconds(10));
            log.info("EMA alert set: {}", emaAlertResult);

            log.info("Waiting 5 seconds before portfolio actions...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }

            // Добавляем 0.5 BTC в портфель
            String btcResult = portfolioManagement.add(Crypto.BTC, new BigDecimal("0.5"))
                    .block(Duration.ofSeconds(5));
            log.info("Added BTC to portfolio. Result: {}", btcResult);

            log.info("Waiting 5 seconds before portfolio actions...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }

            // Добавляем 2.0 ETH в портфель
            String ethResult = portfolioManagement.add(Crypto.ETH, new BigDecimal("2.0"))
                    .block(Duration.ofSeconds(5));
            log.info("Added ETH to portfolio. Result: {}", ethResult);

            log.info("Waiting 10 seconds before adding more BTC...");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }

            
            
            // Получаем общую стоимость портфеля
            String portfolioValue = portfolioManagement.getPortfolioPrice()
                    .block(Duration.ofSeconds(5));
            log.info("Portfolio value: {}", portfolioValue);
            
            log.info("Waiting 5 seconds before checking detailed portfolio...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }

             // Добавляем еще 0.3 BTC в портфель (суммируется с предыдущим количеством)
             String moreBtcResult = portfolioManagement.add(Crypto.BTC, new BigDecimal("0.3"))
             .block(Duration.ofSeconds(5));
     log.info("Added more BTC to portfolio. Result: {}", moreBtcResult);
     
     log.info("Waiting 5 seconds before portfolio actions...");
     try {
         Thread.sleep(5000);
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         log.error("Sleep interrupted: {}", e.getMessage());
     }
     
     // Удаляем часть ETH
     String removeEthResult = portfolioManagement.remove(Crypto.ETH, new BigDecimal("1.5"))
             .block(Duration.ofSeconds(5));
     log.info("Removed ETH from portfolio. Result: {}", removeEthResult);
     
            log.info("Waiting 5 minutes before checking portfolio value...");
            try {
                Thread.sleep(300000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }
            
            // Получаем детальную информацию о портфеле с изменениями цен каждого актива
            String portfolioDetails = portfolioManagement.getAssetsPrice()
                    .block(Duration.ofSeconds(5));
            log.info("Portfolio details: {}", portfolioDetails);

            log.info("Waiting 5 minutes before showing alerts...");
            try {
                Thread.sleep(300000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Sleep interrupted: {}", e.getMessage());
            }
            
            // Получаем все установленные алерты
            String allAlerts = alertsHandling.showAlerts()
                    .block(Duration.ofSeconds(2));
            log.info("Current alerts: {}", allAlerts); 

            // Держим программу запущенной
            log.info("BitBotX application is running. Press Ctrl+C to stop.");
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("Application interrupted. Shutting down...");
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Fatal error in BitBotX application: {}", e.getMessage(), e);
        }
        log.info("BitBotX application stopped.");
    }

} 