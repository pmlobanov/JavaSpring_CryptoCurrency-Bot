package spbstu.mcs.telegramBot.security;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class EncryptionService {
    private static final String ALGORITHM = "AES";
    private final SecretKeySpec keySpec;

    public EncryptionService() {
        // Используем фиксированный ключ для шифрования
        String secretKey = "BitXBotSecretKey2024";
        this.keySpec = createKeySpec(secretKey);
    }
    
    /**
     * Создает ключ AES правильного размера из предоставленного секретного ключа
     * @param key Исходный ключ
     * @return SecretKeySpec с правильной длиной для AES
     */
    private SecretKeySpec createKeySpec(String key) {
        try {
            // Use SHA-256 to get a 32-byte key regardless of input length
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] digestedKey = sha.digest(keyBytes);
            // This will always be 32 bytes (256 bits), perfect for AES-256
            return new SecretKeySpec(digestedKey, ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error creating encryption key: {}", e.getMessage());
            throw new RuntimeException("Failed to create encryption key", e);
        }
    }

    public String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedBytes = cipher.doFinal(value.getBytes());
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            log.error("Error encrypting value: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt value", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decryptedBytes);
        } catch (Exception e) {
            log.error("Error decrypting value: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt value", e);
        }
    }
} 