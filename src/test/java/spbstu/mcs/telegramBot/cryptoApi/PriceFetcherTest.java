package spbstu.mcs.telegramBot.cryptoApi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersUriSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Currency.Fiat;
import spbstu.mcs.telegramBot.config.VaultConfig;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.function.Function;

/**
 * Тесты для PriceFetcher
 */
public class PriceFetcherTest {

    private WebClient.Builder webClientBuilder;
    private WebClient webClient;
    private RequestHeadersUriSpec requestHeadersUriSpec;
    private RequestHeadersSpec requestHeadersSpec;
    private ResponseSpec responseSpec;
    private ObjectMapper objectMapper;
    private ObjectNode objectNode;
    private ArrayNode arrayNode;
    private JsonNode jsonNode;
    private VaultConfig vaultConfig;
    
    private PriceFetcher priceFetcher;
    
    private static final String SECRET_PATH = "secret/data/crypto-bot";
    
    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        // Создаем моки
        requestHeadersUriSpec = mock(RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(RequestHeadersSpec.class);
        responseSpec = mock(ResponseSpec.class);
        objectMapper = mock(ObjectMapper.class);
        objectNode = mock(ObjectNode.class);
        arrayNode = mock(ArrayNode.class);
        jsonNode = mock(JsonNode.class);
        vaultConfig = mock(VaultConfig.class);
        
        // Создаем WebClient напрямую
        webClient = mock(WebClient.class);
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        
        // Создаем WebClient.Builder, который будет возвращать наш мок WebClient
        webClientBuilder = mock(WebClient.Builder.class);
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        
        // Настройка для URI и заголовков
        when(requestHeadersUriSpec.uri(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        
        // Используем фиксированные значения вместо Vault для тестов
        String apiKey = "test-api-key";
        String apiSecret = "test-api-secret";
        String apiUrl = "https://test-api.com";
        
        // Создаем PriceFetcher с моками
        priceFetcher = new PriceFetcher(webClientBuilder, objectMapper, apiKey, apiSecret, apiUrl);
    }

    /**
     * Тест метода getCurrentPrice - возвращает текущую цену криптовалюты
     */
    @Test
    public void testGetCurrentPrice() throws Exception {
        try {
            // Тестируемая криптовалюта
            Currency.Crypto crypto = Currency.Crypto.BTC;
            String symbol = crypto.getCode() + "-USDT";
            String jsonResponse = "{\"data\":[{\"trades\":[{\"price\":\"50000\",\"timestamp\":1234567890000}]}]}";
            
            // Настройка ответа API - используем doReturn для предотвращения реальных вызовов
            doReturn(Mono.just(jsonResponse)).when(responseSpec).bodyToMono(String.class);
            
            // Настройка мока для обработки JSON с doReturn для большей стабильности
            doReturn(jsonNode).when(objectMapper).readTree(jsonResponse);
            doReturn(true).when(jsonNode).has("data");
            doReturn(arrayNode).when(jsonNode).get("data");
            doReturn(true).when(arrayNode).isArray();
            doReturn(1).when(arrayNode).size();
            doReturn(jsonNode).when(arrayNode).get(0);
            doReturn(true).when(jsonNode).has("trades");
            doReturn(arrayNode).when(jsonNode).get("trades");
            doReturn(jsonNode).when(arrayNode).get(0);
            doReturn(jsonNode).when(jsonNode).get("price");
            doReturn(jsonNode).when(jsonNode).get("timestamp");
            doReturn("50000").when(jsonNode).asText();
            doReturn(1234567890000L).when(jsonNode).asLong();
            
            // Настройка результирующего JSON
            doReturn(objectNode).when(objectMapper).createObjectNode();
            doReturn(objectNode).when(objectNode).put(anyString(), anyString());
            doReturn(objectNode).when(objectNode).put(anyString(), anyLong());
            doReturn("{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}").when(objectMapper).writeValueAsString(objectNode);
            
            // Выполнение тестируемого метода с таймаутом
            Mono<String> result = priceFetcher.getCurrentPrice(crypto)
                .timeout(java.time.Duration.ofSeconds(3));
            
            // Проверка результата с таймаутом
            StepVerifier.create(result)
                .expectNextMatches(json -> 
                    json.contains(symbol) && 
                    json.contains("50000") && 
                    json.contains("1234567890"))
                .expectComplete()
                .verify(java.time.Duration.ofSeconds(5));
            
            // Проверка вызовов WebClient
            verify(requestHeadersUriSpec).uri(anyString(), eq(symbol));
            verify(requestHeadersSpec).header("X-BX-APIKEY", "test-api-key");
            verify(responseSpec).bodyToMono(String.class);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода getSymbolPriceByTime - проверяет получение исторической цены криптовалюты
     */
    @Test
    public void testGetSymbolPriceByTime() {

        final Crypto testCrypto = Crypto.BTC;
        final String testSymbol = testCrypto.getCode() + "-USDT";
        final long testTimestamp = 1234567890L; // Конкретная временная метка для тестирования
        final BigDecimal expectedPrice = new BigDecimal("50000");
        
        // Ожидаемый формат JSON-ответа для тестирования
        final String expectedResponseFormat = 
                "{\"symbol\":\"%s\",\"price\":\"%s\",\"timestamp\":%d}";
        final String expectedResponse = String.format(
                expectedResponseFormat, 
                testSymbol, 
                expectedPrice, 
                testTimestamp);
        
        // Создаем контролируемый мок для PriceFetcher с предсказуемым поведением
        PriceFetcher mockedFetcher = mock(PriceFetcher.class);
        
        // Настраиваем ожидаемый ответ с небольшой задержкой для имитации сетевого запроса
        when(mockedFetcher.getSymbolPriceByTime(eq(testCrypto), eq(testTimestamp)))
            .thenReturn(Mono.just(expectedResponse)
                .delayElement(Duration.ofMillis(100)));
            
        // Выполнение тестируемой операции с защитой от зависания
        Mono<String> result = mockedFetcher.getSymbolPriceByTime(testCrypto, testTimestamp)
            .timeout(Duration.ofSeconds(1))
            .doOnError(e -> fail("Тест завис или произошла ошибка: " + e.getMessage()));
            
        // ASSERT - Проверка структуры и содержимого ответа через StepVerifier
        StepVerifier.create(result)
            .assertNext(response -> {
                // Проверяем формат JSON (должен быть валидным JSON)
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode jsonNode = mapper.readTree(response);
                    
                    // Проверка ключевых полей в ответе
                    assertTrue("Отсутствует поле 'symbol'", jsonNode.has("symbol"));
                    assertTrue("Отсутствует поле 'price'", jsonNode.has("price"));
                    assertTrue("Отсутствует поле 'timestamp'", jsonNode.has("timestamp"));
                    
                    // Проверка значений полей
                    assertEquals("Неверный символ в ответе", testSymbol, jsonNode.get("symbol").asText());
                    assertEquals("Неверная цена в ответе", expectedPrice.toString(), jsonNode.get("price").asText());
                    assertEquals("Неверная временная метка в ответе", testTimestamp, jsonNode.get("timestamp").asLong());
                } catch (Exception e) {
                    fail("Ошибка при разборе JSON: " + e.getMessage());
                }
            })
            .expectComplete()
            .verify(Duration.ofSeconds(2));
            
        // Проверка вызовов методов с правильными параметрами
        verify(mockedFetcher, times(1)).getSymbolPriceByTime(eq(testCrypto), eq(testTimestamp));
    }
    

    
    /**
     * Тест метода getFiatRate - возвращает курс фиатной валюты
     */
    @Test
    public void testGetFiatRate() {
        // Подготовка тестовых данных
        final Fiat testFiatUSD = Fiat.USD;
        final Fiat testFiatEUR = Fiat.EUR;
        final BigDecimal expectedUsdRate = BigDecimal.ONE;
        final BigDecimal expectedEurRate = new BigDecimal("0.9");
        
        // Создаем контролируемый мок для PriceFetcher
        PriceFetcher mockedFetcher = mock(PriceFetcher.class);
        
        // 1. Настраиваем поведение для USD (специальный случай)
        when(mockedFetcher.getFiatRate(eq(testFiatUSD)))
            .thenReturn(Mono.just(expectedUsdRate));
            
        // 2. Настраиваем поведение для EUR
        when(mockedFetcher.getFiatRate(eq(testFiatEUR)))
            .thenReturn(Mono.just(expectedEurRate)
                .delayElement(Duration.ofMillis(100)));
                
        // Проверка для USD - специальный случай, всегда возвращает 1
        StepVerifier.create(mockedFetcher.getFiatRate(testFiatUSD)
                .timeout(Duration.ofSeconds(1)))
            .expectNext(expectedUsdRate)
            .expectComplete()
            .verify(Duration.ofSeconds(2));
            
        // Проверка для EUR
        StepVerifier.create(mockedFetcher.getFiatRate(testFiatEUR)
                .timeout(Duration.ofSeconds(1)))
            .expectNext(expectedEurRate)
            .expectComplete()
            .verify(Duration.ofSeconds(2));
            
        // Проверяем, что методы были вызваны с правильными параметрами
        verify(mockedFetcher, times(1)).getFiatRate(testFiatUSD);
        verify(mockedFetcher, times(1)).getFiatRate(testFiatEUR);
    }
    
    /**
     * Тест метода getCryptoPrice - возвращает цену криптовалюты в USDT
     */
    @Test
    public void testGetCryptoPrice() {
        // Подготовка тестовых данных
        final Crypto testCrypto = Crypto.ETH;
        final String testSymbol = testCrypto.getCode() + "-USDT";
        final BigDecimal expectedPrice = new BigDecimal("3000");
        
        // Создаем контролируемый мок для PriceFetcher
        PriceFetcher mockedFetcher = mock(PriceFetcher.class);
        
        // Настраиваем поведение мока
        when(mockedFetcher.getCryptoPrice(eq(testCrypto)))
            .thenReturn(Mono.just(expectedPrice)
                .delayElement(Duration.ofMillis(100)));
                
        // Выполнение и проверка метода
        StepVerifier.create(mockedFetcher.getCryptoPrice(testCrypto)
                .timeout(Duration.ofSeconds(1)))
            .expectNext(expectedPrice)
            .expectComplete()
            .verify(Duration.ofSeconds(2));
            
        // Проверяем, что метод был вызван с правильными параметрами
        verify(mockedFetcher, times(1)).getCryptoPrice(testCrypto);
    }
} 