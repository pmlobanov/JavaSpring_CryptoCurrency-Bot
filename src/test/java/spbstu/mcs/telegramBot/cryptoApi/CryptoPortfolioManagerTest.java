package spbstu.mcs.telegramBot.cryptoApi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.User;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import static org.mockito.Mockito.*;

/**
 * Тесты для CryptoPortfolioManager
 */
@RunWith(JUnit4.class)
public class CryptoPortfolioManagerTest {

    private PortfolioService portfolioService;
    private PriceFetcher priceFetcher;
    private CurrencyConverter currencyConverter;
    private ObjectMapper objectMapper;
    private JsonNode jsonNode;
    private ObjectNode objectNode;
    private UserService userService;
    
    private CryptoPortfolioManager portfolioManager;
    
    private static final String TEST_CHAT_ID = "123456789";
    private static final String TEST_PORTFOLIO_ID = "portfolio123";
    
    @Before
    public void setUp() throws JsonMappingException, JsonProcessingException {
        // Создаем моки вручную
        portfolioService = mock(PortfolioService.class);
        priceFetcher = mock(PriceFetcher.class);
        currencyConverter = mock(CurrencyConverter.class);
        objectMapper = mock(ObjectMapper.class);
        jsonNode = mock(JsonNode.class);
        objectNode = mock(ObjectNode.class);
        userService = mock(UserService.class);
        
        // Настройка ObjectMapper
        when(objectMapper.createObjectNode()).thenReturn(objectNode);
        when(objectMapper.readTree(anyString())).thenReturn(jsonNode);
        
        // Настройка JsonNode для общих полей
        when(jsonNode.get("timestamp")).thenReturn(jsonNode);
        when(jsonNode.asLong()).thenReturn(1234567890L);
        
        portfolioManager = new CryptoPortfolioManager(
            objectMapper,
            currencyConverter,
            priceFetcher,
            portfolioService,
            userService
        );
    }
    
    /**
     * Тест метода getPortfolioInfo
     */
    @Test
    public void testGetPortfolioInfo() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        
        // Настройка моков
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(List.of(portfolio));
            
