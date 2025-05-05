package spbstu.mcs.telegramBot.cryptoApi.model;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Представляет актив в портфеле.
 * Содержит информацию о количестве и последней цене актива.
 */
public class Asset {
    private String symbol;
    private BigDecimal count;
    private BigDecimal lastPrice;
    private long lastPriceTimestamp;
    
    /**
     * Создает новый актив с указанным количеством и ценой.
     * Количество округляется до 2 знаков после запятой.
     * 
     * @param symbol Символ актива (например, "BTC-USD") 
     * @param count Количество актива
     * @param lastPrice Последняя цена актива
     * @param lastPriceTimestamp Временная метка последней цены
     */
    public Asset(String symbol, BigDecimal count, BigDecimal lastPrice, long lastPriceTimestamp) {
        this.symbol = symbol;
        this.count = count.setScale(2, RoundingMode.HALF_UP);
        this.lastPrice = lastPrice;
        this.lastPriceTimestamp = lastPriceTimestamp;
    }
    
    /**
     * Возвращает символ актива.
     * 
     * @return Символ актива
     */
    public String getSymbol() {
        return symbol;
    }
    
    /**
     * Устанавливает символ актива.
     * 
     * @param symbol Новый символ актива
     */
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    
    /**
     * Устанавливает новое количество актива.
     * 
     * @param count Новое количество актива
     */
    public void setCount(BigDecimal count) {
        this.count = count.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Устанавливает новую цену и временную метку.
     * 
     * @param lastPrice Новая цена актива
     * @param lastPriceTimestamp Новая временная метка
     */
    public void setPrice(BigDecimal lastPrice, long lastPriceTimestamp) {
        this.lastPrice = lastPrice;
        this.lastPriceTimestamp = lastPriceTimestamp;
    }
    
    /**
     * Возвращает количество актива.
     * 
     * @return Количество актива
     */
    public BigDecimal getCount() {
        return count;
    }
    
    /**
     * Возвращает последнюю цену актива.
     * 
     * @return Последняя цена актива
     */
    public BigDecimal getLastPrice() {
        return lastPrice;
    }
    
    /**
     * Возвращает временную метку последней цены.
     * 
     * @return Временная метка последней цены
     */
    public long getLastPriceTimestamp() {
        return lastPriceTimestamp;
    }
    
    /**
     * Возвращает текущую стоимость актива (количество * цена).
     * 
     * @return Стоимость актива
     */
    public BigDecimal getValue() {
        return count.multiply(lastPrice).setScale(2, RoundingMode.HALF_UP);
    }
} 