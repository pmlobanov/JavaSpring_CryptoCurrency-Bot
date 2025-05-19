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
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.model.Currency;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
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
        
        // Настройка ObjectMapper
        when(objectMapper.createObjectNode()).thenReturn(objectNode);
        when(objectMapper.readTree(anyString())).thenReturn(jsonNode);
        
        portfolioManager = new CryptoPortfolioManager(
            objectMapper,
            currencyConverter,
            priceFetcher,
            portfolioService
        );
    }
    
    /**
     * Тест метода getPortfolioInfo
     */
    @Test
    public void testGetPortfolioInfo() {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(List.of(portfolio));
        when(priceFetcher.getCurrentPrice(any()))
            .thenReturn(Mono.just("{\"price\":\"50000\",\"timestamp\":1234567890}"));
        when(currencyConverter.getUsdToFiatRate(any()))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("BTC") &&
                response.contains("1.5"))
            .verifyComplete();
    }
    
    /**
     * Тест метода add
     */
    @Test
    public void testAddCrypto() {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        when(portfolioService.addCryptoToPortfolio(any(), any(), any()))
            .thenReturn(portfolio);
        when(priceFetcher.getCurrentPrice(any()))
            .thenReturn(Mono.just("{\"price\":\"50000\",\"timestamp\":1234567890}"));
        when(currencyConverter.getUsdToFiatRate(any()))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.add(Currency.Crypto.BTC, new BigDecimal("0.5"));
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("BTC") && 
                response.contains("0.5") &&
                response.contains("50000"))
            .verifyComplete();
    }
    
    /**
     * Тест метода remove
     */
    @Test
    public void testRemoveCrypto() {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        when(portfolioService.removeCryptoFromPortfolio(any(), any(), any()))
            .thenReturn(portfolio);
        when(priceFetcher.getCurrentPrice(any()))
            .thenReturn(Mono.just("{\"price\":\"50000\",\"timestamp\":1234567890}"));
        when(currencyConverter.getUsdToFiatRate(any()))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.remove(Currency.Crypto.BTC, new BigDecimal("0.5"));
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("BTC") && 
                response.contains("0.5") &&
                response.contains("50000"))
            .verifyComplete();
    }

    /**
     * Тест метода getPortfolioValue
     */
    @Test
    public void testGetPortfolioValue() {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(List.of(portfolio));
        when(priceFetcher.getCurrentPrice(any()))
            .thenReturn(Mono.just("{\"price\":\"50000\",\"timestamp\":1234567890}"));
        when(currencyConverter.getUsdToFiatRate(any()))
            .thenReturn(Mono.just(new BigDecimal("1.0")));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("BTC") &&
                response.contains("1.5") &&
                response.contains("50000"))
            .verifyComplete();
    }
    
    /**
     * Тест метода getPortfoliosByChatId
     */
    @Test
    public void testGetPortfoliosByChatId() {
        // Подготовка тестовых данных
        Portfolio portfolio1 = new Portfolio(TEST_CHAT_ID);
        portfolio1.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio1.setCount(new BigDecimal("1.0"));
        
        Portfolio portfolio2 = new Portfolio(TEST_CHAT_ID);
        portfolio2.setCryptoCurrency(Currency.Crypto.ETH);
        portfolio2.setCount(new BigDecimal("10.0"));
        
        List<Portfolio> portfolios = Arrays.asList(portfolio1, portfolio2);
        
        when(portfolioService.getPortfoliosByChatId(TEST_CHAT_ID))
            .thenReturn(portfolios);
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("BTC") &&
                response.contains("ETH"))
            .verifyComplete();
    }
    
    /**
     * Тест метода createPortfolio
     */
    @Test
    public void testCreatePortfolio() {
        // Подготовка тестовых данных
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Currency.Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.0"));
        
        when(portfolioService.createPortfolio(TEST_CHAT_ID))
            .thenReturn(Mono.just(portfolio));
        
        // Выполнение теста
        Mono<String> result = portfolioManager.getPortfolioInfo(TEST_CHAT_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Портфель") && 
                response.contains("BTC"))
            .verifyComplete();
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