package spbstu.mcs.telegramBot.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.model.Currency;

import java.util.List;

/**
 * Репозиторий для работы с портфелями в MongoDB.
 * Обеспечивает доступ к данным портфелей и поддерживает стандартные CRUD-операции.
 *
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Стандартные операции CRUD через {@link MongoRepository}</li>
 *   <li>Поиск портфеля по названию</li>
 *   <li>Поиск портфелей по фиатной валюте</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * // Поиск портфеля по имени
 * Portfolio portfolio = portfolioRepository.findByName("Мой портфель");
 *
 * // Поиск портфелей в USD
 * List<Portfolio> usdPortfolios = portfolioRepository.findByFiatCurrency(Currency.Fiat.USD);
 *
 * // Сохранение нового портфеля
 * portfolioRepository.save(new Portfolio("Новый портфель", Currency.Fiat.USD));
 * }</pre>
 *
 * @see MongoRepository
 * @see Portfolio
 * @see Currency.Fiat
 */
@Repository
public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    /**
     * Находит портфель по его названию.
     *
     * @param name название портфеля для поиска (чувствительно к регистру)
     * @return найденный портфель или {@code null}, если портфель не найден
     * @throws org.springframework.dao.DataAccessException при ошибках доступа к данным
     */
    Portfolio findByName(String name);

    /**
     * Находит все портфели пользователя по идентификатору чата.
     *
     * @param chatId идентификатор чата пользователя
     * @return список портфелей пользователя
     * @throws org.springframework.dao.DataAccessException при ошибках доступа к данным
     */
    List<Portfolio> findByChatId(String chatId);
}