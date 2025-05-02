package ru.spbstu.telematics.bitbotx.model;

import java.math.BigDecimal;

/**
 * Алерт на основе EMA (Exponential Moving Average).
 * Срабатывает при пересечении ценой средней и изменении направления тренда.
 */
public class AlertEMA extends Alert {
    private final BigDecimal startEMA;
    private BigDecimal emaValue;
    private boolean isUpper;  // true: EMA выше цены, false: EMA ниже цены
    
    public AlertEMA(String symbol, BigDecimal startPrice, long startTimestamp, BigDecimal emaValue) {
        super(symbol, startPrice, startTimestamp, AlertType.EMA);
        this.startEMA = emaValue;
        this.emaValue = emaValue;
        this.isUpper = emaValue.compareTo(startPrice) > 0;
    }
    
    /**
     * Проверяет пересечение ценой EMA и смену тренда.
     * Алерт срабатывает, когда цена пересекает EMA и направление тренда меняется.
     * 
     * @param currentPrice Текущая цена актива
     * @return true, если алерт сработал
     */
    @Override
    public boolean checkTrigger(BigDecimal currentPrice) {
        this.currentPrice = currentPrice;
        boolean newIsUpper = emaValue.compareTo(currentPrice) > 0;
        boolean triggered = newIsUpper != isUpper;
        
        if (triggered) {
            isUpper = newIsUpper;
        }
        
        return triggered;
    }
    
    @Override
    public String getDescription() {
        String direction = isUpper ? "above" : "below";
        return String.format("EMA alert for %s: EMA is %s price", 
                symbol, direction);
    }
    
    public BigDecimal getStartEMA() {
        return startEMA;
    }
    
    public BigDecimal getEmaValue() {
        return emaValue;
    }
    
    public void setEmaValue(BigDecimal emaValue) {
        this.emaValue = emaValue;
    }
    
    public boolean isUpper() {
        return isUpper;
    }
    
    public void setIsUpper(boolean isUpper) {
        this.isUpper = isUpper;
    }
    
    // Методы совместимости с новой структурой для правильного отображения в JSON
    public BigDecimal startPrice() {
        return getStartPrice();
    }
    
    public long startTimestamp() {
        return getStartTimestamp();
    }
} 