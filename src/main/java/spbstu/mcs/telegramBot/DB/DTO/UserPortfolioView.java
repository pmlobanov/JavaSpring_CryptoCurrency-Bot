package spbstu.mcs.telegramBot.DB.DTO;

import spbstu.mcs.telegramBot.DB.collections.Notification;
import spbstu.mcs.telegramBot.DB.collections.Portfolio;
import spbstu.mcs.telegramBot.DB.repositories.UserRepository;

import java.util.List;

/**
 * Data Transfer Object (DTO) для представления данных пользователя вместе с его портфелями и уведомлениями.
 * Использует Java record для неизменяемости и сокращения boilerplate-кода.
 *
 * <p>Содержит:</p>
 * <ul>
 *   <li>Идентификатор пользователя</li>
 *   <li>Список уведомлений пользователя</li>
 *   <li>Список портфелей пользователя</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * UserPortfolioView view = new UserPortfolioView(
 *     "user123",
 *     notificationService.getUserNotifications(userId),
 *     portfolioService.getUserPortfolios(userId)
 * );
 * }</pre>
 *
 * <p>Основное назначение:</p>
 * <ul>
 *   <li>Агрегация данных для API ответов</li>
 *   <li>Передача данных между слоями приложения</li>
 *   <li>Сокращение количества обращений к БД при комплексных запросах</li>
 * </ul>
 *
 * @param chatId уникальный идентификатор пользователя
 * @param notifications список уведомлений пользователя (может быть пустым, но не null)
 * @param portfolios список портфелей пользователя (может быть пустым, но не null)
 *
 * @see Notification
 * @see Portfolio
 * @see UserRepository
 */
public record UserPortfolioView(
        String chatId,
        List<Notification> notifications,
        List<Portfolio> portfolios
) {
}