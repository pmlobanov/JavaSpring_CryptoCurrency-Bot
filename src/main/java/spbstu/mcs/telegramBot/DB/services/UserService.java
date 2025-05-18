package spbstu.mcs.telegramBot.DB.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.DTO.UserPortfolioView;
import spbstu.mcs.telegramBot.model.Notification;
import spbstu.mcs.telegramBot.model.Portfolio;
import spbstu.mcs.telegramBot.model.User;
import spbstu.mcs.telegramBot.util.ChatIdMasker;

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
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private final MongoTemplate mongoTemplate;
    private final MongoCollection<Document> userCollection;
    private PortfolioService portfolioService;
    private NotificationService notificationService;

    @Autowired
    public UserService(MongoTemplate mongoTemplate, 
                      MongoCollection<Document> userCollection) {
        this.mongoTemplate = mongoTemplate;
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
     * Создает нового пользователя с указанными параметрами.
     *
     * @param userTgName имя пользователя в Telegram
     * @param chatId ID чата пользователя в Telegram
     * @return созданный пользователь
     */
    public Mono<User> createUser(String userTgName, String chatId) {
        String maskedChatId = maskChatId(chatId);
        log.info("Creating new user with userTgName: {} and chatId: {}", userTgName, maskedChatId);
        
        return Mono.fromCallable(() -> {
            User user = new User(userTgName, chatId);
            Document doc = userToDocument(user);
            userCollection.insertOne(doc);
            log.info("Successfully created user with chatId: {}", maskedChatId);
            return user;
        }).onErrorResume(e -> {
            log.error("Error creating user with chatId {}: {}", maskedChatId, e.getMessage());
            return Mono.error(new RuntimeException("Failed to create user", e));
        });
    }

    /**
     * Добавляет портфель к списку пользователя.
     *
     * @param chatId идентификатор чата пользователя
     * @param portfolioId идентификатор портфеля для добавления
     * @param userTgName имя пользователя в Telegram (может быть null)
     * @return Mono<Void> индикатор завершения операции
     */
    public Mono<Void> addPortfolioToUser(String chatId, String portfolioId, String userTgName) {
        return Mono.fromRunnable(() -> {
            try {
                List<org.bson.conversions.Bson> updates = new ArrayList<>();
                updates.add(Updates.addToSet("portfolioIds", portfolioId));
                if (userTgName != null) {
                    updates.add(Updates.set("userTgName", userTgName));
                }
                userCollection.updateOne(
                    Filters.eq("chatId", chatId),
                    Updates.combine(updates)
                );
            } catch (Exception e) {
                log.error("Error adding portfolio to user: {}", e.getMessage());
                throw new NoSuchElementException("User not found or update failed");
            }
        });
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
     * Находит пользователя по имени в Telegram.
     *
     * @param userTgName имя пользователя в Telegram
     * @return найденный пользователь или ошибка, если не найден
     */
    public Mono<User> getUserByTgName(String userTgName) {
        return Mono.fromCallable(() -> userCollection.find(Filters.eq("userTgName", userTgName)).first())
            .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
            .map(this::documentToUser);
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
     * Находит пользователя по ID чата в Telegram.
     *
     * @param chatId ID чата пользователя в Telegram
     * @return Mono<User> найденный пользователь или пустой Mono, если не найден
     */
    public Mono<User> getUserByChatId(String chatId) {
        String maskedChatId = ChatIdMasker.maskChatId(chatId);
        log.info("Searching for user with chatId: {}", maskedChatId);
        
        if (chatId == null || chatId.trim().isEmpty()) {
            log.warn("Invalid chatId provided");
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            Document doc = userCollection.find(Filters.eq("chatId", chatId.trim())).first();
            if (doc == null) {
                log.warn("User not found with chatId: {}", maskedChatId);
                return null;
            }
            User user = documentToUser(doc);
            log.info("Found user with chatId: {}", maskedChatId);
            return user;
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
        doc.append("_id", user.getChatId());
        doc.append("userTgName", user.getUserTgName())
           .append("chatId", user.getChatId())
           .append("hasStarted", user.isHasStarted())
           .append("portfolioIds", user.getPortfolioIds() != null ? user.getPortfolioIds() : new ArrayList<>())
           .append("notificationIds", user.getNotificationIds() != null ? user.getNotificationIds() : new ArrayList<>());
        return doc;
    }

    private User documentToUser(Document doc) {
        if (doc == null) {
            return null;
        }
        User user = new User();
        user.setUserTgName(doc.getString("userTgName"));
        user.setChatId(doc.getString("chatId"));
        if (doc.containsKey("hasStarted")) {
            Boolean hasStarted = doc.getBoolean("hasStarted");
            if (hasStarted != null && hasStarted) {
                user.setHasStarted(true);
            }
        }
        if (doc.containsKey("portfolioIds")) {
            List<String> portfolioIds = doc.getList("portfolioIds", String.class);
            if (portfolioIds != null) {
                for (String id : portfolioIds) {
                    user.addPortfolioId(id);
                }
            }
        }
        if (doc.containsKey("notificationIds")) {
            List<String> notificationIds = doc.getList("notificationIds", String.class);
            if (notificationIds != null) {
                for (String id : notificationIds) {
                    user.addNotificationId(id);
                }
            }
        }
        return user;
    }
}