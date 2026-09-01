package com.ottking.devcode.network;

import android.content.Context;
import android.util.Log;

import com.ottking.devcode.security.SecurityUtils;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;

/**
 * Singleton Retrofit client manager with high-performance connection pooling,
 * SSL/TLS handling, AES-256-GCM encryption/decryption, and HMAC-SHA256 verification.
 */
public class RetrofitClient {

    private static final String TAG = "RetrofitClient";
    private static RetrofitClient instance;

    private final Retrofit retrofit;
    private final ApiService apiService;
    private final OkHttpClient okHttpClient;

    public static class EncryptedRequest {
        public final RequestBody requestBody;
        public final String signature;

        public EncryptedRequest(RequestBody requestBody, String signature) {
            this.requestBody = requestBody;
            this.signature = signature;
        }
    }

    private RetrofitClient(Context context) {
        // High efficiency OkHttpClient with connection pooling for fast API calls
        this.okHttpClient = new OkHttpClient.Builder()
                .connectionPool(new ConnectionPool(16, 5, TimeUnit.MINUTES))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        String baseUrl = SecurityUtils.getApiUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .build();

        this.apiService = retrofit.create(ApiService.class);
    }

    public static synchronized RetrofitClient getInstance(Context context) {
        if (instance == null) {
            instance = new RetrofitClient(context.getApplicationContext());
        }
        return instance;
    }

    public ApiService getApiService() {
        return apiService;
    }

    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }

    /**
     * Prepares an encrypted request body and HMAC signature from raw JSON.
     */
    public static EncryptedRequest prepareEncryptedRequest(String plainJson) {
        try {
            JSONObject requestObj = new JSONObject();
            String encryptedPayload = SecurityUtils.encryptAesGcm(plainJson, SecurityUtils.getEncryptionKey());
            String signature = SecurityUtils.generateHmac(plainJson, SecurityUtils.getHmacKey());
            requestObj.put("encrypted_payload", encryptedPayload);
            requestObj.put("signature", signature);

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestObj.toString()
            );
            return new EncryptedRequest(body, signature);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing encrypted request: " + e.getMessage(), e);
            RequestBody plainBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    plainJson
            );
            return new EncryptedRequest(plainBody, "");
        }
    }

    /**
     * Extracts and decrypts raw response body from Retrofit Response (handles both success and error bodies).
     */
    public static String extractAndDecryptResponse(Response<ResponseBody> response) throws IOException {
        if (response == null) {
            return null;
        }

        String rawResponse = null;
        if (response.isSuccessful() && response.body() != null) {
            rawResponse = response.body().string();
        } else if (response.errorBody() != null) {
            rawResponse = response.errorBody().string();
        }

        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return null;
        }

        return parseAndDecrypt(rawResponse);
    }

    /**
     * Decrypts AES-256-GCM / HMAC signed or plain JSON response.
     */
    public static String parseAndDecrypt(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return null;
        }
        try {
            String trimmed = rawResponse.trim();

            // Direct split payload check (base64Iv.base64Cipher)
            if (trimmed.contains(".") && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                String decrypted = SecurityUtils.decryptAesGcm(trimmed, SecurityUtils.getEncryptionKey());
                if (decrypted != null) {
                    return decrypted;
                }
            }

            if (trimmed.startsWith("{")) {
                JSONObject json = new JSONObject(trimmed);
                if (json.has("encrypted_payload")) {
                    String encryptedPayload = json.getString("encrypted_payload");
                    String signature = json.optString("signature", "");

                    String decrypted = SecurityUtils.decryptAesGcm(encryptedPayload, SecurityUtils.getEncryptionKey());
                    if (decrypted != null) {
                        if (!signature.isEmpty()) {
                            boolean isSignatureValid = SecurityUtils.verifySignature(decrypted, signature, SecurityUtils.getHmacKey())
                                    || SecurityUtils.verifySignature(encryptedPayload, signature, SecurityUtils.getHmacKey());
                            if (!isSignatureValid) {
                                Log.e(TAG, "Invalid response HMAC signature!");
                                return null;
                            }
                        }
                        return decrypted;
                    } else {
                        Log.e(TAG, "Failed to decrypt response payload!");
                        return null;
                    }
                } else if (json.has("status") || json.has("categories") || json.has("channels")) {
                    // Unencrypted JSON response fallback
                    return trimmed;
                }
            }
            return trimmed;
        } catch (Exception e) {
            Log.e(TAG, "Invalid response or parsing exception", e);
            return null;
        }
    }
}
