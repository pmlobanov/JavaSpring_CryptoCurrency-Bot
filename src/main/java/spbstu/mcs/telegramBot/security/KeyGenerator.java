package spbstu.mcs.telegramBot.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class KeyGenerator {
    public static void main(String[] args) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            
            String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
            String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
            
            System.out.println("Private Key (add to application.properties):");
            System.out.println("private.key=" + privateKey);
            System.out.println("\nPublic Key (for reference):");
            System.out.println(publicKey);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
} 