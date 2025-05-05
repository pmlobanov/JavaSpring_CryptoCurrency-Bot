package ru.spbstu.telematics.java.DB.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.spbstu.telematics.java.DB.collections.ApiKey;
import ru.spbstu.telematics.java.DB.encryption.KeyLoader;
import ru.spbstu.telematics.java.DB.encryption.PrivateKeyProvider;
import ru.spbstu.telematics.java.DB.encryption.RsaCryptor;
import ru.spbstu.telematics.java.DB.repositories.ApiKeyRepository;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.NoSuchElementException;

@Service
public class ApiKeyService {
    @Autowired
    private PrivateKeyProvider privateKeyProvider;
    @Autowired
    private ApiKeyRepository apiKeyRepository;

    public void setApiKey(String plainApiKey) throws Exception {
        boolean exists = apiKeyRepository.existsById("0");
        // Генерируем пару ключей RSA
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        // Кодируем ключи в Base64
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

        // Шифруем API-ключ публичным ключом
        PublicKey publicKey = KeyLoader.loadPublicKey(publicKeyBase64);
        String encryptedApiKey = RsaCryptor.encrypt(publicKey, plainApiKey);
        ApiKey apiKey;
        if (exists) {
             apiKey = apiKeyRepository.findById("0").orElseThrow(() ->
                    new NoSuchElementException("No ApiKey in DB"));
        }
        else{
            // Сохраняем публичный ключ и зашифрованный API-ключ в базе
            apiKey = new ApiKey();
        }
        apiKey.setEncryptedApiKey(encryptedApiKey);
        apiKey.setPublicKey(publicKeyBase64);
        apiKeyRepository.save(apiKey);

        // Сохраняем приватный ключ в переменную окружения (пример для текущего процесса)
        // В реальном приложении приватный ключ нужно сохранять в безопасном месте вне кода,
        // например, в системных переменных окружения или vault.
        setPrivateKeyInEnv(privateKeyBase64);
    }

    public String getApikey() throws Exception {
        ApiKey apiKey = apiKeyRepository.findById("0")
                .orElseThrow(() -> new NoSuchElementException("No ApiKey in DB"));

        String encryptedApiKey = apiKey.getEncryptedApiKey();
        if (encryptedApiKey == null || encryptedApiKey.isEmpty()) {
            throw new IllegalStateException("Encrypted API key is empty");
        }

        PrivateKey privateKey = getPrivateKeyFromEnv();
        return RsaCryptor.decrypt(privateKey, encryptedApiKey);
    }

    private void setPrivateKeyInEnv(String privateKeyBase64) {
        // Пример: добавить в системные свойства (только для текущего процесса)
        // Для реального окружения приватные ключи задаются вне приложения
        System.setProperty("private.key", privateKeyBase64);
    }

    // Метод для получения приватного ключа из переменных окружения
    public PrivateKey getPrivateKeyFromEnv() throws Exception {
        String privateKeyBase64 = System.getProperty("private.key");
        if (privateKeyBase64 == null) {
            throw new IllegalStateException("Private key not found in environment");
        }
        return KeyLoader.loadPrivateKey(privateKeyBase64);
    }
}

