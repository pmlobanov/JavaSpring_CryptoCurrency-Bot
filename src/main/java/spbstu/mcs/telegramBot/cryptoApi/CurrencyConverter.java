package spbstu.mcs.telegramBot.cryptoApi;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import spbstu.mcs.telegramBot.cryptoApi.model.Currency.Fiat;
import spbstu.mcs.telegramBot.service.KafkaConsumerService;

import java.math.BigDecimal;

/**
 * Сервис для получения курсов валют.
 */

@Component
@Scope("singleton")
public class CurrencyConverter {
    private static final Logger log = LoggerFactory.getLogger(BitBotX.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiUrl;

    public CurrencyConverter(
            @Value("${currency.api.url}") String apiUrl,
            ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .build();
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