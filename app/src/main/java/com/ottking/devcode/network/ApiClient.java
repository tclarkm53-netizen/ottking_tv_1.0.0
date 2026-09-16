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
import com.ottking.devcode.model.NotificationItem;
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

    public static String getAppVersionName(Context context) {
        try {
            android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (pInfo.versionName != null && !pInfo.versionName.isEmpty()) {
                return pInfo.versionName;
            }
        } catch (Exception ignored) {}
        return com.ottking.devcode.BuildConfig.VERSION_NAME;
    }

    public static int getAppVersionCode(Context context) {
        try {
            android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                return (int) pInfo.getLongVersionCode();
            } else {
                return pInfo.versionCode;
            }
        } catch (Exception ignored) {}
        return com.ottking.devcode.BuildConfig.VERSION_CODE;
    }

    public static List<CategoryEntity> getDefaultCategories() {
        List<CategoryEntity> list = new ArrayList<>();
        list.add(new CategoryEntity(1, "All", "ic_tv"));
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
                    String versionName = getAppVersionName(context);
                    String encodedVer = java.net.URLEncoder.encode(versionName, "UTF-8");
                    int versionCode = getAppVersionCode(context);

                    String devId = (prefs != null) ? prefs.getDeviceId() : "";
                    String appId = SecurityUtils.APP_ID;
                    String encodedDev = java.net.URLEncoder.encode(devId, "UTF-8");
                    String encodedApp = java.net.URLEncoder.encode(appId, "UTF-8");

                    // Fetch Categories with app version, device_id, and app_id parameters
                    String catUrlStr = baseUrl + "categories?app_version=" + encodedVer
                            + "&version_name=" + encodedVer
                            + "&version_code=" + versionCode
                            + "&device_id=" + encodedDev
                            + "&app_id=" + encodedApp;
                    String catJsonStr = executeHttpGet(catUrlStr);

                    if (catJsonStr != null) {
                        JSONObject catObj = new JSONObject(catJsonStr);
                        // Capture edge-cookie from categories route
                        String catEdgeCookie = catObj.optString("edge_cookie", catObj.optString("edge-cookie", catObj.optString("edgeCookie", "")));
                        if (!catEdgeCookie.isEmpty()) {
                            prefs.setEdgeCookie(catEdgeCookie);
                            android.util.Log.d("ApiClient", "Captured edge-cookie from categories route: " + catEdgeCookie);
                        }

                        if (catObj.optString("status").equals("success") || catObj.has("categories")) {
                            JSONArray catArray = catObj.optJSONArray("categories");
                            if (catArray != null) {
                                boolean hasAllCategory = false;
                                for (int i = 0; i < catArray.length(); i++) {
                                    JSONObject item = catArray.getJSONObject(i);
                                    int cId = item.getInt("id");
                                    String cName = item.getString("name");
                                    String cIcon = item.optString("icon", "ic_tv");

                                    if ("all".equalsIgnoreCase(cName.trim()) || "all channels".equalsIgnoreCase(cName.trim())) {
                                        cName = "All";
                                        hasAllCategory = true;
                                    }

                                    catEntities.add(new CategoryEntity(cId, cName, cIcon));
                                }

                                if (!hasAllCategory && !catEntities.isEmpty()) {
                                    int specialAllId = 1;
                                    boolean id1Used = false;
                                    for (CategoryEntity c : catEntities) {
                                        if (c.id == 1) {
                                            id1Used = true;
                                            break;
                                        }
                                    }
                                    if (id1Used) specialAllId = 0;
                                    catEntities.add(0, new CategoryEntity(specialAllId, "All", "ic_tv"));
                                } else if (hasAllCategory) {
                                    for (int i = 0; i < catEntities.size(); i++) {
                                        if ("All".equalsIgnoreCase(catEntities.get(i).name)) {
                                            if (i != 0) {
                                                CategoryEntity allCat = catEntities.remove(i);
                                                catEntities.add(0, allCat);
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Fetch Channels with live session token, device_id, app_id, and app version parameters
                    String chanUrlStr = baseUrl + "channels?session_token=" + sessionToken
                            + "&device_id=" + encodedDev
                            + "&app_id=" + encodedApp
                            + "&app_version=" + encodedVer
                            + "&version_name=" + encodedVer
                            + "&version_code=" + versionCode;
                    String chanJsonStr = executeHttpGet(chanUrlStr);

                    if (chanJsonStr != null) {
                        JSONObject chanObj = new JSONObject(chanJsonStr);
                        // Capture edge-cookie from channels route
                        String chanEdgeCookie = chanObj.optString("edge_cookie", chanObj.optString("edge-cookie", chanObj.optString("edgeCookie", "")));
                        if (!chanEdgeCookie.isEmpty()) {
                            prefs.setEdgeCookie(chanEdgeCookie);
                            android.util.Log.d("ApiClient", "Captured edge-cookie from channels route: " + chanEdgeCookie);
                        }

                        if (chanObj.optString("status").equals("success") || chanObj.has("channels")) {
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
                        com.ottking.devcode.security.DatabaseKeyManager keyMgr = com.ottking.devcode.security.DatabaseKeyManager.getInstance(context);
                        java.util.List<ChannelEntity> encryptedChannels = new java.util.ArrayList<>();
                        for (ChannelEntity ch : chanEntities) {
                            encryptedChannels.add(new ChannelEntity(
                                    ch.id,
                                    ch.name,
                                    ch.logoUrl,
                                    keyMgr.encryptStreamUrl(ch.streamUrl),
                                    ch.categoryId,
                                    ch.isPremium,
                                    ch.streamType
                            ));
                        }
                        db.channelDao().deleteAll();
                        db.channelDao().insertAll(encryptedChannels);
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
                body.put("app_id", SecurityUtils.APP_ID);
                body.put("app_version", getAppVersionName(context));
                body.put("version_name", getAppVersionName(context));
                body.put("version_code", getAppVersionCode(context));

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "login", body.toString());
                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    String loginEdgeCookie = resObj.optString("edge_cookie", resObj.optString("edge-cookie", resObj.optString("edgeCookie", "")));
                    if (!loginEdgeCookie.isEmpty()) {
                        prefs.setEdgeCookie(loginEdgeCookie);
                        android.util.Log.d("ApiClient", "login: Captured edge-cookie: " + loginEdgeCookie);
                    }

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
                body.put("app_id", SecurityUtils.APP_ID);
                body.put("username", username);
                body.put("app_version", getAppVersionName(context));
                body.put("version_name", getAppVersionName(context));
                body.put("version_code", getAppVersionCode(context));

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "logout", body.toString());

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

    public void checkSession(ApiCallback<UserInfo> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();

                if (sessionToken == null || sessionToken.trim().isEmpty()) {
                    mainHandler.post(() -> callback.onError("No active session"));
                    return;
                }

                JSONObject body = new JSONObject();
                body.put("session_token", sessionToken);
                body.put("device_id", deviceId);
                body.put("app_id", SecurityUtils.APP_ID);
                body.put("app_version", getAppVersionName(context));
                body.put("version_name", getAppVersionName(context));
                body.put("version_code", getAppVersionCode(context));

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "check-session", body.toString());
                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    // Capture edge-cookie from check-session response body if present
                    String edgeCookie = resObj.optString("edge_cookie", resObj.optString("edge-cookie", resObj.optString("edgeCookie", "")));
                    if (edgeCookie.isEmpty()) {
                        JSONObject uObj = resObj.optJSONObject("user_info");
                        if (uObj != null) {
                            edgeCookie = uObj.optString("edge_cookie", uObj.optString("edge-cookie", uObj.optString("edgeCookie", "")));
                        }
                    }
                    if (!edgeCookie.isEmpty()) {
                        prefs.setEdgeCookie(edgeCookie);
                        android.util.Log.d("ApiClient", "checkSession: Captured edge-cookie: " + edgeCookie);
                    }

                    if ("success".equalsIgnoreCase(resObj.optString("status")) || resObj.optBoolean("valid", false)) {
                        JSONObject userObj = resObj.optJSONObject("user_info");
                        if (userObj != null) {
                            String username = userObj.optString("username", prefs.getUsername());
                            String pkg = userObj.optString("package", prefs.getUserPackage());
                            String expiry = userObj.optString("expiry_date", prefs.getUserExpiry());

                            prefs.setUsername(username);
                            prefs.setUserPackage(pkg);
                            prefs.setUserExpiry(expiry);

                            UserInfo info = new UserInfo(username, pkg, expiry, deviceId);
                            mainHandler.post(() -> callback.onSuccess(info));
                        } else {
                            UserInfo info = new UserInfo(prefs.getUsername(), prefs.getUserPackage(), prefs.getUserExpiry(), deviceId);
                            mainHandler.post(() -> callback.onSuccess(info));
                        }
                    } else {
                        String msg = resObj.optString("message", "Session expired or invalid");
                        mainHandler.post(() -> callback.onError(msg));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Server connection error during session check"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Session check error: " + e.getLocalizedMessage()));
            }
        });
    }

    public void fetchNotifications(ApiCallback<List<NotificationItem>> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();
                String versionName = getAppVersionName(context);
                String encodedVer = java.net.URLEncoder.encode(versionName, "UTF-8");
                int versionCode = getAppVersionCode(context);

                String urlStr = SecurityUtils.getApiUrl() + "notifications?session_token=" 
                        + java.net.URLEncoder.encode(sessionToken != null ? sessionToken : "", "UTF-8")
                        + "&device_id=" + java.net.URLEncoder.encode(deviceId != null ? deviceId : "", "UTF-8")
                        + "&app_version=" + encodedVer
                        + "&version_name=" + encodedVer
                        + "&version_code=" + versionCode;

                String responseStr = executeHttpGet(urlStr);
                if (responseStr != null) {
                    JSONObject obj = new JSONObject(responseStr);
                    // Capture edge-cookie if present in notifications response
                    String notifCookie = obj.optString("edge_cookie", obj.optString("edge-cookie", obj.optString("edgeCookie", "")));
                    if (!notifCookie.isEmpty()) {
                        prefs.setEdgeCookie(notifCookie);
                    }

                    List<NotificationItem> list = new ArrayList<>();
                    JSONArray array = obj.optJSONArray("notifications");
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject item = array.getJSONObject(i);
                            String id = item.optString("id", "notif_server_" + i);
                            String title = item.optString("title", "Notice");
                            String message = item.optString("message", "");
                            String time = item.optString("time", item.optString("timestamp", "Today"));
                            String type = item.optString("type", "SYSTEM");
                            String actionText = item.optString("action_text", "View");
                            boolean isRead = item.optBoolean("is_read", false);

                            int iconRes = com.ottking.devcode.R.drawable.ic_notifications;
                            if ("CHANNEL".equalsIgnoreCase(type)) {
                                iconRes = com.ottking.devcode.R.drawable.ic_tv;
                            } else if ("UPDATE".equalsIgnoreCase(type)) {
                                iconRes = com.ottking.devcode.R.drawable.ic_play;
                            }

                            list.add(new NotificationItem(id, title, message, time, iconRes, type, isRead, actionText));
                        }
                    }
                    mainHandler.post(() -> callback.onSuccess(list));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to fetch notifications from server"));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Notifications fetch error: " + e.getLocalizedMessage()));
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
                String urlStr = SecurityUtils.getApiUrl() + "update?version_code=" + currentVersionCode 
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
                body.put("device_id", prefs.getDeviceId());
                body.put("app_id", SecurityUtils.APP_ID);
                body.put("app_version", getAppVersionName(context));
                body.put("version_name", getAppVersionName(context));
                body.put("version_code", getAppVersionCode(context));

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

                String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "submit-reports", body.toString());
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
     * Requests authenticated stream token from server endpoint (stream-token).
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
                body.put("app_id", SecurityUtils.APP_ID);
                body.put("username", username);
                body.put("channel_id", channelId);
                body.put("stream_url", streamUrl != null ? streamUrl : "");
                body.put("app_client", appClient);
                body.put("app_version", getAppVersionName(context));
                body.put("version_name", getAppVersionName(context));
                body.put("version_code", getAppVersionCode(context));
                body.put("timestamp", System.currentTimeMillis() / 1000);

                String endpoint = SecurityUtils.getApiUrl() + "stream-token";
                String responseStr = executeHttpPost(endpoint, body.toString());

                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    if ("success".equalsIgnoreCase(resObj.optString("status"))) {
                        String streamToken = resObj.optString("stream_token", "");
                        long expiresAt = resObj.optLong("expires_at", System.currentTimeMillis() + 14400000);
                        String authStreamUrl = resObj.optString("authorized_stream_url", streamUrl);
                        String authHeader = resObj.optString("authorization_header", "Bearer " + streamToken);
                        String verifiedClient = resObj.optString("app_client", appClient);

                        String resEdgeCookie = resObj.optString("edge_cookie", resObj.optString("edge-cookie", resObj.optString("edgeCookie", prefs.getEdgeCookie())));
                        if (resEdgeCookie != null && !resEdgeCookie.isEmpty()) {
                            prefs.setEdgeCookie(resEdgeCookie);
                        }

                        StreamTokenAuth tokenAuth = new StreamTokenAuth(
                                streamToken,
                                verifiedClient,
                                expiresAt,
                                authStreamUrl,
                                authHeader,
                                true,
                                resObj.optString("message", "Token verified"),
                                resEdgeCookie
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
     * Mode 1 (App Launch / Startup):
     * সুধু মাত্র অ্যাপ চালু হওয়ার সময় যে কুকি টা সার্ভার দিবে সেটা আস্তে কোনো পে-লোড লাগবে না।
     * Fetches the initial bootstrap edge-cookie on app startup without sending any payload.
     */
    public void fetchInitialAppCookie(ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                String cookieEndpoint = SecurityUtils.getApiUrl() + "cookie";
                // Simple GET request with NO query parameters and NO payload body
                String responseStr = executeHttpGet(cookieEndpoint);

                AppPreferences prefs = AppPreferences.getInstance(context);
                String capturedCookie = (prefs != null) ? prefs.getEdgeCookie() : "";

                if (responseStr != null && !responseStr.isEmpty()) {
                    JSONObject resObj = new JSONObject(responseStr);
                    String cookieInRes = resObj.optString("cookie",
                            resObj.optString("new_cookie",
                            resObj.optString("edge_cookie",
                            resObj.optString("edge-cookie",
                            resObj.optString("token", "")))));

                    if (cookieInRes.isEmpty() && resObj.has("cookie_json") && resObj.get("cookie_json") instanceof JSONObject) {
                        JSONObject cJson = resObj.getJSONObject("cookie_json");
                        cookieInRes = cJson.optString("edge_cookie", cJson.optString("cookie", ""));
                    }

                    if (!cookieInRes.isEmpty()) {
                        capturedCookie = cookieInRes;
                        if (prefs != null) {
                            prefs.setEdgeCookie(cookieInRes);
                        }
                        android.util.Log.d("ApiClient", "Initial app-launch cookie received (no payload): " + cookieInRes);
                    }
                }

                final String resultCookie = (capturedCookie != null) ? capturedCookie : "";
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(resultCookie));
                }
            } catch (Exception e) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String cached = (prefs != null) ? prefs.getEdgeCookie() : "";
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(cached));
                }
            }
        });
    }

    /**
     * Mode 2 (Runtime Playback & Renewal):
     * রান টাইমে সেই লাষ্ট কুকি সার্ভার এ পেলোডে পাঠালে যাচাই করে নিউ কুকি ইস্যু করে দেবে।
     * Sends the last saved cookie in the payload (JSON and headers).
     * The server verifies the last cookie and issues a brand new cookie.
     */
    public void fetchRuntimeNewCookie(int channelId, String streamUrl, ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = prefs.getSessionToken();
                String deviceId = prefs.getDeviceId();
                String username = prefs.getUsername().isEmpty() ? "GuestUser" : prefs.getUsername();
                String appClient = SecurityUtils.getXAppClientToken();
                String versionName = getAppVersionName(context);
                int versionCode = getAppVersionCode(context);
                String lastCookie = prefs.getEdgeCookie();

                JSONObject body = new JSONObject();
                body.put("session_token", sessionToken);
                body.put("device_id", deviceId);
                body.put("username", username);
                body.put("channel_id", channelId);
                body.put("stream_url", streamUrl != null ? streamUrl : "");
                body.put("app_client", appClient);
                body.put("app_version", versionName);
                body.put("version_name", versionName);
                body.put("version_code", versionCode);
                body.put("timestamp", System.currentTimeMillis() / 1000);

                // Pass the LAST cookie in JSON payload so server can verify it
                if (lastCookie != null && !lastCookie.isEmpty()) {
                    body.put("last_cookie", lastCookie);
                    body.put("edge_cookie", lastCookie);
                    body.put("cookie", lastCookie);

                    JSONObject cookieJsonObj = new JSONObject();
                    cookieJsonObj.put("last_cookie", lastCookie);
                    cookieJsonObj.put("edge_cookie", lastCookie);
                    cookieJsonObj.put("cookie", lastCookie);
                    cookieJsonObj.put("channel_id", channelId);
                    cookieJsonObj.put("device_id", deviceId);
                    cookieJsonObj.put("timestamp", System.currentTimeMillis());
                    body.put("cookie_json", cookieJsonObj);
                }

                // 1. Primary: Dedicated realtime "cookie" route (POST)
                String cookieEndpoint = SecurityUtils.getApiUrl() + "cookie";
                String responseStr = executeHttpPost(cookieEndpoint, body.toString());

                // 2. Secondary fallback: Dedicated realtime "cookie" route (GET with last_cookie query)
                if (responseStr == null) {
                    try {
                        String getCookieUrl = cookieEndpoint + "?channel_id=" + channelId
                                + "&device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8")
                                + "&session_token=" + java.net.URLEncoder.encode(sessionToken != null ? sessionToken : "", "UTF-8")
                                + "&app_version=" + java.net.URLEncoder.encode(versionName, "UTF-8");
                        if (lastCookie != null && !lastCookie.isEmpty()) {
                            getCookieUrl += "&last_cookie=" + java.net.URLEncoder.encode(lastCookie, "UTF-8");
                            getCookieUrl += "&edge_cookie=" + java.net.URLEncoder.encode(lastCookie, "UTF-8");
                        }
                        responseStr = executeHttpGet(getCookieUrl);
                    } catch (Exception ignored) {}
                }

                // 3. Fallback to stream-token route if /cookie route is not yet active on server
                if (responseStr == null) {
                    String fallbackEndpoint = SecurityUtils.getApiUrl() + "stream-token";
                    responseStr = executeHttpPost(fallbackEndpoint, body.toString());
                }

                String capturedCookie = prefs.getEdgeCookie();
                if (responseStr != null) {
                    JSONObject resObj = new JSONObject(responseStr);
                    // Extract new issued cookie from server
                    String newCookie = resObj.optString("new_cookie",
                            resObj.optString("cookie",
                            resObj.optString("edge_cookie",
                            resObj.optString("edge-cookie",
                            resObj.optString("edgeCookie",
                            resObj.optString("token",
                            resObj.optString("data", "")))))));

                    if (newCookie.isEmpty() && resObj.has("cookie_json") && resObj.get("cookie_json") instanceof JSONObject) {
                        JSONObject cJson = resObj.getJSONObject("cookie_json");
                        newCookie = cJson.optString("new_cookie", cJson.optString("edge_cookie", cJson.optString("cookie", "")));
                    }

                    if (newCookie.isEmpty() && resObj.has("data") && resObj.get("data") instanceof JSONObject) {
                        JSONObject dataObj = resObj.getJSONObject("data");
                        newCookie = dataObj.optString("cookie", dataObj.optString("edge_cookie", dataObj.optString("edge-cookie", "")));
                    }

                    if (!newCookie.isEmpty()) {
                        capturedCookie = newCookie;
                        prefs.setEdgeCookie(newCookie);
                        android.util.Log.d("ApiClient", "Verified last cookie. New runtime cookie issued: " + newCookie);
                    }
                }

                final String finalCookie = (capturedCookie != null) ? capturedCookie : "";
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(finalCookie));
                }
            } catch (Exception e) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String cachedCookie = (prefs != null) ? prefs.getEdgeCookie() : "";
                if (callback != null) {
                    mainHandler.post(() -> callback.onSuccess(cachedCookie));
                }
            }
        });
    }

    /**
     * Dedicated Realtime "cookie" route (alias for fetchRuntimeNewCookie).
     */
    public void getCookieRoute(int channelId, String streamUrl, ApiCallback<String> callback) {
        fetchRuntimeNewCookie(channelId, streamUrl, callback);
    }

    /**
     * Alias for getCookieRoute
     */
    public void fetchRealtimeCookie(int channelId, String streamUrl, ApiCallback<String> callback) {
        getCookieRoute(channelId, streamUrl, callback);
    }

    /**
     * Refreshes edge-cookie periodically (every 25s) from channel / check-session route as requested by user.
     * Extracts and saves the fresh edge-cookie in preferences and returns it to callback.
     */
    public void refreshEdgeCookie(int channelId, String streamUrl, ApiCallback<String> callback) {
        refreshEdgeCookieFromSessionOrChannels(channelId, streamUrl, callback);
    }

    public void refreshEdgeCookieFromSessionOrChannels(int channelId, String streamUrl, ApiCallback<String> callback) {
        executor.execute(() -> {
            try {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String sessionToken = (prefs != null) ? prefs.getSessionToken() : "";
                String deviceId = (prefs != null) ? prefs.getDeviceId() : "";
                String appId = SecurityUtils.APP_ID;

                String newCookie = null;

                // Priority 1: Request check-session route (auto-issues fresh edge-cookie)
                try {
                    JSONObject body = new JSONObject();
                    body.put("session_token", sessionToken != null ? sessionToken : "");
                    body.put("device_id", deviceId);
                    body.put("app_id", appId);
                    body.put("channel_id", channelId);
                    body.put("app_version", getAppVersionName(context));
                    body.put("version_name", getAppVersionName(context));
                    body.put("version_code", getAppVersionCode(context));

                    String responseStr = executeHttpPost(SecurityUtils.getApiUrl() + "check-session", body.toString());
                    if (responseStr != null && !responseStr.isEmpty()) {
                        JSONObject resObj = new JSONObject(responseStr);
                        newCookie = resObj.optString("edge_cookie", resObj.optString("edge-cookie", resObj.optString("edgeCookie", "")));
                        if (newCookie.isEmpty()) {
                            JSONObject uObj = resObj.optJSONObject("user_info");
                            if (uObj != null) {
                                newCookie = uObj.optString("edge_cookie", uObj.optString("edge-cookie", uObj.optString("edgeCookie", "")));
                            }
                        }
                    }
                } catch (Exception checkEx) {
                    android.util.Log.w("ApiClient", "check-session cookie refresh: " + checkEx.getMessage());
                }

                // Priority 2: Fallback to channels route if check-session did not return cookie
                if (newCookie == null || newCookie.isEmpty()) {
                    try {
                        String encodedVer = java.net.URLEncoder.encode(getAppVersionName(context), "UTF-8");
                        String encodedDev = java.net.URLEncoder.encode(deviceId != null ? deviceId : "", "UTF-8");
                        String encodedApp = java.net.URLEncoder.encode(appId, "UTF-8");
                        String encodedToken = java.net.URLEncoder.encode(sessionToken != null ? sessionToken : "", "UTF-8");

                        String chanUrl = SecurityUtils.getApiUrl() + "channels?session_token=" + encodedToken
                                + "&device_id=" + encodedDev
                                + "&app_id=" + encodedApp
                                + "&app_version=" + encodedVer
                                + "&version_name=" + encodedVer
                                + "&version_code=" + getAppVersionCode(context);

                        String chanRes = executeHttpGet(chanUrl);
                        if (chanRes != null && !chanRes.isEmpty()) {
                            JSONObject chanObj = new JSONObject(chanRes);
                            newCookie = chanObj.optString("edge_cookie", chanObj.optString("edge-cookie", chanObj.optString("edgeCookie", "")));
                        }
                    } catch (Exception chanEx) {
                        android.util.Log.w("ApiClient", "channels cookie refresh: " + chanEx.getMessage());
                    }
                }

                // Priority 3: Fallback to getCookieRoute if needed
                if ((newCookie == null || newCookie.isEmpty()) && (channelId > 0 || (streamUrl != null && !streamUrl.isEmpty()))) {
                    getCookieRoute(channelId, streamUrl, callback);
                    return;
                }

                if (newCookie != null && !newCookie.isEmpty()) {
                    if (prefs != null) {
                        prefs.setEdgeCookie(newCookie);
                    }
                    final String finalCookie = newCookie;
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(finalCookie));
                    }
                } else {
                    String current = (prefs != null) ? prefs.getEdgeCookie() : "";
                    if (current != null && !current.isEmpty()) {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onSuccess(current));
                        }
                    } else {
                        if (callback != null) {
                            mainHandler.post(() -> callback.onError("Failed to refresh edge-cookie from session/channels"));
                        }
                    }
                }
            } catch (Exception e) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                String current = (prefs != null) ? prefs.getEdgeCookie() : "";
                if (current != null && !current.isEmpty()) {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onSuccess(current));
                    }
                } else {
                    if (callback != null) {
                        mainHandler.post(() -> callback.onError(e.getMessage()));
                    }
                }
            }
        });
    }

    public StreamTokenAuth generateLocalStreamToken(int channelId, String streamUrl) {
        AppPreferences prefs = AppPreferences.getInstance(context);
        String sessionToken = prefs.getSessionToken();
        String deviceId = prefs.getDeviceId();
        String appClient = SecurityUtils.getXAppClientToken();
        String edgeCookie = prefs.getEdgeCookie();
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
                "Local verified stream ticket",
                edgeCookie
        );
    }

    public String getCapturedEdgeCookie() {
        AppPreferences prefs = AppPreferences.getInstance(context);
        return prefs != null ? prefs.getEdgeCookie() : "";
    }

    private void captureEdgeCookieFromConnection(HttpURLConnection conn) {
        if (conn == null) return;
        try {
            String captured = null;
            java.util.Map<String, java.util.List<String>> headers = conn.getHeaderFields();
            if (headers != null) {
                // 1. Direct Edge-Cookie headers (case-insensitive over all response headers)
                for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                    String key = entry.getKey();
                    if (key != null && (key.equalsIgnoreCase("Edge-Cookie") 
                            || key.equalsIgnoreCase("edge-cookie") 
                            || key.equalsIgnoreCase("X-Edge-Cookie")
                            || key.equalsIgnoreCase("Edge_Cookie")
                            || key.equalsIgnoreCase("edge_cookie"))) {
                        java.util.List<String> values = entry.getValue();
                        if (values != null && !values.isEmpty() && values.get(0) != null && !values.get(0).trim().isEmpty()) {
                            captured = values.get(0).trim();
                            break;
                        }
                    }
                }

                // 2. Set-Cookie header inspection
                if (captured == null || captured.isEmpty()) {
                    for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.entrySet()) {
                        String key = entry.getKey();
                        if (key != null && key.equalsIgnoreCase("Set-Cookie")) {
                            java.util.List<String> cookies = entry.getValue();
                            if (cookies != null) {
                                for (String cookie : cookies) {
                                    if (cookie == null) continue;
                                    String lower = cookie.toLowerCase(Locale.US);
                                    if (lower.contains("edge_cookie=")) {
                                        int start = lower.indexOf("edge_cookie=") + "edge_cookie=".length();
                                        int end = cookie.indexOf(';', start);
                                        captured = (end != -1) ? cookie.substring(start, end).trim() : cookie.substring(start).trim();
                                        break;
                                    } else if (lower.contains("edge-cookie=")) {
                                        int start = lower.indexOf("edge-cookie=") + "edge-cookie=".length();
                                        int end = cookie.indexOf(';', start);
                                        captured = (end != -1) ? cookie.substring(start, end).trim() : cookie.substring(start).trim();
                                        break;
                                    }
                                }
                            }
                        }
                        if (captured != null && !captured.isEmpty()) break;
                    }
                }
            }

            if (captured != null && !captured.isEmpty()) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                if (prefs != null) {
                    prefs.setEdgeCookie(captured);
                    android.util.Log.d("ApiClient", "Captured backend Edge-Cookie header: " + captured);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("ApiClient", "Error capturing edge-cookie header", e);
        }
    }

    private String executeHttpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("X-Api-Key", SecurityUtils.getApiKey());
        conn.setRequestProperty("X-App-Client", SecurityUtils.getXAppClientToken());

        // Mandatory App ID headers on all GET requests
        String appId = SecurityUtils.APP_ID;
        conn.setRequestProperty("X-App-Id", appId);
        conn.setRequestProperty("app_id", appId);
        conn.setRequestProperty("App-Id", appId);
        conn.setRequestProperty("X-Package-Name", appId);
        conn.setRequestProperty("package_name", appId);

        // Inject App Version headers to every GET route
        String versionName = getAppVersionName(context);
        String versionCode = String.valueOf(getAppVersionCode(context));
        conn.setRequestProperty("X-App-Version", versionName);
        conn.setRequestProperty("App-Version", versionName);
        conn.setRequestProperty("X-Version-Name", versionName);
        conn.setRequestProperty("X-Version-Code", versionCode);
        conn.setRequestProperty("Version-Code", versionCode);
        conn.setRequestProperty("User-Agent", "OTT-KING TV/" + versionName);

        AppPreferences prefs = AppPreferences.getInstance(context);
        if (prefs != null) {
            String token = prefs.getSessionToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("X-Session-Token", token);
                conn.setRequestProperty("Session-Token", token);
            }
            String devId = prefs.getDeviceId();
            if (devId != null && !devId.isEmpty()) {
                conn.setRequestProperty("X-Device-Id", devId);
                conn.setRequestProperty("device_id", devId);
                conn.setRequestProperty("Device-Id", devId);
            }
            String edgeCookie = prefs.getEdgeCookie();
            if (edgeCookie != null && !edgeCookie.isEmpty()) {
                conn.setRequestProperty("Edge-Cookie", edgeCookie);
                conn.setRequestProperty("edge-cookie", edgeCookie);
                conn.setRequestProperty("X-Edge-Cookie", edgeCookie);
                conn.setRequestProperty("Cookie", "edge_cookie=" + edgeCookie + "; edge-cookie=" + edgeCookie);
                try {
                    JSONObject cJson = new JSONObject();
                    cJson.put("edge_cookie", edgeCookie);
                    cJson.put("cookie", edgeCookie);
                    conn.setRequestProperty("X-Cookie-Json", cJson.toString());
                    conn.setRequestProperty("X-Edge-Cookie-Json", cJson.toString());
                } catch (Exception ignored) {}
            }
        }

        int code = conn.getResponseCode();
        captureEdgeCookieFromConnection(conn);
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
        conn.setRequestProperty("X-App-Client", SecurityUtils.getXAppClientToken());

        // Mandatory App ID headers on all POST requests
        String appId = SecurityUtils.APP_ID;
        conn.setRequestProperty("X-App-Id", appId);
        conn.setRequestProperty("app_id", appId);
        conn.setRequestProperty("App-Id", appId);
        conn.setRequestProperty("X-Package-Name", appId);
        conn.setRequestProperty("package_name", appId);

        // Inject App Version headers to every POST route
        String versionName = getAppVersionName(context);
        String versionCode = String.valueOf(getAppVersionCode(context));
        conn.setRequestProperty("X-App-Version", versionName);
        conn.setRequestProperty("App-Version", versionName);
        conn.setRequestProperty("X-Version-Name", versionName);
        conn.setRequestProperty("X-Version-Code", versionCode);
        conn.setRequestProperty("Version-Code", versionCode);
        conn.setRequestProperty("User-Agent", "OTT-KING TV/" + versionName);

        AppPreferences prefs = AppPreferences.getInstance(context);
        String devId = "";
        if (prefs != null) {
            String token = prefs.getSessionToken();
            if (token != null && !token.isEmpty()) {
                conn.setRequestProperty("X-Session-Token", token);
                conn.setRequestProperty("Session-Token", token);
            }
            devId = prefs.getDeviceId();
            if (devId != null && !devId.isEmpty()) {
                conn.setRequestProperty("X-Device-Id", devId);
                conn.setRequestProperty("device_id", devId);
                conn.setRequestProperty("Device-Id", devId);
            }
            String edgeCookie = prefs.getEdgeCookie();
            if (edgeCookie != null && !edgeCookie.isEmpty()) {
                conn.setRequestProperty("Edge-Cookie", edgeCookie);
                conn.setRequestProperty("edge-cookie", edgeCookie);
                conn.setRequestProperty("X-Edge-Cookie", edgeCookie);
                conn.setRequestProperty("Cookie", "edge_cookie=" + edgeCookie + "; edge-cookie=" + edgeCookie);
                try {
                    JSONObject cJson = new JSONObject();
                    cJson.put("edge_cookie", edgeCookie);
                    cJson.put("cookie", edgeCookie);
                    conn.setRequestProperty("X-Cookie-Json", cJson.toString());
                    conn.setRequestProperty("X-Edge-Cookie-Json", cJson.toString());
                } catch (Exception ignored) {}
            }
        }

        // Auto-inject device_id and app_id into JSON body if missing
        try {
            JSONObject bodyJson = new JSONObject(jsonBody);
            if (!bodyJson.has("device_id") && devId != null && !devId.isEmpty()) {
                bodyJson.put("device_id", devId);
            }
            if (!bodyJson.has("app_id")) {
                bodyJson.put("app_id", appId);
            }
            jsonBody = bodyJson.toString();
        } catch (Exception ignored) {}

        JSONObject requestObj = new JSONObject();
        String encryptedPayload = SecurityUtils.encryptAesGcm(jsonBody, SecurityUtils.getEncryptionKey());
        String signature = SecurityUtils.generateHmac(jsonBody, SecurityUtils.getHmacKey());
        requestObj.put("encrypted_payload", encryptedPayload);
        requestObj.put("signature", signature);

        String postBody = requestObj.toString();
        conn.setRequestProperty("X-Signature", signature);
        conn.setRequestProperty("X-HMAC-Signature", signature);

        OutputStream os = conn.getOutputStream();
        os.write(postBody.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();

        int code = conn.getResponseCode();
        captureEdgeCookieFromConnection(conn);
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

                    // Extract edge_cookie from decrypted payload if returned
                    try {
                        JSONObject decObj = new JSONObject(decrypted);
                        String edgeCookie = decObj.optString("edge_cookie", decObj.optString("edge-cookie", decObj.optString("edgeCookie", decObj.optString("Edge-Cookie", ""))));
                        if (!edgeCookie.isEmpty()) {
                            AppPreferences prefs = AppPreferences.getInstance(context);
                            if (prefs != null) {
                                prefs.setEdgeCookie(edgeCookie);
                                android.util.Log.d("ApiClient", "Extracted edge_cookie from decrypted response: " + edgeCookie);
                            }
                        }
                    } catch (Exception ignored) {}

                    return decrypted;
                } else {
                    android.util.Log.e("ApiClient", "Failed to decrypt response payload!");
                    return null;
                }
            } else {
                // Response is plaintext JSON - check for edge_cookie
                try {
                    String edgeCookie = json.optString("edge_cookie", json.optString("edge-cookie", json.optString("edgeCookie", json.optString("Edge-Cookie", ""))));
                    if (!edgeCookie.isEmpty()) {
                        AppPreferences prefs = AppPreferences.getInstance(context);
                        if (prefs != null) {
                            prefs.setEdgeCookie(edgeCookie);
                            android.util.Log.d("ApiClient", "Extracted edge_cookie from plaintext response: " + edgeCookie);
                        }
                    }
                } catch (Exception ignored) {}

                return rawResponse;
            }
        } catch (Exception e) {
            android.util.Log.e("ApiClient", "Invalid response or parsing exception", e);
            return null;
        }
    }
}
