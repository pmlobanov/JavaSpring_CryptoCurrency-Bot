package spbstu.mcs.telegramBot.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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
 * Notification alert = Notification.createValueThreshold(Currency.Crypto.BTC, 50000.0);
 * }</pre>
 *
 * @see Document
 * @see Currency.Crypto
 */
@Data
@Document(collection = "notifications")
public class Notification {
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

    @Field("cryptoCurrency")
    private Currency.Crypto cryptoCurrency;

    @Field("fiatCurrency")
    private Currency.Fiat fiatCurrency;

    public void setThresholdType(ThresholdType thresholdType) {
        this.thresholdType = thresholdType;
    }

    @Field("thresholdType")
    private ThresholdType thresholdType;

    @Field(value = "isActive", write = Field.Write.NON_NULL)
    private Boolean isActive;

    @Field("chatId")
    private String chatId;

    @Field("upperBoundary")
    private Double upperBoundary;

    @Field("lowerBoundary")
    private Double lowerBoundary;

    @Field("startPrice")
    private Double startPrice;

    @Field("startTimestamp")
    private Long startTimestamp;

    public Long getTriggerTimestamp() {
        return triggerTimestamp;
    }

    public void setTriggerTimestamp(Long triggerTimestamp) {
        this.triggerTimestamp = triggerTimestamp;
    }

    @Field("triggerTimestamp")
    private Long triggerTimestamp;

    public Double getUpPercent() {
        return upPercent;
    }

    public void setUpPercent(Double upPercent) {
        this.upPercent = upPercent;
    }

    public Boolean getActive() {
        return isActive;
    }

    public void setActive(Boolean active) {
        isActive = active;
    }

    public Long getStartTimestamp() {
        return startTimestamp;
    }

    public void setStartTimestamp(Long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public Double getDownPercent() {
        return downPercent;
    }

    public void setDownPercent(Double downPercent) {
        this.downPercent = downPercent;
    }

    public Double getStartEMA() {
        return startEMA;
    }

    public void setStartEMA(Double startEMA) {
        this.startEMA = startEMA;
    }

    public Double getCurrentEMA() {
        return currentEMA;
    }

    public void setCurrentEMA(Double currentEMA) {
        this.currentEMA = currentEMA;
    }

    // Для PERCENT алертов
    @Field("upPercent")
    private Double upPercent;

    @Field("downPercent")
    private Double downPercent;

    // Для EMA алертов
    @Field("startEMA")
    private Double startEMA;

    @Field("currentEMA")
    private Double currentEMA;

    @Field("isAbove")
    private Boolean isAbove; // Для EMA алертов: true если EMA выше цены, false если ниже

    /**
     * Конструктор без параметров для Spring Data MongoDB
     */
    public Notification() {
    }

    /**
     * Конструктор для создания уведомления
     */
    public Notification(String id, Currency.Crypto cryptoCurrency, Currency.Fiat fiatCurrency, ThresholdType thresholdType, boolean isActive,
                        String chatId, Double upperBoundary, Double lowerBoundary, Double startPrice) {
        this.id = id;
        this.cryptoCurrency = cryptoCurrency;
        this.fiatCurrency = fiatCurrency;
        this.thresholdType = thresholdType;
        if (thresholdType != ThresholdType.EMA) {
            this.isActive = isActive;
            this.upperBoundary = upperBoundary;
            this.lowerBoundary = lowerBoundary;
        }
        this.chatId = chatId;
        this.startPrice = startPrice;
        this.startTimestamp = System.currentTimeMillis() / 1000; // Unix timestamp в секундах
    }

    /**
     * Возвращает уникальный идентификатор уведомления.
     * @return строковый идентификатор
     */
    public String getId() {
        return id;
    }

    /**
     * Возвращает отслеживаемую криптовалюту.
     * @return криптовалюта
     */
    public Currency.Crypto getCryptoCurrency() {
        return cryptoCurrency;
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
    public boolean isActive() {
        if (thresholdType == ThresholdType.EMA) {
            return true;
        }
        return isActive != null && isActive;
    }

    /**
     * Устанавливает статус активности уведомления.
     * @param isActive флаг активности
     */
    public void setIsActive(boolean isActive) {
        if (thresholdType != ThresholdType.EMA) {
            this.isActive = isActive;
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
                ", thresholdType=" + thresholdType +
                ", isActive=" + isActive +
                ", chatId='" + chatId + '\'' +
                ", upperBoundary=" + upperBoundary +
                ", lowerBoundary=" + lowerBoundary +
                ", startPrice=" + startPrice +
                '}';
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public Double getUpperBoundary() {
        return upperBoundary;
    }

    public void setUpperBoundary(Double upperBoundary) {
        this.upperBoundary = upperBoundary;
    }

    public Double getLowerBoundary() {
        return lowerBoundary;
    }

    public void setLowerBoundary(Double lowerBoundary) {
        this.lowerBoundary = lowerBoundary;
    }

    public Double getStartPrice() {
        return startPrice;
    }

    public void setStartPrice(Double startPrice) {
        this.startPrice = startPrice;
    }

    public Double getActiveThreshold() {
        return upperBoundary;
    }

    public Currency.Fiat getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(Currency.Fiat fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public Boolean getIsAbove() {
        return isAbove;
    }

    public void setIsAbove(Boolean isAbove) {
        this.isAbove = isAbove;
    }
}