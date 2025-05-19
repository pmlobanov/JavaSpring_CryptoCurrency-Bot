package spbstu.mcs.telegramBot.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "admins")
public class Admin {
    @Id
    private String id;
    private String username;
    private String encryptedApiKey;
    private LocalDateTime apiKeyExpiry;
    private LocalDateTime createdAt;

    public Admin() {
        this.createdAt = LocalDateTime.now();
    }

    public Admin(String username, String encryptedApiKey) {
        this();
        this.username = username;
        this.encryptedApiKey = encryptedApiKey;
        this.apiKeyExpiry = LocalDateTime.now().plusDays(30);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }

    public LocalDateTime getApiKeyExpiry() {
        return apiKeyExpiry;
    }

    public void setApiKeyExpiry(LocalDateTime apiKeyExpiry) {
        this.apiKeyExpiry = apiKeyExpiry;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
} 