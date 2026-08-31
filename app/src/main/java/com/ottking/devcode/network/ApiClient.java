package com.ottking.devcode.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.model.Category;
import com.ottking.devcode.model.Channel;
import com.ottking.devcode.model.StreamTokenAuth;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;
import com.ottking.devcode.utils.NetworkUtils;

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
        List<CategoryEntity> list = new ArrayList<>();
        list.add(new CategoryEntity(1, "All Channels", "ic_tv"));
        list.add(new CategoryEntity(2, "News Live", "ic_tv"));
        list.add(new CategoryEntity(3, "Sports Live", "ic_tv"));
        list.add(new CategoryEntity(4, "Entertainment", "ic_tv"));
        list.add(new CategoryEntity(5, "Movies & Series", "ic_tv"));
        list.add(new CategoryEntity(6, "Music TV", "ic_tv"));
        return list;
    }

    public static List<ChannelEntity> getDefaultChannels() {
        List<ChannelEntity> list = new ArrayList<>();
        list.add(new ChannelEntity(1, "Somoy TV Live", "https://i.ibb.co/L5Q0J6q/somoy.png", "https://live.bdix.tv/live/somoy/playlist.m3u8", 2, false, "hls"));
        list.add(new ChannelEntity(2, "Jamuna TV Live", "https://i.ibb.co/4p5Y7yN/jamuna.png", "https://live.bdix.tv/live/jamuna/playlist.m3u8", 2, false, "hls"));
        list.add(new ChannelEntity(3, "T Sports Live HD", "https://i.ibb.co/XzVq0qW/tsports.png", "https://live.bdix.tv/live/tsports/playlist.m3u8", 3, false, "hls"));
        list.add(new ChannelEntity(4, "GTV Sports Live", "https://i.ibb.co/P4Jp8g7/gtv.png", "https://live.bdix.tv/live/gtv/playlist.m3u8", 3, false, "hls"));
        list.add(new ChannelEntity(5, "Channel 24 Live", "https://i.ibb.co/KjqfH0Y/channel24.png", "https://live.bdix.tv/live/channel24/playlist.m3u8", 2, false, "hls"));
        list.add(new ChannelEntity(6, "Bongo Cinema HD", "https://i.ibb.co/7XgW99T/bongocinema.png", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", 5, false, "hls"));
        list.add(new ChannelEntity(7, "NTV Bangladesh Live", "https://i.ibb.co/0VpLh0p/ntv.png", "https://live.bdix.tv/live/ntv/playlist.m3u8", 4, false, "hls"));
        list.add(new ChannelEntity(8, "Music Bangladesh HD", "https://i.ibb.co/2vB5nK1/music.png", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8", 6, false, "hls"));
        return list;
    }

    public void syncCategoriesAndChannels(final ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                if (!NetworkUtils.isNetworkConnected(context)) {
                    mainHandler.post(() -> callback.onError("ইন্টারনেট সংযোগ নেই। দয়া করে আপনার নেটওয়ার্ক চেক করুন।"));
                    return;
                }

                AppPreferences prefs = AppPreferences.getInstance(context);
                String baseUrl = SecurityUtils.DEBUG_API_URL;
                
                // Real-time local date expiry check
                boolean isSubActive = NetworkUtils.isSubscriptionActive(context);
                String sessionToken = isSubActive ? prefs.getSessionToken() : "";

                List<CategoryEntity> catEntities = new ArrayList<>();
                List<ChannelEntity> chanEntities = new ArrayList<>();
                boolean serverSuccess = false;
                String serverErrorMessage = null;

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

                    // Fetch Channels with live session token
                    String chanUrlStr = baseUrl + "channels.php?session_token=" + sessionToken;
                    String chanJsonStr = executeHttpGet(chanUrlStr);

                    if (chanJsonStr != null) {
                        JSONObject chanObj = new JSONObject(chanJsonStr);
                        if (chanObj.optString("status").equals("success")) {
                            JSONArray chanArray = chanObj.optJSONArray("channels");
                            if (chanArray != null) {
                                for (int i = 0; i < chanArray.length(); i++) {
                                    JSONObject item = chanArray.getJSONObject(i);
                                    boolean isPremium = item.optInt("is_premium", 0) == 1;
                                    
                                    // If subscription has expired and channel is strictly premium hidden by server, skip
                                    if (!isSubActive && isPremium) {
                                        continue;
                                    }

                                    chanEntities.add(new ChannelEntity(
                                            item.getInt("id"),
                                            item.getString("name"),
                                            item.optString("logo_url"),
                                            item.getString("stream_url"),
                                            item.getInt("category_id"),
                                            isPremium,
                                            item.optString("stream_type", "hls")
                                    ));
                                }
                            }
                        }
                    }

                    serverSuccess = (!catEntities.isEmpty() || !chanEntities.isEmpty());
                } catch (Exception netEx) {
                    netEx.printStackTrace();
                    serverErrorMessage = netEx.getLocalizedMessage();
                    serverSuccess = false;
                }

                AppDatabase db = AppDatabase.getInstance(context);

                if (serverSuccess && (!catEntities.isEmpty() || !chanEntities.isEmpty())) {
                    if (!catEntities.isEmpty()) {
                        db.categoryDao().deleteAll();
                        db.categoryDao().insertAll(catEntities);
                    }
                    if (!chanEntities.isEmpty()) {
                        db.channelDao().deleteAll();
                        db.channelDao().insertAll(chanEntities);
                    }
                    String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
                    prefs.setLastSyncTime(timestamp);
                    mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    String finalErr = (serverErrorMessage != null && !serverErrorMessage.isEmpty())
                            ? "সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি: " + serverErrorMessage
                            : "সার্ভার থেকে চ্যানেল ডেটা পাওয়া যায়নি।";
                    mainHandler.post(() -> callback.onError(finalErr));
                }
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onError("সার্ভার এরর: " + e.getLocalizedMessage()));
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

    /**
     * Requests authenticated stream token from server endpoint (stream-token.php).
     * Verifies X-App-Client, user session, device binding, and issues signed stream token.
     */
    public void fetchStreamToken(int channelId, String streamUrl, ApiCallback<StreamTokenAuth> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();
                String username = prefs.getUsername().isEmpty() ? "GuestUser" : prefs.getUsername();
                String appClient = SecurityUtils.getXAppClientToken();

                JSONObject body = new JSONObject();
                body.put("session_token", sessionToken);
                body.put("device_id", deviceId);
                body.put("username", username);
                body.put("channel_id", channelId);
                body.put("stream_url", streamUrl != null ? streamUrl : "");
                body.put("app_client", appClient);
                body.put("timestamp", System.currentTimeMillis() / 1000);

                String endpoint = SecurityUtils.getApiUrl() + "stream-token.php";
                String responseStr = executeHttpPost(endpoint, body.toString());

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if ("success".equalsIgnoreCase(resObj.optString("status"))) {
                        String streamToken = resObj.optString("stream_token", "");
                        long expiresAt = resObj.optLong("expires_at", System.currentTimeMillis() + 14400000);
                        String authStreamUrl = resObj.optString("authorized_stream_url", streamUrl);
                        String authHeader = resObj.optString("authorization_header", "Bearer " + streamToken);
                        String verifiedClient = resObj.optString("app_client", appClient);

                        StreamTokenAuth tokenAuth = new StreamTokenAuth(
                                streamToken,
                                verifiedClient,
                                expiresAt,
                                authStreamUrl,
                                authHeader,
                                true,
                                resObj.optString("message", "Token verified")
                        );
                        mainHandler.post(() -> callback.onSuccess(tokenAuth));
                        return;
                    } else {
                        String err = resObj.optString("message", "Stream authorization failed.");
                        mainHandler.post(() -> callback.onError(err));
                        return;
                    }
                }

                // If offline or custom endpoint fallback, generate local secure cryptographic token
                StreamTokenAuth localAuth = generateLocalStreamToken(channelId, streamUrl);
                mainHandler.post(() -> callback.onSuccess(localAuth));
            } catch (Exception e) {
                // Fallback to local cryptographic token to avoid blocking playback
                StreamTokenAuth localAuth = generateLocalStreamToken(channelId, streamUrl);
                mainHandler.post(() -> callback.onSuccess(localAuth));
            }
        });
    }

    public StreamTokenAuth generateLocalStreamToken(int channelId, String streamUrl) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        String sessionToken = prefs.getSessionToken();
        String deviceId = prefs.getDeviceId();
        String appClient = SecurityUtils.getXAppClientToken();
        long exp = (System.currentTimeMillis() / 1000) + 14400; // 4 hours

        String payload = channelId + ":" + deviceId + ":" + (sessionToken != null ? sessionToken : "") + ":" + appClient + ":" + exp;
        String sig = SecurityUtils.generateHmac(payload, SecurityUtils.getHmacKey());
        String token = android.util.Base64.encodeToString((payload + "." + sig).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);

        return new StreamTokenAuth(
                token,
                appClient,
                exp * 1000,
                streamUrl,
                "Bearer " + token,
                true,
                "Local verified stream ticket"
        );
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
                                android.util.Log.e("ApiClient", "Invalid response HMAC signature!");
                                return null;
                            }
                        }
                        return decrypted;
                    } else {
                        android.util.Log.e("ApiClient", "Failed to decrypt response payload!");
                        return null;
                    }
                } else if (json.has("status") || json.has("categories") || json.has("channels")) {
                    // Unencrypted JSON response fallback
                    return trimmed;
                }
            }
            return trimmed;
        } catch (Exception e) {
            android.util.Log.e("ApiClient", "Invalid response or parsing exception", e);
            return null;
        }
    }
}
