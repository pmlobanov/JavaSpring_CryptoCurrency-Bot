package ru.spbstu.telematics.java.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Класс, представляющий уведомление о достижении порогового значения для криптовалюты.
 * Хранится в коллекции MongoDB "notifications".
 *
 * <p>Поддерживает три типа пороговых значений:</p>
 * <ul>
 *   <li>VALUE - абсолютное значение</li>
 *   <li>PERCENT - процентное изменение</li>
 *   <li>EMA - экспоненциальное скользящее среднее</li>
 * </ul>
 *
 * <p>Пример создания уведомления:</p>
 * <pre>{@code
 * Notification alert = Notification.createValueThreshold(CryptoCurrency.BTC, 50000.0);
 * }</pre>
 *
 * @see Document
 * @see CryptoCurrency
 */
@Document(collection = "notifications")
public class Notification {
    /**
     * Возвращает уникальный идентификатор уведомления.
     * @return строковый идентификатор
     */
    public String getId() {
        return this.id;
    }

    /**
     * Тип порогового значения для уведомления.
     */
    public enum ThresholdType {
        /** Абсолютное значение */
        VALUE,
        /** Процентное изменение */
        PERCENT,
        /** Экспоненциальное скользящее среднее */
        EMA
    }

    @Id
    private String id;
    private CryptoCurrency cryptoCurrency;
    private Double valueThreshold;
    private Double percentThreshold;
    private Double emaValue;
    private ThresholdType thresholdType;
    private Boolean isActive;

    /**
     * Приватный конструктор для создания уведомления.
     * @param cryptoCurrency криптовалюта для отслеживания
     * @param value пороговое значение
     * @param type тип порога
     */
    private Notification(CryptoCurrency cryptoCurrency,
                         Double value,
                         ThresholdType type) {
        this.cryptoCurrency = cryptoCurrency;
        this.thresholdType = type;
        this.isActive = true;

        switch (type) {
            case VALUE:
                this.valueThreshold = value;
                break;
            case PERCENT:
                this.percentThreshold = value;
                break;
            case EMA:
                this.emaValue = value;
                break;
        }
    }

    // Конструктор для Spring Data
    @PersistenceConstructor
    public Notification(String id, CryptoCurrency cryptoCurrency,
                        Double valueThreshold, Double percentThreshold,
                        Double emaValue, ThresholdType thresholdType,
                        Boolean isActive) {
        this.id = id;
        this.cryptoCurrency = cryptoCurrency;
        this.valueThreshold = valueThreshold;
        this.percentThreshold = percentThreshold;
        this.emaValue = emaValue;
        this.thresholdType = thresholdType;
        this.isActive = isActive;
    }

    /**
     * Создает уведомление с абсолютным пороговым значением.
     * @param currency криптовалюта
     * @param threshold пороговое значение
     * @return новый экземпляр Notification
     */
    public static Notification createValueThreshold(CryptoCurrency currency, Double threshold) {
        return new Notification(currency, threshold, ThresholdType.VALUE);
    }
    /**
     * Создает уведомление с процентным изменением.
     * @param currency криптовалюта
     * @param percent процент изменения
     * @return новый экземпляр Notification
     */
    public static Notification createPercentThreshold(CryptoCurrency currency, Double percent) {
        return new Notification(currency, percent, ThresholdType.PERCENT);
    }
    /**
     * Создает уведомление на основе EMA.
     * @param currency криптовалюта
     * @param emaValue значение EMA
     * @return новый экземпляр Notification
     */
    public static Notification createEMAThreshold(CryptoCurrency currency, Double emaValue) {
        return new Notification(currency, emaValue, ThresholdType.EMA);
    }

    /**
     * Возвращает отслеживаемую криптовалюту.
     * @return криптовалюта
     */
    public CryptoCurrency getCryptoCurrency() {
        return cryptoCurrency;
    }

    /**
     * Возвращает активное пороговое значение в зависимости от типа уведомления.
     * @return текущее пороговое значение
     * @throws IllegalStateException если тип порога неизвестен
     */
    public Double getActiveThreshold() {
        switch (thresholdType) {
            case VALUE: return valueThreshold;
            case PERCENT: return percentThreshold;
            case EMA: return emaValue;
            default: throw new IllegalStateException("Unknown threshold type");
        }
    }
    /**
     * Возвращает тип порогового значения.
     * @return тип порога
     */
    public ThresholdType getThresholdType() {
        return thresholdType;
    }

    /**
     * Проверяет, активно ли уведомление.
     * @return true если уведомление активно
     */
    public Boolean isActive() {
        return isActive;
    }

    /**
     * Деактивирует уведомление.
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Устанавливает статус активности уведомления.
     * @param marlOfActiveness флаг активности
     */
    public void setIsActive(Boolean marlOfActiveness){
        this.isActive = marlOfActiveness;
    }

    /**
     * Проверяет валидность состояния уведомления.
     * @throws IllegalStateException если задано некорректное количество порогов
     */
    public void validate() {
        if (Stream.of(valueThreshold, percentThreshold, emaValue)
                .filter(Objects::nonNull)
                .count() != 1) {
            throw new IllegalStateException("Должен быть задан ровно один порог");
        }
    }

    /**
     * Возвращает строковое представление уведомления.
     * @return строковое описание объекта
     */
    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", cryptoCurrency=" + cryptoCurrency +
                ", valueThreshold=" + valueThreshold +
                ", percentThreshold=" + percentThreshold +
                ", emaValue=" + emaValue +
                ", thresholdType=" + thresholdType +
                ", isActive=" + isActive +
                '}';
    }

}