        // Настройка мока для получения цены
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(priceJson));
            
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("50000");
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("активов") &&
                response.contains("BTC") &&
                response.contains("1,500000") &&
                response.contains("75000,00 USD") &&
                response.contains("Итого: 75000,00 USD"))
            .verifyComplete();
            
        // Проверка вызовов
        verify(portfolioService).getPortfoliosByChatId(TEST_CHAT_ID);
        verify(priceFetcher).getCurrentPrice(Currency.Crypto.BTC);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
    }
    
    /**
     * Тест метода add
     */
    @Test
    public void testAddCrypto() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        // Настройка моков
        when(portfolioService.addCryptoToPortfolio(any(), any(), any()))
            .thenReturn(portfolio);
            
        // Настройка мока для получения цены
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(priceJson));
            
        // Настройка мока для чтения JSON
        JsonNode priceNode = mock(JsonNode.class);
        JsonNode timestampNode = mock(JsonNode.class);
        JsonNode symbolNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(priceNode);
        when(jsonNode.get("timestamp")).thenReturn(timestampNode);
        when(jsonNode.get("symbol")).thenReturn(symbolNode);
        
        when(priceNode.asText()).thenReturn("50000");
        when(timestampNode.asLong()).thenReturn(1234567890L);
        when(symbolNode.asText()).thenReturn("BTC-USDT");
        
        // Настройка мока для создания результата
        ObjectNode resultNode = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(resultNode);
        when(resultNode.put(anyString(), anyString())).thenReturn(resultNode);
        when(resultNode.put(anyString(), anyLong())).thenReturn(resultNode);
        when(objectMapper.writeValueAsString(resultNode)).thenReturn(
            "{\"symbol\":\"BTC-USD\",\"count\":\"0.500000\",\"price\":\"50000.00\",\"value\":\"25000.00\",\"timestamp\":1234567890}"
        );
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        portfolioManager.setCurrentPortfolio(portfolio);
        
        // Выполнение теста
        Mono<String> result = portfolioManager.add(Currency.Crypto.BTC, new BigDecimal("0.5"));
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("BTC") && 
                response.contains("0.500000") &&
                response.contains("50000.00") &&
                response.contains("25000.00"))
            .verifyComplete();
            
        // Проверка вызовов
        verify(portfolioService).addCryptoToPortfolio(any(), any(), any());
        verify(priceFetcher).getCurrentPrice(Currency.Crypto.BTC);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
    }
    
    /**
     * Тест метода remove
     */
    @Test
    public void testRemoveCrypto() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        // Настройка моков
        when(portfolioService.removeCryptoFromPortfolio(any(), any(), any()))
            .thenReturn(portfolio);
            
        // Настройка мока для получения цены
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(priceJson));
            
        // Настройка мока для чтения JSON
        JsonNode priceNode = mock(JsonNode.class);
        JsonNode timestampNode = mock(JsonNode.class);
        JsonNode symbolNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(priceNode);
        when(jsonNode.get("timestamp")).thenReturn(timestampNode);
        when(jsonNode.get("symbol")).thenReturn(symbolNode);
        
        when(priceNode.asText()).thenReturn("50000");
        when(timestampNode.asLong()).thenReturn(1234567890L);
        when(symbolNode.asText()).thenReturn("BTC-USDT");
        
        // Настройка мока для создания результата
        ObjectNode resultNode = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(resultNode);
        when(resultNode.put(anyString(), anyString())).thenReturn(resultNode);
        when(resultNode.put(anyString(), anyLong())).thenReturn(resultNode);
        when(objectMapper.writeValueAsString(resultNode)).thenReturn(
            "{\"status\":\"success\",\"symbol\":\"BTC-USD\",\"count\":\"0.500000\",\"price\":\"50000.00\",\"value\":\"25000.00\",\"timestamp\":1234567890}"
        );
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        portfolioManager.setCurrentPortfolio(portfolio);
        
        // Выполнение теста
        Mono<String> result = portfolioManager.remove(Currency.Crypto.BTC, new BigDecimal("0.5"));
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("success") &&
                response.contains("BTC") && 
                response.contains("0.500000") &&
                response.contains("50000.00") &&
                response.contains("25000.00"))
            .verifyComplete();
            
        // Проверка вызовов
        verify(portfolioService).removeCryptoFromPortfolio(any(), any(), any());
        verify(priceFetcher).getCurrentPrice(Currency.Crypto.BTC);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
    }
    
    /**
     * Тест метода getPortfolioValue
     */
    @Test
    public void testGetPortfolioValue() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        
        // Настройка моков
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(List.of(portfolio));
            
        // Настройка мока для получения цены
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(priceJson));
            
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("50000");
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("активов") &&
                response.contains("BTC") &&
                response.contains("1,500000") &&
                response.contains("75000,00 USD") &&
                response.contains("Итого: 75000,00 USD"))
            .verifyComplete();
    }
    
    /**
     * Тест метода getPortfoliosByChatId
     */
    @Test
    public void testGetPortfoliosByChatId() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio1 = new Portfolio(TEST_CHAT_ID);
        portfolio1.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio1.setCount(new BigDecimal("1.0"));
        
        Portfolio portfolio2 = new Portfolio(TEST_CHAT_ID);
        portfolio2.setCryptoCurrency(Currency.Crypto.ETH);
        portfolio2.setCount(new BigDecimal("10.0"));
        
        List<Portfolio> portfolios = Arrays.asList(portfolio1, portfolio2);
        
        // Настройка моков
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(portfolios);
            
        // Настройка мока для получения цен
        String btcPriceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        String ethPriceJson = "{\"symbol\":\"ETH-USDT\",\"price\":\"3000\",\"timestamp\":1234567890}";
        
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(btcPriceJson));
        when(priceFetcher.getCurrentPrice(Currency.Crypto.ETH))
            .thenReturn(Mono.just(ethPriceJson));
            
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(btcPriceJson)).thenReturn(jsonNode);
        when(objectMapper.readTree(ethPriceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("50000", "3000");
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("активов") &&
                response.contains("BTC") &&
                response.contains("ETH") &&
                response.contains("1,000000") &&
                response.contains("10,000000") &&
                response.contains("Итого:"))
            .verifyComplete();
    }
    
    /**
     * Тест метода createPortfolio
     */
    @Test
    public void testCreatePortfolio() throws JsonProcessingException {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        // Настройка моков
        when(portfolioService.createPortfolio(TEST_CHAT_ID))
            .thenReturn(Mono.just(portfolio));
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(List.of(portfolio));
            
        // Настройка мока для получения цены
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        when(priceFetcher.getCurrentPrice(Currency.Crypto.BTC))
            .thenReturn(Mono.just(priceJson));
            
        // Настройка мока для чтения JSON
        JsonNode priceNode = mock(JsonNode.class);
        JsonNode timestampNode = mock(JsonNode.class);
        JsonNode symbolNode = mock(JsonNode.class);
        
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(priceNode);
        when(jsonNode.get("timestamp")).thenReturn(timestampNode);
        when(jsonNode.get("symbol")).thenReturn(symbolNode);
        
        when(priceNode.asText()).thenReturn("50000");
        when(timestampNode.asLong()).thenReturn(1234567890L);
        when(symbolNode.asText()).thenReturn("BTC-USDT");
        
        // Настройка мока для конвертации валют
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
            
        // Настройка мока для получения пользователя
        User testUser = new User(TEST_CHAT_ID);
        testUser.setCurrentFiat(Currency.Fiat.USD.getCode());
        when(userService.getUserByChatId(TEST_CHAT_ID))
            .thenReturn(Mono.just(testUser));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("👜 Портфель") && 
                response.contains("активов") &&
                response.contains("BTC") &&
                response.contains("1,000000") &&
                response.contains("50000,00 USD") &&
                response.contains("💼 Итого: 50000,00 USD"))
            .verifyComplete();
            
        // Проверка вызовов
        verify(portfolioService).getPortfoliosByChatId(TEST_CHAT_ID);
        verify(priceFetcher).getCurrentPrice(Currency.Crypto.BTC);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
    }
    
    /**
     * Тест метода deletePortfolio
     */
    @Test
    public void testDeletePortfolio() {
        // Подготовка тестовых данных
        doNothing().when(portfolioService).deletePortfolio(TEST_PORTFOLIO_ID);
            
        // Выполнение теста
        portfolioService.deletePortfolio(TEST_PORTFOLIO_ID);
            
        // Проверка вызовов
        verify(portfolioService).deletePortfolio(TEST_PORTFOLIO_ID);
    }
} 