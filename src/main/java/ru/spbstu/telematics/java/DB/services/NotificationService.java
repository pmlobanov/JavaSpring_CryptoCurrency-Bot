package ru.spbstu.telematics.java.DB.services;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import ru.spbstu.telematics.java.DB.collections.Notification;
import org.springframework.data.mongodb.core.query.Update;
import ru.spbstu.telematics.java.DB.collections.Portfolio;
import ru.spbstu.telematics.java.DB.collections.TrackedCryptoCurrency;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;
import ru.spbstu.telematics.java.DB.repositories.NotificationRepository;

import java.util.List;
import java.util.NoSuchElementException;


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
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private MongoTemplate mongoTemplate;


    /**
     * Создает уведомление о достижении порогового значения.
     *
     * @param userId идентификатор пользователя
     * @param currency криптовалюта для отслеживания
     * @param thresholdValue пороговое значение
     * @return созданное уведомление
     * @throws org.springframework.dao.DataAccessException при ошибках работы с БД
     */
    public Notification createValueAlert(String userId,
                                         CryptoCurrency currency,
                                         Double thresholdValue) {
        Notification notification = Notification.createValueThreshold(
                currency,
                thresholdValue
        );
        Notification savedNotification = notificationRepository.save(notification);
        userService.addNotificationToUser(userId, savedNotification.getId());
        return savedNotification;
    }


    /**
     * Проверяет и активирует уведомления для портфеля.
     * Выполняет сложную агрегацию для определения сработавших уведомлений.
     *
     * @param portfolioId идентификатор портфеля
     * @throws org.springframework.dao.DataAccessException при ошибках агрегации
     */
    public void checkAndTriggerAlerts(String portfolioId) {
        // 1. Cобираем данные для обновления
        Aggregation aggregation = Aggregation.newAggregation(
                //получили все методы по id
                Aggregation.match(Criteria.where("_id").is(portfolioId)),
                // рзвернули в элементы, где у каждого один элемент listOfCurrencies т.е одна валюта
                Aggregation.unwind("listOfCurrencies"),
                // аналог left join в SQL. Приписываем notifications к тем классам, где отслеживаемая валюта равна валюте в notifications.
                // итого получаем, для каждой валюты - свой список уведомлений
                Aggregation.lookup("notifications", "listOfCurrencies.trackedCrypto.cryptoCurrency", "cryptoCurrency", "notifications"),
                // развернули, чтобы для каждого класса было только одно уведомление  - так легче проходиться по списку
                // то есть в итоге получаем список пар валюта-уведомление.
                Aggregation.unwind("notifications"),
                // ну ти теперь смотрим, что если значения в отслеживаемой валюте - больше превышает наш порог - сетим уведомление в актив.
                Aggregation.project()
                        .and("listOfCurrencies.trackedCrypto.lastSeenValue").as("currentValue")
                        .and("listOfCurrencies.initialPrice").as("initialPrice")
                        .and("notifications").as("notification")
                        .andExpression("""
                (notifications.thresholdType == 'VALUE' && currentValue >= notifications.valueThreshold) ||
                (notifications.thresholdType == 'PERCENT' && 
                 ((currentValue - initialPrice)/initialPrice*100 >= notifications.percentThreshold))
                """).as("shouldTrigger"),
                Aggregation.match(Criteria.where("shouldTrigger").is(true))
        );

        // 2. Выполнение агрегации
        List<Document> results = mongoTemplate.aggregate(aggregation, "portfolios", Document.class)
                .getMappedResults();

        // 3. Активация уведомлений
        results.forEach(doc -> {
            Notification notification = mongoTemplate.getConverter().read(
                    Notification.class,
                    doc.get("notification", Document.class)
            );
            triggerNotification(notification);
        });
    }

    /**
     * Активирует уведомление и сохраняет его в БД.
     *
     * @param notification уведомление для активации
     * @throws IllegalArgumentException если notification == null
     */
    private void triggerNotification(Notification notification) {

        notification.setIsActive(true);
        mongoTemplate.save(notification);
    }


    /**
     * Проверяет условия срабатывания уведомления.
     *
     * @param notification уведомление для проверки
     * @param currentValue текущее значение
     * @param initialValue начальное значение
     * @return true если условия срабатывания выполнены
     */
    private boolean shouldTrigger(Notification notification,
                                  Double currentValue,
                                  Double initialValue) {
        switch (notification.getThresholdType()) {
            case VALUE:
                return currentValue >= notification.getActiveThreshold();
            case PERCENT:
                Double changePercent = ((currentValue - initialValue) / initialValue) * 100;
                return changePercent >= notification.getActiveThreshold();
            case EMA:
                // Логика для EMA Тоже пока ждем от Максима, то, как он будет выдавать мне значения.
                return false;
            default:
                return false;
        }
    }

    /**
     * Удаляет уведомление по идентификатору.
     *
     * @param notificationId идентификатор уведомления
     * @throws NoSuchElementException если уведомление не найдено
     */
    public void deleteNotification(String notificationId){
        notificationRepository.deleteById(notificationId);
    }

    /**
     * Деактивирует уведомление.
     *
     * @param notificationId идентификатор уведомления
     * @throws NoSuchElementException если уведомление не найдено
     * @throws org.springframework.dao.DataAccessException при ошибках обновления
     */
    public void deactivateNotification(String notificationId){
        Query query = Query.query(Criteria.where("_id").is(notificationId));

        Update update = new Update().set("isActive", false);
        UpdateResult result = mongoTemplate.updateFirst(query, update, Notification.class);

        if (result.getModifiedCount() == 0) {
            throw new NoSuchElementException("Notification not found with id: " + notificationId);
        }

    }


    /**
     * Возвращает уведомление по идентификатору.
     *
     * @param notificationId идентификатор уведомления
     * @return найденное уведомление
     * @throws NoSuchElementException если уведомление не найдено
     */
    public Notification getNotification(String notificationId){
        return notificationRepository.findById(notificationId).orElseThrow(
                () -> new NoSuchElementException("Notification not found with id: " + notificationId));
    }

}
