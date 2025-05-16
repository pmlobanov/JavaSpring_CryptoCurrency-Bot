package spbstu.mcs.telegramBot.cryptoApi;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import spbstu.mcs.telegramBot.DB.collections.Notification;
import spbstu.mcs.telegramBot.DB.repositories.NotificationRepository;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Currency.Crypto;
import spbstu.mcs.telegramBot.service.TelegramBotService;
import spbstu.mcs.telegramBot.cryptoApi.CurrencyConverter;
import spbstu.mcs.telegramBot.DB.collections.User;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

/**
 * Тесты для AlertsHandling
 */
@RunWith(JUnit4.class)
public class AlertsHandlingTest {

    private TelegramBotService telegramBotService;
    private NotificationService notificationService;
    private UserService userService;
    private ObjectMapper objectMapper;
    private CurrencyConverter currencyConverter;
    private PriceFetcher priceFetcher;
    private NotificationRepository notificationRepository;
    private ObjectNode objectNode;
    private ArrayNode arrayNode;
    private JsonNode jsonNode;
    
    private AlertsHandling alertsHandling;
    
    private static final String TEST_CHAT_ID = "123456789";
    
    @Before
    public void setUp() {
        telegramBotService = mock(TelegramBotService.class);
        notificationService = mock(NotificationService.class);
        userService = mock(UserService.class);
        objectMapper = mock(ObjectMapper.class);
        currencyConverter = mock(CurrencyConverter.class);
        priceFetcher = mock(PriceFetcher.class);
        notificationRepository = mock(NotificationRepository.class);
        objectNode = mock(ObjectNode.class);
        arrayNode = mock(ArrayNode.class);
        jsonNode = mock(JsonNode.class);
        
        // Настройка ObjectMapper
        when(objectMapper.createObjectNode()).thenReturn(objectNode);
        when(objectNode.putArray(anyString())).thenReturn(arrayNode);
        when(objectNode.put(anyString(), anyString())).thenReturn(objectNode);
        
        alertsHandling = new AlertsHandling(
            objectMapper,
            currencyConverter,
            priceFetcher,
            telegramBotService,
            notificationService,
            notificationRepository,
            userService
        );
    }
    
    /**
     * Тест метода setAlertVal
     */
    @Test
    public void testSetAlert() throws JsonProcessingException {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        Crypto crypto = Crypto.BTC;
        String symbol = crypto.getCode() + "-USDT";
        BigDecimal maxPrice = new BigDecimal("55000");
        BigDecimal minPrice = new BigDecimal("45000");
        
        // Имитация JSON ответа от getCurrentPrice
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"49000\",\"timestamp\":1234567890}";
        
        // Настройка мока для getCurrentPrice
        when(priceFetcher.getCurrentPrice(crypto))
            .thenReturn(Mono.just(priceJson));
        
        // Настройка мока для getUsdToFiatRate
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(BigDecimal.ONE)); // Коэффициент 1.0 для USD
        
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.get("timestamp")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("49000");
        when(jsonNode.asLong()).thenReturn(1234567890L);
        
        // Настройка мока для сохранения уведомления
        Notification notification = new Notification(
            null, 
            crypto, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.VALUE, 
            true,
            chatId, 
            maxPrice.doubleValue(), 
            minPrice.doubleValue(), 
            new BigDecimal("49000").doubleValue()
        );
        
        when(notificationService.createUserNotification(any(Notification.class)))
            .thenReturn(Mono.just(notification));
        
