package spbstu.mcs.telegramBot.DB.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.security.KeyPairGenerator;

@Component
@Slf4j
public class PrivateKeyProvider {

    private PrivateKey privateKey;

    public PrivateKeyProvider(@Value("${private.key:}") String privateKeyBase64) throws Exception {
        // First try to get the key from system properties
        String key = System.getProperty("private.key");
        if (key == null || key.isEmpty()) {
            // If not in system properties, try application properties
            if (privateKeyBase64 == null || privateKeyBase64.isEmpty()) {
                log.warn("No private key found in system properties or application properties");
                // Initialize with a temporary key that will be replaced later
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);
                this.privateKey = keyGen.generateKeyPair().getPrivate();
                return;
            }
            key = privateKeyBase64;
        }
        
        try {
            byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(keySpec);
        } catch (Exception e) {
            log.error("Error loading private key: {}", e.getMessage());
            throw e;
        }
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
