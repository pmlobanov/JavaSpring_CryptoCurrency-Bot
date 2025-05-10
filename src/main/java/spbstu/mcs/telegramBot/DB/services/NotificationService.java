package spbstu.mcs.telegramBot.DB.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.collections.Notification;
import spbstu.mcs.telegramBot.DB.repositories.NotificationRepository;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.model.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spbstu.mcs.telegramBot.DB.collections.User;
import spbstu.mcs.telegramBot.DB.repositories.UserRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
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
    private final PriceFetcher priceFetcher;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    public NotificationService(NotificationRepository notificationRepository, 
                             MongoTemplate mongoTemplate,
                             PriceFetcher priceFetcher,
                             UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.mongoTemplate = mongoTemplate;
        this.priceFetcher = priceFetcher;
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

    public Mono<Boolean> checkAlert(Notification notification) {
        Currency.Crypto crypto = notification.getCryptoCurrency();
        return priceFetcher.getCurrentPrice(crypto)
                .map(priceJson -> {
                    try {
                        BigDecimal currentPrice = new BigDecimal(priceJson);
                        Double threshold = notification.getActiveThreshold();
                        
                        switch (notification.getThresholdType()) {
                            case VALUE:
                                return currentPrice.compareTo(BigDecimal.valueOf(threshold)) > 0;
                            case PERCENT:
                                BigDecimal percentageChange = BigDecimal.valueOf(threshold);
                                BigDecimal currentPercentage = currentPrice
                                    .multiply(new BigDecimal("100"))
                                    .divide(BigDecimal.valueOf(threshold), 4, BigDecimal.ROUND_HALF_UP);
                                return currentPercentage.compareTo(percentageChange) > 0;
                            case EMA:
                                return currentPrice.compareTo(BigDecimal.valueOf(threshold)) > 0;
                            default:
                                return false;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                });
    }

    public Mono<Boolean> checkAlert(Notification notification, BigDecimal currentPrice, BigDecimal startPrice) {
        return Mono.just(notification)
            .map(n -> {
                Double threshold = n.getActiveThreshold();
                switch (n.getThresholdType()) {
                    case VALUE:
                        return currentPrice.compareTo(BigDecimal.valueOf(threshold)) > 0;
                    case PERCENT:
                        if (startPrice != null) {
                            BigDecimal percentageChange = BigDecimal.valueOf(threshold);
                            BigDecimal currentPercentage = currentPrice.subtract(startPrice)
                                .divide(startPrice, 4, BigDecimal.ROUND_HALF_UP)
                                .multiply(new BigDecimal("100"));
                            return currentPercentage.compareTo(percentageChange) > 0;
                        }
                        return false;
                    case EMA:
                        return currentPrice.compareTo(BigDecimal.valueOf(threshold)) > 0;
                    default:
                        return false;
                }
            });
    }

    public Mono<Void> addNotificationToUser(String chatId, String notificationId) {
        return Mono.fromRunnable(() -> {
            Optional<User> userOpt = userRepository.findOptionalByChatId(chatId);
            User user = userOpt.orElseThrow(() -> new NoSuchElementException("User not found"));
            
            if (user.getNotificationIds() == null) {
                user.setNotificationIds(new ArrayList<>());
            }
            
            user.getNotificationIds().add(notificationId);
            userRepository.save(user);
        });
    }
}
