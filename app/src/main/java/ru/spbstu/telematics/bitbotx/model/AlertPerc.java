package ru.spbstu.telematics.bitbotx.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Алерт на основе процентного отклонения от начальной цены.
 * Срабатывает, когда текущая цена отклоняется на заданный процент вверх или вниз.
 */
public record AlertPerc(
    String symbol,
    BigDecimal startPrice,
    long startTimestamp,
    BigDecimal downPercent,
    BigDecimal upPercent,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    boolean triggered,
    Long triggerTimestamp
) {
    public AlertPerc(String symbol, BigDecimal startPrice, long startTimestamp, BigDecimal downPercent, BigDecimal upPercent) {
        this(
            symbol, 
            startPrice, 
            startTimestamp, 
            downPercent, 
            upPercent, 
            startPrice.multiply(BigDecimal.ONE.subtract(downPercent.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP),
            startPrice.multiply(BigDecimal.ONE.add(upPercent.divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP),
            false,
            null
        );
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
     * @return Новый экземпляр AlertPerc с обновленными параметрами
     */
    public AlertPerc withTrigger() {
        return new AlertPerc(symbol, startPrice, startTimestamp, downPercent, upPercent, minPrice, maxPrice, true, System.currentTimeMillis() / 1000);
    }
    
    /**
     * Возвращает описание алерта для логирования.
     */
    public String getDescription() {
        return "Percent alert for " + symbol;
    }
    
    public boolean isTriggered() {
        return triggered;
    }
    
    public Long getTriggerTimestamp() {
        return triggerTimestamp;
    }
} 