        // Выполнение тестируемого метода
        Mono<String> result = alertsHandling.setAlertVal(crypto, maxPrice, minPrice, chatId);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> response.contains("Алерт установлен"))
            .verifyComplete();
        
        // Проверка вызовов
        verify(priceFetcher).getCurrentPrice(crypto);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
        verify(notificationService).createUserNotification(any(Notification.class));
    }

    /**
     * Тест метода setAlertPerc
     */
    @Test
    public void testSetAlertPerc() throws JsonProcessingException {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        Crypto crypto = Crypto.BTC;
        BigDecimal upPercent = new BigDecimal("5.0");
        BigDecimal downPercent = new BigDecimal("3.0");
        
        // Имитация JSON ответа от getCurrentPrice
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        
        // Настройка мока для getCurrentPrice
        when(priceFetcher.getCurrentPrice(crypto))
            .thenReturn(Mono.just(priceJson));
        
        // Настройка мока для getUsdToFiatRate
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(BigDecimal.ONE)); // Коэффициент 1.0 для USD
        
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(priceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.get("timestamp")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("50000");
        when(jsonNode.asLong()).thenReturn(1234567890L);
        
        // Настройка мока для сохранения уведомления
        Notification notification = new Notification(
            null, 
            crypto, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.PERCENT, 
            true,
            chatId, 
            52500.0, // +5%
            48500.0, // -3%
            50000.0
        );
        
        when(notificationService.createUserNotification(any(Notification.class)))
            .thenReturn(Mono.just(notification));
        
        // Выполнение тестируемого метода
        Mono<String> result = alertsHandling.setAlertPerc(crypto, upPercent, downPercent, chatId);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Алерт установлен") && 
                response.contains("+5,00%") && 
                response.contains("-3,00%"))
            .verifyComplete();
        
        // Проверка вызовов
        verify(priceFetcher).getCurrentPrice(crypto);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
        verify(notificationService).createUserNotification(any(Notification.class));
    }
    
    /**
     * Тест метода setAlertEMA
     */
    @Test
    public void testSetAlertEMA() throws JsonProcessingException {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        Crypto crypto = Crypto.BTC;
        
        // Имитация JSON ответа от getCurrentPrice
        String priceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"50000\",\"timestamp\":1234567890}";
        List<Mono<BigDecimal>> priceMonos = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            priceMonos.add(Mono.just(new BigDecimal("50000")));
        }
        
        // Настройка мока для getCurrentPrice
        when(priceFetcher.getCurrentPrice(crypto))
            .thenReturn(Mono.just(priceJson));
        
        // Настройка мока для getSymbolPriceByTime
        when(priceFetcher.getSymbolPriceByTime(eq(crypto), anyLong()))
            .thenReturn(Mono.just("{\"price\":\"50000\"}"));
        
        // Настройка мока для parsePrice
        when(priceFetcher.getCryptoPrice(any(Crypto.class)))
            .thenReturn(Mono.just(new BigDecimal("50000")));
            
        // Настройка мока для getUsdToFiatRate
        when(currencyConverter.getUsdToFiatRate(any(Currency.Fiat.class)))
            .thenReturn(Mono.just(BigDecimal.ONE)); // Коэффициент 1.0 для USD
        
        // Настройка мока для чтения JSON
        when(objectMapper.readTree(anyString())).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.get("timestamp")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("50000");
        when(jsonNode.asLong()).thenReturn(1234567890L);
        
        // Настройка мока для сохранения уведомления
        Notification notification = new Notification(
            null, 
            crypto, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.EMA, 
            true,
            chatId, 
            null,
            null,
            50000.0
        );
        
        when(notificationService.createUserNotification(any(Notification.class)))
            .thenReturn(Mono.just(notification));
        
        // Выполнение тестируемого метода
        Mono<String> result = alertsHandling.setAlertEMA(crypto, chatId);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNextMatches(response -> 
                response.contains("Алерт EMA установлен для BTC") &&
                response.contains("Текущая цена:") && 
                response.contains("Начальное EMA (SMA 20)"))
            .verifyComplete();
        
        // Проверка вызовов
        verify(priceFetcher).getCurrentPrice(crypto);
        verify(currencyConverter).getUsdToFiatRate(any(Currency.Fiat.class));
        verify(notificationService).createUserNotification(any(Notification.class));
    }
    
    /**
     * Тест метода showAlerts
     */
    @Test
    public void testShowAlerts() throws JsonProcessingException {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        
        // Создаем тестовые уведомления
        Notification notification1 = new Notification(
            "notif1", 
            Crypto.BTC, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.VALUE, 
            true,
            chatId, 
            50000.0, 
            45000.0, 
            49000.0
        );
        
        Notification notification2 = new Notification(
            "notif2", 
            Crypto.ETH, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.VALUE, 
            true,
            chatId, 
            3000.0, 
            2800.0, 
            2900.0
        );
        
        // Настройка мока для получения уведомлений
        when(notificationService.getAllActiveAlerts())
            .thenReturn(Flux.fromIterable(Arrays.asList(notification1, notification2)));
        
        // Подготовка дополнительных моков для обработки JSON
        when(objectMapper.createObjectNode()).thenReturn(objectNode);
        when(objectMapper.createArrayNode()).thenReturn(arrayNode);
        when(objectNode.set(eq("alerts"), any(ArrayNode.class))).thenReturn(objectNode);
        
        // Мокируем JsonNode для каждого уведомления
        ObjectNode alertNode = mock(ObjectNode.class);
        when(objectMapper.createObjectNode()).thenReturn(alertNode);
        when(alertNode.put(anyString(), anyString())).thenReturn(alertNode);
        when(alertNode.put(anyString(), anyDouble())).thenReturn(alertNode);
        when(alertNode.put(anyString(), anyBoolean())).thenReturn(alertNode);
        
        // Мокируем добавление в массив
        when(arrayNode.add(any(ObjectNode.class))).thenReturn(arrayNode);
        
        // Мокируем финальное преобразование в строку
        String expectedJson = "{\"alerts\":[{\"type\":\"VALUE\",\"symbol\":\"BTC\",\"fiat\":\"USD\"},{\"type\":\"VALUE\",\"symbol\":\"ETH\",\"fiat\":\"USD\"}]}";
        when(objectMapper.writeValueAsString(any(ObjectNode.class))).thenReturn(expectedJson);
        
        // Выполнение тестируемого метода
        Mono<String> result = alertsHandling.showAlerts();
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNext(expectedJson)
            .verifyComplete();
        
        // Проверка вызовов
        verify(notificationService).getAllActiveAlerts();
        verify(objectMapper).writeValueAsString(any(ObjectNode.class));
    }
    
    /**
     * Тест метода deleteAlert
     */
    @Test
    public void testDeleteAlert() throws JsonProcessingException {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        String symbol = "BTC";
        String type = "VALUE";
        
        // Создаем тестовое уведомление
        Notification notification = new Notification(
            "notif1", 
            Crypto.BTC, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.VALUE, 
            true,
            chatId, 
            50000.0, 
            45000.0, 
            49000.0
        );
        
        // Настройка мока для получения уведомлений
        when(notificationService.getActiveAlerts(eq(Crypto.BTC)))
            .thenReturn(Flux.just(notification));
        
        // Настройка мока для удаления уведомления
        when(notificationService.delete(any(Notification.class)))
            .thenReturn(Mono.empty());
        
        // Выполнение тестируемого метода
        Mono<String> result = alertsHandling.deleteAlert(symbol, type, chatId);
        
        // Проверка результата
        StepVerifier.create(result)
            .expectNext("Alert deleted successfully")
            .verifyComplete();
        
        // Проверка вызовов
        verify(notificationService).getActiveAlerts(eq(Crypto.BTC));
        verify(notificationService).delete(any(Notification.class));
    }
    
    /**
     * Тест метода deleteAllAlerts
     */
    @Test
    public void testDeleteAllAlerts() {
        // Тестовые данные
        String chatId = TEST_CHAT_ID;
        
        // Настройка мока для удаления всех уведомлений
        when(notificationService.deleteAllAlerts(chatId))
            .thenReturn(Mono.empty());
            
        // Настройка мока для отправки сообщения
        when(telegramBotService.sendResponseAsync(eq(chatId), anyString()))
            .thenReturn(Mono.empty());
        
        // Выполнение тестируемого метода
        Mono<Void> result = alertsHandling.deleteAllAlerts(chatId);
        
        // Проверка результата
        StepVerifier.create(result)
            .verifyComplete();
        
        // Проверка вызовов
        verify(notificationService).deleteAllAlerts(chatId);
        verify(telegramBotService).sendResponseAsync(eq(chatId), anyString());
    }
    
    /**
     * Тест метода checkAlerts
     */
    @Test
    public void testCheckAlerts() throws JsonProcessingException {
        // Создаем тестовое уведомление VALUE
        Notification valueAlert = new Notification(
            "notif1", 
            Crypto.BTC, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.VALUE, 
            true,
            TEST_CHAT_ID, 
            50000.0, // верхний порог
            45000.0, // нижний порог
            48000.0  // начальная цена
        );
        
        // Создаем тестовое уведомление PERCENT
        Notification percentAlert = new Notification(
            "notif2", 
            Crypto.ETH, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.PERCENT, 
            true,
            TEST_CHAT_ID, 
            3150.0, // верхний порог (+5%)
            2850.0, // нижний порог (-5%)
            3000.0  // начальная цена
        );
        
        // Создаем тестовое уведомление EMA
        Notification emaAlert = new Notification(
            "notif3", 
            Crypto.XRP, 
            Currency.Fiat.USD, 
            Notification.ThresholdType.EMA, 
            true,
            TEST_CHAT_ID, 
            null,
            null,
            0.5    // начальная цена
        );
        
        // Мокируем получение всех активных алертов
        when(notificationService.getAllActiveAlerts())
            .thenReturn(Flux.fromIterable(Arrays.asList(valueAlert, percentAlert, emaAlert)));
        
        // Мокируем ответы JSON для getCurrentPrice вместо getCryptoPrice
        // Создаем JSON с ценами выше порогов для срабатывания
        String btcPriceJson = "{\"symbol\":\"BTC-USDT\",\"price\":\"51000\",\"timestamp\":1234567890}";
        String ethPriceJson = "{\"symbol\":\"ETH-USDT\",\"price\":\"3200\",\"timestamp\":1234567890}";
        String xrpPriceJson = "{\"symbol\":\"XRP-USDT\",\"price\":\"0.6\",\"timestamp\":1234567890}";
            
        // Настройка мока для getCurrentPrice
        when(priceFetcher.getCurrentPrice(Crypto.BTC))
            .thenReturn(Mono.just(btcPriceJson));
        when(priceFetcher.getCurrentPrice(Crypto.ETH))
            .thenReturn(Mono.just(ethPriceJson));
        when(priceFetcher.getCurrentPrice(Crypto.XRP))
            .thenReturn(Mono.just(xrpPriceJson));
            
        // Мокируем чтение JSON
        when(objectMapper.readTree(btcPriceJson)).thenReturn(jsonNode);
        when(objectMapper.readTree(ethPriceJson)).thenReturn(jsonNode);
        when(objectMapper.readTree(xrpPriceJson)).thenReturn(jsonNode);
        when(jsonNode.get("price")).thenReturn(jsonNode);
        when(jsonNode.asText()).thenReturn("51000", "3200", "0.6");

        // Мокируем получение пользователя
        User testUser = new User();
        testUser.setChatId(TEST_CHAT_ID);
        when(userService.getUserByChatId(anyString()))
            .thenReturn(Mono.just(testUser));
            
        // Мокируем сохранение уведомления
        when(notificationService.save(any(Notification.class)))
            .thenReturn(Mono.just(valueAlert));
            
        // Мокируем отправку сообщения
        when(telegramBotService.sendResponseAsync(eq(TEST_CHAT_ID), anyString()))
            .thenReturn(Mono.empty());

        // Выполнение тестируемого метода
        alertsHandling.checkAlerts();
        
        // Проверка вызовов - получение активных алертов
        verify(notificationService).getAllActiveAlerts();
        
        // Проверка вызовов - получение текущих цен
        verify(priceFetcher, atLeastOnce()).getCurrentPrice(any(Crypto.class));
    }
} 