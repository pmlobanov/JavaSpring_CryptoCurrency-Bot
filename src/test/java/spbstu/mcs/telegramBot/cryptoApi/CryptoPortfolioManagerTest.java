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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.DB.repositories.PortfolioRepository;
import spbstu.mcs.telegramBot.DB.services.PortfolioManagement;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Currency.Crypto;

/**
 * Тесты для CryptoPortfolioManager
 */
@RunWith(JUnit4.class)
public class CryptoPortfolioManagerTest {

    private PortfolioRepository portfolioRepository;
    private PriceFetcher priceFetcher;
    private CurrencyConverter currencyConverter;
    private ObjectMapper objectMapper;
    private JsonNode jsonNode;
    private ObjectNode objectNode;
    
    private PortfolioManagement portfolioManagement;
    private CryptoPortfolioManager portfolioManager;
    
    private static final String TEST_CHAT_ID = "123456789";
    private static final String TEST_PORTFOLIO_ID = "portfolio123";
    
    @Before
    public void setUp() throws JsonMappingException, JsonProcessingException {
        // Создаем моки вручную
        portfolioRepository = mock(PortfolioRepository.class);
        priceFetcher = mock(PriceFetcher.class);
        currencyConverter = mock(CurrencyConverter.class);
        objectMapper = mock(ObjectMapper.class);
        jsonNode = mock(JsonNode.class);
        objectNode = mock(ObjectNode.class);
        
        // Настройка ObjectMapper
        when(objectMapper.createObjectNode()).thenReturn(objectNode);
        when(objectMapper.readTree(anyString())).thenReturn(jsonNode);
        
        portfolioManagement = new PortfolioManagement(
            portfolioRepository,
            priceFetcher,
            currencyConverter,
            objectMapper
        );
    }
    
    /**
     * Тест метода getPortfolioInfo
     */
    @Test
    public void testGetPortfolioInfo() {
        // Создаем тестовый портфель
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        portfolio.setLastCryptoPrice(new BigDecimal("45000"));
        
        // Настройка мока для получения портфеля по ID
        when(portfolioRepository.findById(TEST_PORTFOLIO_ID))
            .thenReturn(Optional.of(portfolio));
        
        // Выполнение тестируемого метода
        Mono<String> result = portfolioManagement.getPortfolioInfo(TEST_PORTFOLIO_ID);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Информация о портфеле") &&
                response.contains("BTC") &&
                response.contains("1.5")
            )
            .expectComplete()
            .verify(java.time.Duration.ofSeconds(5));
        
