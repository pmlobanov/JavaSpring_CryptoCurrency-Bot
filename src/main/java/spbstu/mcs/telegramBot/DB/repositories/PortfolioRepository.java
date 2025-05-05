package spbstu.mcs.telegramBot.DB.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;

/**
 * Репозиторий для работы с портфелями в MongoDB.
 * Обеспечивает доступ к данным портфелей и поддерживает стандартные CRUD-операции.
 *
 * <p>Основные возможности:</p>
 * <ul>
 *   <li>Стандартные операции CRUD через {@link MongoRepository}</li>
 *   <li>Поиск портфеля по названию</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * // Поиск портфеля по имени
 * Portfolio portfolio = portfolioRepository.findByPortfolioName("Мой портфель");
 *
 * // Сохранение нового портфеля
 * portfolioRepository.save(new Portfolio("Новый портфель"));
 * }</pre>
 *
 * @see MongoRepository
 * @see Portfolio
 */

public interface PortfolioRepository extends MongoRepository<Portfolio, String> {

    /**
     * Находит портфель по его названию.
     *
     * @param name название портфеля для поиска (чувствительно к регистру)
     * @return найденный портфель или {@code null}, если портфель не найден
     * @throws org.springframework.dao.DataAccessException при ошибках доступа к данным
     */
    Portfolio findByName(String name);
}