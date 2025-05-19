package spbstu.mcs.telegramBot.DB.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.repositories.NotificationRepository;
import spbstu.mcs.telegramBot.DB.repositories.UserRepository;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.model.Notification;
import spbstu.mcs.telegramBot.model.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Сервис для работы с уведомлениями пользователей.
 * Обеспечивает создание, активацию и управление уведомлениями о изменениях курсов криптовалют.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание уведомлений различных типов</li>
 *   <li>Проверка условий срабатывания уведомлений</li>
 *   <li>Управление статусом уведомлений (активация/деактивация)</li>
 *   <li>Работа с пользовательскими уведомлениями</li>
 * </ul>
 */
@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;
  //  private final PriceFetcher priceFetcher;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, 
                             MongoTemplate mongoTemplate,
                             //PriceFetcher priceFetcher,
                             @Lazy UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
       // this.priceFetcher = priceFetcher;
        this.userRepository = userRepository;
    }

    public Flux<Notification> getActiveAlerts(String chatId) {
        return Flux.fromIterable(notificationRepository.findByChatIdAndIsActiveTrue(chatId));
    }

    public Flux<Notification> getActiveAlerts(Currency.Crypto cryptoCurrency) {
        return Flux.fromIterable(notificationRepository.findByCryptoCurrencyAndIsActiveTrue(cryptoCurrency));
    }

    public Flux<Notification> getAllActiveAlerts() {
        return Flux.fromIterable(notificationRepository.findAll())
            .filter(notification -> 
                notification.getThresholdType() == Notification.ThresholdType.EMA || 
                Boolean.TRUE.equals(notification.isActive())
            );
    }

    public Flux<Notification> getAllActiveAlerts(String chatId) {
        return Flux.fromIterable(notificationRepository.findByChatIdAndIsActiveTrue(chatId));
    }

    public Flux<Notification> getAllUserAlerts(String chatId) {
        return Flux.fromIterable(notificationRepository.findByChatId(chatId));
    }

    public Mono<Notification> save(Notification notification) {
        log.info("Saving notification: {}", notification);
        Notification saved = notificationRepository.save(notification);
        log.info("Saved notification: {}", saved);
        return Mono.just(saved);
    }

    public Mono<Void> delete(Notification notification) {
        return Mono.fromRunnable(() -> notificationRepository.delete(notification));
    }

    public Mono<Void> deleteAll() {
        return Mono.fromRunnable(() -> notificationRepository.deleteAll());
    }

    public Mono<Void> deleteAllAlerts() {
        return deleteAll();
    }

    public Mono<Void> deleteAllAlerts(String chatId) {
        return Mono.fromRunnable(() -> notificationRepository.deleteByChatId(chatId));
    }

    public Mono<Notification> getNotification(String id) {
        return Mono.justOrEmpty(notificationRepository.findById(id))
            .switchIfEmpty(Mono.error(new NoSuchElementException("Notification not found with id: " + id)));
    }

    public Mono<Void> addNotificationToUser(String chatId, String notificationId) {
        return Mono.fromRunnable(() -> {
            Optional<User> userOpt = userRepository.findOptionalByChatId(chatId);
            User user = userOpt.orElseThrow(() -> new NoSuchElementException("User not found"));
            
            if (user.getNotificationIds() == null) {
                user.setNotificationIds(new ArrayList<>());
            }
            
            if (!user.getNotificationIds().contains(notificationId)) {
            user.getNotificationIds().add(notificationId);
            userRepository.save(user);
                log.info("Added notification {} to user with chatId: {}", notificationId, chatId);
            } else {
                log.info("Notification {} already exists for user with chatId: {}", notificationId, chatId);
            }
        });
    }

    /**
     * Создает уведомление определенного типа и добавляет его ID в notificationIds пользователя.
     * @param notification уведомление
     * @param type тип уведомления (VALUE, PERCENT, EMA)
     * @return Mono<Notification> сохраненное уведомление
     */
    public Mono<Notification> createTypedNotification(Notification notification, Notification.ThresholdType type) {
        notification.setThresholdType(type);
        return save(notification)
            .flatMap(saved -> {
                log.info("Created {} notification for user {} with ID {}", 
                    type, saved.getChatId(), saved.getId());
                return addNotificationToUser(saved.getChatId(), saved.getId())
                    .thenReturn(saved);
            });
    }

    /**
     * Получает все уведомления определенного типа для пользователя.
     * @param chatId ID чата пользователя
     * @param type тип уведомления
     * @return Flux с уведомлениями указанного типа
     */
    public Flux<Notification> getUserNotificationsByType(String chatId, Notification.ThresholdType type) {
        return Flux.fromIterable(notificationRepository.findByChatIdAndThresholdType(chatId, type));
    }

    /**
     * Получает все активные уведомления определенного типа для пользователя.
     * @param chatId ID чата пользователя
     * @param type тип уведомления
     * @return Flux с активными уведомлениями указанного типа
     */
    public Flux<Notification> getActiveNotificationsByType(String chatId, Notification.ThresholdType type) {
        return Flux.fromIterable(notificationRepository.findByChatIdAndThresholdTypeAndIsActiveTrue(chatId, type));
    }

    /**
     * Удаляет все уведомления определенного типа у пользователя.
     * @param chatId ID чата пользователя
     * @param type тип уведомления
     * @return Mono<Void>
     */
    public Mono<Void> deleteAllNotificationsByType(String chatId, Notification.ThresholdType type) {
        return Mono.fromRunnable(() -> {
            notificationRepository.deleteByChatIdAndThresholdType(chatId, type);
            log.info("Deleted all {} notifications for user {}", type, chatId);
        });
    }

    /**
     * Проверяет наличие уведомления определенного типа у пользователя.
     * @param chatId ID чата пользователя
     * @param type тип уведомления
     * @return Mono<Boolean> true если есть уведомления указанного типа
     */
    public Mono<Boolean> hasNotificationType(String chatId, Notification.ThresholdType type) {
        return Mono.fromCallable(() -> {
            List<Notification> notifications = notificationRepository.findByChatIdAndThresholdType(chatId, type);
            return !notifications.isEmpty();
        });
    }

    /**
     * Получает количество уведомлений определенного типа у пользователя.
     * @param chatId ID чата пользователя
     * @param type тип уведомления
     * @return Mono<Integer> количество уведомлений
     */
    public Mono<Integer> getNotificationCountByType(String chatId, Notification.ThresholdType type) {
        return Mono.fromCallable(() -> 
            notificationRepository.findByChatIdAndThresholdType(chatId, type).size()
        );
    }

    /**
     * Создает уведомление и добавляет его ID в notificationIds пользователя.
     * @param notification уведомление
     * @return Mono<Notification> сохраненное уведомление
     */
    public Mono<Notification> createUserNotification(Notification notification) {
        return save(notification)
            .flatMap(saved -> {
                log.info("Created notification of type {} for user {}", 
                    saved.getThresholdType(), saved.getChatId());
                return addNotificationToUser(saved.getChatId(), saved.getId())
                    .thenReturn(saved);
            });
    }
}
