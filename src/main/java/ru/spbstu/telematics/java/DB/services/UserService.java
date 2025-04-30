package ru.spbstu.telematics.java.DB.services;

import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import ru.spbstu.telematics.java.DB.DTO.UserPortfolioView;
import ru.spbstu.telematics.java.DB.collections.Notification;
import ru.spbstu.telematics.java.DB.collections.Portfolio;
import ru.spbstu.telematics.java.DB.collections.User;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;
import ru.spbstu.telematics.java.DB.currencies.FiatCurrency;
import ru.spbstu.telematics.java.DB.repositories.UserRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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
    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final MongoTemplate mongoTemplate;

    @Autowired
    private PortfolioService portfolioService;
    @Autowired
    private NotificationService notificationService;

    /**
     * Создает нового пользователя с указанными параметрами.
     *
     * @param tgName имя пользователя в Telegram
     * @param fiat предпочитаемая фиатная валюта
     * @param defaultCrypto криптовалюта по умолчанию
     * @return созданный пользователь
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    public User createUser(String tgName, FiatCurrency fiat, CryptoCurrency defaultCrypto) {
        return userRepository.save(new User(tgName, fiat, defaultCrypto));
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
     * @param userId идентификатор пользователя
     * @param notificationId идентификатор уведомления для добавления
     * @throws NoSuchElementException если пользователь не найден
     */
    public void addNotificationToUser(String userId, String notificationId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

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
    public UserPortfolioView getUserPortfolioData(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        List<Portfolio> portfolios = user.getPortfolioIds() == null ? List.of() :
                user.getPortfolioIds().stream()
                        .map(portfolioService::getPortfolio)
                        .collect(Collectors.toList());

        List<Notification> notifications = user.getNotificationIds() == null ? List.of() :
                user.getNotificationIds().stream()
                        .map(notificationService::getNotification)
                        .collect(Collectors.toList());

        return new UserPortfolioView(userId, notifications, portfolios);
    }

    /**
     * Конструктор сервиса с внедрением зависимостей.
     *
     * @param userRepository репозиторий пользователей
     * @param mongoTemplate MongoTemplate для работы с MongoDB
     */
    public UserService(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Находит пользователя по имени в Telegram.
     *
     * @param userTgName имя пользователя в Telegram
     * @return найденный пользователь или null, если не найден
     */
    public User findByUserTgName(String userTgName) {
        return userRepository.findByUserTgName(userTgName);
    }

    /**
     * Обновляет предпочитаемую фиатную валюту пользователя.
     *
     * @param userId идентификатор пользователя
     * @param newCurrency новая фиатная валюта
     * @throws org.springframework.dao.DataAccessException при ошибках обновления
     */
    public void updateUserFiatCurrency(String userId, FiatCurrency newCurrency) {
        Query query = new Query(Criteria.where("id").is(userId));
        Update update = new Update().set("fiatCurrency", newCurrency);
        mongoTemplate.updateFirst(query, update, User.class);
    }

    /**
     * Обновляет криптовалюту по умолчанию для пользователя.
     *
     * @param userId идентификатор пользователя
     * @param newCurrency новая криптовалюта по умолчанию
     * @throws org.springframework.dao.DataAccessException при ошибках обновления
     */
    public void updateUserDefaultCryptoCurrency(String userId, CryptoCurrency newCurrency) {
        Query query = new Query(Criteria.where("id").is(userId));
        Update update = new Update().set("defaultCryptoCurrrency", newCurrency);
        mongoTemplate.updateFirst(query, update, User.class);
    }

    /**
     * Возвращает активные уведомления пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список активных уведомлений (может быть пустым)
     */
    public List<Notification> getActiveUserNotifications(String userId) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("_id").is(userId)),
                Aggregation.lookup("notifications", "notificationIds", "_id", "notifications"),
                Aggregation.unwind("notifications"),
                Aggregation.replaceRoot("notifications"),
                Aggregation.match(Criteria.where("isActive").is(true))
        );

        return mongoTemplate.aggregate(aggregation, "users", Notification.class)
                .getMappedResults();
    }

    /**
     * Возвращает активные уведомления пользователя.
     *
     * @param userId идентификатор пользователя
     * @return список активных уведомлений (может быть пустым)
     */
    public List<Notification> getUserNotifications(String userId) {
        // 1. Получаем пользователя
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // 2. Если нет уведомлений - возвращаем пустой список
        if (user.getNotificationIds() == null || user.getNotificationIds().isEmpty()) {
            return Collections.emptyList();
        }

        // 3. Получаем все уведомления по списку ID
        return user.getNotificationIds().stream().map(id -> notificationService.getNotification(id)).toList();
    }
}