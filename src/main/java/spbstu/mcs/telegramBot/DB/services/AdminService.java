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
            // Don't filter by isActive, check all admins and validate by expiration date
            List<Admin> admins = adminRepository.findAll();
                
            log.info("Validating API key: {}", apiKey);
            log.info("Found {} admins to check against", admins.size());
            
            // For direct database query approach
            String encryptedInputKey = encryptionService.encrypt(apiKey);
            log.info("Encrypted input key for comparison: {}", encryptedInputKey.substring(0, 10) + "...");
            
            // Try direct database query first (faster)
            Optional<Admin> adminByEncryptedKey = admins.stream()
                .filter(a -> a.getEncryptedApiKey().equals(encryptedInputKey))
                .findFirst();
                
            if (adminByEncryptedKey.isPresent()) {
                Admin admin = adminByEncryptedKey.get();
                log.info("Found admin by direct encrypted key comparison: {}", admin.getUsername());
                
                // Check if key is expired
                LocalDateTime now = LocalDateTime.now();
                if (admin.getApiKeyExpiry() != null && now.isAfter(admin.getApiKeyExpiry())) {
                    log.warn("API key expired for admin: {}", admin.getUsername());
                    // Временно сохраняем открытый ключ для возврата
                    admin.setEncryptedApiKey(apiKey);
                    return admin;
                }
                
                return admin;
            }
                
            // If direct comparison fails, try decryption approach
            for (Admin admin : admins) {
                try {
                    String storedEncryptedKey = admin.getEncryptedApiKey();
                    log.info("Admin: {}, Stored encrypted key: {}", admin.getUsername(), 
                        storedEncryptedKey.substring(0, Math.min(10, storedEncryptedKey.length())) + "...");
                    
                    // Try to decrypt the stored key and compare with input
                    String decryptedStoredKey = encryptionService.decrypt(storedEncryptedKey);
                    log.info("Decrypted stored key for admin {}: {}", admin.getUsername(), 
                        decryptedStoredKey);
                    
                    // Compare the input key with the decrypted stored key
                    if (apiKey.equals(decryptedStoredKey)) {
                        log.info("API key match found for admin: {}", admin.getUsername());
                        
                        // Check if key is expired by date
                        LocalDateTime now = LocalDateTime.now();
                        if (admin.getApiKeyExpiry() != null && now.isAfter(admin.getApiKeyExpiry())) {
                            log.warn("API key expired for admin: {}", admin.getUsername());
                            // Временно сохраняем открытый ключ для возврата
                            admin.setEncryptedApiKey(apiKey);
                            return admin;
                        }
                        
                        return admin;
                    }
                } catch (Exception e) {
                    log.error("Error processing API key for admin {}: {}", admin.getUsername(), e.getMessage());
                }
            }
            log.warn("No matching API key found among {} admins", admins.size());
            return null;
        })
        .filter(admin -> admin != null)
        .doOnError(error -> log.error("Error validating API key: {}", error.getMessage()));
    }

    public Mono<Admin> refreshApiKey(String username) {
        return Mono.fromCallable(() -> {
            Optional<Admin> optAdmin = adminRepository.findByUsername(username);
            if (optAdmin.isEmpty()) {
                throw new IllegalArgumentException("Администратор не найден");
            }
            
            Admin admin = optAdmin.get();
            
            String newApiKey = UUID.randomUUID().toString();
            String encryptedApiKey = encryptionService.encrypt(newApiKey);
            
            admin.setEncryptedApiKey(encryptedApiKey);
            admin.setApiKeyExpiry(LocalDateTime.now().plusDays(30));
            admin.setUpdatedAt(LocalDateTime.now());
            
            admin = adminRepository.save(admin);
            
            log.info("Refreshed API key for admin: {}", username);
            
            // Временно сохраняем открытый ключ для возврата
            admin.setEncryptedApiKey(newApiKey);
            return admin;
        })
        .doOnError(error -> log.error("Error refreshing API key: {}", error.getMessage()));
    }

    public Mono<Boolean> deactivateAdmin(String username) {
        return Mono.fromCallable(() -> {
            Optional<Admin> optAdmin = adminRepository.findByUsername(username);
            if (optAdmin.isEmpty()) {
                return false;
            }
            log.info("Deactivated admin: {} (no isActive field, only date-based expiration)", username);
            return true;
        })
        .doOnError(error -> log.error("Error deactivating admin: {}", error.getMessage()));
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