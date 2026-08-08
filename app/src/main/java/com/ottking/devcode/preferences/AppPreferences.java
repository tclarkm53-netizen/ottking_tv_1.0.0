package com.ottking.devcode.preferences;

import android.content.Context;
import android.content.SharedPreferences;

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
    private static final String KEY_LAST_PLAYED_CHANNEL = "last_played_channel_id";

    private static AppPreferences instance;
    private final SharedPreferences prefs;

    private AppPreferences(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
        return prefs.getString(KEY_SESSION_TOKEN, "");
    }

    public void setSessionToken(String token) {
        prefs.edit().putString(KEY_SESSION_TOKEN, token).apply();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public String getUserPackage() {
        return prefs.getString(KEY_PACKAGE, "Free Tier");
    }

    public void setUserPackage(String pkg) {
        prefs.edit().putString(KEY_PACKAGE, pkg).apply();
    }

    public String getUserExpiry() {
        return prefs.getString(KEY_EXPIRY, "N/A");
    }

    public void setUserExpiry(String expiry) {
        prefs.edit().putString(KEY_EXPIRY, expiry).apply();
    }

    public String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, "");
        if (id.isEmpty()) {
            id = java.util.UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
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

    public String getBufferSettings() {
        return prefs.getString("buffer_settings", "Standard (3 sec)");
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
        prefs.edit()
                .remove(KEY_SESSION_TOKEN)
                .remove(KEY_USERNAME)
                .remove(KEY_PACKAGE)
                .remove(KEY_EXPIRY)
                .apply();
    }
}
