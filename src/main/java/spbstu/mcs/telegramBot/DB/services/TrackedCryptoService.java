package spbstu.mcs.telegramBot.DB.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.DB.collections.TrackedCryptoCurrency;
import spbstu.mcs.telegramBot.DB.currencies.CryptoCurrency;
import spbstu.mcs.telegramBot.DB.repositories.TrackedCryptoCurrencyRepository;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Сервис для отслеживания криптовалют и управления их значениями.
 * Обеспечивает основные операции с отслеживаемыми криптовалютами.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Добавление новых криптовалют для отслеживания</li>
 *   <li>Обновление текущих значений криптовалют</li>
 *   <li>Получение информации о криптовалютах</li>
 * </ul>
 */
@Service
public class TrackedCryptoService {
    @Autowired
    private TrackedCryptoCurrencyRepository cryptoRepository;

    /**
     * Добавляет новую криптовалюту для отслеживания.
     *
     * @param currency тип криптовалюты для отслеживания
     * @param initialValue начальное значение криптовалюты
     * @return созданный объект отслеживаемой криптовалюты
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    public TrackedCryptoCurrency trackNewCurrency(CryptoCurrency currency, Double initialValue) {
        return cryptoRepository.save(new TrackedCryptoCurrency(currency, initialValue));
    }

    /**
     * Обновляет значение отслеживаемой криптовалюты.
     *
     * @param cryptoId идентификатор отслеживаемой криптовалюты
     * @param newValue новое значение криптовалюты
     * @return обновленный объект криптовалюты
     * @throws ClassNotFoundException если криптовалюта с указанным ID не найдена
     * @throws org.springframework.dao.DataAccessException при ошибках обновления
     */
    public TrackedCryptoCurrency updateCurrencyValue(String cryptoId, Double newValue) throws ClassNotFoundException {
        Optional<TrackedCryptoCurrency> crypto = cryptoRepository.findById(cryptoId);
        if(crypto.isPresent()){
            crypto.get().setLastSeenValue(newValue);
            crypto.get().setDateOfValueUpdate(Instant.now());
            return cryptoRepository.save(crypto.get());
        }else throw new ClassNotFoundException("No crypto with such ID");

    }

    /**
     * Возвращает список всех отслеживаемых криптовалют.
     *
     * @return список всех криптовалют (может быть пустым)
     */
    public List<TrackedCryptoCurrency> getAllTrackedCurrencies() {
        return cryptoRepository.findAll();
    }

    /**
     * Находит криптовалюту по идентификатору.
     *
     * @param cryptoId идентификатор криптовалюты
     * @return найденная криптовалюта
     * @throws NoSuchElementException если криптовалюта не найдена
     */
    public TrackedCryptoCurrency getCurrencyById(String cryptoId) {
        if (cryptoId == null || cryptoId.isEmpty()) {
            throw new IllegalArgumentException("Crypto ID cannot be null or empty");
        }

        try {
            return cryptoRepository.findById(cryptoId)
                    .orElseThrow(() -> new NoSuchElementException(
                            "Crypto currency not found with id: " + cryptoId));
        } catch (Exception e) {
            throw new RuntimeException("Error accessing database", e);
        }
    }
}
