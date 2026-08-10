package com.ottking.mobile.devcode.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.ottking.mobile.devcode.config.Config;
import com.ottking.mobile.devcode.model.PlaylistModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PreferenceUtils {

    private static final String PREF_NAME = "iptv_preferences";
    private static final String KEY_LOW_QUALITY = "enable_low_quality";
    private static final String KEY_HW_ACC = "hw_acceleration";
    private static final String KEY_CUSTOM_M3U_PLAYLISTS = "custom_m3u_playlists";
    private static final String KEY_FLOATING_PLAYER = "enable_floating_player";
    private static final String KEY_IS_LOGGED_IN = "is_logged_in";
    private static final String KEY_USER_NAME = "user_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_SERVER = "user_server";
    private static final String KEY_API_URL = "api_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_HMAC_KEY = "hmac_key";
    private static final String KEY_ENCRYPTION_KEY = "encryption_key";

    public static final String DEFAULT_API_URL = Config.API_URL;
    public static final String DEFAULT_API_KEY = Config.API_KEY;
    public static final String DEFAULT_HMAC_KEY = Config.HMAC_KEY;
    public static final String DEFAULT_ENCRYPTION_KEY = Config.ENCRYPTION_KEY;

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getApiUrl(Context context) {
        return getPrefs(context).getString(KEY_API_URL, DEFAULT_API_URL);
    }

    public static String getApiKey(Context context) {
        return getPrefs(context).getString(KEY_API_KEY, DEFAULT_API_KEY);
    }

    public static String getHmacKey(Context context) {
        return getPrefs(context).getString(KEY_HMAC_KEY, DEFAULT_HMAC_KEY);
    }

    public static String getEncryptionKey(Context context) {
        return getPrefs(context).getString(KEY_ENCRYPTION_KEY, DEFAULT_ENCRYPTION_KEY);
    }

    public static void setServerApiConfig(Context context, String url, String apiKey, String hmacKey, String encryptionKey) {
        getPrefs(context).edit()
                .putString(KEY_API_URL, url != null ? url.trim() : DEFAULT_API_URL)
                .putString(KEY_API_KEY, apiKey != null ? apiKey.trim() : DEFAULT_API_KEY)
                .putString(KEY_HMAC_KEY, hmacKey != null ? hmacKey.trim() : DEFAULT_HMAC_KEY)
                .putString(KEY_ENCRYPTION_KEY, encryptionKey != null ? encryptionKey.trim() : DEFAULT_ENCRYPTION_KEY)
                .apply();
    }

    private static final String KEY_WHATSAPP_URL = "whatsapp_url";
    private static final String KEY_TELEGRAM_URL = "telegram_url";
    private static final String KEY_DEVELOPER_INFO = "developer_info";

    public static final String DEFAULT_WHATSAPP_URL = "https://wa.me/8801700000000";
    public static final String DEFAULT_TELEGRAM_URL = "https://t.me/telegram";
    public static final String DEFAULT_DEVELOPER_INFO = "Official Stream IPTV Engine v2.4.0\nPowered by Secure Cloud Infrastructure & Real-Time AES-256 Content Delivery System.";

    public static String getWhatsAppUrl(Context context) {
        return getPrefs(context).getString(KEY_WHATSAPP_URL, DEFAULT_WHATSAPP_URL);
    }

    public static String getTelegramUrl(Context context) {
        return getPrefs(context).getString(KEY_TELEGRAM_URL, DEFAULT_TELEGRAM_URL);
    }

    public static String getDeveloperInfo(Context context) {
        return getPrefs(context).getString(KEY_DEVELOPER_INFO, DEFAULT_DEVELOPER_INFO);
    }

    public static void setSupportInfo(Context context, String whatsappUrl, String telegramUrl, String devInfo) {
        SharedPreferences.Editor editor = getPrefs(context).edit();
        if (whatsappUrl != null && !whatsappUrl.isEmpty()) {
            editor.putString(KEY_WHATSAPP_URL, whatsappUrl);
        }
        if (telegramUrl != null && !telegramUrl.isEmpty()) {
            editor.putString(KEY_TELEGRAM_URL, telegramUrl);
        }
        if (devInfo != null && !devInfo.isEmpty()) {
            editor.putString(KEY_DEVELOPER_INFO, devInfo);
        }
        editor.apply();
    }

    private static final String KEY_CATEGORY_ICONS_JSON = "category_icons_json";

    public static String getCategoryIconUrl(Context context, String categoryName) {
        if (categoryName == null || categoryName.trim().isEmpty()) return "";
        try {
            String jsonStr = getPrefs(context).getString(KEY_CATEGORY_ICONS_JSON, "{}");
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
            String name = categoryName.trim();
            if (json.has(name)) {
                return json.optString(name, "");
            }
            if (json.has(name.toLowerCase())) {
                return json.optString(name.toLowerCase(), "");
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static void setCategoryIcon(Context context, String categoryName, String iconUrl) {
        if (categoryName == null || categoryName.trim().isEmpty() || iconUrl == null || iconUrl.trim().isEmpty()) return;
        try {
            SharedPreferences prefs = getPrefs(context);
            String jsonStr = prefs.getString(KEY_CATEGORY_ICONS_JSON, "{}");
            org.json.JSONObject json = new org.json.JSONObject(jsonStr);
            String name = categoryName.trim();
            String url = iconUrl.trim();
            json.put(name, url);
            json.put(name.toLowerCase(), url);
            prefs.edit().putString(KEY_CATEGORY_ICONS_JSON, json.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void saveCategoryIconMap(Context context, org.json.JSONObject iconMap) {
        if (iconMap == null) return;
        try {
            SharedPreferences prefs = getPrefs(context);
            String jsonStr = prefs.getString(KEY_CATEGORY_ICONS_JSON, "{}");
            org.json.JSONObject existing = new org.json.JSONObject(jsonStr);
            java.util.Iterator<String> keys = iconMap.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                String val = iconMap.optString(k, "");
                if (!val.trim().isEmpty()) {
                    existing.put(k.trim(), val.trim());
                    existing.put(k.trim().toLowerCase(), val.trim());
                }
            }
            prefs.edit().putString(KEY_CATEGORY_ICONS_JSON, existing.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static boolean isFloatingPlayerEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_FLOATING_PLAYER, false);
    }

    public static void setFloatingPlayerEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_FLOATING_PLAYER, enabled).apply();
    }

    private static final String KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry";
    private static final String KEY_DEVICE_ID = "device_id";

    public static boolean isLoggedIn(Context context) {
        return getPrefs(context).getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public static String getDeviceId(Context context) {
        String id = getPrefs(context).getString(KEY_DEVICE_ID, "");
        if (id.isEmpty()) {
            id = "DEV-" + Math.abs(android.os.Build.FINGERPRINT.hashCode() % 89999 + 10000) + "-X9";
            getPrefs(context).edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public static long getSubscriptionExpiry(Context context) {
        // Default 30 days if set, otherwise 0
        return getPrefs(context).getLong(KEY_SUBSCRIPTION_EXPIRY, 0L);
    }

    public static boolean isSubscriptionValid(Context context) {
        if (!isLoggedIn(context)) {
            return false;
        }
        long expiry = getSubscriptionExpiry(context);
        return expiry > System.currentTimeMillis();
    }

    public static String getSubscriptionExpiryFormatted(Context context) {
        if (!isLoggedIn(context)) {
            return "No Active VIP Subscription";
        }
        long expiry = getSubscriptionExpiry(context);
        if (expiry <= System.currentTimeMillis()) {
            return "EXPIRED (Renewal Required)";
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
        return "VIP Active (Expires: " + sdf.format(new java.util.Date(expiry)) + ")";
    }

    public static String getUserName(Context context) {
        return getPrefs(context).getString(KEY_USER_NAME, "Guest User");
    }

    public static String getUserEmail(Context context) {
        return getPrefs(context).getString(KEY_USER_EMAIL, "guest@streamtv.com");
    }

    public static String getUserServerUrl(Context context) {
        return getPrefs(context).getString(KEY_USER_SERVER, "http://iptv.server.com:8080");
    }

    private static final String KEY_PLAN_NAME = "subscription_plan_name";

    public static String getPlanName(Context context) {
        return getPrefs(context).getString(KEY_PLAN_NAME, "VIP Ultra Premium");
    }

    public static void logout(Context context) {
        setLoginState(context, false, "Guest User", "guest@streamtv.com", "", 0L, "Free Guest");
    }

    public static void setLoginState(Context context, boolean loggedIn, String name, String email, String serverUrl, long expiryTimeMs, String planName) {
        getPrefs(context).edit()
                .putBoolean(KEY_IS_LOGGED_IN, loggedIn)
                .putString(KEY_USER_NAME, loggedIn ? (name != null ? name : "VIP User") : "Guest User")
                .putString(KEY_USER_EMAIL, loggedIn ? (email != null ? email : "") : "guest@streamtv.com")
                .putString(KEY_USER_SERVER, loggedIn ? (serverUrl != null ? serverUrl : "") : "")
                .putString(KEY_PLAN_NAME, loggedIn ? (planName != null ? planName : "VIP Premium") : "Free Guest")
                .putLong(KEY_SUBSCRIPTION_EXPIRY, loggedIn ? expiryTimeMs : 0L)
                .apply();
    }

    public static boolean isLowQualityEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_LOW_QUALITY, false);
    }

    public static void setLowQualityEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_LOW_QUALITY, enabled).apply();
    }

    public static boolean isHwAccelerationEnabled(Context context) {
        return getPrefs(context).getBoolean(KEY_HW_ACC, true);
    }

    public static void setHwAccelerationEnabled(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_HW_ACC, enabled).apply();
    }

    public static List<PlaylistModel> getCustomM3uPlaylists(Context context) {
        List<PlaylistModel> list = new ArrayList<>();
        String json = getPrefs(context).getString(KEY_CUSTOM_M3U_PLAYLISTS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String id = obj.optString("id");
                String title = obj.optString("title");
                String description = obj.optString("description");
                String categoryFilter = obj.optString("categoryFilter");
                String iconUrl = obj.optString("iconUrl");
                int channelCount = obj.optInt("channelCount", 0);
                list.add(new PlaylistModel(id, title, description, categoryFilter, iconUrl, channelCount));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void saveCustomM3uPlaylists(Context context, List<PlaylistModel> list) {
        try {
            JSONArray array = new JSONArray();
            if (list != null) {
                for (PlaylistModel p : list) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", p.getId());
                    obj.put("title", p.getTitle());
                    obj.put("description", p.getDescription());
                    obj.put("categoryFilter", p.getCategoryFilter());
                    obj.put("iconUrl", p.getIconUrl());
                    obj.put("channelCount", p.getChannelCount());
                    array.put(obj);
                }
            }
            getPrefs(context).edit().putString(KEY_CUSTOM_M3U_PLAYLISTS, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addCustomM3uPlaylist(Context context, PlaylistModel playlist) {
        List<PlaylistModel> list = getCustomM3uPlaylists(context);
        list.add(playlist);
        saveCustomM3uPlaylists(context, list);
    }

    public static void removeCustomM3uPlaylist(Context context, String playlistTitle) {
        List<PlaylistModel> list = getCustomM3uPlaylists(context);
        List<PlaylistModel> updated = new ArrayList<>();
        for (PlaylistModel p : list) {
            if (!p.getTitle().equalsIgnoreCase(playlistTitle) && !p.getCategoryFilter().equalsIgnoreCase(playlistTitle)) {
                updated.add(p);
            }
        }
        saveCustomM3uPlaylists(context, updated);
    }

    private static final String KEY_LAST_PLAYED_ID = "last_played_id";
    private static final String KEY_LAST_PLAYED_URL = "last_played_url";
    private static final String KEY_LAST_PLAYED_TITLE = "last_played_title";
    private static final String KEY_LAST_PLAYED_CATEGORY = "last_played_category";
    private static final String KEY_LAST_PLAYED_TYPE = "last_played_type";
    private static final String KEY_LAST_PLAYED_LOGO = "last_played_logo";
    private static final String KEY_LAST_PLAYED_POSITION = "last_played_position";
    private static final String KEY_LAST_PLAYED_TIMESTAMP = "last_played_timestamp";
    private static final String KEY_AUTO_PLAY_ON_SCREEN_ON = "auto_play_on_screen_on";
    private static final String KEY_AUTO_PLAY_ON_STARTUP = "auto_play_on_startup";

    public static void saveLastPlayedStream(Context context, int id, String url, String title, String type, String category, String logoUrl, long position) {
        if (context == null || url == null || url.trim().isEmpty()) return;
        getPrefs(context).edit()
                .putInt(KEY_LAST_PLAYED_ID, id)
                .putString(KEY_LAST_PLAYED_URL, url.trim())
                .putString(KEY_LAST_PLAYED_TITLE, title != null ? title.trim() : "Live TV Channel")
                .putString(KEY_LAST_PLAYED_TYPE, type != null ? type.trim() : "hls")
                .putString(KEY_LAST_PLAYED_CATEGORY, category != null ? category.trim() : "tv")
                .putString(KEY_LAST_PLAYED_LOGO, logoUrl != null ? logoUrl.trim() : "")
                .putLong(KEY_LAST_PLAYED_POSITION, Math.max(0, position))
                .putLong(KEY_LAST_PLAYED_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    public static boolean hasLastPlayedStream(Context context) {
        if (context == null) return false;
        String url = getPrefs(context).getString(KEY_LAST_PLAYED_URL, "");
        return !url.trim().isEmpty();
    }

    public static String getLastPlayedStreamUrl(Context context) {
        return getPrefs(context).getString(KEY_LAST_PLAYED_URL, "");
    }

    public static String getLastPlayedTitle(Context context) {
        return getPrefs(context).getString(KEY_LAST_PLAYED_TITLE, "Last Stream");
    }

    public static String getLastPlayedType(Context context) {
        return getPrefs(context).getString(KEY_LAST_PLAYED_TYPE, "hls");
    }

    public static String getLastPlayedCategory(Context context) {
        return getPrefs(context).getString(KEY_LAST_PLAYED_CATEGORY, "tv");
    }

    public static String getLastPlayedLogo(Context context) {
        return getPrefs(context).getString(KEY_LAST_PLAYED_LOGO, "");
    }

    public static int getLastPlayedId(Context context) {
        return getPrefs(context).getInt(KEY_LAST_PLAYED_ID, 0);
    }

    public static long getLastPlayedPosition(Context context) {
        return getPrefs(context).getLong(KEY_LAST_PLAYED_POSITION, 0L);
    }

    public static boolean isAutoPlayOnScreenOn(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_PLAY_ON_SCREEN_ON, true);
    }

    public static void setAutoPlayOnScreenOn(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PLAY_ON_SCREEN_ON, enabled).apply();
    }

    public static boolean isAutoPlayOnStartup(Context context) {
        return getPrefs(context).getBoolean(KEY_AUTO_PLAY_ON_STARTUP, true);
    }

    public static void setAutoPlayOnStartup(Context context, boolean enabled) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_PLAY_ON_STARTUP, enabled).apply();
    }
}
