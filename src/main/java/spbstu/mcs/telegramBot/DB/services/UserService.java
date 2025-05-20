package spbstu.mcs.telegramBot.DB.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.DTO.UserPortfolioView;
import spbstu.mcs.telegramBot.model.Notification;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.model.User;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для управления пользователями и их данными.
 * Обеспечивает создание пользователей, управление их портфелями и уведомлениями,
 * а также обновление пользовательских настроек.
 *
 * <p>Основные функции:</p>
 * <ul>
 *   <li>Создание и управление учетными записями пользователей</li>
 *   <li>Работа с портфелями и уведомлениями пользователей</li>
 *   <li>Обновление валютных предпочтений</li>
 *   <li>Получение агрегированных данных пользователя</li>
 * </ul>
 */
@Service
@Slf4j
public class UserService {
    private final MongoCollection<Document> userCollection;
    private PortfolioService portfolioService;
    private NotificationService notificationService;
    private static final Set<String> PUBLIC_COMMANDS = new HashSet<>(Arrays.asList("/start", "/help"));

    @Autowired
    public UserService(MongoCollection<Document> userCollection) {
        this.userCollection = userCollection;
        log.info("UserService initialized with MongoDB connection");
    }
    
    @Autowired
    public void setPortfolioService(@Lazy PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }
    
    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private String maskChatId(String chatId) {
        if (chatId == null || chatId.length() < 4) {
            return "****";
        }
        return chatId.substring(0, 2) + "****" + chatId.substring(chatId.length() - 2);
    }

    /**
     * Создает нового пользователя
     * @param chatId идентификатор чата пользователя
     * @return Mono с созданным пользователем
     */
    public Mono<User> createUser(String chatId) {
        String maskedChatId = maskChatId(chatId);
        log.info("Creating new user with chatId: {}", maskedChatId);
        
        return Mono.fromCallable(() -> {
            User user = new User(chatId);
            Document doc = userToDocument(user);
            return userCollection.insertOne(doc);
        })
        .map(result -> {
            User user = new User(chatId);
            user.setId(result.getInsertedId().asObjectId().getValue().toString());
            return user;
        })
        .doOnError(error -> log.error("Error creating user: {}", error.getMessage()));
    }

