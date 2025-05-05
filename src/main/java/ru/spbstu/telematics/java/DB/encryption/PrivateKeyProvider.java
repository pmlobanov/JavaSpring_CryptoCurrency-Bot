package ru.spbstu.telematics.java.DB.encryption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Component
public class PrivateKeyProvider {

    private PrivateKey privateKey;

    public PrivateKeyProvider(@Value("${private.key}") String privateKeyBase64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = keyFactory.generatePrivate(keySpec);
    }

    public PrivateKey getPrivateKey() {
        return privateKey;
    }
}
