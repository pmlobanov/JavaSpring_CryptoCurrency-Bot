package ru.spbstu.telematics.bitbotx.model;

import java.math.BigDecimal;

/**
 * Алерт на основе абсолютных значений минимальной и максимальной цены.
 * Срабатывает, когда текущая цена выходит за пределы заданного диапазона.
 */
public record AlertVal(
    String symbol,
    BigDecimal startPrice,
    long startTimestamp,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    boolean triggered,
    Long triggerTimestamp
) {
    public AlertVal(String symbol, BigDecimal startPrice, long startTimestamp, BigDecimal minPrice, BigDecimal maxPrice) {
        this(symbol, startPrice, startTimestamp, minPrice, maxPrice, false, null);
    }
    
    /**
     * Проверяет, сработал ли алерт на основе текущей цены.
     * Алерт срабатывает, если цена выходит за пределы диапазона (minPrice, maxPrice).
     * Если алерт уже сработал (triggered = true), то метод вернет false.
     * 
     * @param currentPrice Текущая цена актива
     * @return true, если алерт сработал впервые, false в остальных случаях
     */
    public boolean checkTrigger(BigDecimal currentPrice) {
        if (triggered) {
            return false; // Уже сработал ранее
        }
        
        boolean isTriggered = currentPrice.compareTo(minPrice) <= 0 || currentPrice.compareTo(maxPrice) >= 0;
        
        if (isTriggered) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Устанавливает флаг triggered в true и сохраняет текущий timestamp.
     * Следует вызывать, когда алерт срабатывает.
     * 
     * @return Новый экземпляр AlertVal с обновленными параметрами
     */
    public AlertVal withTrigger() {
        return new AlertVal(symbol, startPrice, startTimestamp, minPrice, maxPrice, true, System.currentTimeMillis() / 1000);
    }
    
    /**
     * Возвращает описание алерта для логирования.
     */
    public String getDescription() {
        return "Price alert for " + symbol;
    }
    
    public boolean isTriggered() {
        return triggered;
    }
    
    public Long getTriggerTimestamp() {
        return triggerTimestamp;
    }
} 