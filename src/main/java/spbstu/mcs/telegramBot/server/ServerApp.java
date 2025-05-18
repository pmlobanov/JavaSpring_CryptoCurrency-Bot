package spbstu.mcs.telegramBot.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.services.AdminService;
import spbstu.mcs.telegramBot.DB.services.ApiKeyService;
import spbstu.mcs.telegramBot.DB.services.UserService;
import spbstu.mcs.telegramBot.config.AppConfigurations;
import spbstu.mcs.telegramBot.cryptoApi.PriceFetcher;
import spbstu.mcs.telegramBot.model.Admin;
import spbstu.mcs.telegramBot.model.Currency;
import spbstu.mcs.telegramBot.security.EncryptionService;
import spbstu.mcs.telegramBot.service.AdminAuthMiddleware;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ServerApp {
    private static final Logger logger = LoggerFactory.getLogger(ServerApp.class);
    private HttpServer server;
    private final AppConfigurations.WebConfiguration.ServerProperties serverProperties;
    private final RouterFunction<ServerResponse> routes;
    private final AdminService adminService;
    private final UserService userService;
    private final AdminAuthMiddleware adminAuthMiddleware;
    private final EncryptionService encryptionService;
    //private final AdminRepository adminService;
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PriceFetcher priceFetcher;
    private final String logFilePath;
    
    // Инжектируем строковые бины из Config
    private final String kafkaBootstrapServers;
    private final String kafkaIncomingTopic;
    private final String kafkaOutgoingTopic;

    public ServerApp(AppConfigurations.WebConfiguration.ServerProperties serverProperties, 
                    RouterFunction<ServerResponse> routes,
                    AdminService adminService,
                    UserService userService,
                    AdminAuthMiddleware adminAuthMiddleware,
                    EncryptionService encryptionService,
                    //AdminRepository adminRepository,
                    ApiKeyService apiKeyService,
                    PriceFetcher priceFetcher,
                    String logFilePath,
                    String kafkaBootstrapServers,
                    String kafkaIncomingTopic,
                    String kafkaOutgoingTopic) {
        this.serverProperties = serverProperties;
        this.routes = routes;
        this.adminService = adminService;
        this.userService = userService;
        this.adminAuthMiddleware = adminAuthMiddleware;
        this.encryptionService = encryptionService;
        //this.adminRepository = adminRepository;
        this.apiKeyService = apiKeyService;
        this.priceFetcher = priceFetcher;
        this.logFilePath = logFilePath;
        this.kafkaBootstrapServers = kafkaBootstrapServers;
        this.kafkaIncomingTopic = kafkaIncomingTopic;
        this.kafkaOutgoingTopic = kafkaOutgoingTopic;
    }

    public void start() {
        try {
            // Проверяем существование файла логов
            File logFile = new File(logFilePath);
            if (!logFile.exists()) {
                logger.error("Log file does not exist at: {}", logFilePath);
                throw new IOException("Log file not found: " + logFilePath);
            }
            
            server = HttpServer.create(new InetSocketAddress(serverProperties.host(), serverProperties.port()), 0);
            
            // Health check endpoint - accessible without authentication
            server.createContext("/healthcheck", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    long startTime = System.currentTimeMillis();
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                        return;
                    }
                    Map<String, Object> healthStatus = new LinkedHashMap<>();
                    healthStatus.put("status", 200);
                    healthStatus.put("timestamp", System.currentTimeMillis());
                    
                    // Check server status
                    Map<String, Object> serverStatus = new LinkedHashMap<>();
                    serverStatus.put("status", "UP");
                    serverStatus.put("host", serverProperties.host());
                    serverStatus.put("port", serverProperties.port());
                    healthStatus.put("server", serverStatus);
                    
                    // Check MongoDB connection
                    Map<String, Object> mongoStatus = new LinkedHashMap<>();
                    try {
                        // Simple health check: try to find all admins
                        adminService.findAll();
                        mongoStatus.put("status", "UP");
                        mongoStatus.put("database", "BitBotDB");
                    } catch (Exception e) {
                        mongoStatus.put("status", "DOWN");
                        mongoStatus.put("error", e.getMessage());
                    }
                    healthStatus.put("mongodb", mongoStatus);
                    
                    // Check Kafka status
                    Map<String, Object> kafkaStatus = new LinkedHashMap<>();
                    try {
                        List<String> topics = new ArrayList<>();
                        topics.add(kafkaIncomingTopic);
                        topics.add(kafkaOutgoingTopic);
                        // Можно добавить другие топики, если есть
                        kafkaStatus.put("status", (kafkaBootstrapServers != null && !kafkaBootstrapServers.isEmpty()) ? "UP" : "DOWN");
                        kafkaStatus.put("bootstrapServers", kafkaBootstrapServers);
                        kafkaStatus.put("topics", topics);
                    } catch (Exception e) {
                        kafkaStatus.put("status", "DOWN");
                        kafkaStatus.put("error", e.getMessage());
                    }
                    healthStatus.put("kafka", kafkaStatus);
                    
                    // Check CryptoAPI status
                    Map<String, Object> cryptoApiStatus = new LinkedHashMap<>();
                    try {
                        // Проверяем доступность priceFetcher
                        String priceResult = "N/A";
                        try {
                            priceResult = priceFetcher.getCurrentPrice(Currency.Crypto.BTC).block();
                        } catch (Exception e) {
                            priceResult = "Ошибка: " + e.getMessage();
                        }
                        cryptoApiStatus.put("status", "UP");
                        cryptoApiStatus.put("priceFetcherResult", priceResult);
                    } catch (Exception e) {
                        cryptoApiStatus.put("status", "DOWN");
                        cryptoApiStatus.put("error", e.getMessage());
                    }
                    healthStatus.put("cryptoApi", cryptoApiStatus);
                    
                    long endTime = System.currentTimeMillis();
                    healthStatus.put("executionTimeMs", endTime - startTime);
                    
                    sendResponse(exchange, 200, objectMapper.writeValueAsString(healthStatus));
                }
            });
            
            // Admin refresh token endpoint
            server.createContext("/admin/refresh", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                        return;
                    }
                    
                    // Check for Authorization header
                    String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                    if (authHeader == null || authHeader.isEmpty()) {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Missing Authorization header"));
                        return;
                    }
                    
                    // Extract the token from Bearer format
                    String token = null;
                    if (authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    } else {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Invalid Authorization format"));
                        return;
                    }
                    
                    // Read request body
                    String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))
                            .lines().collect(Collectors.joining("\n"));
                    
                    try {
                        Map<String, Object> body = objectMapper.readValue(requestBody, Map.class);
                        String username = (String) body.get("username");
                        
                        if (username == null || username.trim().isEmpty()) {
                            sendResponse(exchange, 400, errorResponse(400, "Username is required"));
                            return;
                        }
                        
                        // Start the response in chunked mode for reactive handling
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, 0);
                        OutputStream outputStream = exchange.getResponseBody();
                        
                        // Process the request reactively
                        validateApiToken(token)
                            .flatMap(adminInfo -> {
                                Admin foundAdmin = adminInfo.getT1();
                                boolean keyExpired = adminInfo.getT2();
                                
                                // Проверка, что админ обновляет только свой собственный токен
                                if (!foundAdmin.getUsername().equals(username)) {
                                    return Mono.error(new SecurityException("You can only refresh your own token"));
                                }
                                
                                // If key expired, we'll refresh it
                                if (keyExpired) {
                                    return refreshExpiredKey(foundAdmin, outputStream);
                                }
                                
                                // Continue with normal refresh
                                return Mono.fromCallable(() -> adminService.findByUsername(username))
                                    .flatMap(optAdmin -> {
                                        if (!optAdmin.isPresent()) {
                                            return Mono.error(new IllegalArgumentException("Admin not found"));
                                        }
                                        
                                        Admin admin = optAdmin.get();
                        String apiKey = java.util.UUID.randomUUID().toString();
                        String encryptedKey = encryptionService.encrypt(apiKey);
                        LocalDateTime expirationDate = LocalDateTime.now().plusDays(30);
                                        
                                        // Update the admin with the new key
                        admin.setEncryptedApiKey(encryptedKey);
                        admin.setApiKeyExpiry(expirationDate);
                        adminService.save(admin);
                        
                        Map<String, Object> responseMap = new LinkedHashMap<>();
                        responseMap.put("message", "Admin token refreshed successfully");
                        responseMap.put("username", admin.getUsername());
                        responseMap.put("apiKey", apiKey);
                        responseMap.put("timestamp", System.currentTimeMillis());
                        
                                        return Mono.just(responseMap);
                                    });
                            })
                            .subscribe(responseMap -> {
                                try {
                                    String response = successResponse(responseMap);
                                    outputStream.write(response.getBytes());
                                    outputStream.flush();
                                    outputStream.close();
                                } catch (Exception e) {
                                    logger.error("Error writing response: {}", e.getMessage(), e);
                                }
                            }, error -> {
                                try {
                                    int statusCode = 500;
                                    if (error instanceof SecurityException) {
                                        statusCode = 403;
                                    } else if (error instanceof IllegalArgumentException) {
                                        statusCode = 404;
                                    }
                                    String errorResp = errorResponse(statusCode, error.getMessage());
                                    outputStream.write(errorResp.getBytes());
                                    outputStream.flush();
                                    outputStream.close();
                                    logger.error("Error in refresh token: {}", error.getMessage(), error);
                                } catch (IOException ioe) {
                                    logger.error("Failed to write error response: {}", ioe.getMessage(), ioe);
                                }
                            });
                        
                    } catch (Exception e) {
                        logger.error("Error refreshing admin token: {}", e.getMessage(), e);
                        sendResponse(exchange, 500, errorResponse(500, "Internal Server Error: " + e.getMessage()));
                    }
                }
            });
            
            // Get all users endpoint
            server.createContext("/admin/users", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    // Проверяем метод запроса
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                        return;
                    }
                    
                    // Check for Authorization header
                    String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                    if (authHeader == null || authHeader.isEmpty()) {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Missing Authorization header"));
                        return;
                    }
                    
                    // Extract the token from Bearer format
                    String token = null;
                    if (authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    } else {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Invalid Authorization format"));
                        return;
                    }
                    
                    try {
                        // Проверка валидности API ключа
                        boolean isValidApiKey = false;
                        Admin foundAdmin = null;
                        String decryptedKey = null;
                        boolean keyExpired = false;
                        
                        // Получаем всех админов через репозиторий
                        List<Admin> admins = adminService.findAll();
                        for (Admin admin : admins) {
                            // Получаем зашифрованный ключ админа
                            String encryptedKey = admin.getEncryptedApiKey();
                            if (encryptedKey == null) continue;
                            
                            // Расшифровываем ключ и сравниваем с полученным
                            decryptedKey = encryptionService.decrypt(encryptedKey);
                            if (token.equals(decryptedKey)) {
                                isValidApiKey = true;
                                foundAdmin = admin;
                                
                                // Проверяем срок действия ключа по дате
                                LocalDateTime now = LocalDateTime.now();
                                if (admin.getApiKeyExpiry() != null && 
                                    now.isAfter(admin.getApiKeyExpiry())) {
                                    keyExpired = true;
                                }
                                break;
                            }
                        }
                        
                        if (!isValidApiKey) {
                            sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Invalid API Key"));
                            return;
                        }
                        
                        // Если ключ истек, обновляем его и возвращаем соответствующее сообщение
                        if (keyExpired && foundAdmin != null) {
                            String newApiKey = java.util.UUID.randomUUID().toString();
                            String newEncryptedKey = encryptionService.encrypt(newApiKey);
                            LocalDateTime expirationDate = LocalDateTime.now().plusDays(30);
                            
                            foundAdmin.setEncryptedApiKey(newEncryptedKey);
                            foundAdmin.setApiKeyExpiry(expirationDate);
                            adminService.save(foundAdmin);
                            
                            Map<String, Object> responseMap = new LinkedHashMap<>();
                            responseMap.put("status", 401);
                            responseMap.put("warning", "Your API key has expired!");
                            responseMap.put("newKey", newApiKey);
                            responseMap.put("timestamp", System.currentTimeMillis());
                            
                            sendResponse(exchange, 401, objectMapper.writeValueAsString(responseMap));
                            return;
                        }
                        
                        // Handle users asynchronously without blocking
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        

                        exchange.sendResponseHeaders(200, 0);
                        OutputStream outputStream = exchange.getResponseBody();
                        
                        // Use collectList() to get all users, then subscribe to process them asynchronously
                        userService.getAllUsers()
                            .map(user -> Map.of(
                                "chatId", user.getChatId(),
                                "hasStarted", user.isHasStarted(),
                                "notificationIds", user.getNotificationIds(),
                                "portfolioIds", user.getPortfolioIds()
                            ))
                            .collectList()
                            .subscribe(users -> {
                                try {
                        // Создаем объект ответа с дополнительной информацией
                        Map<String, Object> responseMap = new HashMap<>();
                        responseMap.put("users", users);
                        responseMap.put("timestamp", System.currentTimeMillis());
                        responseMap.put("count", users.size());
                        // Convert to JSON and send response
                                    String response = successResponse(responseMap);
                                    outputStream.write(response.getBytes());
                                    outputStream.flush();
                                    outputStream.close();
                                } catch (Exception e) {
                                    logger.error("Error writing response: {}", e.getMessage(), e);
                                }
                            }, error -> {
                                try {
                                    String errorResp = errorResponse(500, "Error getting users: " + error.getMessage());
                                    outputStream.write(errorResp.getBytes());
                                    outputStream.flush();
                                    outputStream.close();
                                    logger.error("Error getting users: {}", error.getMessage(), error);
                                } catch (IOException ioe) {
                                    logger.error("Failed to write error response: {}", ioe.getMessage(), ioe);
                                }
                            });
                        
                        // Note: We don't close the response here since it will be closed in the subscribe() callback
                        
                    } catch (Exception e) {
                        logger.error("Error in users handler: {}", e.getMessage(), e);
                        sendResponse(exchange, 500, errorResponse(500, "Internal Server Error: " + e.getMessage()));
                    }
                }
            });
            
            // Logs endpoint
            server.createContext("/admin/logs", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    // Проверяем метод запроса
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                        return;
                    }
                    
                    // Check for Authorization header
                    String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
                    if (authHeader == null || authHeader.isEmpty()) {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Missing Authorization header"));
                        return;
                    }
                    
                    // Extract the token from Bearer format
                    String token = null;
                    if (authHeader.startsWith("Bearer ")) {
                        token = authHeader.substring(7);
                    } else {
                        sendResponse(exchange, 401, errorResponse(401, "Unauthorized - Invalid Authorization format"));
                        return;
                    }
                    
                    String finalToken = token;
                    try {
                        // Get the log file path
                        File logFile = new File(logFilePath);
                        
                        if (!logFile.exists() || !logFile.isFile()) {
                            sendResponse(exchange, 404, errorResponse(404, "Log file not found"));
                            return;
                        }
                        
                        // Process request reactively
                        validateApiToken(finalToken)
                            .flatMap(adminInfo -> {
                                Admin foundAdmin = adminInfo.getT1();
                                boolean keyExpired = adminInfo.getT2();
                                
                                // If key expired, refresh it and return a warning
                                if (keyExpired) {
                                    // Set headers for expired token error response
                                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                                    
                                    return refreshExpiredKey(foundAdmin, null)
                                        .map(responseMap -> {
                                            try {
                                                String response = objectMapper.writeValueAsString(responseMap);
                                                exchange.sendResponseHeaders(401, response.getBytes().length);
                                                OutputStream os = exchange.getResponseBody();
                                                os.write(response.getBytes());
                                                os.close();
                                            } catch (Exception e) {
                                                logger.error("Error sending expired key response: {}", e.getMessage(), e);
                                            }
                                            return false; // Signal we've handled the response
                                        });
                                }
                                
                                // Valid token, proceed with log file
                                return Mono.fromCallable(() -> {
                                    try {
                        byte[] fileBytes = Files.readAllBytes(logFile.toPath());
                        // Set headers for file download
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"application.log\"");
                        exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileBytes.length));
                        // Send the file
                        exchange.sendResponseHeaders(200, fileBytes.length);
                        OutputStream outputStream = exchange.getResponseBody();
                        outputStream.write(fileBytes);
                        outputStream.close();
                                        return true; // Signal success
                                    } catch (Exception e) {
                        // Даже при ошибке отправки файла возвращаем статус 200, но с сообщением об ошибке
                        exchange.sendResponseHeaders(200, 0);
                        OutputStream outputStream = exchange.getResponseBody();
                        outputStream.write(("Ошибка при отправке логов: " + e.getMessage()).getBytes());
                        outputStream.close();
                                        logger.error("Error sending log file: {}", e.getMessage(), e);
                        return false;
                                    }
                                });
                            })
                            .subscribe(
                                result -> logger.debug("Log file request processed: {}", result),
                                error -> {
                                    try {
                                        int statusCode = 500;
                                        if (error instanceof SecurityException) {
                                            statusCode = 401;
                                        }
                                        sendResponse(exchange, statusCode, errorResponse(statusCode, error.getMessage()));
                                    } catch (IOException e) {
                                        logger.error("Error sending error response: {}", e.getMessage(), e);
                                    }
                                }
                            );
                    } catch (Exception e) {
                        logger.error("Error getting logs: {}", e.getMessage(), e);
                        sendResponse(exchange, 500, errorResponse(500, "Internal Server Error: " + e.getMessage()));
                    }
                }
            });
            
            // HTML form for /admin/users
            server.createContext("/admin/users/form", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                    return;
                }
                String html = """
                    <html><head><title>Users Admin Form</title></head><body>
                    <h2>Получить список пользователей</h2>
                    <form method='get' action='/admin/users' onsubmit='event.preventDefault(); fetchUsers();'>
                        <label>API Token (Bearer): <input type='text' id='token' name='token' required></label><br><br>
                        <button type='submit'>Получить пользователей</button>
                    </form>
                    <pre id='result'></pre>
                    <script>
                    function fetchUsers() {
                        const token = document.getElementById('token').value;
                        fetch('/admin/users', {
                            method: 'GET',
                            headers: { 'Authorization': 'Bearer ' + token }
                        })
                        .then(r => r.text()).then(t => document.getElementById('result').textContent = t);
                    }
                    </script>
                    </body></html>
                """;
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.getBytes().length);
                exchange.getResponseBody().write(html.getBytes());
                exchange.getResponseBody().close();
            });

            // HTML form for /admin/refresh
            server.createContext("/admin/refresh/form", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                    return;
                }
                String html = """
                    <html><head><title>Refresh Admin Token</title></head><body>
                    <h2>Обновить токен администратора</h2>
                    <form id='refreshForm' onsubmit='event.preventDefault(); refreshToken();'>
                        <label>API Token (Bearer): <input type='text' id='token' name='token' required></label><br><br>
                        <label>Username: <input type='text' id='username' name='username' required></label><br><br>
                        <button type='submit'>Обновить токен</button>
                    </form>
                    <pre id='result'></pre>
                    <script>
                    function refreshToken() {
                        const token = document.getElementById('token').value;
                        const username = document.getElementById('username').value;
                        fetch('/admin/refresh', {
                            method: 'POST',
                            headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' },
                            body: JSON.stringify({ username })
                        })
                        .then(r => r.text()).then(t => document.getElementById('result').textContent = t);
                    }
                    </script>
                    </body></html>
                """;
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.getBytes().length);
                exchange.getResponseBody().write(html.getBytes());
                exchange.getResponseBody().close();
            });

            // HTML form for /admin/logs
            server.createContext("/admin/logs/form", exchange -> {
                if (!"GET".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, errorResponse(405, "Method Not Allowed"));
                    return;
                }
                String html = """
                    <html><head><title>Logs Admin Form</title></head><body>
                    <h2>Скачать логи</h2>
                    <form id='logsForm' onsubmit='event.preventDefault(); downloadLogs();'>
                        <label>API Token (Bearer): <input type='text' id='token' name='token' required></label><br><br>
                        <button type='submit'>Скачать логи</button>
                    </form>
                    <pre id='result'></pre>
                    <script>
                    function downloadLogs() {
                        const token = document.getElementById('token').value;
                        fetch('/admin/logs', {
                            method: 'GET',
                            headers: { 'Authorization': 'Bearer ' + token }
                        })
                        .then(response => {
                            if (response.ok) {
                                return response.blob();
                            } else {
                                return response.text().then(text => { throw new Error(text); });
                            }
                        })
                        .then(blob => {
                            const url = window.URL.createObjectURL(blob);
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = 'application.log';
                            document.body.appendChild(a);
                            a.click();
                            a.remove();
                        })
                        .catch(e => document.getElementById('result').textContent = e);
                    }
                    </script>
                    </body></html>
                """;
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, html.getBytes().length);
                exchange.getResponseBody().write(html.getBytes());
                exchange.getResponseBody().close();
            });
            
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();
            
            logger.info("API Server started on {}:{}", serverProperties.host(), serverProperties.port());
            System.out.println("API Server started on " + serverProperties.host() + ":" + serverProperties.port());
        } catch (IOException e) {
            logger.error("Failed to start API server: {}", e.getMessage(), e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            logger.info("API Server stopped");
        }
    }
    
    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        // Если ответ не содержит статус, добавим его
        if (!body.contains("\"status\"")) {
            // Преобразуем старый ответ в Map или используем новый Map
            Map<String, Object> responseMap;
            if (body.startsWith("{") && body.endsWith("}")) {
                try {
                    responseMap = objectMapper.readValue(body, Map.class);
                } catch (Exception e) {
                    // Если не можем распарсить как Map, создаем новый с оригинальным сообщением
                    responseMap = new HashMap<>();
                    responseMap.put("message", body);
                }
            } else {
                responseMap = new HashMap<>();
                responseMap.put("message", body);
            }
            
            // Добавляем status код
            responseMap.put("status", statusCode);
            
            // Преобразуем обратно в JSON
            body = objectMapper.writeValueAsString(responseMap);
        }
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(body.getBytes());
        os.close();
    }
    
    // Обновленные методы для формирования стандартизированных ответов
    private String errorResponse(int statusCode, String errorMessage) {
        // Используем LinkedHashMap чтобы сохранить порядок полей
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", statusCode);
        response.put("error", errorMessage);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            return "{\"status\":" + statusCode + ",\"error\":\"" + errorMessage + "\"}";
        }
    }
    
    private String successResponse(Map<String, Object> data) {
        // Используем LinkedHashMap чтобы сохранить порядок полей
        Map<String, Object> orderedData = new LinkedHashMap<>();
        orderedData.put("status", 200);
        
        // Добавляем все остальные данные
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!entry.getKey().equals("status")) {
                orderedData.put(entry.getKey(), entry.getValue());
            }
        }
        
        try {
            return objectMapper.writeValueAsString(orderedData);
        } catch (Exception e) {
            return "{\"status\":200,\"message\":\"Success\"}";
        }
    }

    // Helper method to validate admin API token
    public Mono<reactor.util.function.Tuple2<Admin, Boolean>> validateApiToken(String token) {
        return Mono.fromCallable(() -> {
            boolean isValidApiKey = false;
            Admin foundAdmin = null;
            String decryptedKey = null;
            boolean keyExpired = false;
            
            // Получаем всех админов через сервис
            List<Admin> admins = adminService.findAll();
            for (Admin admin : admins) {
                // Получаем зашифрованный ключ админа
                String encryptedKey = admin.getEncryptedApiKey();
                if (encryptedKey == null) continue;
                
                // Расшифровываем ключ и сравниваем с полученным
                decryptedKey = encryptionService.decrypt(encryptedKey);
                if (token.equals(decryptedKey)) {
                    isValidApiKey = true;
                    foundAdmin = admin;
                    
                    // Проверяем срок действия ключа по дате
                    LocalDateTime now = LocalDateTime.now();
                    if (admin.getApiKeyExpiry() != null && 
                        now.isAfter(admin.getApiKeyExpiry())) {
                        keyExpired = true;
                    }
                    break;
                }
            }
            
            if (!isValidApiKey || foundAdmin == null) {
                throw new SecurityException("Unauthorized - Invalid API Key");
            }
            
            return reactor.util.function.Tuples.of(foundAdmin, keyExpired);
        });
    }
    
    // Helper method to refresh an expired key
    private Mono<Map<String, Object>> refreshExpiredKey(Admin admin, OutputStream outputStream) {
        String newApiKey = java.util.UUID.randomUUID().toString();
        String newEncryptedKey = encryptionService.encrypt(newApiKey);
        LocalDateTime expirationDate = LocalDateTime.now().plusDays(30);
        
        // Update admin with new key
        admin.setEncryptedApiKey(newEncryptedKey);
        admin.setApiKeyExpiry(expirationDate);
        adminService.save(admin);
        
        Map<String, Object> responseMap = new LinkedHashMap<>();
        responseMap.put("status", 401);
        responseMap.put("warning", "Your API key has expired!");
        responseMap.put("newKey", newApiKey);
        responseMap.put("timestamp", System.currentTimeMillis());
        
        return Mono.just(responseMap);
    }
}