package spbstu.mcs.telegramBot.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Класс, представляющий портфель криптовалют.
 * Хранится в коллекции MongoDB "portfolios".
 *
 * <p>Каждый портфель содержит:</p>
 * <ul>
 *   <li>Название портфеля</li>
 *   <li>Список криптовалютных активов ({@link CryptoHolding})</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * Portfolio portfolio = new Portfolio("Мой портфель");
 * portfolio.setListOfCurrencies(List.of(
 *     new CryptoHolding(trackedCrypto, 1.5, 50000.0)
 * ));
 * }</pre>
 *
 * @see Document
 * @see DBRef
 */
@Document(collection = "portfolios")
public class Portfolio {

    @Id
    private String id;
    private String name;
    private List<CryptoHolding> listOfCurrencies;

    /**
     * Возвращает уникальный идентификатор портфеля.
     * @return строковый идентификатор
     */
    public String getId() {
        return id;
    }

    /**
     * Возвращает название портфеля.
     * @return название портфеля
     */
    public String getName() {
        return name;
    }

    /**
     * Устанавливает название портфеля.
     * @param name новое название портфеля
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Возвращает список криптовалютных активов.
     * @return список объектов {@link CryptoHolding}
     */
    public List<CryptoHolding> getListOfCurrencies() {
        return listOfCurrencies;
    }

    /**
     * Устанавливает список криптовалютных активов.
     * @param listOfCurrencies новый список активов
     */
    public void setListOfCurrencies(List<CryptoHolding> listOfCurrencies) {
        this.listOfCurrencies = listOfCurrencies;
    }

    /**
     * Возвращает строковое представление портфеля.
     * @return строковое описание объекта
     */
    @Override
    public String toString() {
        return "Portfolio{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", listOfCurrencies=" + listOfCurrencies +
                '}';
    }

    /**
     * Вложенный класс для хранения информации о криптовалютном активе.
     * Использует ссылку ({@link DBRef}) на отслеживаемую криптовалюту.
     */
    public static class CryptoHolding {
        @DBRef // Ссылка на TrackedCryptoCurrency
        private TrackedCryptoCurrency trackedCrypto;
        private Double amount;        // Количество валюты// Цена на момент добавления
        private Instant addedAt;     // Время добавления
        /**
         * Возвращает количество криптовалюты.
         * @return количество валюты
         */
        public Double getAmount() {
            return amount;
        }

        /**
         * Устанавливает количество криптовалюты.
         * @param amount новое количество
         */
        public void setAmount(Double amount) {
            this.amount = amount;
        }

        /**
         * Возвращает ссылку на отслеживаемую криптовалюту.
         * @return объект {@link TrackedCryptoCurrency}
         */
        public TrackedCryptoCurrency getTrackedCrypto() {
            return trackedCrypto;
        }

        /**
         * Устанавливает ссылку на отслеживаемую криптовалюту.
         * @param trackedCrypto новый объект криптовалюты
         */
        public void setTrackedCrypto(TrackedCryptoCurrency trackedCrypto) {
            this.trackedCrypto = trackedCrypto;
        }

        /**
         * Возвращает дату добавления актива.
         * @return временная метка добавления
         */
        public Instant getAddedAt() {
            return addedAt;
        }

        /**
         * Устанавливает дату добавления актива.
         * @param addedAt новая временная метка
         */
        public void setAddedAt(Instant addedAt) {
            this.addedAt = addedAt;
        }

        /**
         * Создает новый криптовалютный актив.
         * @param trackedCrypto отслеживаемая криптовалюта
         * @param amount количество валюты
         */

        public CryptoHolding(TrackedCryptoCurrency trackedCrypto, Double amount) {
            this.trackedCrypto = trackedCrypto;
            this.amount = amount;
            this.addedAt = Instant.now();
        }
    }

    /**
     * Создает новый портфель с указанным именем.
     * @param name название портфеля
     */
    public Portfolio (String name){
        this.name = name;
    }





}
