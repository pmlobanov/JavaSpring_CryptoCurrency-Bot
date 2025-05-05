package spbstu.mcs.telegramBot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import spbstu.mcs.telegramBot.cryptoApi.*;


@Configuration
@EnableScheduling
public class SecurityConfig {

    @Value("${bingx.api.key}")
    private String apiKey;

    @Value("${bingx.api.secret}")
    private String apiSecret;

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .baseUrl("https://open-api.bingx.com");
    }

    @Bean
    public PriceFetcher priceFetcher(WebClient.Builder webClientBuilder) {
        return new PriceFetcher(webClientBuilder, apiKey, apiSecret);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CryptoInformation cryptoInformation(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        return new CryptoInformation(priceFetcher, objectMapper, currencyConverter);
    }

    @Bean
    public AlertsHandling alertsHandling(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        return new AlertsHandling(priceFetcher, objectMapper, currencyConverter);
    }

    @Bean
    public PortfolioManagement portfolioManagement(PriceFetcher priceFetcher, ObjectMapper objectMapper, CurrencyConverter currencyConverter) {
        return new PortfolioManagement(priceFetcher, objectMapper, currencyConverter);
    }
}