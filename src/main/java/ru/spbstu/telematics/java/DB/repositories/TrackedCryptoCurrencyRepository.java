package ru.spbstu.telematics.java.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import ru.spbstu.telematics.java.DB.collections.TrackedCryptoCurrency;

/**
 * Репозиторий для работы с отслеживаемыми криптовалютами в MongoDB.
 * Наследует стандартные CRUD-операции из {@link MongoRepository}.
 *
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Сохранение, обновление и удаление записей о криптовалютах</li>
 *   <li>Поиск по идентификатору</li>
 *   <li>Получение всех отслеживаемых криптовалют</li>
 * </ul>
 *
 * <p>Примеры использования:</p>
 * <pre>{@code
 * // Сохранение новой отслеживаемой криптовалюты
 * TrackedCryptoCurrency currency = new TrackedCryptoCurrency(CryptoCurrency.BTC, 50000.0);
 * trackedCryptoRepository.save(currency);
 *
 * // Поиск по ID
 * Optional<TrackedCryptoCurrency> found = trackedCryptoRepository.findById("123abc");
 *
 * // Получение всех записей
 * List<TrackedCryptoCurrency> allCurrencies = trackedCryptoRepository.findAll();
 * }</pre>
 *
 * @see MongoRepository
 * @see TrackedCryptoCurrency
 */

public interface TrackedCryptoCurrencyRepository extends MongoRepository<TrackedCryptoCurrency, String> {
    // Наследует все стандартные методы MongoRepository:
    // save(), findById(), findAll(), deleteById() и другие
}