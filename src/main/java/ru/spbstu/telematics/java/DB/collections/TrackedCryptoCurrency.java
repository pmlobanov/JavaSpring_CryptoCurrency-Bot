package ru.spbstu.telematics.java.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;

import java.time.Instant;

/**
 * Класс, представляющий отслеживаемую криптовалюту с текущим значением и временем последнего обновления.
 * Хранится в коллекции MongoDB "TrackedCryptoCurrencies".
 *
 * <p>Основные характеристики:</p>
 * <ul>
 *   <li>Тип криптовалюты ({@link CryptoCurrency})</li>
 *   <li>Последнее зафиксированное значение</li>
 *   <li>Временная метка последнего обновления</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * TrackedCryptoCurrency trackedBtc = new TrackedCryptoCurrency(CryptoCurrency.BTC, 50000.0);
 * trackedBtc.setLastSeenValue(51000.0); // Обновление значения
 * }</pre>
 *
 * @see Document
 * @see CryptoCurrency
 */
@Document(collection = "TrackedCryptoCurrencies")
public class TrackedCryptoCurrency {


    @Id
    private String id;

    private CryptoCurrency cryptoCurrency;
    private Double lastSeenValue;
    private Instant dateOfValueUpdate;


    /**
     * Создает новый объект отслеживаемой криптовалюты.
     * @param cryptoCurrency тип криптовалюты
     * @param lastSeenValue текущее значение
     */
    @PersistenceConstructor
    public TrackedCryptoCurrency(CryptoCurrency cryptoCurrency, Double lastSeenValue ){
        this.cryptoCurrency = cryptoCurrency;
        this.lastSeenValue = lastSeenValue ;
        this.dateOfValueUpdate =  Instant.now();
    }

    /**
     * Возвращает тип криптовалюты.
     * @return объект {@link CryptoCurrency}
     */
    public CryptoCurrency getCryptoCurrency() {
        return cryptoCurrency;
    }

    /**
     * Устанавливает тип криптовалюты.
     * @param cryptoCurrency новый тип криптовалюты
     */
    public void setCryptoCurrency(CryptoCurrency cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    /**
     * Возвращает последнее зафиксированное значение.
     * @return числовое значение криптовалюты
     */
    public Double getLastSeenValue() {
        return lastSeenValue;
    }

    /**
     * Обновляет значение криптовалюты и устанавливает текущее время как время обновления.
     * @param lastSeenValue новое значение
     */
    public void setLastSeenValue(Double lastSeenValue) {
        this.lastSeenValue = lastSeenValue;
    }

    /**
     * Возвращает время последнего обновления значения.
     * @return временная метка последнего обновления
     */
    public Instant getDateOfValueUpdate() {
        return dateOfValueUpdate;
    }

    /**
     * Устанавливает время обновления значения.
     * @param dateOfValueUpdate новая временная метка
     */
    public void setDateOfValueUpdate(Instant dateOfValueUpdate) {
        this.dateOfValueUpdate = dateOfValueUpdate;
    }

    /**
     * Возвращает уникальный идентификатор отслеживаемой криптовалюты.
     * @return строковый идентификатор документа
     */
    public String getId() {
        return id;
    }

    /**
     * Возвращает строковое представление объекта.
     * @return строковое описание со всеми полями
     */
    @Override
    public String toString() {
        return "TrackedCryptoCurrency{" +
                "id='" + id + '\'' +
                ", cryptoCurrency=" + cryptoCurrency +
                ", lastSeenValue=" + lastSeenValue +
                ", dateOfValueUpdate=" + dateOfValueUpdate +
                '}';
    }
}
