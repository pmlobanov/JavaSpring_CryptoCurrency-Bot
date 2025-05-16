package spbstu.mcs.telegramBot.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import spbstu.mcs.telegramBot.DB.services.AdminService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.DB.collections.Admin;
import spbstu.mcs.telegramBot.DB.collections.User;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class AdminAuthMiddleware {
    private final AdminService adminService;
    private final UserService userService;
    private static final Set<String> PUBLIC_COMMANDS = new HashSet<>(Arrays.asList("/start", "/help"));

    @Autowired
    public AdminAuthMiddleware(AdminService adminService, @Lazy UserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    public Mono<Boolean> isPublicCommand(String command) {
        return Mono.just(PUBLIC_COMMANDS.contains(command.toLowerCase()));
    }

    /**
     * Проверяет авторизацию пользователя
     * @param command команда
     * @param chatId ID чата пользователя
     * @param authHeader заголовок авторизации (не используется)
     * @return Mono<Boolean> true если команда публичная или пользователь авторизован
     */
    public Mono<Boolean> checkUserAuthorization(String command, String chatId, String authHeader) {
        return isPublicCommand(command)
            .flatMap(isPublic -> {
                if (isPublic) {
                    return Mono.just(true);
                }

                // Для обычных команд проверяем наличие пользователя в БД
                return userService.getUserByChatId(chatId)
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