    /**
     * Добавляет портфель пользователю
     * @param chatId идентификатор чата пользователя
     * @param portfolioId идентификатор портфеля
     * @return Mono<Void>
     */
    public Mono<Void> addPortfolioToUser(String chatId, String portfolioId) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.addToSet("portfolioIds", portfolioId));
        
        return Mono.fromRunnable(() -> {
                userCollection.updateOne(
                    Filters.eq("chatId", chatId),
                    Updates.combine(updates)
                );
        })
        .then()
        .doOnError(error -> log.error("Error adding portfolio to user: {}", error.getMessage()));
    }

    /**
     * Добавляет уведомление к списку пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @param notificationId идентификатор уведомления для добавления
     * @return Mono<Void> индикатор завершения операции
     */
    public Mono<Void> addNotificationToUser(String chatId, String notificationId) {
        String maskedChatId = maskChatId(chatId);
        return Mono.fromCallable(() -> userCollection.find(Filters.eq("chatId", chatId)).first())
            .switchIfEmpty(Mono.error(new NoSuchElementException("User not found")))
            .flatMap(user -> {
                final List<String> notificationIdList = new ArrayList<>(
                    user.getList("notificationIds", String.class) != null ?
                    user.getList("notificationIds", String.class) : 
                    Collections.emptyList()
                );
                notificationIdList.add(notificationId);
                
                return Mono.fromRunnable(() -> {
                    userCollection.updateOne(
                        Filters.eq("chatId", chatId),
                        Updates.set("notificationIds", notificationIdList)
                    );
                    log.info("Added notification to user with chatId: {}", maskedChatId);
                });
            })
            .onErrorResume(e -> {
                log.error("Error adding notification to user with chatId {}: {}", maskedChatId, e.getMessage());
                return Mono.error(new RuntimeException("Failed to add notification", e));
            })
            .then();
    }

    /**
     * Возвращает агрегированные данные пользователя (портфели и уведомления).
     *
     * @param userId идентификатор пользователя
     * @return DTO с данными пользователя
     * @throws NoSuchElementException если пользователь не найден
     */
    public Mono<UserPortfolioView> getUserPortfolioData(String userId) {
        return Mono.justOrEmpty(userCollection.find(Filters.eq("_id", userId)).first())
            .switchIfEmpty(Mono.error(new NoSuchElementException("User not found")))
            .flatMap(userDoc -> {
                List<String> portfolioIds = userDoc.getList("portfolioIds", String.class);
                List<Portfolio> portfolios = portfolioIds == null ? Collections.emptyList() :
                    portfolioIds.stream()
                        .map(id -> portfolioService.getPortfolio(id))
                        .collect(Collectors.toList());

                List<String> notificationIds = userDoc.getList("notificationIds", String.class);

                return Flux.fromIterable(notificationIds != null ? notificationIds : Collections.emptyList())
                    .flatMap(notificationService::getNotification)
                    .collectList()
                    .map(notifications -> new UserPortfolioView(userId, notifications, portfolios));
            });
    }

    /**
     * Получает пользователя по идентификатору чата
     * @param chatId идентификатор чата пользователя
     * @return Mono с найденным пользователем
     */
    public Mono<User> getUserByChatId(String chatId) {
        return Mono.fromCallable(() -> userCollection.find(Filters.eq("chatId", chatId)).first())
            .map(this::documentToUser)
            .doOnError(error -> log.error("Error getting user by chatId: {}", error.getMessage()));
    }

    /**
     * Возвращает активные уведомления пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Flux активных уведомлений
     */
    public Flux<Notification> getActiveUserNotifications(String chatId) {
        Document doc = userCollection.find(Filters.eq("chatId", chatId)).first();
        if (doc == null) {
            throw new NoSuchElementException("User not found");
        }
        
        List<String> notificationIds = doc.getList("notificationIds", String.class);
        if (notificationIds == null || notificationIds.isEmpty()) {
            return Flux.empty();
        }
        
        return Flux.fromIterable(notificationIds)
            .flatMap(notificationService::getNotification)
            .filter(Objects::nonNull)
            .filter(Notification::isActive);
    }

    /**
     * Возвращает все уведомления пользователя.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return flux с уведомлениями пользователя
     */
    public Flux<Notification> getUserNotifications(String chatId) {
        return Mono.fromCallable(() -> userCollection.find(Filters.eq("chatId", chatId)).first())
            .switchIfEmpty(Mono.error(new NoSuchElementException("User not found")))
            .flatMapMany(doc -> {
                List<String> notificationIds = doc.getList("notificationIds", String.class);
                if (notificationIds == null || notificationIds.isEmpty()) {
                    return Flux.empty();
                }
                return Flux.fromIterable(notificationIds)
                    .flatMap(notificationId -> notificationService.getNotification(notificationId));
            });
    }

    /**
     * Получает всех пользователей.
     *
     * @return список всех пользователей
     */
    public Flux<User> getAllUsers() {
        return Flux.defer(() -> Flux.fromIterable(() -> userCollection.find().iterator()))
            .map(this::documentToUser);
    }

    /**
     * Удаляет пользователя по ID чата.
     *
     * @param chatId идентификатор чата пользователя для удаления
     * @return Mono<Void> индикатор завершения операции
     */
    public Mono<Void> deleteUser(String chatId) {
        String maskedChatId = maskChatId(chatId);
        return Mono.fromRunnable(() -> {
            try {
                userCollection.deleteOne(Filters.eq("chatId", chatId));
                log.info("Deleted user with chatId: {}", maskedChatId);
            } catch (Exception e) {
                log.error("Error deleting user with chatId {}: {}", maskedChatId, e.getMessage());
                throw new RuntimeException("Failed to delete user", e);
            }
        });
    }

    /**
     * Сохраняет пользователя в базу данных.
     *
     * @param user пользователь для сохранения
     * @return Mono<User> сохраненный пользователь
     */
    public Mono<User> save(User user) {
        String maskedChatId = maskChatId(user.getChatId());
        log.info("Saving user with chatId: {}", maskedChatId);
        return Mono.fromCallable(() -> {
            Document doc = userToDocument(user);
            try {
                userCollection.replaceOne(Filters.eq("_id", user.getChatId()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
                log.info("Successfully saved user with chatId: {}, hasStarted: {}", maskedChatId, user.isHasStarted());
                return user;
            } catch (Exception e) {
                log.error("Error saving user with chatId {}: {}", maskedChatId, e.getMessage(), e);
                throw e;
            }
        })
        .doOnError(error -> log.error("Error saving user with chatId {}: {}", maskedChatId, error.getMessage()));
    }

    /**
     * Закрывает соединение с MongoDB (если необходимо).
     * Это метод теперь не требуется, так как MongoDB клиент управляется Spring.
     */
    public void close() {
        log.info("UserService closing - MongoDB connection is managed by Spring");
    }

    private Document userToDocument(User user) {
        Document doc = new Document();
        if (user.getId() != null) {
            doc.append("_id", user.getId());
        }
        doc.append("chatId", user.getChatId())
           .append("hasStarted", user.isHasStarted())
           .append("portfolioIds", user.getPortfolioIds())
           .append("notificationIds", user.getNotificationIds())
           .append("currentCrypto", user.getCurrentCrypto())
           .append("currentFiat", user.getCurrentFiat());
        return doc;
    }

    private User documentToUser(Document doc) {
        User user = new User();
        if (doc.get("_id") != null) {
            user.setId(doc.get("_id").toString());
        }
        user.setChatId(doc.getString("chatId"));
        user.setHasStarted(doc.getBoolean("hasStarted", false));
        user.setPortfolioIds(doc.getList("portfolioIds", String.class));
        user.setNotificationIds(doc.getList("notificationIds", String.class));
        user.setCurrentCrypto(doc.getString("currentCrypto"));
        user.setCurrentFiat(doc.getString("currentFiat"));
        return user;
    }

    /**
     * Проверяет, является ли команда публичной
     * @param command команда для проверки
     * @return true если команда публичная
     */
    public Mono<Boolean> isPublicCommand(String command) {
        return Mono.just(PUBLIC_COMMANDS.contains(command.toLowerCase()));
                }

    /**
     * Проверяет авторизацию пользователя
     * @param command команда
     * @param chatId ID чата пользователя
     * @return Mono<Boolean> true если команда публичная или пользователь авторизован
     */
    public Mono<Boolean> checkUserAuthorization(String command, String chatId) {
        return isPublicCommand(command)
            .flatMap(isPublic -> {
                if (isPublic) {
                    return Mono.just(true);
                }

                // Для обычных команд проверяем наличие пользователя в БД
                return getUserByChatId(chatId)
                    .map(user -> user.isHasStarted())
                    .defaultIfEmpty(false);
            })
            .doOnError(error -> log.error("Error checking authorization: {}", error.getMessage()));
                }

    /**
     * Получает сообщение об ошибке авторизации
     * @param command команда, для которой проверялась авторизация
     * @return сообщение об ошибке
     */
    public String getAuthorizationErrorMessage(String command) {
        return "❌ Пожалуйста, начните работу с ботом командой /start";
    }
}