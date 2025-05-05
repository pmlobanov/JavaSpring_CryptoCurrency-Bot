package spbstu.mcs.telegramBot.DB.services;

import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.DB.collections.TrackedCryptoCurrency;
import spbstu.mcs.telegramBot.DB.currencies.CryptoCurrency;
import spbstu.mcs.telegramBot.DB.repositories.PortfolioRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сервис для управления портфелями криптовалют.
 * Обеспечивает создание, модификацию и расчет стоимости портфелей.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание и удаление портфелей</li>
 *   <li>Добавление/удаление криптовалютных активов</li>
 *   <li>Расчет текущей стоимости портфеля</li>
 *   <li>Обновление количества криптовалют в портфеле</li>
 * </ul>
 */
@Service
public class PortfolioService {
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private TrackedCryptoService cryptoService;
    @Autowired
    private UserService userService;
    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Создает новый портфель и связывает его с пользователем.
     *
     * @param name название портфеля
     * @param userId идентификатор пользователя
     * @return созданный портфель
     * @throws org.springframework.dao.DataAccessException при ошибках сохранения
     */
    //TODO возможно лучше смотреть по UserTgName
    public Portfolio createPortfolio(String name, String userId) {
        Portfolio portfolio = new Portfolio(name);
        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        // Обновляем список портфелей у пользователя
        userService.addPortfolioToUser(userId, savedPortfolio.getId());
        return savedPortfolio;
    }

    /**
     * Добавляет криптовалюту в портфель.
     *
     * @param portfolioId идентификатор портфеля
     * @param cryptoId идентификатор криптовалюты
     * @param amount количество для добавления
     * @return обновленный портфель
     * @throws NoSuchElementException если портфель или криптовалюта не найдены
     * @throws IllegalArgumentException если валюта уже существует в портфеле
     */
    public Portfolio addCurrencyToPortfolio(String portfolioId, String cryptoId, Double amount) {
        // Проверка входных параметров
        if (portfolioId == null || portfolioId.isEmpty()) {
            throw new IllegalArgumentException("Portfolio ID cannot be null or empty");
        }
        if (cryptoId == null || cryptoId.isEmpty()) {
            throw new IllegalArgumentException("Crypto ID cannot be null or empty");
        }
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        // Получаем криптовалюту и портфель
        TrackedCryptoCurrency crypto = cryptoService.getCurrencyById(cryptoId);
        Portfolio portfolio = getPortfolio(portfolioId);

        // Инициализируем список, если он null
        if (portfolio.getListOfCurrencies() == null) {
            portfolio.setListOfCurrencies(new ArrayList<>());
        }

        // Проверка на существование валюты в портфеле с null-безопасностью
        boolean currencyExists = portfolio.getListOfCurrencies().stream()
                .filter(Objects::nonNull) // Фильтруем null-элементы
                .anyMatch(h -> h.getTrackedCrypto() != null
                        && cryptoId.equals(h.getTrackedCrypto().getId()));

        if (currencyExists) {
            throw new IllegalArgumentException("Currency already exists in portfolio");
        }

        // Создаем новую запись
        Portfolio.CryptoHolding holding = new Portfolio.CryptoHolding(
                crypto,
                amount
        );

        try {
            // Атомарное обновление в MongoDB
            Query query = Query.query(Criteria.where("_id").is(portfolioId));
            Update update = new Update().push("listOfCurrencies", holding);
            UpdateResult result = mongoTemplate.updateFirst(query, update, Portfolio.class);

            if (result.getModifiedCount() == 0) {
                throw new IllegalStateException("Failed to update portfolio");
            }

            return getPortfolio(portfolioId);
        } catch (Exception e) {
            throw new RuntimeException("Error adding currency to portfolio", e);
        }
    }

