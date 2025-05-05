package spbstu.mcs.telegramBot.cryptoApi.model;

import java.math.BigDecimal;

/**
 * Базовый класс для алертов по цене.
 */
public abstract class Alert {
    protected final String symbol;
    protected final BigDecimal startPrice;
    protected final long startTimestamp;
    protected final AlertType type;
    protected BigDecimal currentPrice;
    
    public Alert(String symbol, BigDecimal startPrice, long startTimestamp, AlertType type) {
        this.symbol = symbol;
        this.startPrice = startPrice;
        this.startTimestamp = startTimestamp;
        this.type = type;
    }
    
    /**
     * Проверяет, сработал ли алерт на основе текущей цены.
     * 
     * @param currentPrice Текущая цена актива
     * @return true, если алерт сработал
     */
    public abstract boolean checkTrigger(BigDecimal currentPrice);
    
    /**
     * Возвращает описание алерта для логирования.
     */
    public abstract String getDescription();
    
    public String getSymbol() {
        return symbol;
    }
    
    public BigDecimal getStartPrice() {
        return startPrice;
    }
    
    public long getStartTimestamp() {
        return startTimestamp;
    }
    
    public AlertType getType() {
        return type;
    }
    
    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }
}