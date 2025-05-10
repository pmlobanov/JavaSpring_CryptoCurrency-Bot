package spbstu.mcs.telegramBot.cryptoApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import spbstu.mcs.telegramBot.model.Currency.Fiat;

import java.math.BigDecimal;

/**
 * Сервис для получения курсов валют.
 */
@Service
@Slf4j
public class CurrencyConverter {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    @Autowired
    public CurrencyConverter(WebClient.Builder webClientBuilder,
                           ObjectMapper objectMapper,
                           @Value("${currency.api.url:https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies}") String apiUrl) {
        this.webClient = webClientBuilder.baseUrl(apiUrl).build();
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
    }

    /**
     * Получает курс USD к указанной фиатной валюте.
     *
     * @param fiat Фиатная валюта
     * @return Mono с курсом обмена
     */
    public Mono<BigDecimal> getUsdToFiatRate(Fiat fiat) {
        return webClient.get()
                .uri("/usd.json")
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(response -> {
                    try {
                        JsonNode rootNode = objectMapper.readTree(response);
                        JsonNode usdNode = rootNode.path("usd");
                        
                        if (!usdNode.has(fiat.getCode().toLowerCase())) {
                            return Mono.error(new RuntimeException(
                                    "Валюта " + fiat.getCode() + " не найдена в ответе"));
                        }
                        
                        BigDecimal rate = new BigDecimal(usdNode.path(fiat.getCode().toLowerCase()).asText());
                        // Округляем до 4 знаков после запятой
                        rate = rate.setScale(4, java.math.RoundingMode.HALF_UP);
                        return Mono.just(rate);
                    } catch (Exception e) {
                        log.error("Error processing API response: {}", e.getMessage());
                        return Mono.error(e);
                    }
                })
                .doOnError(e -> log.error("Error getting USD to {} rate: {}", 
                        fiat.getCode(), e.getMessage()));
    }
} 