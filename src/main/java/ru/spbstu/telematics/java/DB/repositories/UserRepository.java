package ru.spbstu.telematics.java.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.telematics.java.DB.collections.User;

/**
 * Репозиторий для работы с пользователями в MongoDB.
 * Обеспечивает стандартные CRUD-операции и специализированные запросы для сущности {@link User}.
 *
 * <p>Наследует все методы {@link MongoRepository} включая:</p>
 * <ul>
 *   <li>{@code save()} - сохранение пользователя</li>
 *   <li>{@code findById()} - поиск по идентификатору</li>
 *   <li>{@code findAll()} - получение всех пользователей</li>
 *   <li>{@code delete()} - удаление пользователя</li>
 * </ul>
 *
 * <p>Примеры использования:</p>
 * <pre>{@code
 * // Создание и сохранение нового пользователя
 * User user = new User("john_doe", FiatCurrency.USD, CryptoCurrency.BTC);
 * userRepository.save(user);
 *
 * // Поиск пользователя по Telegram имени
 * User foundUser = userRepository.findByUserTgName("john_doe");
 * }</pre>
 *
 * @see MongoRepository
 * @see User
 */

public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Находит пользователя по его Telegram имени.
     * Поиск выполняется с учетом регистра символов.
     *
     * @param userTgName Telegram имя пользователя для поиска (чувствительно к регистру)
     * @return найденный пользователь или {@code null}, если пользователь не существует
     * @throws org.springframework.dao.DataAccessException при ошибках доступа к данным
     */
    User findByUserTgName(String userTgName);
}