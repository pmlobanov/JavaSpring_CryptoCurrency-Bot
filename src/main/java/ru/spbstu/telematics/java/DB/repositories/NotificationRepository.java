package ru.spbstu.telematics.java.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.telematics.java.DB.collections.Notification;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;

import java.util.List;

/**
 * Репозиторий для работы с уведомлениями в MongoDB.
 * Расширяет {@link MongoRepository} для базовых CRUD операций с коллекцией уведомлений.
 *
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Стандартные CRUD операции через {@link MongoRepository}</li>
 *   <li>Поиск активных уведомлений по криптовалюте</li>
 *   <li>Поиск активных уведомлений пользователя</li>
 * </ul>
 *
 * <p>Примеры использования:</p>
 * <pre>{@code
 * // Найти все активные уведомления для BTC
 * List<Notification> btcAlerts = notificationRepository
 *     .findByCryptoCurrencyAndIsActiveTrue(CryptoCurrency.BTC);
 *
 * // Найти активные уведомления пользователя
 * List<Notification> userAlerts = notificationRepository
 *     .findByUserIdAndIsActiveTrue("user123");
 * }</pre>
 *
 * @see MongoRepository
 * @see Notification
 * @see CryptoCurrency
 */

public interface NotificationRepository extends MongoRepository<Notification, String> {

}