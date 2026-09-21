package com.ottking.devcode.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import com.ottking.devcode.security.SecureTokenStorage;

public class AppPreferences {
    private static final String PREF_NAME = "ott_king_prefs";
    private static final String KEY_BOOT_PLAYER = "boot_player_enabled";
    private static final String KEY_AUTO_SYNC = "auto_sync_enabled";
    private static final String KEY_LAST_SYNC_TIME = "last_sync_time";
    private static final String KEY_SESSION_TOKEN = "session_token";
    private static final String KEY_USERNAME = "user_username";
    private static final String KEY_PACKAGE = "user_package";
    private static final String KEY_EXPIRY = "user_expiry";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_EDGE_COOKIE = "edge_cookie";
    private static final String KEY_LAST_PLAYED_CHANNEL = "last_played_channel_id";

    private static AppPreferences instance;
    private final SharedPreferences prefs;
    private final SecureTokenStorage secureStorage;

    private AppPreferences(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.secureStorage = SecureTokenStorage.getInstance(context);
        migratePlaintextTokens();
    }

    private void migratePlaintextTokens() {
        // Transparently migrate existing plaintext tokens to hardware-encrypted storage
        if (prefs.contains(KEY_SESSION_TOKEN)) {
            String plainToken = prefs.getString(KEY_SESSION_TOKEN, "");
            if (!plainToken.isEmpty()) {
                secureStorage.putSecureString(KEY_SESSION_TOKEN, plainToken);
            }
            prefs.edit().remove(KEY_SESSION_TOKEN).apply();
        }

        if (prefs.contains(KEY_USERNAME)) {
            String plainUsername = prefs.getString(KEY_USERNAME, "");
            if (!plainUsername.isEmpty()) {
                secureStorage.putSecureString(KEY_USERNAME, plainUsername);
            }
            prefs.edit().remove(KEY_USERNAME).apply();
        }

        if (prefs.contains(KEY_DEVICE_ID)) {
            String plainDeviceId = prefs.getString(KEY_DEVICE_ID, "");
            if (!plainDeviceId.isEmpty()) {
                secureStorage.putSecureString(KEY_DEVICE_ID, plainDeviceId);
            }
            prefs.edit().remove(KEY_DEVICE_ID).apply();
        }
    }

    public static synchronized AppPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new AppPreferences(context);
        }
        return instance;
    }

    public boolean isBootPlayerEnabled() {
        return prefs.getBoolean(KEY_BOOT_PLAYER, false);
    }

    public void setBootPlayerEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BOOT_PLAYER, enabled).apply();
    }

    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC, true);
    }

    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
    }

    public String getLastSyncTime() {
        return prefs.getString(KEY_LAST_SYNC_TIME, "Never");
    }

    public void setLastSyncTime(String timeStr) {
        prefs.edit().putString(KEY_LAST_SYNC_TIME, timeStr).apply();
    }

    public String getSessionToken() {
        return secureStorage.getSecureString(KEY_SESSION_TOKEN, "");
    }

    public boolean isLoggedIn() {
        String token = getSessionToken();
        return token != null && !token.trim().isEmpty();
    }

    public String getAppSession() {
        return getSessionToken();
    }

    public String getUserToken() {
        return getSessionToken();
    }

    public String getGuestSession() {
        String guest = prefs.getString("guest_session", "");
        if (guest.isEmpty()) {
            guest = "guest_" + getDeviceId().replace("-", "").substring(0, Math.min(16, getDeviceId().length()));
            prefs.edit().putString("guest_session", guest).apply();
        }
        return guest;
    }

    public String getHmacKey() {
        return com.ottking.devcode.network.Config.getHmacKey();
    }

    public void setSessionToken(String token) {
        secureStorage.putSecureString(KEY_SESSION_TOKEN, token);
    }

    public String getUsername() {
        return secureStorage.getSecureString(KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        secureStorage.putSecureString(KEY_USERNAME, username);
    }

    public String getUserPackage() {
        return secureStorage.getSecureString(KEY_PACKAGE, "Free Tier");
    }

    public void setUserPackage(String pkg) {
        secureStorage.putSecureString(KEY_PACKAGE, pkg);
    }

    public String getUserExpiry() {
        return secureStorage.getSecureString(KEY_EXPIRY, "N/A");
    }

    public void setUserExpiry(String expiry) {
        secureStorage.putSecureString(KEY_EXPIRY, expiry);
    }

    public String getDeviceId() {
        String id = secureStorage.getSecureString(KEY_DEVICE_ID, "");
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            secureStorage.putSecureString(KEY_DEVICE_ID, id);
        }
        return id;
    }

    public String getEdgeCookie() {
        return secureStorage.getSecureString(KEY_EDGE_COOKIE, "");
    }

    public void setEdgeCookie(String cookie) {
        if (cookie != null) {
            secureStorage.putSecureString(KEY_EDGE_COOKIE, cookie.trim());
        }
    }

    public boolean isHardwareAccelerationEnabled() {
        return prefs.getBoolean("hardware_acceleration", true);
    }

    public void setHardwareAccelerationEnabled(boolean enabled) {
        prefs.edit().putBoolean("hardware_acceleration", enabled).apply();
    }

    public String getVideoResolution() {
        return prefs.getString("video_resolution", "Auto (Adaptive)");
    }

    public void setVideoResolution(String resolution) {
        prefs.edit().putString("video_resolution", resolution).apply();
    }

    public String getRetrySettings() {
        return prefs.getString("retry_settings", "Auto (3 Retries)");
    }

    public void setRetrySettings(String retry) {
        prefs.edit().putString("retry_settings", retry).apply();
    }

    public static final String DEFAULT_BUFFER_SETTING = "Instant Live (20ms startup, 60s buffer - Zero Buffering)";

    public String getBufferSettings() {
        String current = prefs.getString("buffer_settings", DEFAULT_BUFFER_SETTING);
        if (current == null || current.isEmpty()) {
            return DEFAULT_BUFFER_SETTING;
        }
        return current;
    }

    public void setBufferSettings(String buffer) {
        prefs.edit().putString("buffer_settings", buffer).apply();
    }

    public int getVideoScreenSize() {
        int size = prefs.getInt("video_screen_size", 0); // 0: Fit, 1: Stretch, 2: Zoom, 3: Original
        if (size < 0 || size > 3) {
            return 0;
        }
        return size;
    }

    public void setVideoScreenSize(int sizeIndex) {
        prefs.edit().putInt("video_screen_size", sizeIndex).apply();
    }

    public int getLastPlayedChannelId() {
        return prefs.getInt(KEY_LAST_PLAYED_CHANNEL, -1);
    }

    public void setLastPlayedChannelId(int channelId) {
        prefs.edit().putInt(KEY_LAST_PLAYED_CHANNEL, channelId).apply();
    }

    public void logout() {
        secureStorage.remove(KEY_SESSION_TOKEN);
        secureStorage.remove(KEY_USERNAME);
        secureStorage.remove(KEY_PACKAGE);
        secureStorage.remove(KEY_EXPIRY);
        secureStorage.remove(KEY_EDGE_COOKIE);
    }
}
