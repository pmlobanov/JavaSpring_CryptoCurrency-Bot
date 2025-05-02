package ru.spbstu.telematics.bitbotx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import ru.spbstu.telematics.bitbotx.model.Currency.Crypto;

/**
 * Компонент для получения цен с биржи BingX
 */
@Slf4j
@Component
@Scope("singleton")
public class PriceFetcher {
    private static final String BINGX_API_URL = "https://open-api.bingx.com";
    private static final int PRICE_SCALE = 8;
    private static final int PERCENT_SCALE = 2;
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiSecret;

    public PriceFetcher(
            WebClient.Builder webClientBuilder,
            @Value("${bingx.api.key}") String apiKey,
            @Value("${bingx.api.secret}") String apiSecret) {
        this.webClient = webClientBuilder.baseUrl(BINGX_API_URL).build();
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /**
     * Получает текущую цену для указанного символа
     * 
     * @param crypto Символ торговой пары (например, "BTC-USDT")
     * @return Mono с JSON-строкой, содержащей цену и timestamp
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PRICE_READER')")
    public Mono<String> getCurrentPrice(Crypto crypto) {
        String symbol = crypto.getCode() + "-USDT";
        return webClient.get()
                .uri("/openApi/spot/v1/ticker/price?symbol={symbol}", symbol)
                .header("X-BX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        
                        if (!root.has("data") || !root.get("data").isArray() || root.get("data").size() == 0) {
                            throw new RuntimeException("Invalid data structure in response");
                        }
                        
                        JsonNode data = root.get("data").get(0);
                        if (!data.has("trades") || !data.get("trades").isArray() || data.get("trades").size() == 0) {
                            throw new RuntimeException("No trades data available");
                        }
                        
                        JsonNode lastTrade = data.get("trades").get(0);
                        
                        ObjectNode result = objectMapper.createObjectNode();
                        result.put("symbol", symbol);
                        result.put("price", lastTrade.get("price").asText());
                        
                        long timestamp = lastTrade.get("timestamp").asLong();
                        result.put("timestamp", timestamp / 1000);
                        
                        String resultJson = objectMapper.writeValueAsString(result);
                        log.info(resultJson);
                        return resultJson;
                    } catch (Exception e) {
                        log.error("Error processing JSON: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnError(error -> log.error("Error fetching price for {}: {}", symbol, error.getMessage()));
    }

    /**
     * Получает цену символа по времени
     * 
     * @param crypto Криптовалюта
     * @param timestamp Временная метка
     * @return Mono с JSON-строкой, содержащей цену и timestamp
     */
    @Async
    @Transactional
    @PreAuthorize("hasRole('PRICE_READER')")
    public Mono<String> getSymbolPriceByTime(Crypto crypto, long timestamp) {
        String symbol = crypto.getCode() + "-USDT";
        
        // Проверяем, нужно ли конвертировать timestamp в миллисекунды
        long timestampInMillis = timestamp;
        if (timestamp < 1000000000000L) { // Если timestamp меньше 2001 года, значит он в секундах
            timestampInMillis = timestamp * 1000;
        }
        
        // Округляем до начала минуты
        long startTime = (timestampInMillis / 60000) * 60000;
        long endTime = startTime + 60000;

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/openApi/market/his/v1/kline")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", "1m")
                        .queryParam("startTime", startTime)
                        .queryParam("endTime", endTime)
                        .queryParam("limit", 1)
                        .build())
                .header("X-BX-APIKEY", apiKey)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> {
                    try {
                        JsonNode root = objectMapper.readTree(json);
                        
                        if (!root.has("data") || !root.get("data").isArray() || root.get("data").size() == 0) {
                            throw new RuntimeException("No candlestick data available");
                        }
                        
                        JsonNode candle = root.get("data").get(0);
                        
                        ObjectNode result = objectMapper.createObjectNode();
                        result.put("symbol", symbol);
                        result.put("price", candle.get(1).asText());
                        result.put("timestamp", startTime / 1000); // Используем начало минуты
                        
                        String resultJson = objectMapper.writeValueAsString(result);
                        log.info(resultJson);
                        return resultJson;
                    } catch (Exception e) {
                        log.error("Error processing candlestick data: {}", e.getMessage());
                        throw new RuntimeException(e);
                    }
                })
                .doOnError(error -> log.error("Error fetching candlestick data for {}: {}", symbol, error.getMessage()));
    }
}
