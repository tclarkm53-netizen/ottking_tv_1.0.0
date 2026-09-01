package com.ottking.devcode.security;

import android.util.Base64;
import com.ottking.devcode.config.Config;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
 
 
public class SecurityUtils {

    public static String getApiUrl() { return Config.BaseUrl; }
    public static String getApiKey() { return Config.ApiKey; }
    public static String getHmacKey() { return Config.HmacKey; }
    public static String getEncryptionKey() { return Config.EncryptionKey; }
    public static String getXAppClientToken() { return Config.getXAppClientTokens(); }

    public static final String HEADER_APP_CLIENT = "X-App-Client";
    public static final String HEADER_STREAM_TOKEN = "X-Stream-Token";
    public static final String HEADER_DEVICE_ID = "X-Device-Id";
    public static final String HEADER_SESSION_TOKEN = "X-Session-Token";

    public static final String DEBUG_API_URL = Config.BaseUrl;
    public static final String DEBUG_API_KEY = Config.ApiKey;
    public static final String DEBUG_HMAC_KEY = Config.HmacKey;
    public static final String DEBUG_ENCRYPTION_KEY = Config.EncryptionKey;
    public static final String DEBUG_X_APP_CLIENT = Config.getXAppClientTokens();

    private static SecretKeySpec getSecretKeySpec(String key) {
        if (key == null) key = "";
        byte[] keyBytes = new byte[32];
        byte[] src = key.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String generateHmac(String payload, String secretKey) {
        if (payload == null || secretKey == null) return "";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hmacBytes) {
                sb.append(String.format(Locale.US, "%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static boolean verifySignature(String payload, String signature, String secretKey) {
        if (payload == null || signature == null || secretKey == null) return false;
        String computed = generateHmac(payload, secretKey);
        return computed.equalsIgnoreCase(signature.trim());
    }

    /**
     * Encrypts plain text using AES-256-GCM matching PHP Crypto_lib.
     * Returns: base64(12_byte_iv) . "." . base64(ciphertext + 16_byte_tag)
     */
    public static String encryptAesGcm(String plainText, String key) {
        if (plainText == null) return null;
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
            return null;
        }
    }

    /**
     * Decrypts payload in format base64(iv) + "." + base64(ciphertext + tag) using AES-256-GCM.
     * Compatible with PHP Crypto_lib / OpenSSL AES-256-GCM.
     */
    public static String decryptAesGcm(String encryptedPayload, String key) {
        if (encryptedPayload == null || !encryptedPayload.contains(".")) {
            return null;
        }
        try {
            String[] parts = encryptedPayload.trim().split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }

            byte[] iv = Base64.decode(parts[0].trim(), Base64.DEFAULT);
            byte[] combined = Base64.decode(parts[1].trim(), Base64.DEFAULT);

            if (iv == null || combined == null || iv.length != 12 || combined.length <= 16) {
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

    /**
     * Sanitizes any message to ensure NO server URLs, hostnames, IP addresses,
     * or backend infrastructure details are ever printed in the UI.
     */
    public static String sanitizeForUI(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "Unable to complete request. Please try again.";
        }
        
        String lower = message.toLowerCase(Locale.US);
        
        // Detect connection/network/host related raw exceptions
        if (lower.contains("unknownhost") || lower.contains("unable to resolve host") 
                || lower.contains("no address associated") || lower.contains("connectexception")
                || lower.contains("failed to connect") || lower.contains("connection refused")
                || lower.contains("sockettimeoutexception") || lower.contains("timeout")
                || lower.contains("sslhandshake") || lower.contains("certpathvalidator")
                || lower.contains("route to host") || lower.contains("network is unreachable")) {
            return "Unable to connect to service. Please check your internet connection and try again.";
        }

        if (lower.contains("http 500") || lower.contains("http 502") || lower.contains("http 503") || lower.contains("http 504")
                || lower.contains("server error") || lower.contains("internal server error")) {
            return "Server is temporarily unavailable. Please try again later.";
        }

        // Clean out any URLs (http://, https://, ftp://)
        String cleaned = message.replaceAll("(?i)https?://[^\\s/$.?#].[^\\s]*", "[Server]");
        // Clean out domain names with extensions (.net, .com, .org, .io, .xyz, etc.)
        cleaned = cleaned.replaceAll("(?i)[a-zA-Z0-9.-]+\\.(net|com|org|io|app|tv|xyz|co|uk|de|online|site|space|cloud)(:\\d+)?", "[Server]");
        // Clean out IPv4 addresses with optional ports
        cleaned = cleaned.replaceAll("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?\\b", "[Server]");
        // Clean out specific Config.BaseUrl if any remnants remain
        if (Config.BaseUrl != null && !Config.BaseUrl.isEmpty()) {
            cleaned = cleaned.replace(Config.BaseUrl, "");
        }
        
        cleaned = cleaned.trim();
        if (cleaned.isEmpty() || cleaned.equals("[Server]")) {
            return "Unable to connect to server. Please try again.";
        }

        return cleaned;
    }

    public static String sanitizeException(Throwable t) {
        if (t == null) return "An unexpected error occurred. Please try again.";
        return sanitizeForUI(t.getLocalizedMessage() != null ? t.getLocalizedMessage() : t.getMessage());
    }
}
