package ru.spbstu.telematics.java.DB.encryption;

import java.security.*;
import javax.crypto.Cipher;
import java.util.Base64;

public class RsaCryptor {
    private static final String ALGORITHM = "RSA";

    // Шифрование публичным ключом
    public static String encrypt(PublicKey publicKey, String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    // Дешифрование приватным ключом
    public static String decrypt(PrivateKey privateKey, String encryptedText) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, "UTF-8");
    }
}
