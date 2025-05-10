package spbstu.mcs.telegramBot.DB.services;

import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.DB.DTO.UserPortfolioView;
import spbstu.mcs.telegramBot.DB.collections.Notification;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.DB.collections.User;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.DB.repositories.UserRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;

/**
 * Сервис для управления пользователями и их данными.
 * Обеспечивает создание пользователей, управление их портфелями и уведомлениями,
 * а также обновление пользовательских настроек.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание и управление учетными записями пользователей</li>
 *   <li>Работа с портфелями и уведомлениями пользователей</li>
 *   <li>Обновление валютных предпочтений</li>
 *   <li>Получение агрегированных данных пользователя</li>
 * </ul>
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    @Autowired
    private final MongoTemplate mongoTemplate;

    @Autowired
    @Lazy
    private PortfolioService portfolioService;
    @Autowired
    private NotificationService notificationService;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Создает нового пользователя с указанными параметрами.
     *
     * @param userTgName имя пользователя в Telegram
     * @param chatId ID чата пользователя в Telegram
     * @return созданный пользователь
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    public User createUser(String userTgName, String chatId) {
        log.info("Creating new user with userTgName: {} and chatId: {}", userTgName, chatId);
        try {
            User user = new User(userTgName, chatId);
            User savedUser = userRepository.save(user);
            log.info("Successfully created user: {}", savedUser);
            return savedUser;
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Добавляет портфель к списку пользователя.
     *
     * @param userId идентификатор пользователя
     * @param portfolioId идентификатор портфеля для добавления
     * @throws NoSuchElementException если пользователь не найден
     */
    public void addPortfolioToUser(String userId, String portfolioId) {
        Query query = new Query(Criteria.where("id").is(userId));
        Update update = new Update().addToSet("portfolioIds", portfolioId);

        UpdateResult result = mongoTemplate.updateFirst(query, update, User.class);

        if (result.getModifiedCount() == 0) {
            throw new NoSuchElementException("User not found or update failed");
        }
    }

    /**
     * Добавляет уведомление к списку пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @param notificationId идентификатор уведомления для добавления
     * @throws NoSuchElementException если пользователь не найден
     */
    public void addNotificationToUser(String chatId, String notificationId) {
        Optional<User> userOpt = userRepository.findOptionalByChatId(chatId);
        User user = userOpt.orElseThrow(() -> new NoSuchElementException("User not found"));
        
        if (user.getNotificationIds() == null) {
            user.setNotificationIds(new ArrayList<>());
        }
        
        user.getNotificationIds().add(notificationId);
        userRepository.save(user);
    }

    /**
     * Возвращает агрегированные данные пользователя (портфели и уведомления).
     *
     * @param userId идентификатор пользователя
     * @return DTO с данными пользователя
     * @throws NoSuchElementException если пользователь не найден
     */
    public Mono<UserPortfolioView> getUserPortfolioData(String userId) {
        return Mono.justOrEmpty(userRepository.findById(userId))
            .switchIfEmpty(Mono.error(new NoSuchElementException("User not found")))
            .flatMap(user -> {
                Flux<Portfolio> portfolios = user.getPortfolioIds() == null ? Flux.empty() :
                    Flux.fromIterable(user.getPortfolioIds())
                        .flatMap(id -> Mono.fromCallable(() -> portfolioService.getPortfolio(id)));

                Flux<Notification> notifications = user.getNotificationIds() == null ? Flux.empty() :
                    Flux.fromIterable(user.getNotificationIds())
                        .flatMap(notificationService::getNotification);

                return Mono.zip(
                    portfolios.collectList(),
                    notifications.collectList()
                ).map(tuple -> new UserPortfolioView(userId, tuple.getT2(), tuple.getT1()));
            });
    }

    /**
     * Находит пользователя по имени в Telegram.
     *
     * @param userTgName имя пользователя в Telegram
     * @return найденный пользователь или null, если не найден
     */
    public User getUserByTgName(String userTgName) {
        User user = userRepository.findByUserTgName(userTgName);
        if (user == null) {
            throw new RuntimeException("User not found");
        }
        return user;
    }

    /**
     * Возвращает активные уведомления пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return список активных уведомлений (может быть пустым)
     */
    public List<Notification> getActiveUserNotifications(String chatId) {
        Optional<User> userOpt = userRepository.findOptionalByChatId(chatId);
        User user = userOpt.orElseThrow(() -> new NoSuchElementException("User not found"));
        
        if (user.getNotificationIds() == null || user.getNotificationIds().isEmpty()) {
            return Collections.emptyList();
        }
        
        return user.getNotificationIds().stream()
            .map(notificationId -> notificationService.getNotification(notificationId)
                .block())
            .filter(Objects::nonNull)
            .filter(Notification::isActive)
            .collect(Collectors.toList());
    }

    /**
     * Возвращает активные уведомления пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return список активных уведомлений (может быть пустым)
     */
    public Flux<Notification> getUserNotifications(String chatId) {
        Optional<User> userOpt = userRepository.findOptionalByChatId(chatId);
        User user = userOpt.orElseThrow(() -> new NoSuchElementException("User not found"));
        
        if (user.getNotificationIds() == null || user.getNotificationIds().isEmpty()) {
            return Flux.empty();
        }
        
        return Flux.fromIterable(user.getNotificationIds())
            .map(notificationId -> notificationService.getNotification(notificationId)
                .block())
            .filter(Objects::nonNull);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public void deleteUser(String userTgName) {
        User user = getUserByTgName(userTgName);
        userRepository.delete(user);
    }

    /**
     * Находит пользователя по ID чата в Telegram.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<User> найденный пользователь или пустой Mono, если не найден
     */
    public Mono<User> getUserByChatId(String chatId) {
        log.info("Searching for user with chatId: {}", chatId);
        try {
            if (chatId == null || chatId.trim().isEmpty()) {
                log.warn("Invalid chatId provided: {}", chatId);
                return Mono.empty();
            }

            Query query = new Query(Criteria.where("chatId").is(chatId.trim()));
            User user = mongoTemplate.findOne(query, User.class);
            
            if (user == null) {
                log.warn("User not found for chatId: {}", chatId);
                return Mono.empty();
            }
            
            log.info("Found user: {}", user);
            return Mono.just(user);
        } catch (Exception e) {
            log.error("Error searching for user with chatId {}: {}", chatId, e.getMessage(), e);
            return Mono.error(e);
        }
    }

    /**
     * Сохраняет пользователя в базу данных.
     *
     * @param user пользователь для сохранения
     * @return Mono<User> сохраненный пользователь
     */
    public Mono<User> save(User user) {
        return Mono.fromCallable(() -> userRepository.save(user));
    }

    /**
     * Находит пользователя по идентификатору чата.
     *
     * @param chatId идентификатор чата пользователя
     * @return найденный пользователь или null, если пользователь не найден
     */
    public User findByChatId(String chatId) {
        return userRepository.findByChatId(chatId);
    }
}