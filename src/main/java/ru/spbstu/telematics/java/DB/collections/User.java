package ru.spbstu.telematics.java.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.spbstu.telematics.java.DB.currencies.CryptoCurrency;
import ru.spbstu.telematics.java.DB.currencies.FiatCurrency;
import ru.spbstu.telematics.java.DB.exceptions.NoSuchRightsExeption;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Класс, представляющий пользователя системы.
 * Хранится в коллекции MongoDB "users".
 *
 * <p>Основные характеристики пользователя:</p>
 * <ul>
 *   <li>Имя в Telegram</li>
 *   <li>Предпочитаемая фиатная валюта</li>
 *   <li>Валюта по умолчанию для криптоопераций</li>
 *   <li>Списки идентификаторов портфелей и уведомлений</li>
 * </ul>
 *
 * <p>Пример использования:</p>
 * <pre>{@code
 * User user = new User("john_doe", FiatCurrency.USD, CryptoCurrency.BTC);
 * user.addPortfolioId("portfolio123");
 * }</pre>
 *
 * @see Document
 * @see FiatCurrency
 * @see CryptoCurrency
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    private String userTgName;
    private FiatCurrency fiatCurrency;
    private CryptoCurrency defaultCryptoCurrrency;

    public Boolean getAdmin() {
        return isAdmin;
    }

    public void setAdmin(Boolean admin) {
        isAdmin = admin;
        //с большой силой приходит большая ответсвенность
    }

    private Boolean isAdmin;

    public PublicKey getPublicKey() {
        if(!this.isAdmin) throw new NoSuchRightsExeption("User doesn't have Admin rights");
        return publicKey;
    }

    private PublicKey publicKey;

    public void setNotificationIds(List<String> notificationIds) {
        this.notificationIds = notificationIds;
    }

    public void setPortfolioIds(List<String> portfolioIds) {
        this.portfolioIds = portfolioIds;
    }

    // Ссылки на портфели (храним только ID)
    private List<String> portfolioIds;
    private List<String> notificationIds;

    /**
     * Конструктор для создания нового пользователя.
     * Аннотация @PersistenceConstructor указывает Spring Data MongoDB на использование этого конструктора при загрузке из БД.
     *
     * @param userTgName имя пользователя в Telegram
     * @param fiatCurrency  предпочитаемая фиатная валюта
     * @param defaultCryptoCurrrency  криптовалюта по умолчанию
     */
    @PersistenceConstructor
    public User(String userTgName, FiatCurrency fiatCurrency , CryptoCurrency defaultCryptoCurrrency ) {
        this.userTgName = userTgName;
        this.defaultCryptoCurrrency = defaultCryptoCurrrency ;
        this.fiatCurrency = fiatCurrency ;
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

    // Сеттеры

    /**
     * Устанавливает имя пользователя в Telegram.
     * @param userTgName новое имя пользователя
     */
    public void setUserTgName(String userTgName) {
        this.userTgName = userTgName;
    }

    /**
     * Устанавливает предпочитаемую фиатную валюту.
     * @param fiatCurrency новая фиатная валюта
     */
    public void setFiatCurrency(FiatCurrency fiatCurrency) {
        this.fiatCurrency = fiatCurrency;
    }

    /**
     * Устанавливает криптовалюту по умолчанию.
     * @param defaultCryptoCurrrency новая криптовалюта по умолчанию
     */
    public void setDefaultCryptoCurrrency(CryptoCurrency defaultCryptoCurrrency) {
        this.defaultCryptoCurrrency = defaultCryptoCurrrency;
    }

    // Геттеры

    /**
     * Возвращает предпочитаемую фиатную валюту пользователя.
     * @return фиатная валюта
     */
    public FiatCurrency getFiatCurrency() {
        return fiatCurrency;
    }

    /**
     * Возвращает имя пользователя в Telegram.
     * @return имя пользователя
     */
    public String getUserTgName() {
        return userTgName;
    }

    /**
     * Возвращает криптовалюту по умолчанию.
     * @return криптовалюта по умолчанию
     */
    public CryptoCurrency getDefaultCryptoCurrrency() {
        return defaultCryptoCurrrency;
    }

    /**
     * Возвращает строковое представление пользователя.
     * @return строковое описание со всеми полями
     */

    @Override
    public String toString() {
        return "User{" +
                "isAdmin=" + isAdmin +
                ", defaultCryptoCurrrency=" + defaultCryptoCurrrency +
                ", fiatCurrency=" + fiatCurrency +
                ", userTgName='" + userTgName + '\'' +
                ", id='" + id + '\'' +
                '}';
    }


}