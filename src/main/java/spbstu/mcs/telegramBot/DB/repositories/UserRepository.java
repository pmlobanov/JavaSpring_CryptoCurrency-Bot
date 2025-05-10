package spbstu.mcs.telegramBot.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import spbstu.mcs.telegramBot.DB.collections.User;

import java.util.List;
import java.util.Optional;

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

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    /**
     * Находит пользователя по его Telegram имени.
     * Поиск выполняется с учетом регистра символов.
     *
     * @param userTgName Telegram имя пользователя для поиска
     * @return найденный пользователь или {@code null}, если пользователь не существует
     */
    User findByUserTgName(String userTgName);

    /**
     * Находит пользователя по идентификатору чата.
     *
     * @param chatId идентификатор чата пользователя
     * @return найденный пользователь или null, если пользователь не найден
     */
    User findByChatId(String chatId);

    /**
     * Находит пользователя по ID чата в Telegram и возвращает результат как Optional.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Optional с найденным пользователем или пустой Optional
     */
    Optional<User> findOptionalByChatId(String chatId);

    /**
     * Проверяет существование пользователя с указанным ID чата.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return true если пользователь существует, false в противном случае
     */
    boolean existsByChatId(String chatId);

    /**
     * Находит всех пользователей, чьи ID чатов содержатся в указанном списке.
     *
     * @param chatIds список ID чатов для поиска
     * @return список найденных пользователей
     */
    List<User> findAllByChatIdIn(List<String> chatIds);

    /**
     * Удаляет пользователя по ID чата.
     *
     * @param chatId ID чата пользователя для удаления
     * @return количество удаленных документов
     */
    long deleteByChatId(String chatId);
}