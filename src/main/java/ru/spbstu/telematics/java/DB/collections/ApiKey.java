package ru.spbstu.telematics.java.DB.collections;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import ru.spbstu.telematics.java.DB.encryption.RsaCryptor;

import java.security.PrivateKey;
import java.security.PublicKey;

@Document(collection = "apiKeys")
public class ApiKey {
    @Id
    private String id = "0"; // так как только один документ может быть

    // Храним зашифрованный ключ
    private String encryptedApiKey;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKeyBase64) {
        this.publicKey = publicKey;
    }

    private String  publicKey;

    public String getId() {
        return id;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }
}
