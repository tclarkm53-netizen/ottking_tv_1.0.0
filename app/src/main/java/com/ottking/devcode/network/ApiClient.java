package com.ottking.devcode.network;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.model.StreamTokenAuth;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;
import com.ottking.devcode.utils.NetworkUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

/**
 * ApiClient powered by Retrofit with connection pooling, encrypted payload handling,
 * and high-availability SQLite synchronization.
 */
public class ApiClient {

    private static final String TAG = "ApiClient";
    private static ApiClient instance;
    private final Context context;
    private final RetrofitClient retrofitClient;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ApiClient(Context context) {
        this.context = context.getApplicationContext();
        this.retrofitClient = RetrofitClient.getInstance(this.context);
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

    /**
     * Checks if a JSON response indicates server maintenance mode.
     */
    private boolean isMaintenanceResponse(JSONObject json) {
        if (json == null) return false;
        String status = json.optString("status", "").toLowerCase(Locale.US);
        if ("maintenance".equals(status) || "maintenance_mode".equals(status) || "under_maintenance".equals(status)) {
            return true;
        }
        if (json.optBoolean("is_maintenance", false) || json.optBoolean("maintenance", false)) {
            return true;
        }
        if (json.optInt("is_maintenance", 0) == 1 || json.optInt("maintenance", 0) == 1) {
            return true;
        }
        return false;
    }

    /**
     * Extracts maintenance message or provides a sensible default.
     */
    private String extractMaintenanceMessage(JSONObject json) {
        if (json != null) {
            String msg = json.optString("message", "");
            if (msg.isEmpty()) msg = json.optString("msg", "");
            if (msg.isEmpty()) msg = json.optString("error", "");
            if (msg.isEmpty()) msg = json.optString("detail", "");
            if (!msg.isEmpty()) return msg;
        }
        return "সার্ভার বর্তমানে মেইনটেনেন্স মোডে আছে। অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন।";
    }

    /**
     * Synchronizes categories and channels via Retrofit.
     */
    public void syncCategoriesAndChannels(final ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            try {
                if (!NetworkUtils.isNetworkConnected(context)) {
                    mainHandler.post(() -> callback.onError(context.getString(com.ottking.devcode.R.string.net_error_no_connection)));
                    return;
                }

                AppPreferences prefs = AppPreferences.getInstance(context);
                boolean isSubActive = NetworkUtils.isSubscriptionActive(context);
                String sessionToken = isSubActive ? prefs.getSessionToken() : "";

                List<CategoryEntity> catEntities = new ArrayList<>();
                List<ChannelEntity> chanEntities = new ArrayList<>();
                boolean serverSuccess = false;
                String serverErrorMessage = null;
                boolean isMaintenance = false;
                String maintenanceMsg = null;

                try {
                    // 1. Fetch Categories via Retrofit ApiService
                    Call<ResponseBody> catCall = retrofitClient.getApiService().getCategories(SecurityUtils.getApiKey());
                    Response<ResponseBody> catResponse = catCall.execute();
                    String catJsonStr = RetrofitClient.extractAndDecryptResponse(catResponse);

                    if (catJsonStr != null) {
                        JSONObject catObj = new JSONObject(catJsonStr);
                        if (isMaintenanceResponse(catObj)) {
                            isMaintenance = true;
                            maintenanceMsg = extractMaintenanceMessage(catObj);
                        } else if ("success".equalsIgnoreCase(catObj.optString("status", "")) || catObj.has("categories")) {
                            JSONArray catArray = catObj.optJSONArray("categories");
                            if (catArray != null) {
                                for (int i = 0; i < catArray.length(); i++) {
                                    JSONObject item = catArray.getJSONObject(i);
                                    int catId = item.optInt("id", item.optInt("category_id", item.optInt("cat_id", i + 1)));
                                    String catName = item.optString("name", item.optString("category_name", item.optString("title", "Category " + catId)));

                                    String catIcon = "";
                                    if (item.has("icon") && !item.isNull("icon")) {
                                        catIcon = item.optString("icon", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("icon_url") && !item.isNull("icon_url")) {
                                        catIcon = item.optString("icon_url", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("category_icon") && !item.isNull("category_icon")) {
                                        catIcon = item.optString("category_icon", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("image") && !item.isNull("image")) {
                                        catIcon = item.optString("image", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("image_url") && !item.isNull("image_url")) {
                                        catIcon = item.optString("image_url", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("logo") && !item.isNull("logo")) {
                                        catIcon = item.optString("logo", "").trim();
                                    }
                                    if (catIcon.isEmpty() && item.has("logo_url") && !item.isNull("logo_url")) {
                                        catIcon = item.optString("logo_url", "").trim();
                                    }
                                    if (catIcon.isEmpty()) {
                                        catIcon = "ic_tv";
                                    }

                                    catEntities.add(new CategoryEntity(catId, catName, catIcon));
                                }
                            }
                        }
                    }

                    // 2. Fetch Channels via Retrofit ApiService (if not already detected maintenance)
                    if (!isMaintenance) {
                        Call<ResponseBody> chanCall = retrofitClient.getApiService().getChannels(sessionToken, SecurityUtils.getApiKey());
                        Response<ResponseBody> chanResponse = chanCall.execute();
                        String chanJsonStr = RetrofitClient.extractAndDecryptResponse(chanResponse);

                        if (chanJsonStr != null) {
                            JSONObject chanObj = new JSONObject(chanJsonStr);
                            if (isMaintenanceResponse(chanObj)) {
                                isMaintenance = true;
                                maintenanceMsg = extractMaintenanceMessage(chanObj);
                            } else if ("success".equalsIgnoreCase(chanObj.optString("status", "")) || chanObj.has("channels")) {
                                JSONArray chanArray = chanObj.optJSONArray("channels");
                                if (chanArray != null) {
                                    for (int i = 0; i < chanArray.length(); i++) {
                                        JSONObject item = chanArray.getJSONObject(i);
                                        boolean isPremium = item.optInt("is_premium", 0) == 1;

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
                    }

                    serverSuccess = !isMaintenance && (!catEntities.isEmpty() || !chanEntities.isEmpty());
                } catch (Exception netEx) {
                    Log.e(TAG, "Network error during sync: " + netEx.getMessage(), netEx);
                    serverErrorMessage = netEx.getLocalizedMessage();
                    serverSuccess = false;
                }

                AppDatabase db = AppDatabase.getInstance(context);

                if (isMaintenance) {
                    // When server maintenance is active, purge local channels/categories and record maintenance mode
                    final String msg = (maintenanceMsg != null && !maintenanceMsg.isEmpty()) ? maintenanceMsg : "সার্ভার বর্তমানে মেইনটেনেন্স মোডে আছে। অনুগ্রহ করে কিছুক্ষণ পর আবার চেষ্টা করুন।";
                    prefs.setMaintenanceMode(true, msg);
                    try {
                        db.categoryDao().deleteAll();
                        db.channelDao().deleteAll();
                    } catch (Exception ignored) {}
                    mainHandler.post(() -> callback.onError("MAINTENANCE:" + msg));
                    return;
                }

                if (serverSuccess && (!catEntities.isEmpty() || !chanEntities.isEmpty())) {
                    prefs.setMaintenanceMode(false, "");
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
                            ? context.getString(com.ottking.devcode.R.string.net_error_server_unavailable)
                            : context.getString(com.ottking.devcode.R.string.net_error_no_channel_data);
                    mainHandler.post(() -> callback.onError(finalErr));
                }
            } catch (Exception e) {
                Log.e(TAG, "Sync exception", e);
                mainHandler.post(() -> callback.onError(context.getString(com.ottking.devcode.R.string.net_error_server_error_format)));
            }
        });
    }

    /**
     * User Login via Retrofit with AES-256-GCM encryption & HMAC verification.
     */
    public void login(String username, String password, ApiCallback<UserInfo> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);
                body.put("device_id", prefs.getDeviceId());

                RetrofitClient.EncryptedRequest encryptedReq = RetrofitClient.prepareEncryptedRequest(body.toString());

                Call<ResponseBody> call = retrofitClient.getApiService().login(
                        encryptedReq.requestBody,
                        SecurityUtils.getApiKey(),
                        encryptedReq.signature
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);

                    if (isMaintenanceResponse(resObj)) {
                        String mMsg = extractMaintenanceMessage(resObj);
                        prefs.setMaintenanceMode(true, mMsg);
                        mainHandler.post(() -> callback.onError("MAINTENANCE:" + mMsg));
                        return;
                    }

                    String status = resObj.optString("status", "");
                    boolean isSuccess = "success".equalsIgnoreCase(status) || resObj.optBoolean("success", false);

                    if (isSuccess) {
                        String token = resObj.optString("session_token", resObj.optString("token", ""));
                        JSONObject userObj = resObj.optJSONObject("user_info");
                        if (userObj == null) {
                            userObj = resObj;
                        }

                        UserInfo info = new UserInfo(
                                userObj.optString("username", username),
                                userObj.optString("package", userObj.optString("subscription", "Standard VIP")),
                                userObj.optString("expiry_date", userObj.optString("expiry", "Active")),
                                userObj.optString("device_id", prefs.getDeviceId())
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
                        // Extract real server response message (e.g. wrong password, already logged in on other device, device limit reached, etc.)
                        String msg = resObj.optString("message", "");
                        if (msg.isEmpty()) msg = resObj.optString("error", "");
                        if (msg.isEmpty()) msg = resObj.optString("msg", "");
                        if (msg.isEmpty()) msg = resObj.optString("detail", "");
                        if (msg.isEmpty()) msg = resObj.optString("reason", "");
                        if (msg.isEmpty()) {
                            msg = "লগইন ব্যর্থ হয়েছে! ইউজারনেম বা পাসওয়ার্ড সঠিক কিনা পরীক্ষা করুন।";
                        }
                        final String finalMsg = msg;
                        mainHandler.post(() -> callback.onError(finalMsg));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("সার্ভারের সাথে সংযোগ স্থাপন করা সম্ভব হয়নি। অনুগ্রহ করে ইন্টারনেট কানেকশন চেক করুন।"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Login exception", e);
                mainHandler.post(() -> callback.onError("লগইন রিকোয়েস্টে ত্রুটি: " + e.getLocalizedMessage()));
            }
        });
    }

    /**
     * User Logout via Retrofit.
     */
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

                RetrofitClient.EncryptedRequest encryptedReq = RetrofitClient.prepareEncryptedRequest(body.toString());

                Call<ResponseBody> call = retrofitClient.getApiService().logout(
                        encryptedReq.requestBody,
                        SecurityUtils.getApiKey(),
                        encryptedReq.signature
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

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

    /**
     * Check App Updates via Retrofit.
     */
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

                Call<ResponseBody> call = retrofitClient.getApiService().checkUpdate(
                        currentVersionCode,
                        currentVersionName,
                        currentVersionCode,
                        currentVersionName,
                        SecurityUtils.getApiKey()
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

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
                Log.e(TAG, "Update check exception", e);
                mainHandler.post(() -> callback.onError("Update check unavailable. Please try again later."));
            }
        });
    }

    /**
     * Submit user problem report via Retrofit.
     */
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

                RetrofitClient.EncryptedRequest encryptedReq = RetrofitClient.prepareEncryptedRequest(body.toString());

                Call<ResponseBody> call = retrofitClient.getApiService().submitReport(
                        encryptedReq.requestBody,
                        SecurityUtils.getApiKey(),
                        encryptedReq.signature
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if (resObj.optString("status", "").equals("success") || resObj.has("message")) {
                        String msg = resObj.optString("message", "Report submitted successfully!");
                        mainHandler.post(() -> callback.onSuccess(SecurityUtils.sanitizeForUI(msg)));
                    } else {
                        String msg = resObj.optString("message", "Failed to submit report. Please try again.");
                        mainHandler.post(() -> callback.onError(SecurityUtils.sanitizeForUI(msg)));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Unable to send report to server. Please try again later."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Report submit exception", e);
                mainHandler.post(() -> callback.onError("Failed to submit report. Please try again later."));
            }
        });
    }

    /**
     * Requests authenticated stream token from server endpoint (stream-token.php) via Retrofit.
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

                RetrofitClient.EncryptedRequest encryptedReq = RetrofitClient.prepareEncryptedRequest(body.toString());

                Call<ResponseBody> call = retrofitClient.getApiService().fetchStreamToken(
                        encryptedReq.requestBody,
                        SecurityUtils.getApiKey(),
                        encryptedReq.signature
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

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

    /**
     * Local cryptographic token generator for instantaneous zero-latency playback startup.
     */
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

    /**
     * Real-time stream tracking (start, heartbeat, stop).
     */
    public void sendStreamTracking(String action, int channelId, String channelName, String streamUrl, String playerStatus, ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();
                String username = prefs.getUsername();

                JSONObject body = new JSONObject();
                body.put("action", action != null ? action : "heartbeat");
                body.put("channel_id", channelId);
                body.put("channel_name", channelName != null ? channelName : "");
                body.put("stream_url", streamUrl != null ? streamUrl : "");
                body.put("player_status", playerStatus != null ? playerStatus : "playing");
                body.put("device_id", deviceId != null ? deviceId : "");
                body.put("username", username != null && !username.isEmpty() ? username : "Guest");
                body.put("session_token", sessionToken != null ? sessionToken : "");

                RetrofitClient.EncryptedRequest encryptedReq = RetrofitClient.prepareEncryptedRequest(body.toString());

                Call<ResponseBody> call = retrofitClient.getApiService().trackStream(
                        encryptedReq.requestBody,
                        SecurityUtils.getApiKey(),
                        encryptedReq.signature
                );

                Response<ResponseBody> response = call.execute();
                String responseStr = RetrofitClient.extractAndDecryptResponse(response);

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if (resObj.optBoolean("is_maintenance", false) || "maintenance".equalsIgnoreCase(resObj.optString("status"))) {
                        String msg = resObj.optString("message", "Maintenance mode active");
                        prefs.setMaintenanceMode(true, msg);
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("MAINTENANCE:" + msg));
                        }
                        return;
                    }
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(responseStr));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess("ok"));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError(e.getMessage()));
                }
            }
        });
    }
}