    /**
     * Рассчитывает текущую стоимость портфеля.
     *
     * @param portfolioId идентификатор портфеля
     * @return общая стоимость портфеля в BigDecimal
     * @throws NoSuchElementException если портфель не найден
     * @throws ClassNotFoundException если возникла проблема с расчетом
     */
    public BigDecimal calculatePortfolioValue(String portfolioId) throws ClassNotFoundException {
        Portfolio portfolio = getPortfolio(portfolioId);
        return portfolio.getListOfCurrencies().stream()
                .map(holding ->  BigDecimal.valueOf(holding.getAmount()).multiply(BigDecimal.valueOf(
                            holding.getTrackedCrypto().getLastSeenValue()
                    )))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Возвращает портфель по идентификатору.
     *
     * @param portfolioId идентификатор портфеля
     * @return найденный портфель
     * @throws NoSuchElementException если портфель не найден
     */
    public Portfolio getPortfolio(String portfolioId){
        return portfolioRepository.findById(portfolioId).orElseThrow(
                () -> new NoSuchElementException("Portfolio not found with id: " + portfolioId));
    }

    /**
     * Обновляет количество указанной криптовалюты в портфеле
     *
     * @param portfolioId ID портфеля
     * @param currencyToUpdate Валюта для обновления
     * @param newAmount Новое количество (должно быть положительным)
     * @throws NoSuchElementException если портфель не найден
     * @throws IllegalArgumentException если newAmount <= 0 или валюта не найдена
     */
    public void updateCurrencyAmount(String portfolioId,
                                     CryptoCurrency currencyToUpdate,
                                     BigDecimal newAmount) {
        // 1. Валидация входных параметров
        if (newAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        Query query = Query.query(
                Criteria.where("_id").is(portfolioId)
                        .and("listOfCurrencies.trackedCrypto.cryptoCurrency").is(currencyToUpdate)
        );

        Update update = new Update()
                .set("listOfCurrencies.$.amount", newAmount.doubleValue());

        UpdateResult result = mongoTemplate.updateFirst(query, update, Portfolio.class);

        if (result.getModifiedCount() == 0) {
            throw new IllegalArgumentException("Currency " + currencyToUpdate + " not found in portfolio");
        }
    }

    /**
     * Удаляет криптовалюту из портфеля.
     *
     * @param portfolioId ID портфеля
     * @param currencyToDelete валюта для удаления
     * @throws NoSuchElementException если портфель не найден
     * @throws IllegalArgumentException если валюта не найдена в портфеле
     */
    public void deleteHoldingFromPortfolio(String portfolioId, CryptoCurrency currencyToDelete) {
        // 1. Создаем запрос для поиска портфеля и удаления элемента из массива
        Query query = Query.query(
                Criteria.where("_id").is(portfolioId)
                        .and("listOfCurrencies.trackedCrypto.cryptoCurrency").is(currencyToDelete)
        );

        // 2. Обновление с использованием оператора $pull
        Update update = new Update().pull("listOfCurrencies",
                new BasicQuery(
                        Criteria.where("trackedCrypto.cryptoCurrency").is(currencyToDelete).getCriteriaObject()
                )
        );

        // 3. Выполняем операцию и проверяем результат
        UpdateResult result = mongoTemplate.updateFirst(query, update, Portfolio.class);

        // 4. Проверяем, был ли фактически удален элемент
        if (result.getModifiedCount() == 0) {
            throw new IllegalArgumentException(
                    "Currency " + currencyToDelete + " not found in portfolio " + portfolioId);
        }

        //TODO логирование
    }

    /**
     * Находит портфель по названию.
     *
     * @param portfolioName название портфеля
     * @return найденный портфель
     * @throws NoSuchElementException если портфель не найден
     */
    public Portfolio getPortfolioId(String portfolioName){
        return portfolioRepository.findByName(portfolioName);
    }


    /**
     * Удаляет портфель.
     *
     * @param portfolioId ID портфеля
     * @throws NoSuchElementException если портфель не найден
     */
    public void deletePortfolio(String portfolioId) {
        if (!portfolioRepository.existsById(portfolioId)) {
            throw new NoSuchElementException("Portfolio not found with id: " + portfolioId);
        }
        portfolioRepository.deleteById(portfolioId);
    }
}