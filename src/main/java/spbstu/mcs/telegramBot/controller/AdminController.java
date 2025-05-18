package spbstu.mcs.telegramBot.controller;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import spbstu.mcs.telegramBot.DB.services.AdminService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.DB.services.ApiKeyService;
import spbstu.mcs.telegramBot.model.Admin;
import spbstu.mcs.telegramBot.model.User;
import spbstu.mcs.telegramBot.service.AdminAuthMiddleware;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminController {
    private final AdminService adminService;
    private final UserService userService;
    private final AdminAuthMiddleware adminAuthMiddleware;
    private final ApiKeyService apiKeyService;

    public AdminController(AdminService adminService, 
                          UserService userService,
                          AdminAuthMiddleware adminAuthMiddleware,
                          ApiKeyService apiKeyService) {
        this.adminService = adminService;
        this.userService = userService;
        this.adminAuthMiddleware = adminAuthMiddleware;
        this.apiKeyService = apiKeyService;
    }

    public Mono<ServerResponse> getUsers(ServerRequest request) {
        List<String> authHeader = request.headers().header("Authorization");
        if (authHeader.isEmpty()) {
            return ServerResponse.status(401).bodyValue(Map.of("error", "Unauthorized"));
        }
        
        return adminAuthMiddleware.checkUserAuthorization("/users", "admin", authHeader.get(0))
            .flatMap(isAuthorized -> {
                if (!isAuthorized) {
                    return ServerResponse.status(401).bodyValue(Map.of("error", "Unauthorized"));
                }
                return userService.getAllUsers()
                    .map(user -> Map.of(
                        "chatId", user.getChatId(),
                        "hasStarted", user.isHasStarted(),
                        "notificationIds", user.getNotificationIds(),
                        "portfolioIds", user.getPortfolioIds()
                    ))
                    .collectList()
                    .flatMap(users -> ServerResponse.ok().bodyValue(users));
            });
    }
} 