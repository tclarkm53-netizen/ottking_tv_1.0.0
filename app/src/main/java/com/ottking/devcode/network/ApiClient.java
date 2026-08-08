package com.ottking.devcode.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.R;
import com.ottking.devcode.model.Category;
import com.ottking.devcode.model.Channel;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiClient {

    private static ApiClient instance;
    private final Context context;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized ApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new ApiClient(context);
        }
        return instance;
    }

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String errorMessage);
    }

    public static List<CategoryEntity> getDefaultCategories() {
        return new ArrayList<>();
    }

    public static List<ChannelEntity> getDefaultChannels() {
        return new ArrayList<>();
    }

    public void syncCategoriesAndChannels(final ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String baseUrl = SecurityUtils.DEBUG_API_URL;
                String sessionToken = prefs.getSessionToken();

                List<CategoryEntity> catEntities = new ArrayList<>();
                List<ChannelEntity> chanEntities = new ArrayList<>();
                boolean serverSuccess = false;

                try {
                    // Fetch Categories
                    String catUrlStr = baseUrl + "categories.php";
                    String catJsonStr = executeHttpGet(catUrlStr);

                    if (catJsonStr != null) {
                        JSONObject catObj = new JSONObject(catJsonStr);
                        if (catObj.optString("status").equals("success")) {
                            JSONArray catArray = catObj.optJSONArray("categories");
                            if (catArray != null) {
                                for (int i = 0; i < catArray.length(); i++) {
                                    JSONObject item = catArray.getJSONObject(i);
                                    catEntities.add(new CategoryEntity(
                                            item.getInt("id"),
                                            item.getString("name"),
                                            item.optString("icon", "ic_tv")
                                    ));
                                }
                            }
                        }
                    }

                    // Fetch Channels
                    String chanUrlStr = baseUrl + "channels.php?session_token=" + sessionToken;
                    String chanJsonStr = executeHttpGet(chanUrlStr);

                    if (chanJsonStr != null) {
                        JSONObject chanObj = new JSONObject(chanJsonStr);
                        if (chanObj.optString("status").equals("success")) {
                            JSONArray chanArray = chanObj.optJSONArray("channels");
                            if (chanArray != null) {
                                for (int i = 0; i < chanArray.length(); i++) {
                                    JSONObject item = chanArray.getJSONObject(i);
                                    chanEntities.add(new ChannelEntity(
                                            item.getInt("id"),
                                            item.getString("name"),
                                            item.optString("logo_url"),
                                            item.getString("stream_url"),
                                            item.getInt("category_id"),
                                            item.optInt("is_premium", 0) == 1,
                                            item.optString("stream_type", "hls")
                                    ));
                                }
                            }
                        }
                    }

                    serverSuccess = (!catEntities.isEmpty() || !chanEntities.isEmpty());
                } catch (Exception netEx) {
                    netEx.printStackTrace();
                    serverSuccess = false;
                }

                AppDatabase db = AppDatabase.getInstance(context);

                if (serverSuccess && (!catEntities.isEmpty() || !chanEntities.isEmpty())) {
                    db.categoryDao().deleteAll();
                    if (!catEntities.isEmpty()) {
                        db.categoryDao().insertAll(catEntities);
                    }
                    db.channelDao().deleteAll();
                    if (!chanEntities.isEmpty()) {
                        db.channelDao().insertAll(chanEntities);
                    }
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                    prefs.setLastSyncTime(timestamp);
                    mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    // Wipe local database if server sync fails or payload security check fails
                    db.categoryDao().deleteAll();
                    db.channelDao().deleteAll();
                    mainHandler.post(() -> callback.onError("Security Mismatch / Decryption Error: Server response is not encrypted with expected AES-256-GCM keys or returned invalid payload."));
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("Server Connection Error: " + e.getLocalizedMessage()));
            }
        });
    }

    public void login(String username, String password, ApiCallback<UserInfo> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                body.put("device_id", prefs.getDeviceId());

                String responseStr = executeHttpPost(SecurityUtils.DEBUG_API_URL + "login.php", body.toString());
                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if (resObj.optString("status").equals("success")) {
                        String token = resObj.getString("session_token");
                        JSONObject userObj = resObj.getJSONObject("user_info");

                        UserInfo info = new UserInfo(
                                userObj.getString("username"),
                                userObj.getString("package"),
                                userObj.getString("expiry_date"),
                                userObj.getString("device_id")
                        );

                        prefs.setSessionToken(token);
                        prefs.setUsername(info.getUsername());
                        prefs.setUserPackage(info.getPackageName());
                        prefs.setUserExpiry(info.getExpiryDate());

                        // Re-sync channels after login to pull premium channels
                        syncCategoriesAndChannels(new ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean result) {}
                            @Override
                            public void onError(String errorMessage) {}
                        });

                        mainHandler.post(() -> callback.onSuccess(info));
                    } else {
                        String msg = resObj.optString("message", "Login failed");
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Server connection error"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error: " + e.getLocalizedMessage()));
            }
        });
    }

    public void logout(ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();
                String username = prefs.getUsername();

                JSONObject body = new JSONObject();
                body.put("session_token", sessionToken);
                body.put("device_id", deviceId);
                body.put("username", username);

                String responseStr = executeHttpPost(SecurityUtils.DEBUG_API_URL + "logout.php", body.toString());

                prefs.logout();

                // Re-sync categories/channels after logout to clear VIP channel access
                syncCategoriesAndChannels(new ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {}
                    @Override
                    public void onError(String errorMessage) {}
                });

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    String msg = resObj.optString("message", "Logged out successfully");
                    mainHandler.post(() -> callback.onSuccess(msg));
                } else {
                    mainHandler.post(() -> callback.onSuccess("Logged out successfully"));
                }
            } catch (Exception e) {
                AppPreferences.getInstance(context).logout();
                mainHandler.post(() -> callback.onSuccess("Logged out successfully"));
            }
        });
    }

    public void checkAppUpdate(ApiCallback<UpdateInfo> callback) {
        executor.execute(() -> {
            try {
                int currentVersionCode = com.ottking.devcode.BuildConfig.VERSION_CODE;
                String currentVersionName = com.ottking.devcode.BuildConfig.VERSION_NAME;
                try {
                    android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        currentVersionCode = (int) pInfo.getLongVersionCode();
                    } else {
                        currentVersionCode = pInfo.versionCode;
                    }
                    if (pInfo.versionName != null && !pInfo.versionName.isEmpty()) {
                        currentVersionName = pInfo.versionName;
                    }
                } catch (Exception ignored) {}

                String encodedVersionName = java.net.URLEncoder.encode(currentVersionName, "UTF-8");
                String urlStr = SecurityUtils.getApiUrl() + "update-app.php?version_code=" + currentVersionCode 
                        + "&version_name=" + encodedVersionName
                        + "&build_version=" + currentVersionCode
                        + "&app_version=" + encodedVersionName;

                String responseStr = executeHttpGet(urlStr);
                if (responseStr != null) {
                    JSONObject obj = new JSONObject(responseStr);
                    int serverVersionCode = obj.optInt("version_code", currentVersionCode);
                    String serverVersionName = obj.optString("version_name", currentVersionName);

                    boolean hasUpdate;
                    if (obj.has("has_update")) {
                        hasUpdate = obj.optBoolean("has_update");
                    } else {
                        hasUpdate = serverVersionCode > currentVersionCode;
                    }

                    String updateUrl = obj.optString("update_url", SecurityUtils.getApiUrl() + "app-release.apk");
                    if (updateUrl.isEmpty()) updateUrl = SecurityUtils.getApiUrl() + "app-release.apk";
                    
                    String serverChangelog = obj.optString("changelog", "");
                    if (serverChangelog.trim().isEmpty()) {
                        serverChangelog = "No changelog details provided by server.";
                    }

                    UpdateInfo info = new UpdateInfo(
                            hasUpdate,
                            serverVersionCode,
                            serverVersionName,
                            serverChangelog,
                            updateUrl
                    );
                    mainHandler.post(() -> callback.onSuccess(info));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to fetch update info from server."));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Update check error: " + e.getLocalizedMessage()));
            }
        });
    }

    public void fetchServerNotifications(String username, String userPackage, ApiCallback<List<NotificationItem>> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String curUsername = username != null ? username : prefs.getUsername();
                String curPackage = userPackage != null ? userPackage : prefs.getUserPackage();

                JSONObject body = new JSONObject();
                body.put("username", curUsername);
                body.put("package", curPackage);
                body.put("session_token", sessionToken);
                body.put("action", "list");

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "notifications.php", body.toString());

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if (resObj.optString("status").equals("success")) {
                        JSONArray arr = resObj.optJSONArray("notifications");
                        List<NotificationItem> items = new ArrayList<>();
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                String id = obj.optString("id", "notif_" + i);
                                String title = obj.optString("title", "Notification");
                                String message = obj.optString("message", "");
                                String time = obj.optString("time", "Just now");
                                String type = obj.optString("type", "SYSTEM");
                                String actionText = obj.optString("action_text", "View");

                                int iconRes = R.drawable.ic_notifications;
                                if ("UPDATE".equalsIgnoreCase(type)) {
                                    iconRes = R.drawable.ic_update;
                                } else if ("CHANNEL".equalsIgnoreCase(type)) {
                                    iconRes = R.drawable.ic_tv;
                                } else if ("USER".equalsIgnoreCase(type)) {
                                    iconRes = R.drawable.ic_play;
                                }

                                items.add(new NotificationItem(id, title, message, time, iconRes, type, false, actionText));
                            }
                        }
                        mainHandler.post(() -> callback.onSuccess(items));
                        return;
                    }
                }
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onSuccess(new ArrayList<>()));
            }
        });
    }

    public void submitReport(String category, String description, ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                JSONObject body = new JSONObject();
                body.put("username", prefs.getUsername().isEmpty() ? "GuestUser" : prefs.getUsername());
                body.put("category", category);

                String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
                String androidVersion = Build.VERSION.RELEASE;
                String cpuAbi = (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0)
                        ? Build.SUPPORTED_ABIS[0]
                        : Build.CPU_ABI;
                int sdkApiVersion = Build.VERSION.SDK_INT;

                String deviceInfo = String.format(Locale.US,
                        "\n\n--- Device Info ---\nDevice Name: %s\nAndroid Version: %s\nCPU Architecture: %s\nSDK API Version: %d",
                        deviceName, androidVersion, cpuAbi, sdkApiVersion);

                body.put("description", description + deviceInfo);
                body.put("device_name", deviceName);
                body.put("android_version", androidVersion);
                body.put("cpu_architecture", cpuAbi);
                body.put("sdk_api_version", sdkApiVersion);
                body.put("device_info", deviceInfo.trim());

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "submit-reports.php", body.toString());
                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if (resObj.optString("status", "").equals("success") || resObj.has("message")) {
                        String msg = resObj.optString("message", "Report submitted successfully!");
                        mainHandler.post(() -> callback.onSuccess(msg));
                    } else {
                        String msg = resObj.optString("message", "Failed to submit report.");
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Server connection error: Unable to send report to server."));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Server error: " + e.getLocalizedMessage()));
            }
        });
    }

    private String executeHttpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("X-Api-Key", SecurityUtils.getApiKey());

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return parseAndDecryptResponse(sb.toString());
        }
        return null;
    }

    private String executeHttpPost(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("X-Api-Key", SecurityUtils.getApiKey());

        JSONObject requestObj = new JSONObject();
        String encryptedPayload = SecurityUtils.encryptAesGcm(jsonBody, SecurityUtils.getEncryptionKey());
        String signature = SecurityUtils.generateHmac(jsonBody, SecurityUtils.getHmacKey());
        requestObj.put("encrypted_payload", encryptedPayload);
        requestObj.put("signature", signature);

        String postBody = requestObj.toString();
        conn.setRequestProperty("X-Signature", signature);

        OutputStream os = conn.getOutputStream();
        os.write(postBody.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return parseAndDecryptResponse(sb.toString());
        }
        return null;
    }

    private String parseAndDecryptResponse(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return null;
        }
        try {
            JSONObject json = new JSONObject(rawResponse);
            if (json.has("encrypted_payload")) {
                String encryptedPayload = json.getString("encrypted_payload");
                String signature = json.optString("signature", "");

                String decrypted = SecurityUtils.decryptAesGcm(encryptedPayload, SecurityUtils.getEncryptionKey());
                if (decrypted != null) {
                    if (!signature.isEmpty()) {
                        boolean isSignatureValid = SecurityUtils.verifySignature(decrypted, signature, SecurityUtils.getHmacKey());
                        if (!isSignatureValid) {
                            android.util.Log.e("ApiClient", "Invalid response HMAC signature!");
                            return null;
                        }
                    }
                    return decrypted;
                } else {
                    android.util.Log.e("ApiClient", "Failed to decrypt response payload!");
                    return null;
                }
            } else {
                android.util.Log.e("ApiClient", "Security Error: Response is unencrypted!");
                return null;
            }
        } catch (Exception e) {
            android.util.Log.e("ApiClient", "Invalid response or parsing exception", e);
            return null;
        }
    }
}
