package spbstu.mcs.telegramBot.DB.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import spbstu.mcs.telegramBot.DB.repositories.AdminRepository;
import spbstu.mcs.telegramBot.model.Admin;
import spbstu.mcs.telegramBot.security.EncryptionService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final MongoTemplate mongoTemplate;
    private final EncryptionService encryptionService;
    private final AdminRepository adminRepository;

    @Autowired
    public AdminService(EncryptionService encryptionService, AdminRepository adminRepository, MongoTemplate mongoTemplate) {
        this.encryptionService = encryptionService;
        this.adminRepository = adminRepository;
        this.mongoTemplate = mongoTemplate;
        log.info("AdminService initialized with MongoDB connection");
    }

    private String maskChatId(String chatId) {
        if (chatId == null || chatId.length() < 4) {
            return "****";
        }
        return chatId.substring(0, 2) + "****" + chatId.substring(chatId.length() - 2);
    }

    public Mono<Admin> createAdmin(String username) {
        return Mono.fromCallable(() -> {
            if (adminRepository.findByUsername(username).isPresent()) {
                throw new IllegalArgumentException("Администратор с таким именем уже существует");
            }
            
            String apiKey = UUID.randomUUID().toString();
            String encryptedApiKey = encryptionService.encrypt(apiKey);
            
            Admin admin = new Admin(username, encryptedApiKey);
            admin = adminRepository.save(admin);
            
            log.info("Created new admin: {}", username);
            
            // Временно сохраняем открытый ключ для возврата, но не сохраняем в БД
            admin.setEncryptedApiKey(apiKey); 
            return admin;
        })
        .doOnError(error -> log.error("Error creating admin: {}", error.getMessage()));
    }

    public Mono<Admin> validateApiKey(String apiKey) {
        return Mono.fromCallable(() -> {
            List<Admin> admins = adminRepository.findAll();
            log.info("Validating API key: {}", apiKey);
            log.info("Found {} admins to check against", admins.size());
            
            String encryptedInputKey = encryptionService.encrypt(apiKey);
            log.info("Encrypted input key for comparison: {}", encryptedInputKey.substring(0, 10) + "...");
            
            Optional<Admin> adminByEncryptedKey = admins.stream()
                .filter(a -> a.getEncryptedApiKey().equals(encryptedInputKey))
                .findFirst();
                
            if (adminByEncryptedKey.isPresent()) {
                Admin admin = adminByEncryptedKey.get();
                log.info("Found admin by encrypted key comparison: {}", admin.getUsername());
                
                // Check if key is expired
                LocalDateTime now = LocalDateTime.now();
                if (admin.getApiKeyExpiry() != null && now.isAfter(admin.getApiKeyExpiry())) {
                    log.warn("API key expired for admin: {}", admin.getUsername());
                    return null;
                }
                
                return admin;
            }
                
            log.warn("No matching API key found among {} admins", admins.size());
            return null;
        })
        .filter(admin -> admin != null)
        .doOnError(error -> log.error("Error validating API key: {}", error.getMessage()));
    }

    /**
     * Обновляет API ключ администратора с указанной датой истечения срока действия
     * @param username имя пользователя администратора
     * @param expiryDate дата истечения срока действия
     * @return Mono с обновленным администратором
     */
    public Mono<Admin> updateApiKey(String username, LocalDateTime expiryDate) {
        return Mono.fromCallable(() -> {
            Optional<Admin> optAdmin = adminRepository.findByUsername(username);
            if (optAdmin.isEmpty()) {
                throw new IllegalArgumentException("Администратор не найден");
            }
            
            Admin admin = optAdmin.get();
            
            String newApiKey = UUID.randomUUID().toString();
            String encryptedApiKey = encryptionService.encrypt(newApiKey);
            
            admin.setEncryptedApiKey(encryptedApiKey);
            admin.setApiKeyExpiry(expiryDate);
            
            admin = adminRepository.save(admin);
            
            log.info("Updated API key for admin: {} with expiry date: {}", username, expiryDate);
            
            // Временно сохраняем открытый ключ для возврата
            admin.setEncryptedApiKey(newApiKey);
            return admin;
        })
        .doOnError(error -> log.error("Error updating API key: {}", error.getMessage()));
    }

    public Mono<Admin> refreshApiKey(String username) {
        return updateApiKey(username, LocalDateTime.now().plusDays(30));
    }

    /**
     * Поиск администратора по имени пользователя
     * @param username имя пользователя администратора
     * @return Optional, содержащий администратора, если найден
     */
    public Optional<Admin> findByUsername(String username) {
        return adminRepository.findByUsername(username);
    }
    
    /**
     * Получить всех администраторов
     * @return список всех администраторов
     */
    public List<Admin> findAll() {
        return adminRepository.findAll();
    }

    public void save(Admin newAdmin){
        this.adminRepository.save(newAdmin);
    }
} 