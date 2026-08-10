package com.ottking.mobile.devcode.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ottking.mobile.devcode.config.Config;
import com.ottking.mobile.devcode.database.ChannelDao;
import com.ottking.mobile.devcode.database.ChannelEntity;
import com.ottking.mobile.devcode.model.AppUpdateInfo;
import com.ottking.mobile.devcode.network.IptvApiService;
import com.ottking.mobile.devcode.network.RetrofitClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;

public class ServerApiManager {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static AppUpdateInfo latestAppUpdateInfo = null;

    public interface SyncCallback {
        void onSuccess(int channelsSyncedCount);
        void onError(String errorMessage);
    }

    public interface AuthCallback {
        void onSuccess(String username, String subscriptionStatus, long expiryTimestamp, String expiryFormatted, String planName);
        void onError(String errorMessage);
    }

    public interface NotificationSyncCallback {
        void onSuccess(List<com.ottking.mobile.devcode.model.NotificationModel> notifications, int newCount);
        void onError(String errorMessage);
    }

    public static void purgeExpiredPremiumChannels(ChannelDao channelDao) {
        if (channelDao == null) return;
        executor.execute(() -> {
            try {
                List<ChannelEntity> all = channelDao.getAllChannels();
                if (all != null) {
                    for (ChannelEntity ch : all) {
                        String sub = ch.getSubCategory() != null ? ch.getSubCategory().toLowerCase() : "";
                        String title = ch.getTitle() != null ? ch.getTitle().toLowerCase() : "";
                        if (sub.contains("vip") || sub.contains("premium") || title.contains("[vip]") || title.contains("[premium]")) {
                            channelDao.deleteBySubCategory(ch.getSubCategory());
                        }
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public interface SessionCallback {
        void onValid(String user, String subscriptionStatus, long expiryTimestamp, String expiryFormatted, String planName);
        void onInvalid(String reason);
    }

    public static void loginUser(Context context, String username, String password, AuthCallback callback) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            mainHandler.post(() -> {
                if (callback != null) callback.onError("Username and password are required.");
            });
            return;
        }

        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);
                String deviceId = PreferenceUtils.getDeviceId(context);
                final String trimmedUser = username.trim();
                final String trimmedPass = password.trim();

                String targetUrl = apiUrl;
                if (targetUrl.contains("?")) {
                    targetUrl += "&route=" + Config.ROUTE_LOGIN;
                } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                    targetUrl += "?route=" + Config.ROUTE_LOGIN;
                } else {
                    targetUrl += "/" + Config.ROUTE_LOGIN;
                }

                String bindParam = "username=" + java.net.URLEncoder.encode(trimmedUser, "UTF-8")
                        + "&password=" + java.net.URLEncoder.encode(trimmedPass, "UTF-8")
                        + "&device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8");
                if (targetUrl.contains("?")) {
                    targetUrl += "&" + bindParam;
                } else {
                    targetUrl += "?" + bindParam;
                }

                URL urlObj = new URL(targetUrl);
                String path = urlObj.getPath();
                if (path == null || path.isEmpty()) path = "/index.php";
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) path += "?" + urlObj.getQuery();

                long timestamp = System.currentTimeMillis();
                String hmacData = timestamp + path;
                String signature = CryptoUtils.generateHmacSha256(hmacData, hmacKey);

                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), signature);
                Response<ResponseBody> retrofitResponse = call.execute();

                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String responseString = retrofitResponse.body().string();
                    JSONObject json;
                    try {
                        json = new JSONObject(responseString);
                    } catch (Exception parseEx) {
                        PreferenceUtils.logout(context);
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError("Invalid server response format: Not valid JSON.");
                        });
                        return;
                    }

                    String status = json.optString("status", "error");

                    if ("success".equalsIgnoreCase(status)) {
                        String user = json.optString("username", trimmedUser);
                        String email = json.optString("email", user.contains("@") ? user : user.toLowerCase() + "@iptvstream.com");
                        String subStatus = json.optString("subscription_status", "ACTIVE");
                        String planName = json.optString("plan", json.optString("plan_name", "VIP Ultra Premium"));
                        String expiryDateStr = json.optString("expiry_date", "");
                        long expiryTs = json.optLong("expiry_timestamp", 0L);

                        if (expiryTs == 0L && !expiryDateStr.isEmpty()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                                java.util.Date date = sdf.parse(expiryDateStr);
                                if (date != null) {
                                    expiryTs = date.getTime();
                                }
                            } catch (Exception ignored) {
                                try {
                                    java.text.SimpleDateFormat sdf2 = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                                    java.util.Date date2 = sdf2.parse(expiryDateStr);
                                    if (date2 != null) expiryTs = date2.getTime();
                                } catch (Exception ignored2) {}
                            }
                        }

                        if (expiryTs == 0L) {
                            expiryTs = System.currentTimeMillis() + (30L * 24L * 60L * 60L * 1000L);
                        }

                        boolean isActive = "ACTIVE".equalsIgnoreCase(subStatus) && (expiryTs > System.currentTimeMillis());
                        long finalExpiryTs = expiryTs;

                        // Strict: Only set login state when server returned success
                        PreferenceUtils.setLoginState(context, true, user, email, apiUrl, finalExpiryTs, planName);

                        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                        String formattedExpiry = fmt.format(new java.util.Date(finalExpiryTs));

                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onSuccess(user, isActive ? "ACTIVE" : "EXPIRED", finalExpiryTs, formattedExpiry, planName);
                            }
                        });
                        return;
                    } else {
                        String msg = json.optString("message", "Invalid credentials. Access denied by server.");
                        PreferenceUtils.logout(context);
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(msg);
                        });
                        return;
                    }
                } else {
                    int code = retrofitResponse.code();
                    String errorMsg = "Authentication failed (Server HTTP " + code + ")";
                    if (retrofitResponse.errorBody() != null) {
                        try {
                            String errStr = retrofitResponse.errorBody().string();
                            JSONObject errJson = new JSONObject(errStr);
                            if (errJson.has("message")) {
                                errorMsg = errJson.getString("message");
                            }
                        } catch (Exception ignored) {}
                    }
                    PreferenceUtils.logout(context);
                    final String finalErrMsg = errorMsg;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(finalErrMsg);
                    });
                }
            } catch (Exception e) {
                PreferenceUtils.logout(context);
                String msg = e.getMessage();
                if (msg == null || msg.isEmpty()) msg = "Unable to connect to authentication server";
                final String finalErr = "Server Connection Failed: " + msg;
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(finalErr);
                });
            }
        });
    }

    public static void validateUserSession(Context context, SessionCallback callback) {
        if (!PreferenceUtils.isLoggedIn(context)) {
            if (callback != null) callback.onInvalid("Not logged in");
            return;
        }

        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);
                String deviceId = PreferenceUtils.getDeviceId(context);
                String username = PreferenceUtils.getUserEmail(context);
                if (username == null || username.isEmpty() || username.equals("guest@streamtv.com")) {
                    username = PreferenceUtils.getUserName(context);
                }

                String targetUrl = apiUrl;
                if (targetUrl.contains("?")) {
                    targetUrl += "&route=" + Config.ROUTE_SESSION_CHECK;
                } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                    targetUrl += "?route=" + Config.ROUTE_SESSION_CHECK;
                } else {
                    targetUrl += "/" + Config.ROUTE_SESSION_CHECK;
                }

                String bindParam = "username=" + java.net.URLEncoder.encode(username, "UTF-8")
                        + "&device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8");
                if (targetUrl.contains("?")) {
                    targetUrl += "&" + bindParam;
                } else {
                    targetUrl += "?" + bindParam;
                }

                URL urlObj = new URL(targetUrl);
                String path = urlObj.getPath();
                if (path == null || path.isEmpty()) path = "/index.php";
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) path += "?" + urlObj.getQuery();

                long timestamp = System.currentTimeMillis();
                String hmacData = timestamp + path;
                String signature = CryptoUtils.generateHmacSha256(hmacData, hmacKey);

                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), signature);
                Response<ResponseBody> retrofitResponse = call.execute();

                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String responseString = retrofitResponse.body().string();
                    JSONObject json = new JSONObject(responseString);
                    String status = json.optString("status", "");

                    if ("success".equalsIgnoreCase(status)) {
                        String user = json.optString("username", username);
                        String email = json.optString("email", user.contains("@") ? user : user.toLowerCase() + "@iptvstream.com");
                        String subStatus = json.optString("subscription_status", "ACTIVE");
                        String planName = json.optString("plan", json.optString("plan_name", "VIP Ultra Premium"));
                        String expiryDateStr = json.optString("expiry_date", "");
                        long expiryTs = json.optLong("expiry_timestamp", 0L);

                        if (expiryTs == 0L && !expiryDateStr.isEmpty()) {
                            try {
                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US);
                                java.util.Date date = sdf.parse(expiryDateStr);
                                if (date != null) expiryTs = date.getTime();
                            } catch (Exception ignored) {}
                        }

                        if (expiryTs == 0L) {
                            expiryTs = PreferenceUtils.getSubscriptionExpiry(context);
                        }

                        boolean isActive = "ACTIVE".equalsIgnoreCase(subStatus) && (expiryTs > System.currentTimeMillis());
                        long finalExpiryTs = expiryTs;
                        PreferenceUtils.setLoginState(context, true, user, email, apiUrl, finalExpiryTs, planName);

                        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                        String formattedExpiry = fmt.format(new java.util.Date(finalExpiryTs));

                        mainHandler.post(() -> {
                            if (callback != null) {
                                callback.onValid(user, isActive ? "ACTIVE" : "EXPIRED", finalExpiryTs, formattedExpiry, planName);
                            }
                        });
                        return;
                    }
                }

                if (retrofitResponse.code() == 401 || retrofitResponse.code() == 403) {
                    PreferenceUtils.logout(context);
                    mainHandler.post(() -> {
                        if (callback != null) callback.onInvalid("Session expired on server");
                    });
                    return;
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onInvalid(e.getMessage());
                });
            }
        });
    }

    public static void syncAllContentFromServer(Context context, ChannelDao channelDao, SyncCallback callback) {
        executor.execute(() -> {
            try {
                // 1. Sync Live TV channels
                syncChannelsFromRoute(context, channelDao, Config.ROUTE_LIVE_TV, new SyncCallback() {
                    @Override
                    public void onSuccess(int tvCount) {
                        // 2. Sync Movies
                        syncChannelsFromRoute(context, channelDao, Config.ROUTE_MOVIES, new SyncCallback() {
                            @Override
                            public void onSuccess(int moviesCount) {
                                // 3. Sync Categories
                                syncCategoriesFromServer(context, null);
                                // 4. Sync Notifications
                                syncNotificationsFromServer(context, null);

                                mainHandler.post(() -> {
                                    if (callback != null) callback.onSuccess(tvCount + moviesCount);
                                });
                            }

                            @Override
                            public void onError(String errorMessage) {
                                mainHandler.post(() -> {
                                    if (callback != null) callback.onSuccess(tvCount);
                                });
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(errorMessage);
                        });
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public static void syncNotificationsFromServer(Context context, NotificationSyncCallback callback) {
        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);

                String targetUrl = apiUrl;
                if (targetUrl.contains("?")) {
                    targetUrl += "&route=notifications";
                } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                    targetUrl += "?route=notifications";
                } else {
                    targetUrl += "/notifications";
                }

                URL urlObj = new URL(targetUrl);
                String path = urlObj.getPath();
                if (path == null || path.isEmpty()) path = "/index.php";
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) path += "?" + urlObj.getQuery();

                long timestamp = System.currentTimeMillis();
                String hmacData = timestamp + path;
                String signature = CryptoUtils.generateHmacSha256(hmacData, hmacKey);

                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), signature);
                Response<ResponseBody> retrofitResponse = call.execute();

                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String body = retrofitResponse.body().string();
                    JSONObject json = null;
                    JSONArray arr = null;
                    if (body.trim().startsWith("[")) {
                        arr = new JSONArray(body);
                    } else {
                        json = new JSONObject(body);
                        arr = json.optJSONArray("notifications");
                    }

                    List<com.ottking.mobile.devcode.model.NotificationModel> list = new ArrayList<>();
                    int newItemsCount = 0;

                    if (arr != null) {
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String id = obj.optString("id", "notif_" + i);
                            String title = obj.optString("title", "Announcement");
                            String message = obj.optString("message", "");
                            String time = obj.optString("timestamp", obj.optString("date", "Today"));
                            String iconType = obj.optString("icon", "bell");

                            int iconRes = com.ottking.mobile.devcode.R.drawable.ic_notification;
                            if ("movie".equalsIgnoreCase(iconType) || "movies".equalsIgnoreCase(iconType)) {
                                iconRes = com.ottking.mobile.devcode.R.drawable.ic_movie;
                            } else if ("sports".equalsIgnoreCase(iconType) || "tv".equalsIgnoreCase(iconType)) {
                                iconRes = com.ottking.mobile.devcode.R.drawable.ic_tv;
                            }

                            // Trigger Android system notification for new items
                            NotificationHelper.showNotificationIfNew(context, id, title, message);

                            list.add(new com.ottking.mobile.devcode.model.NotificationModel(id, title, message, time, iconRes, false));
                            newItemsCount++;
                        }
                    }

                    final int finalNewCount = newItemsCount;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(list, finalNewCount);
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Notifications returned HTTP " + retrofitResponse.code());
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    public interface AppUpdateCallback {
        void onUpdateInfoReceived(AppUpdateInfo updateInfo, boolean isUpdateAvailable);
        void onError(String errorMessage);
    }

    public static AppUpdateInfo getLatestAppUpdateInfo() {
        return latestAppUpdateInfo;
    }

    public static void syncChannelsFromServer(Context context, ChannelDao channelDao, SyncCallback callback) {
        syncChannelsFromRoute(context, channelDao, Config.ROUTE_LIVE_TV, callback);
    }

    public static void syncChannelsFromRoute(Context context, ChannelDao channelDao, String routeName, SyncCallback callback) {
        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);
                String encKey = PreferenceUtils.getEncryptionKey(context);

                String userEmail = PreferenceUtils.getUserEmail(context);
                String deviceId = PreferenceUtils.getDeviceId(context);
                boolean subValid = PreferenceUtils.isSubscriptionValid(context);

                String targetUrl = apiUrl;
                if (routeName != null && !routeName.isEmpty()) {
                    if (targetUrl.contains("?")) {
                        targetUrl += "&route=" + routeName;
                    } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                        targetUrl += "?route=" + routeName;
                    } else {
                        targetUrl += "/" + routeName;
                    }
                }
                String bindParam = "user_id=" + java.net.URLEncoder.encode(userEmail, "UTF-8")
                        + "&device_id=" + java.net.URLEncoder.encode(deviceId, "UTF-8")
                        + "&sub_active=" + (subValid ? "1" : "0");
                if (targetUrl.contains("?")) {
                    targetUrl += "&" + bindParam;
                } else {
                    targetUrl += "?" + bindParam;
                }

                URL urlObj = new URL(targetUrl);
                String path = urlObj.getPath();
                if (path == null || path.isEmpty()) {
                    path = "/index.php";
                }
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) {
                    path += "?" + urlObj.getQuery();
                }

                long timestamp = System.currentTimeMillis();
                String hmacData = timestamp + path;
                String signature = CryptoUtils.generateHmacSha256(hmacData, hmacKey);

                // Initialize Retrofit API Service
                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), signature);

                Response<ResponseBody> retrofitResponse = call.execute();

                if (!retrofitResponse.isSuccessful() || retrofitResponse.code() == 503) {
                    String errBody = retrofitResponse.errorBody() != null ? retrofitResponse.errorBody().string() : "";
                    int code = retrofitResponse.code();

                    if (code == 503 || isMaintenancePayload(null, errBody)) {
                        if (channelDao != null) {
                            try { channelDao.deleteAll(); } catch (Exception ignored) {}
                        }
                        String msg = extractMaintenanceMessage(errBody, "Server is currently under maintenance. Please try again later.");
                        final String finalMsg = Config.MAINTENANCE_STATUS_CODE + " " + msg;
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(finalMsg);
                        });
                        return;
                    } else if (code == 404) {
                        final String err = "ROUTE_NOT_FOUND: 404 - Server Route Not Found. The requested endpoint path was not found on the server.";
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(err);
                        });
                        return;
                    } else if (code == 401 || code == 403) {
                        final String err = "HANDSHAKE_FAILED: Security Handshake Failed (HTTP " + code + "). Mismatched API key or HMAC signature.";
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(err);
                        });
                        return;
                    } else {
                        final String err = "SERVER_ERROR: Server Response Error (HTTP " + code + "). Please try again later.";
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(err);
                        });
                        return;
                    }
                }

                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String responseString = retrofitResponse.body().string();
                    JSONObject jsonResponse = null;
                    try {
                        jsonResponse = new JSONObject(responseString);
                    } catch (Exception ignored) {}

                    if (jsonResponse != null) {
                        AppUpdateInfo parsedUpdate = AppUpdateInfo.fromJson(jsonResponse);
                        if (parsedUpdate != null) {
                            latestAppUpdateInfo = parsedUpdate;
                        }
                        String waUrl = jsonResponse.optString("whatsapp_url", jsonResponse.optString("whatsapp", ""));
                        String tgUrl = jsonResponse.optString("telegram_url", jsonResponse.optString("telegram", ""));
                        String devInfo = jsonResponse.optString("developer_info", jsonResponse.optString("dev_info", ""));
                        if (!waUrl.isEmpty() || !tgUrl.isEmpty() || !devInfo.isEmpty()) {
                            PreferenceUtils.setSupportInfo(context, waUrl, tgUrl, devInfo);
                        }

                        // Parse category icons sent from server
                        if (jsonResponse.has("category_icons")) {
                            JSONObject catIcons = jsonResponse.optJSONObject("category_icons");
                            if (catIcons != null) {
                                PreferenceUtils.saveCategoryIconMap(context, catIcons);
                            }
                        }
                        if (jsonResponse.has("categories")) {
                            JSONArray catArr = jsonResponse.optJSONArray("categories");
                            if (catArr != null) {
                                for (int k = 0; k < catArr.length(); k++) {
                                    JSONObject catObj = catArr.optJSONObject(k);
                                    if (catObj != null) {
                                        String cName = catObj.optString("name", catObj.optString("title", ""));
                                        String cIcon = catObj.optString("icon", catObj.optString("icon_url", catObj.optString("logo", "")));
                                        if (!cName.isEmpty() && !cIcon.isEmpty()) {
                                            PreferenceUtils.setCategoryIcon(context, cName, cIcon);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isMaintenancePayload(jsonResponse, responseString)) {
                        if (channelDao != null) {
                            channelDao.deleteAll();
                        }
                        String msg = jsonResponse != null ? jsonResponse.optString("message", "Server is under maintenance. Please try again later.") : "Server is under maintenance. Please try again later.";
                        final String finalMsg2 = Config.MAINTENANCE_STATUS_CODE + " " + msg;
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(finalMsg2);
                        });
                        return;
                    }

                    String status = jsonResponse != null ? jsonResponse.optString("status") : "";
                    if ("success".equalsIgnoreCase(status) || jsonResponse == null || responseString.trim().startsWith("[")) {
                        String iv = jsonResponse != null ? jsonResponse.optString("iv") : "";
                        String encryptedData = jsonResponse != null ? jsonResponse.optString("data") : "";

                        String decryptedJson = null;
                        if (encryptedData != null && !encryptedData.isEmpty() && iv != null && !iv.isEmpty()) {
                            decryptedJson = CryptoUtils.decryptAes256Cbc(encryptedData, iv, encKey);
                        }

                        if (decryptedJson == null || decryptedJson.isEmpty()) {
                            decryptedJson = jsonResponse != null ? jsonResponse.optString("channels", responseString) : responseString;
                        }

                        if (decryptedJson.startsWith("{")) {
                            try {
                                JSONObject decObj = new JSONObject(decryptedJson);
                                if (isMaintenancePayload(decObj, decryptedJson)) {
                                    if (channelDao != null) {
                                        channelDao.deleteAll();
                                    }
                                    String msg = decObj.optString("message", "Server is under maintenance. Please try again later.");
                                    final String finalMsg3 = Config.MAINTENANCE_STATUS_CODE + " " + msg;
                                    mainHandler.post(() -> {
                                        if (callback != null) callback.onError(finalMsg3);
                                    });
                                    return;
                                }
                            } catch (Exception ignored) {}
                        }

                        JSONArray channelsArray;
                        if (decryptedJson.startsWith("[")) {
                            channelsArray = new JSONArray(decryptedJson);
                        } else {
                            JSONObject obj = new JSONObject(decryptedJson);
                            channelsArray = obj.getJSONArray("channels");
                        }

                        List<ChannelEntity> existingFavs = channelDao != null ? channelDao.getFavoriteChannels() : null;
                        java.util.Set<String> favUrls = new java.util.HashSet<>();
                        java.util.Set<String> favTitles = new java.util.HashSet<>();
                        if (existingFavs != null) {
                            for (ChannelEntity fav : existingFavs) {
                                if (fav.getStreamUrl() != null && !fav.getStreamUrl().trim().isEmpty()) {
                                    favUrls.add(fav.getStreamUrl().trim());
                                }
                                if (fav.getTitle() != null && !fav.getTitle().trim().isEmpty()) {
                                    favTitles.add(fav.getTitle().trim().toLowerCase());
                                }
                            }
                        }

                        List<ChannelEntity> newChannels = new ArrayList<>();
                        for (int i = 0; i < channelsArray.length(); i++) {
                            JSONObject c = channelsArray.getJSONObject(i);
                            String title = c.optString("title", "Unknown Channel");
                            String streamUrl = c.optString("streamUrl", "");
                            String logoUrl = c.optString("logoUrl", "");
                            String defaultCat = ("movies".equalsIgnoreCase(routeName) || "movie".equalsIgnoreCase(routeName)) ? "movie" : "tv";
                            String category = c.optString("category", defaultCat);
                            String subCategory = c.optString("subCategory", "General");
                            
                            // App must have no default favorites. Only if user previously favorited this channel locally, preserve it.
                            boolean isFavorite = false;
                            if (!streamUrl.isEmpty() && favUrls.contains(streamUrl.trim())) {
                                isFavorite = true;
                            } else if (!title.isEmpty() && favTitles.contains(title.trim().toLowerCase())) {
                                isFavorite = true;
                            }

                            String streamType = c.optString("streamType", "hls");
                            String quality = c.optString("quality", "1080p HD");

                            // Check if channel is Premium / VIP
                            boolean isPremiumChannel = c.optBoolean("is_premium", c.optBoolean("isPremium", c.optBoolean("premium", false)))
                                    || subCategory.toLowerCase().contains("vip")
                                    || subCategory.toLowerCase().contains("premium")
                                    || title.toLowerCase().contains("[vip]")
                                    || title.toLowerCase().contains("[premium]");

                            // If channel is premium but subscription is expired or user not logged in, automatically hide / filter it out!
                            if (isPremiumChannel && !subValid) {
                                continue;
                            }

                            if (!streamUrl.isEmpty()) {
                                newChannels.add(new ChannelEntity(
                                        title,
                                        streamUrl,
                                        logoUrl,
                                        category,
                                        subCategory,
                                        isFavorite,
                                        streamType,
                                        quality
                                ));
                            }
                        }

                        if (!newChannels.isEmpty()) {
                            if (channelDao != null) {
                                if ("movies".equalsIgnoreCase(routeName) || "movie".equalsIgnoreCase(routeName)) {
                                    channelDao.deleteByCategory("movie");
                                    channelDao.deleteByCategory("movies");
                                } else if ("live_tv".equalsIgnoreCase(routeName) || "live_tv_channels".equalsIgnoreCase(routeName) || "tv".equalsIgnoreCase(routeName)) {
                                    channelDao.deleteByCategory("tv");
                                    channelDao.deleteByCategory("live_tv");
                                } else if (routeName != null && !routeName.isEmpty()) {
                                    channelDao.deleteByCategory(routeName);
                                } else {
                                    channelDao.deleteAll();
                                }
                                channelDao.insertAll(newChannels);
                            }
                            final int count = newChannels.size();
                            mainHandler.post(() -> {
                                if (callback != null) callback.onSuccess(count);
                            });
                        } else {
                            if (channelDao != null) {
                                if ("movies".equalsIgnoreCase(routeName) || "movie".equalsIgnoreCase(routeName)) {
                                    channelDao.deleteByCategory("movie");
                                    channelDao.deleteByCategory("movies");
                                } else if ("live_tv".equalsIgnoreCase(routeName) || "live_tv_channels".equalsIgnoreCase(routeName) || "tv".equalsIgnoreCase(routeName)) {
                                    channelDao.deleteByCategory("tv");
                                    channelDao.deleteByCategory("live_tv");
                                }
                            }
                            mainHandler.post(() -> {
                                if (callback != null) callback.onSuccess(0);
                            });
                        }
                    } else {
                        String msg = jsonResponse.optString("message", "Server returned status: " + status);
                        mainHandler.post(() -> {
                            if (callback != null) callback.onError(msg);
                        });
                    }
                } else {
                    final String err = "Server HTTP Error: " + retrofitResponse.code();
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError(err);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                String message = e.getMessage() != null ? e.getMessage() : "";
                final String errMsg;
                if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException || e instanceof java.io.IOException) {
                    errMsg = "NO_NETWORK: No Internet Connection. Please check your internet or network settings and try again.";
                } else {
                    errMsg = "SERVER_ERROR: Connection failed: " + (message.isEmpty() ? "Unknown Error" : message);
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(errMsg);
                });
            }
        });
    }

    public static void syncCategoriesFromServer(Context context, SyncCallback callback) {
        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);

                String targetUrl = apiUrl;
                String routeName = Config.ROUTE_CATEGORIES;
                if (targetUrl.contains("?")) {
                    targetUrl += "&route=" + routeName;
                } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                    targetUrl += "?route=" + routeName;
                } else {
                    targetUrl += "/" + routeName;
                }

                URL urlObj = new URL(targetUrl);
                String path = urlObj.getPath();
                if (path == null || path.isEmpty()) path = "/index.php";
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) path += "?" + urlObj.getQuery();

                long timestamp = System.currentTimeMillis();
                String hmacData = timestamp + path;
                String signature = CryptoUtils.generateHmacSha256(hmacData, hmacKey);

                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), signature);

                Response<ResponseBody> retrofitResponse = call.execute();
                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String responseString = retrofitResponse.body().string();
                    JSONObject jsonResponse = null;
                    JSONArray jsonArray = null;
                    try {
                        if (responseString.trim().startsWith("[")) {
                            jsonArray = new JSONArray(responseString);
                        } else {
                            jsonResponse = new JSONObject(responseString);
                        }
                    } catch (Exception ignored) {}

                    int count = 0;
                    if (jsonArray != null) {
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject catObj = jsonArray.optJSONObject(i);
                            if (catObj != null) {
                                String cName = catObj.optString("name", catObj.optString("title", catObj.optString("category", "")));
                                String cIcon = catObj.optString("icon", catObj.optString("icon_url", catObj.optString("logo", "")));
                                if (!cName.isEmpty() && !cIcon.isEmpty()) {
                                    PreferenceUtils.setCategoryIcon(context, cName, cIcon);
                                    count++;
                                }
                            }
                        }
                    } else if (jsonResponse != null) {
                        if (jsonResponse.has("category_icons")) {
                            JSONObject catIcons = jsonResponse.optJSONObject("category_icons");
                            if (catIcons != null) {
                                PreferenceUtils.saveCategoryIconMap(context, catIcons);
                                count += catIcons.length();
                            }
                        }
                        if (jsonResponse.has("categories")) {
                            JSONArray catArr = jsonResponse.optJSONArray("categories");
                            if (catArr != null) {
                                for (int k = 0; k < catArr.length(); k++) {
                                    JSONObject catObj = catArr.optJSONObject(k);
                                    if (catObj != null) {
                                        String cName = catObj.optString("name", catObj.optString("title", catObj.optString("category", "")));
                                        String cIcon = catObj.optString("icon", catObj.optString("icon_url", catObj.optString("logo", "")));
                                        if (!cName.isEmpty() && !cIcon.isEmpty()) {
                                            PreferenceUtils.setCategoryIcon(context, cName, cIcon);
                                            count++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    final int totalCount = count;
                    mainHandler.post(() -> {
                        if (callback != null) callback.onSuccess(totalCount);
                    });
                } else {
                    mainHandler.post(() -> {
                        if (callback != null) callback.onError("Categories sync returned HTTP " + retrofitResponse.code());
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError(e.getMessage());
                });
            }
        });
    }

    private static boolean isMaintenancePayload(JSONObject json, String rawString) {
        if (json != null) {
            String status = json.optString("status", "");
            String appStatus = json.optString("app_status", "");
            String mode = json.optString("mode", "");
            boolean isMaintBool = json.optBoolean("maintenance", false) || json.optBoolean("maintenance_mode", false);
            int isMaintInt = json.optInt("maintenance", 0);
            String isMaintStr = json.optString("maintenance", "");

            if ("maintenance".equalsIgnoreCase(status) || "maintain".equalsIgnoreCase(status) ||
                "maintenance".equalsIgnoreCase(appStatus) || "maintain".equalsIgnoreCase(appStatus) ||
                "maintenance".equalsIgnoreCase(mode) || isMaintBool || isMaintInt == 1 ||
                "true".equalsIgnoreCase(isMaintStr) || "1".equals(isMaintStr)) {
                return true;
            }
        }
        if (rawString != null) {
            String lower = rawString.toLowerCase();
            if (lower.contains("\"status\":\"maintenance\"") ||
                lower.contains("\"status\": \"maintenance\"") ||
                lower.contains("\"app_status\":\"maintenance\"") ||
                lower.contains("\"app_status\": \"maintenance\"") ||
                lower.contains("\"maintenance\":true") ||
                lower.contains("\"maintenance\": true") ||
                lower.contains("\"maintenance\":1") ||
                lower.contains("\"maintenance\": 1") ||
                lower.contains("\"maintenance_mode\":true")) {
                return true;
            }
        }
        return false;
    }

    private static String extractMaintenanceMessage(String body, String defaultMsg) {
        if (body == null || body.trim().isEmpty()) return defaultMsg;
        try {
            JSONObject json = new JSONObject(body);
            if (json.has("message")) return json.getString("message");
            if (json.has("msg")) return json.getString("msg");
        } catch (Exception ignored) {}
        return defaultMsg;
    }

    public static void checkAppUpdate(Context context, AppUpdateCallback callback) {
        executor.execute(() -> {
            try {
                String apiUrl = PreferenceUtils.getApiUrl(context);
                String apiKey = PreferenceUtils.getApiKey(context);
                String hmacKey = PreferenceUtils.getHmacKey(context);

                String targetUrl = apiUrl;
                if (targetUrl.contains("?")) {
                    targetUrl += "&route=" + Config.ROUTE_UPDATE;
                } else if (targetUrl.endsWith(".php") || targetUrl.endsWith("/")) {
                    targetUrl += "?route=" + Config.ROUTE_UPDATE;
                } else {
                    targetUrl += "/" + Config.ROUTE_UPDATE;
                }

                URL urlObj = new URL(targetUrl);
                String endpointPath = urlObj.getPath();
                if (urlObj.getQuery() != null && !urlObj.getQuery().isEmpty()) {
                    endpointPath += "?" + urlObj.getQuery();
                }

                long timestamp = System.currentTimeMillis();
                String hmacSignature = CryptoUtils.generateHmacSha256(timestamp + endpointPath, hmacKey);

                IptvApiService apiService = RetrofitClient.getApiService(targetUrl);
                Call<ResponseBody> call = apiService.fetchFromDynamicUrl(targetUrl, apiKey, String.valueOf(timestamp), hmacSignature);
                Response<ResponseBody> retrofitResponse = call.execute();

                if (retrofitResponse.isSuccessful() && retrofitResponse.body() != null) {
                    String body = retrofitResponse.body().string();
                    JSONObject json = new JSONObject(body);
                    AppUpdateInfo updateInfo = AppUpdateInfo.fromJson(json);

                    if (updateInfo != null) {
                        latestAppUpdateInfo = updateInfo;
                        int installedCode = 1;
                        try {
                            android.content.pm.PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                installedCode = (int) pInfo.getLongVersionCode();
                            } else {
                                installedCode = pInfo.versionCode;
                            }
                        } catch (Exception ignored) {}

                        boolean isAvailable = updateInfo.isUpdateAvailable(installedCode);
                        mainHandler.post(() -> {
                            if (callback != null) callback.onUpdateInfoReceived(updateInfo, isAvailable);
                        });
                        return;
                    }
                }
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("No update metadata received");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) callback.onError("Check update error: " + e.getMessage());
                });
            }
        });
    }

    public static String getSampleServerPhpCode() {
        return "<?php\n" +
                "// ==========================================================================\n" +
                "// IPTV PRODUCTION API SERVER SCRIPT (PHP + MySQL / JSON Database)\n" +
                "// Handles User Auth, Session Validation, Hardware Device ID & VIP Channel Filter\n" +
                "// ==========================================================================\n" +
                "header('Access-Control-Allow-Origin: *');\n" +
                "header('Content-Type: application/json; charset=utf-8');\n\n" +
                "$route = $_GET['route'] ?? 'live_tv';\n" +
                "$usersDb = [\n" +
                "    'admin' => [\n" +
                "        'username' => 'admin',\n" +
                "        'password' => '123456',\n" +
                "        'email' => 'admin@iptvstream.com',\n" +
                "        'plan' => 'VIP Lifetime Ultra 4K',\n" +
                "        'expiry_timestamp' => 1893456000000\n" +
                "    ],\n" +
                "    'vipuser' => [\n" +
                "        'username' => 'vipuser',\n" +
                "        'password' => 'vip123',\n" +
                "        'email' => 'vipuser@streamtv.com',\n" +
                "        'plan' => 'VIP Premium Annual',\n" +
                "        'expiry_timestamp' => 1798761600000\n" +
                "    ]\n" +
                "];\n\n" +
                "// 1. User Authentication Endpoint\n" +
                "if ($route === 'login') {\n" +
                "    $username = trim($_POST['username'] ?? $_GET['username'] ?? '');\n" +
                "    $password = trim($_POST['password'] ?? $_GET['password'] ?? '');\n" +
                "    $deviceId = trim($_GET['device_id'] ?? $_POST['device_id'] ?? 'DEV-UNKNOWN');\n\n" +
                "    if (empty($username) || empty($password)) {\n" +
                "        http_response_code(400);\n" +
                "        echo json_encode(['status' => 'error', 'message' => 'Username and password are required']);\n" +
                "        exit();\n" +
                "    }\n" +
                "    $user = $usersDb[$username] ?? null;\n" +
                "    if ($user && $user['password'] === $password) {\n" +
                "        $curMs = round(microtime(true) * 1000);\n" +
                "        $isActive = ($curMs < $user['expiry_timestamp']);\n" +
                "        echo json_encode([\n" +
                "            'status' => 'success',\n" +
                "            'message' => 'Login successful',\n" +
                "            'username' => $user['username'],\n" +
                "            'email' => $user['email'],\n" +
                "            'plan' => $user['plan'],\n" +
                "            'subscription_status' => $isActive ? 'ACTIVE' : 'EXPIRED',\n" +
                "            'expiry_timestamp' => $user['expiry_timestamp'],\n" +
                "            'expiry_date' => date('Y-m-d H:i:s', (int)($user['expiry_timestamp']/1000))\n" +
                "        ], JSON_PRETTY_PRINT);\n" +
                "    } else {\n" +
                "        http_response_code(401);\n" +
                "        echo json_encode(['status' => 'error', 'message' => 'Account not registered in server database']);\n" +
                "    }\n" +
                "    exit();\n" +
                "}\n\n" +
                "// 2. Session Validation Endpoint\n" +
                "if ($route === 'session_check') {\n" +
                "    $username = trim($_GET['user_id'] ?? $_POST['username'] ?? '');\n" +
                "    $user = $usersDb[$username] ?? null;\n" +
                "    if ($user) {\n" +
                "        $curMs = round(microtime(true) * 1000);\n" +
                "        $isActive = ($curMs < $user['expiry_timestamp']);\n" +
                "        echo json_encode([\n" +
                "            'status' => 'success',\n" +
                "            'username' => $user['username'],\n" +
                "            'email' => $user['email'],\n" +
                "            'plan' => $user['plan'],\n" +
                "            'subscription_status' => $isActive ? 'ACTIVE' : 'EXPIRED',\n" +
                "            'expiry_timestamp' => $user['expiry_timestamp'],\n" +
                "            'expiry_date' => date('Y-m-d H:i:s', (int)($user['expiry_timestamp']/1000))\n" +
                "        ], JSON_PRETTY_PRINT);\n" +
                "    } else {\n" +
                "        http_response_code(401);\n" +
                "        echo json_encode(['status' => 'error', 'message' => 'Session expired or user not found']);\n" +
                "    }\n" +
                "    exit();\n" +
                "}\n" +
                "?>";
    }
}
