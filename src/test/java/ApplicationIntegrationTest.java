


import org.junit.jupiter.api.*;
import ru.spbstu.telematics.java.DB.ApplicationContextManager;
import ru.spbstu.telematics.java.DB.DTO.UserPortfolioView;
import ru.spbstu.telematics.java.DB.collections.Notification;
import ru.spbstu.telematics.java.DB.collections.Portfolio;
import ru.spbstu.telematics.java.DB.collections.TrackedCryptoCurrency;
import ru.spbstu.telematics.java.DB.collections.User;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;
import ru.spbstu.telematics.java.DB.currencies.FiatCurrency;
import ru.spbstu.telematics.java.DB.services.NotificationService;
import ru.spbstu.telematics.java.DB.services.PortfolioService;
import ru.spbstu.telematics.java.DB.services.TrackedCryptoService;
import ru.spbstu.telematics.java.DB.services.UserService;

import java.util.List;

import static com.mongodb.assertions.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ApplicationIntegrationTest {
    private static ApplicationContextManager contextManager;
    private UserService userService;
    private PortfolioService portfolioService;
    private NotificationService notificationService;
    private TrackedCryptoService trackedCryptoService;

    @BeforeAll
    static void setupAll() {
        // Инициализация контекста один раз для всех тестов
        contextManager = ApplicationContextManager.create();
    }

    @BeforeEach
    void setup() {
        // Получаем сервисы из контекста
        userService = contextManager.getUserService();
        portfolioService = contextManager.getPortfolioService();
        notificationService = contextManager.getNotificationService();
        trackedCryptoService = contextManager.getTrackedCryptoService();
    }

    @AfterEach
    void cleanup() {
        // Очищаем базу данных после каждого теста
        try (var context = ApplicationContextManager.create()) {
            context.getMongoTemplate().getDb().drop();
        }
    }

    @AfterAll
    static void tearDownAll() {
        // Закрываем контекст после всех тестов
        if (contextManager != null) {
            contextManager.close();
        }
    }

    @Test
    void testUserAndPortfolioCreation() {
        // Создаем пользователя
        User user = userService.createUser("test_user", FiatCurrency.USD, CryptoCurrency.BTC);
        assertNotNull(user.getId());

        // Создаем портфель
        Portfolio portfolio = portfolioService.createPortfolio("main_portfolio", user.getId());
        assertNotNull(portfolio.getId());

        // Проверяем, что портфель добавился пользователю
        UserPortfolioView view = userService.getUserPortfolioData(user.getId());
        assertEquals(1, view.portfolios().size());
        assertEquals("main_portfolio", view.portfolios().get(0).getName());
    }

    @Test
    void testAddCurrencyToPortfolio() {
        // Создаем тестовые данные
        User user = userService.createUser("crypto_user", FiatCurrency.EUR, CryptoCurrency.ETH);
        Portfolio portfolio = portfolioService.createPortfolio("crypto_portfolio", user.getId());

        // Добавляем отслеживаемую криптовалюту
        TrackedCryptoCurrency trackedBtc = trackedCryptoService.trackNewCurrency(
                CryptoCurrency.BTC, 50000.0);

        // Добавляем в портфель
        Portfolio updatedPortfolio = portfolioService.addCurrencyToPortfolio(
                portfolio.getId(), trackedBtc.getId(), 1.5);

        // Проверяем
        assertEquals(1, updatedPortfolio.getListOfCurrencies().size());
        assertEquals(1.5, updatedPortfolio.getListOfCurrencies().get(0).getAmount());
    }

    @Test
    void testNotificationWorkflow() {
        // Создаем пользователя
        User user = userService.createUser("alert_user", FiatCurrency.USD, CryptoCurrency.BTC);

        // Создаем уведомление
        Notification notification = notificationService.createValueAlert(
                user.getId(), CryptoCurrency.BTC, 60000.0);

        // Проверяем
        List<Notification> activeNotifications =
                userService.getUserNotifications(user.getId());
        assertEquals(1, activeNotifications.size());
        assertEquals(60000.0, activeNotifications.get(0).getActiveThreshold());

        // Деактивируем
        notificationService.deactivateNotification(notification.getId());
        assertEquals(0, userService.getActiveUserNotifications(user.getId()).size());
    }

    @Test
    void testCurrencyUpdates() {
        // Создаем пользователя
        User user = userService.createUser("currency_user", FiatCurrency.USD, CryptoCurrency.BTC);

        // Обновляем настройки
        userService.updateUserFiatCurrency(user.getId(), FiatCurrency.EUR);
        userService.updateUserDefaultCryptoCurrency(user.getId(), CryptoCurrency.ETH);

        // Проверяем обновления
        User updated = userService.findByUserTgName("currency_user");
        assertEquals(FiatCurrency.EUR, updated.getFiatCurrency());
        assertEquals(CryptoCurrency.ETH, updated.getDefaultCryptoCurrrency());
    }

    @Test
    void testFullIntegrationScenario() {
        // 1. Создаем пользователя
        User user = userService.createUser("full_test_user", FiatCurrency.USD, CryptoCurrency.BTC);

        // 2. Создаем портфель
        Portfolio portfolio = portfolioService.createPortfolio("investment", user.getId());

        // 3. Добавляем криптовалюты
        TrackedCryptoCurrency btc = trackedCryptoService.trackNewCurrency(CryptoCurrency.BTC, 50000.0);
        TrackedCryptoCurrency eth = trackedCryptoService.trackNewCurrency(CryptoCurrency.ETH, 3000.0);

        portfolioService.addCurrencyToPortfolio(portfolio.getId(), btc.getId(), 0.5);
        portfolioService.addCurrencyToPortfolio(portfolio.getId(), eth.getId(), 10.0);

        // 4. Создаем уведомления
        notificationService.createValueAlert(user.getId(), CryptoCurrency.BTC, 60000.0);
        notificationService.createValueAlert(user.getId(), CryptoCurrency.ETH, 3500.0);

        // 5. Получаем полные данные
        UserPortfolioView view = userService.getUserPortfolioData(user.getId());

        // Проверяем
        assertEquals(1, view.portfolios().size());
        assertEquals(2, view.portfolios().get(0).getListOfCurrencies().size());
        assertEquals(2, view.notifications().size());
    }


}