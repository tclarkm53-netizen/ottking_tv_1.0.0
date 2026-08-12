package com.ottking.mobile.devcode.utils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    public static String generateHmacSha256(String data, String key) {
        try {
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            byte[] hash = sha256HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static boolean verifyHmacSha256(String data, String key, String expectedSignature) {
        if (expectedSignature == null || expectedSignature.isEmpty()) return false;
        String generated = generateHmacSha256(data, key);
        return generated.equalsIgnoreCase(expectedSignature);
    }

    /**
     * Decrypts payload encrypted with AES-256-GCM (Format: base64(IV).base64(CipherText + 16-byte Tag))
     * Matches PHP Crypto_lib decrypt_payload()
     */
    public static String decryptAes256Gcm(String encryptedPayload, String secretKey) {
        try {
            if (encryptedPayload == null || encryptedPayload.trim().isEmpty()) {
                return null;
            }

            String ivBase64;
            String cipherBase64;

            if (encryptedPayload.contains(".")) {
                String[] parts = encryptedPayload.split("\\.", 2);
                if (parts.length != 2) return null;
                ivBase64 = parts[0].trim();
                cipherBase64 = parts[1].trim();
            } else {
                return null;
            }

            byte[] ivBytes = decodeBase64(ivBase64);
            byte[] cipherTextWithTag = decodeBase64(cipherBase64);

            byte[] keyBytes = prepareKey(secretKey);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(cipherTextWithTag);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decrypts AES-256-GCM when IV and cipherText are provided separately (Hex or Base64)
     */
    public static String decryptAes256Gcm(String encryptedData, String iv, String secretKey) {
        try {
            byte[] ivBytes = isHex(iv) ? hexToBytes(iv) : decodeBase64(iv);
            byte[] cipherTextWithTag = isHex(encryptedData) ? hexToBytes(encryptedData) : decodeBase64(encryptedData);

            byte[] keyBytes = prepareKey(secretKey);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, ivBytes);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            byte[] decrypted = cipher.doFinal(cipherTextWithTag);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Encrypts plainText using AES-256-GCM (Format: base64(12-byte IV).base64(CipherText + 16-byte Tag))
     * Matches PHP Crypto_lib encrypt_payload()
     */
    public static String encryptAes256Gcm(String plainText, String secretKey) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);

            byte[] keyBytes = prepareKey(secretKey);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] cipherTextWithTag = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String ivBase64 = encodeBase64(iv);
            String cipherBase64 = encodeBase64(cipherTextWithTag);

            return ivBase64 + "." + cipherBase64;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Legacy AES-256-CBC Decryption Fallback
     */
    public static String decryptAes256Cbc(String hexEncryptedData, String hexIv, String secretKey) {
        try {
            byte[] cipherText = hexToBytes(hexEncryptedData);
            byte[] ivBytes = hexToBytes(hexIv);

            byte[] keyBytes = prepareKey(secretKey);

            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(cipherText);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] prepareKey(String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            return padded;
        }
        return keyBytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() < 2) {
            return new byte[0];
        }
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    private static boolean isHex(String s) {
        if (s == null || s.isEmpty() || s.length() % 2 != 0) return false;
        return s.matches("^[0-9a-fA-F]+$");
    }

    private static String encodeBase64(byte[] bytes) {
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private static byte[] decodeBase64(String str) {
        return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
    }
}
