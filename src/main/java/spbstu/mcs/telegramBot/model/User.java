package spbstu.mcs.telegramBot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import spbstu.mcs.telegramBot.model.Currency;
import java.beans.ConstructorProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Класс, представляющий пользователя системы.
 * Хранится в коллекции MongoDB "users".
 *
 * <p>Основные характеристики пользователя:</p>
 * <ul>
 *   <li>Имя в Telegram</li>
 *   <li>ID чата в Telegram</li>
 *   <li>Списки идентификаторов портфелей и уведомлений</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * User user = new User("john_doe", Currency.Fiat.USD, Currency.Crypto.BTC);
 * user.addPortfolioId("portfolio123");
 * }</pre>
 *
 * @see Document
 * @see Currency.Fiat
 * @see Currency.Crypto
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Field("chatId")
    private String chatId;

    @Field("hasStarted")
    private boolean hasStarted;

    @Field("portfolioIds")
    private List<String> portfolioIds;

    @Field("notificationIds")
    private List<String> notificationIds;

    /**
     * Конструктор без параметров для Spring Data MongoDB
     */
    public User() {
        this.portfolioIds = new ArrayList<>();
        this.notificationIds = new ArrayList<>();
        this.hasStarted = false;
    }

    /**
     * Создает нового пользователя
     * @param chatId идентификатор чата пользователя
     */
    public User(String chatId) {
        this();
        this.chatId = chatId;
    }

    /**
     * Создает нового пользователя с указанными портфелями и уведомлениями
     * @param chatId идентификатор чата пользователя
     * @param portfolioIds список идентификаторов портфелей
     * @param notificationIds список идентификаторов уведомлений
     */
    public User(String chatId, List<String> portfolioIds, List<String> notificationIds) {
        this.chatId = chatId;
        this.portfolioIds = portfolioIds != null ? portfolioIds : new ArrayList<>();
        this.notificationIds = notificationIds != null ? notificationIds : new ArrayList<>();
        this.hasStarted = false;
    }

    /**
     * Возвращает список идентификаторов портфелей пользователя.
     * @return список ID портфелей (может быть null)
     */
    public List<String> getPortfolioIds() {
        return portfolioIds;
    }

    /**
     * Возвращает список идентификаторов уведомлений пользователя.
     * @return список ID уведомлений (может быть null)
     */
    public List<String> getNotificationIds() {
        return notificationIds;
    }

    /**
     * Возвращает уникальный идентификатор пользователя.
     * @return строковый ID
     */
    public String getId() {
        return id;
    }

    /**
     * Устанавливает уникальный идентификатор пользователя.
     * @param id новый ID пользователя
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Добавляет идентификатор портфеля в список пользователя.
     * @param portfolioId ID портфеля для добавления
     */
    public void addPortfolioId(String portfolioId) {
        if (portfolioIds == null) portfolioIds = new ArrayList<>();
        portfolioIds.add(portfolioId);
    }

    /**
     * Добавляет идентификатор уведомления в список пользователя.
     * @param notificationId ID уведомления для добавления
     */
    public void addNotificationId(String notificationId) {
        if (notificationIds == null) notificationIds = new ArrayList<>();
        notificationIds.add(notificationId);
    }

    /**
     * Удаляет идентификатор портфеля из списка пользователя.
     * @param portfolioId ID портфеля для удаления
     */
    public void removePortfolioId(String portfolioId) {
        if (portfolioIds != null) portfolioIds.remove(portfolioId);
    }

    /**
     * Удаляет идентификатор уведомления из списка пользователя.
     * @param notificationId ID уведомления для удаления
     */
    public void removeNotificationId(String notificationId) {
        if (notificationIds != null) notificationIds.remove(notificationId);
    }

    /**
     * Устанавливает список идентификаторов уведомлений пользователя.
     * @param notificationIds новый список ID уведомлений
     */
    public void setNotificationIds(List<String> notificationIds) {
        this.notificationIds = notificationIds;
    }

    /**
     * Устанавливает список идентификаторов портфелей пользователя.
     * @param portfolioIds новый список ID портфелей
     */
    public void setPortfolioIds(List<String> portfolioIds) {
        this.portfolioIds = portfolioIds;
    }

    // Сеттеры

    /**
     * Устанавливает ID чата пользователя в Telegram.
     * @param chatId новое ID чата пользователя
     */
    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    /**
     * Возвращает ID чата пользователя в Telegram.
     * @return ID чата пользователя
     */
    public String getChatId() {
        return chatId;
    }

    /**
     * Возвращает строковое представление пользователя.
     * @return строковое описание со всеми полями
     */
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", chatId='" + chatId + '\'' +
                ", hasStarted=" + hasStarted +
                ", portfolioIds=" + portfolioIds +
                ", notificationIds=" + notificationIds +
                '}';
    }

    public boolean isHasStarted() {
        return hasStarted;
    }

    public void setHasStarted(boolean hasStarted) {
        this.hasStarted = hasStarted;
    }
}