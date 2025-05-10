package spbstu.mcs.telegramBot.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import spbstu.mcs.telegramBot.model.Currency;

import java.math.BigDecimal;

/**
 * Класс, представляющий портфель криптовалют.
 * Хранится в коллекции MongoDB "portfolios".
 *
 * <p>Каждый портфель содержит:</p>
 * <ul>
 *   <li>Название портфеля</li>
 *   <li>Фиатную валюту портфеля</li>
 *   <li>Список криптовалютных активов с их количествами</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * Portfolio portfolio = new Portfolio("Мой портфель", Currency.Fiat.USD);
 * portfolio.addCrypto(Currency.Crypto.BTC, new BigDecimal("1.5"));
 * }</pre>
 *
 * @see Document
 * @see Currency.Crypto
 * @see Currency.Fiat
 */
@Document(collection = "portfolios")
public class Portfolio {
    @Id
    private String id;
    private String name;
    private Currency.Fiat fiatCurrency;
    private Currency.Crypto cryptoCurrency;
    private BigDecimal count;
    private Long createdAt;
    private String chatId;
    
    // Новые поля для отслеживания цен
    private BigDecimal lastPortfolioPrice;
    private Long lastPortfolioPriceTimestamp;
    private BigDecimal lastCryptoPrice;
    private Long lastCryptoPriceTimestamp;

    /**
     * No-args constructor required by Spring Data MongoDB
     */
    public Portfolio() {
        this.createdAt = System.currentTimeMillis() / 1000;
    }

    /**
     * Создает новый портфель с указанной фиатной валютой.
     *
     * @param fiatCurrency фиатная валюта портфеля
     * @param chatId идентификатор чата пользователя
     */
    public Portfolio(Currency.Fiat fiatCurrency, String chatId) {
        this.fiatCurrency = fiatCurrency;
        this.chatId = chatId;
        this.createdAt = System.currentTimeMillis() / 1000;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Currency.Fiat getFiatCurrency() {
        return fiatCurrency;
    }

    public void setFiatCurrency(Currency.Fiat fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    public Currency.Crypto getCryptoCurrency() {
        return cryptoCurrency;
    }

    public void setCryptoCurrency(Currency.Crypto cryptoCurrency) {
        this.cryptoCurrency = cryptoCurrency;
    }

    public BigDecimal getCount() {
        return count;
    }

    public void setCount(BigDecimal count) {
        this.count = count;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getLastPortfolioPrice() {
        return lastPortfolioPrice;
    }

    public void setLastPortfolioPrice(BigDecimal lastPortfolioPrice) {
        this.lastPortfolioPrice = lastPortfolioPrice;
    }

    public Long getLastPortfolioPriceTimestamp() {
        return lastPortfolioPriceTimestamp;
    }

    public void setLastPortfolioPriceTimestamp(Long lastPortfolioPriceTimestamp) {
        this.lastPortfolioPriceTimestamp = lastPortfolioPriceTimestamp;
    }

    public BigDecimal getLastCryptoPrice() {
        return lastCryptoPrice;
    }

    public void setLastCryptoPrice(BigDecimal lastCryptoPrice) {
        this.lastCryptoPrice = lastCryptoPrice;
    }

    public Long getLastCryptoPriceTimestamp() {
        return lastCryptoPriceTimestamp;
    }

    public void setLastCryptoPriceTimestamp(Long lastCryptoPriceTimestamp) {
        this.lastCryptoPriceTimestamp = lastCryptoPriceTimestamp;
    }

    public void updateLastCryptoPrice(BigDecimal price, Long timestamp) {
        this.lastCryptoPrice = price;
        this.lastCryptoPriceTimestamp = timestamp;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    @Override
    public String toString() {
        return "Portfolio{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", fiatCurrency=" + fiatCurrency +
                ", cryptoCurrency=" + cryptoCurrency +
                ", count=" + count +
                ", createdAt=" + createdAt +
                ", lastPortfolioPrice=" + lastPortfolioPrice +
                ", lastPortfolioPriceTimestamp=" + lastPortfolioPriceTimestamp +
                ", lastCryptoPrice=" + lastCryptoPrice +
                ", lastCryptoPriceTimestamp=" + lastCryptoPriceTimestamp +
                '}';
    }
}
