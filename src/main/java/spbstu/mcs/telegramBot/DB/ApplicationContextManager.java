package spbstu.mcs.telegramBot.DB;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import spbstu.mcs.telegramBot.DB.config.MongoConfig;
import spbstu.mcs.telegramBot.DB.services.*;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.DB.services.PortfolioService;
import spbstu.mcs.telegramBot.DB.services.NotificationService;
import spbstu.mcs.telegramBot.DB.services.TrackedCryptoService;
/**
 * Менеджер для управления Spring контекстом и доступа к сервисам приложения.
 * Реализует {@link AutoCloseable} для автоматического закрытия контекста при использовании в try-with-resources.
 *
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Инициализация Spring контекста с конфигурацией MongoDB</li>
 *   <li>Предоставление доступа к основным сервисам приложения</li>
 *   <li>Автоматическое освобождение ресурсов</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * try (ApplicationContextManager manager = ApplicationContextManager.create()) {
 *     UserService userService = manager.getUserService();
 *     // Работа с сервисом...
 * }
 * }</pre>
 *
 * @see AutoCloseable
 * @see AnnotationConfigApplicationContext
 * @see MongoConfig
 */
public class ApplicationContextManager implements AutoCloseable {
    private static AnnotationConfigApplicationContext context;

    // Сервисы
    private UserService userService;
    private PortfolioService portfolioService;
    private NotificationService notificationService;
    private TrackedCryptoService trackedCryptoService;
    private MongoTemplate mongoTemplate;

    /**
     * Конструктор менеджера. Инициализирует Spring контекст и загружает сервисы.
     */
    public ApplicationContextManager() {
        initializeContext();
        initializeServices();
    }


    /**
     * Инициализирует Spring контекст, если он ещё не был создан.
     */
    private void initializeContext() {
        if (context == null) {
            context = new AnnotationConfigApplicationContext(MongoConfig.class);// Важно: не забывайте refresh()
        }
    }
    /**
     * Загружает сервисы из Spring контекста.
     */
    private void initializeServices() {
        this.userService = context.getBean(UserService.class);
        this.portfolioService = context.getBean(PortfolioService.class);
        this.notificationService = context.getBean(NotificationService.class);
        this.trackedCryptoService = context.getBean(TrackedCryptoService.class);
        this.mongoTemplate = context.getBean(MongoTemplate.class);
    }

    /**
     * Возвращает сервис для работы с пользователями.
     * @return экземпляр {@link UserService}
     */
    public UserService getUserService() {
        return userService;
    }

    /**
     * Возвращает сервис для работы с портфелями.
     * @return экземпляр {@link PortfolioService}
     */
    public PortfolioService getPortfolioService() {
        return portfolioService;
    }

    /**
     * Возвращает сервис для работы с уведомлениями.
     * @return экземпляр {@link NotificationService}
     */
    public NotificationService getNotificationService() {
        return notificationService;
    }

    /**
     * Возвращает сервис для работы с отслеживаемыми криптовалютами.
     * @return экземпляр {@link TrackedCryptoService}
     */
    public TrackedCryptoService getTrackedCryptoService() {
        return trackedCryptoService;
    }

    /**
     * Закрывает Spring контекст и освобождает ресурсы.
     * Вызывается автоматически при использовании в try-with-resources.
     */
    @Override
    public void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /**
     * Статический фабричный метод для создания экземпляра менеджера.
     * @return новый экземпляр {@link ApplicationContextManager}
     */
    // Статический метод для быстрого доступа
    public static ApplicationContextManager create() {
        return new ApplicationContextManager();
    }

    public MongoTemplate getMongoTemplate() {
        return mongoTemplate;
    }
}