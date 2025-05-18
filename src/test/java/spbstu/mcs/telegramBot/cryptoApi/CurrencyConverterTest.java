package spbstu.mcs.telegramBot.cryptoApi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.io.IOException;

import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Currency.Fiat;
import spbstu.mcs.telegramBot.config.VaultConfig;

/**
 * Тесты для CurrencyConverter
 */
@RunWith(JUnit4.class)
public class CurrencyConverterTest {
    
    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;
    private WebClient.RequestHeadersSpec requestHeadersSpec;
    private WebClient.ResponseSpec responseSpec;
    private ObjectMapper objectMapper;
    private JsonNode rootNode;
    private JsonNode usdNode;
    private VaultConfig vaultConfig;
    
    private CurrencyConverter currencyConverter;
    
    private static final String SECRET_PATH = "secret/data/crypto-bot";
    
    @Before
    public void setUp() {
        // Создаем моки
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);
        objectMapper = mock(ObjectMapper.class);
        rootNode = mock(JsonNode.class);
        usdNode = mock(JsonNode.class);
        
        // Создаем WebClient напрямую
        webClient = mock(WebClient.class);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        
        // Создаем WebClient.Builder
        webClientBuilder = mock(WebClient.Builder.class);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // Настройка для URI и ответов
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        
        // Используем фиксированное значение URL для тестов
        String apiUrl = "https://test-currency-api.com";
        
        // Create CurrencyConverter with mocked dependencies
        currencyConverter = new CurrencyConverter(webClientBuilder, objectMapper, apiUrl);
    }
    
    /**
     * Тест метода getUsdToFiatRate
     */
    @Test
    public void testGetUsdToFiatRate() throws IOException {
        try {
            // Test data
            Fiat fiat = Fiat.EUR;
            String responseJson = "{\"usd\":{\"eur\":\"0.92\"}}";
            
            // Mocking response - используем doReturn для предотвращения реальных вызовов
            doReturn(Mono.just(responseJson)).when(responseSpec).bodyToMono(String.class);
            
            // Mocking ObjectMapper
            doReturn(rootNode).when(objectMapper).readTree(responseJson);
            doReturn(usdNode).when(rootNode).path("usd");
            doReturn(true).when(usdNode).has("eur");
            doReturn(usdNode).when(usdNode).path("eur");
            doReturn("0.92").when(usdNode).asText();
            
            // Execute test method с таймаутом
            Mono<BigDecimal> result = currencyConverter.getUsdToFiatRate(fiat)
                .timeout(java.time.Duration.ofSeconds(3));
            
            // Verify с таймаутом
            StepVerifier.create(result)
                .expectNext(new BigDecimal("0.9200"))
                .expectComplete()
                .verify(java.time.Duration.ofSeconds(5));
            
            // Verify method calls
            verify(webClient).get();
            verify(requestHeadersUriSpec).uri("/usd.json");
            verify(responseSpec).bodyToMono(String.class);
            verify(objectMapper).readTree(responseJson);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
}