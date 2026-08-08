package com.ottking.devcode.security;

import android.util.Base64;
import com.ottking.devcode.network.Config;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public class SecurityUtils {

    public static String getApiUrl() { return Config.getApiUrl(); }
    public static String getApiKey() { return Config.getApiKey(); }
    public static String getHmacKey() { return Config.getHmacKey(); }
    public static String getEncryptionKey() { return Config.getEncryptionKey(); }

    public static final String DEBUG_API_URL = Config.getApiUrl();
    public static final String DEBUG_API_KEY = Config.getApiKey();
    public static final String DEBUG_HMAC_KEY = Config.getHmacKey();
    public static final String DEBUG_ENCRYPTION_KEY = Config.getEncryptionKey();

    private static SecretKeySpec getSecretKeySpec(String key) {
        byte[] keyBytes = new byte[32];
        byte[] src = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String generateHmac(String payload, String secretKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static boolean verifySignature(String payload, String signature, String secretKey) {
        if (payload == null || signature == null) return false;
        String computed = generateHmac(payload, secretKey);
        return computed.equalsIgnoreCase(signature);
    }

    /**
     * Encrypts plainText using AES-256-GCM.
     * Returns base64(iv) + "." + base64(ciphertext + 16-byte tag)
     */
    public static String encryptAesGcm(String plainText, String key) {
        try {
            byte[] iv = new byte[12]; // 12-byte IV for GCM
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKeySpec(key), gcmSpec);

            byte[] encryptedCombined = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP);
            String base64Combined = Base64.encodeToString(encryptedCombined, Base64.NO_WRAP);

            return base64Iv + "." + base64Combined;
        } catch (Exception e) {
            e.printStackTrace();
            return plainText;
        }
    }

    /**
     * Decrypts payload in format base64(iv) + "." + base64(ciphertext + tag) using AES-256-GCM.
     */
    public static String decryptAesGcm(String encryptedPayload, String key) {
        try {
            if (encryptedPayload == null || !encryptedPayload.contains(".")) {
                return encryptedPayload;
            }
            String[] parts = encryptedPayload.split("\\.", 2);
            if (parts.length != 2) {
                return encryptedPayload;
            }

            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] combined = Base64.decode(parts[1], Base64.NO_WRAP);

            if (iv == null || combined == null || combined.length <= 16) {
                return null;
            }

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKeySpec(key), gcmSpec);

            byte[] plainTextBytes = cipher.doFinal(combined);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String encryptAes(String plainText, String key) {
        return encryptAesGcm(plainText, key);
    }

    public static String decryptAes(String base64Text, String key) {
        return decryptAesGcm(base64Text, key);
    }
}
