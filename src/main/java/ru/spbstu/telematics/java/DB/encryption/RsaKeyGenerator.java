package ru.spbstu.telematics.java.DB.encryption;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class RsaKeyGenerator {
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); // 2048 бит - стандарт безопасности
        return generator.generateKeyPair();
    }
}