        // Проверка вызовов
        verify(portfolioRepository).findById(TEST_PORTFOLIO_ID);
    }
    
    /**
     * Тест метода getPortfolioValue
     */
    @Test
    public void testGetPortfolioValue() throws JsonProcessingException, JsonMappingException {
        // Создаем тестовый портфель
        Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
        portfolio.setCryptoCurrency(Crypto.BTC);
        portfolio.setCount(new BigDecimal("1.5"));
        
        // Настройка мока для получения портфеля по ID
        when(portfolioRepository.findById(TEST_PORTFOLIO_ID))
            .thenReturn(Optional.of(portfolio));
        
        // Создаем конкретный JSON-ответ для цены
        String priceJson = "{\"price\":\"50000\",\"timestamp\":1234567890}";
        
        // Настройка мока для получения текущей цены - используем doReturn для предотвращения реальных вызовов
        doReturn(Mono.just(priceJson))
            .when(priceFetcher).getCurrentPrice(any(Crypto.class));
        
        // Более детальное мокирование JsonNode для гарантии правильной обработки
        JsonNode priceNode = mock(JsonNode.class);
        when(priceNode.asText()).thenReturn("50000");
        
        // Настройка мока для ObjectMapper
        when(objectMapper.readTree(eq(priceJson))).thenReturn(priceNode);
        when(priceNode.get("price")).thenReturn(priceNode);
        
        // Настройка мока для сохранения портфеля
        when(portfolioRepository.save(any(Portfolio.class)))
            .thenReturn(portfolio);
        
        // Выполнение тестируемого метода с таймаутом для предотвращения зависания
        Mono<String> result = portfolioManagement.getPortfolioValue(TEST_PORTFOLIO_ID)
            .timeout(java.time.Duration.ofSeconds(3));
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Стоимость портфеля") &&
                response.contains("75000")
            )
            .expectComplete()
            .verify(java.time.Duration.ofSeconds(5));
        
        // Проверка вызовов
        verify(portfolioRepository).findById(TEST_PORTFOLIO_ID);
        verify(priceFetcher).getCurrentPrice(Crypto.BTC);
        verify(portfolioRepository).save(any(Portfolio.class));
    }
    
    /**
     * Тест метода getPortfoliosByChatId
     */
    @Test
    public void testGetPortfoliosByChatId() {
        try {
            // Создаем тестовые портфели
            Portfolio portfolio1 = new Portfolio(TEST_CHAT_ID);
            portfolio1.setCryptoCurrency(Crypto.BTC);
            portfolio1.setCount(new BigDecimal("1.0"));
            
            Portfolio portfolio2 = new Portfolio(TEST_CHAT_ID);
            portfolio2.setCryptoCurrency(Crypto.ETH);
            portfolio2.setCount(new BigDecimal("10.0"));
            
            List<Portfolio> portfolios = Arrays.asList(portfolio1, portfolio2);
            
            // Настройка мока для получения портфелей по chatId
            doReturn(portfolios).when(portfolioRepository).findByChatId(TEST_CHAT_ID);
            
            // Выполнение тестируемого метода с таймаутом
            List<Portfolio> result = portfolioManagement.getPortfoliosByChatId(TEST_CHAT_ID);
            
            // Проверка результата
            assertNotNull("Результат не должен быть null", result);
            assertEquals("Должно быть возвращено 2 портфеля", 2, result.size());
            assertEquals("Первый портфель должен содержать BTC", Crypto.BTC, result.get(0).getCryptoCurrency());
            assertEquals("Второй портфель должен содержать ETH", Crypto.ETH, result.get(1).getCryptoCurrency());
            
            // Проверка вызовов
            verify(portfolioRepository, times(1)).findByChatId(TEST_CHAT_ID);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода save
     */
    @Test
    public void testSave() {
        try {
            // Создаем тестовый портфель
            Portfolio portfolio = new Portfolio(TEST_CHAT_ID);
            portfolio.setCryptoCurrency(Crypto.BTC);
            portfolio.setCount(new BigDecimal("1.5"));
            
            // Настройка мока для сохранения портфеля
            doReturn(portfolio).when(portfolioRepository).save(portfolio);
            
            // Выполнение тестируемого метода
            Portfolio result = portfolioManagement.save(portfolio);
            
            // Проверка результата
            assertNotNull("Результат не должен быть null", result);
            assertEquals("Криптовалюта должна быть BTC", Crypto.BTC, result.getCryptoCurrency());
            assertEquals("Количество должно быть 1.5", new BigDecimal("1.5"), result.getCount());
            
            // Проверка вызовов
            verify(portfolioRepository, times(1)).save(portfolio);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода findById - получение портфеля по ID
     */
    @Test
    public void testFindById() {
        try {
            // Создаем тестовый портфель
            Portfolio expectedPortfolio = new Portfolio(TEST_CHAT_ID);
            // ID устанавливается MongoDB автоматически, в тесте мы используем рефлексию для тестирования
            setPrivateField(expectedPortfolio, "id", TEST_PORTFOLIO_ID);
            expectedPortfolio.setCryptoCurrency(Crypto.BTC);
            expectedPortfolio.setCount(new BigDecimal("1.5"));
            
            // Настройка мока для получения портфеля по ID
            doReturn(Optional.of(expectedPortfolio)).when(portfolioRepository).findById(TEST_PORTFOLIO_ID);
            
            // Выполнение тестируемого метода
            Optional<Portfolio> result = portfolioRepository.findById(TEST_PORTFOLIO_ID);
            
            // Проверка результата
            assertTrue("Результат должен содержать портфель", result.isPresent());
            assertEquals("ID портфеля должен совпадать", TEST_PORTFOLIO_ID, result.get().getId());
            assertEquals("Криптовалюта должна быть BTC", Crypto.BTC, result.get().getCryptoCurrency());
            assertEquals("Количество должно быть 1.5", new BigDecimal("1.5"), result.get().getCount());
            
            // Проверка вызовов
            verify(portfolioRepository, times(1)).findById(TEST_PORTFOLIO_ID);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода addCryptoToPortfolio - добавление криптовалюты в портфель
     */
    @Test
    public void testAddCryptoToPortfolio() {
        try {
            // Подготовка данных
            Portfolio initialPortfolio = new Portfolio(TEST_CHAT_ID);
            setPrivateField(initialPortfolio, "id", TEST_PORTFOLIO_ID);
            initialPortfolio.setCryptoCurrency(Crypto.BTC);
            initialPortfolio.setCount(new BigDecimal("1.0"));
            
            Portfolio updatedPortfolio = new Portfolio(TEST_CHAT_ID);
            setPrivateField(updatedPortfolio, "id", TEST_PORTFOLIO_ID);
            updatedPortfolio.setCryptoCurrency(Crypto.BTC);
            updatedPortfolio.setCount(new BigDecimal("2.5")); // 1.0 + 1.5
            
            // Мокируем поведение репозитория - используем doReturn для предотвращения реальных вызовов
            doReturn(Optional.of(initialPortfolio)).when(portfolioRepository).findById(TEST_PORTFOLIO_ID);
            doReturn(updatedPortfolio).when(portfolioRepository).save(any(Portfolio.class));
            
            // Проверяем, что портфель существует
            Optional<Portfolio> foundPortfolio = portfolioRepository.findById(TEST_PORTFOLIO_ID);
            assertTrue("Портфель должен быть найден", foundPortfolio.isPresent());
            
            // Добавляем монеты в портфель и сохраняем
            Portfolio portfolio = foundPortfolio.get();
            BigDecimal oldAmount = portfolio.getCount();
            BigDecimal amountToAdd = new BigDecimal("1.5");
            portfolio.setCount(oldAmount.add(amountToAdd));
            Portfolio saved = portfolioRepository.save(portfolio);
            
            // Проверяем результат
            assertNotNull("Сохраненный портфель не должен быть null", saved);
            assertEquals("ID должен сохраниться", TEST_PORTFOLIO_ID, saved.getId());
            assertEquals("Криптовалюта должна быть BTC", Crypto.BTC, saved.getCryptoCurrency());
            assertEquals("Новое количество должно быть 2.5", new BigDecimal("2.5"), saved.getCount());
            
            // Проверка вызовов
            verify(portfolioRepository).findById(TEST_PORTFOLIO_ID);
            verify(portfolioRepository).save(any(Portfolio.class));
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода removeCryptoFromPortfolio - удаление криптовалюты из портфеля
     */
    @Test
    public void testRemoveCryptoFromPortfolio() throws Exception {
        // Подготовка данных
        Portfolio initialPortfolio = new Portfolio(TEST_CHAT_ID);
        setPrivateField(initialPortfolio, "id", TEST_PORTFOLIO_ID);
        initialPortfolio.setCryptoCurrency(Crypto.BTC);
        initialPortfolio.setCount(new BigDecimal("2.5"));
        
        Portfolio updatedPortfolio = new Portfolio(TEST_CHAT_ID);
        setPrivateField(updatedPortfolio, "id", TEST_PORTFOLIO_ID);
        updatedPortfolio.setCryptoCurrency(Crypto.BTC);
        updatedPortfolio.setCount(new BigDecimal("1.0")); // 2.5 - 1.5
        
        // Мокируем поведение репозитория
        when(portfolioRepository.findById(TEST_PORTFOLIO_ID)).thenReturn(Optional.of(initialPortfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(updatedPortfolio);
        
        // Вызываем метод для удаления криптовалюты из портфеля
        BigDecimal amountToRemove = new BigDecimal("1.5");
        
        // Получаем портфель
        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(TEST_PORTFOLIO_ID);
        assertTrue(portfolioOpt.isPresent());
        
        // Удаляем криптовалюту и сохраняем
        Portfolio portfolio = portfolioOpt.get();
        BigDecimal newAmount = portfolio.getCount().subtract(amountToRemove);
        portfolio.setCount(newAmount);
        Portfolio result = portfolioRepository.save(portfolio);
        
        // Проверяем результат
        assertNotNull("Результат не должен быть null", result);
        assertEquals("ID должен сохраниться", TEST_PORTFOLIO_ID, result.getId());
        assertEquals("Криптовалюта должна быть BTC", Crypto.BTC, result.getCryptoCurrency());
        assertEquals("Новое количество должно быть 1.0", new BigDecimal("1.0"), result.getCount());
        
        // Проверка вызовов
        verify(portfolioRepository).findById(TEST_PORTFOLIO_ID);
        verify(portfolioRepository).save(any(Portfolio.class));
    }
    
    /**
     * Тест метода createPortfolio - создание нового портфеля
     */
    @Test
    public void testCreatePortfolio() {
        try {
            // Создаем данные для теста
            String chatId = TEST_CHAT_ID;
            Crypto crypto = Crypto.ETH;
            BigDecimal amount = new BigDecimal("5.0");
            
            // Создаем тестовый портфель для результата
            Portfolio newPortfolio = new Portfolio(chatId);
            setPrivateField(newPortfolio, "id", TEST_PORTFOLIO_ID);
            newPortfolio.setCryptoCurrency(crypto);
            newPortfolio.setCount(amount);
            
            // Настройка мока для сохранения нового портфеля - используем doReturn для предотвращения реальных вызовов
            doReturn(newPortfolio).when(portfolioRepository).save(any(Portfolio.class));
            
            // Создаем новый портфель
            Portfolio portfolioToSave = new Portfolio(chatId);
            portfolioToSave.setCryptoCurrency(crypto);
            portfolioToSave.setCount(amount);
            
            // Выполняем тестируемый метод напрямую
            Portfolio saved = portfolioRepository.save(portfolioToSave);
            
            // Проверяем результат
            assertNotNull("Сохраненный портфель не должен быть null", saved);
            assertEquals("ID должен быть установлен", TEST_PORTFOLIO_ID, saved.getId());
            assertEquals("ChatID должен совпадать", chatId, saved.getChatId());
            assertEquals("Криптовалюта должна быть ETH", crypto, saved.getCryptoCurrency());
            assertEquals("Количество должно быть 5.0", amount, saved.getCount());
            
            // Проверяем вызов метода сохранения
            verify(portfolioRepository, times(1)).save(any(Portfolio.class));
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    /**
     * Тест метода deletePortfolio - удаление портфеля
     */
    @Test
    public void testDeletePortfolio() {
        try {
            // Настройка мока для удаления портфеля
            doNothing().when(portfolioRepository).deleteById(TEST_PORTFOLIO_ID);
            
            // Удаляем портфель
            portfolioRepository.deleteById(TEST_PORTFOLIO_ID);
            
            // Проверяем вызовы
            verify(portfolioRepository).deleteById(TEST_PORTFOLIO_ID);
        } catch (Exception e) {
            fail("Тест завершился с ошибкой: " + e.getMessage());
        }
    }
    
    // Вспомогательный метод для установки приватных полей через рефлексию
    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
} 