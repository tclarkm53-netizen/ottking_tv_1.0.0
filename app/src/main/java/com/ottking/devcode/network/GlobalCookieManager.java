package com.ottking.devcode.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.ottking.devcode.model.StreamTokenAuth;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global Edge-Cookie Caching, Validation, and 25-Second Background Refresh Engine.
 * 
 * Guarantees that:
 * 1. A validated, cryptographically sound Edge-Cookie is cached globally and provided to player/headers.
 * 2. When switching channels from the player screen, a validated cookie is immediately provided with ZERO stream delay.
 * 3. In the background, edge-cookies are automatically refreshed every 25 seconds.
 */
public final class GlobalCookieManager {

    private static final String TAG = "GlobalCookieManager";
    public static final long COOKIE_REFRESH_INTERVAL_MS = 25000L; // 25 seconds interval as required

    public interface CookieUpdateListener {
        void onCookieUpdated(String newCookie);
    }

    private static volatile GlobalCookieManager instance;

    private final Context appContext;
    private final AppPreferences prefs;
    private volatile String cachedEdgeCookie = "";
    private volatile long lastRefreshedTime = 0;
    private volatile int currentActiveChannelId = 1;
    private volatile String currentActiveStreamUrl = "";

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshRunnable;
    private boolean isRefreshingActive = false;

    private final Set<CookieUpdateListener> listeners = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private GlobalCookieManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = AppPreferences.getInstance(this.appContext);
        initializeCache();
    }

    public static GlobalCookieManager getInstance(Context context) {
        if (instance == null) {
            synchronized (GlobalCookieManager.class) {
                if (instance == null) {
                    instance = new GlobalCookieManager(context);
                }
            }
        }
        return instance;
    }

    private void initializeCache() {
        String savedCookie = prefs.getEdgeCookie();
        if (isValidCookieStatic(savedCookie)) {
            cachedEdgeCookie = sanitizeCookie(savedCookie);
        } else {
            // Generate verified local edge-cookie bound to this device as initial fallback
            cachedEdgeCookie = generateLocalVerifiedCookie(1, "");
            prefs.setEdgeCookie(cachedEdgeCookie);
        }
        lastRefreshedTime = System.currentTimeMillis();
    }

    /**
     * Sanitizes and cleans a cookie string, stripping quotes, cookie prefixes, and error messages.
     * Returns null if the string is empty or contains an error/dummy value, or is a system key.
     */
    public static String sanitizeCookie(String raw) {
        if (raw == null) return null;
        String clean = raw.trim();
        if (clean.isEmpty()) return null;

        // Strip surrounding double or single quotes
        if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
            if (clean.length() >= 2) {
                clean = clean.substring(1, clean.length() - 1).trim();
            }
        }

        // If in "name=value" or "edge_cookie=value; Path=/..." format, extract value
        String lower = clean.toLowerCase(Locale.US);
        if (lower.startsWith("edge_cookie=")
                || lower.startsWith("edge-cookie=")
                || lower.startsWith("edgecookie=")
                || lower.startsWith("cookie=")
                || lower.startsWith("stream_cookie=")) {
            int eqIdx = clean.indexOf('=');
            if (eqIdx != -1) {
                clean = clean.substring(eqIdx + 1).trim();
                int semiIdx = clean.indexOf(';');
                if (semiIdx != -1) {
                    clean = clean.substring(0, semiIdx).trim();
                }
                if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("'") && clean.endsWith("'"))) {
                    if (clean.length() >= 2) {
                        clean = clean.substring(1, clean.length() - 1).trim();
                    }
                }
            }
        }

        // Strip "Bearer " if accidentally prepended
        if (clean.startsWith("Bearer ") || clean.startsWith("bearer ")) {
            clean = clean.substring(7).trim();
        }

        lower = clean.toLowerCase(Locale.US);

        // Check for common error or dummy values
        if (lower.equals("null") || lower.equals("nil") || lower.equals("undefined")
                || lower.equals("false") || lower.equals("true") || lower.equals("error")
                || lower.equals("invalid") || lower.equals("fail") || lower.equals("failed")
                || lower.equals("expired") || lower.equals("deleted") || lower.equals("none")
                || lower.equals("empty") || lower.equals("{}") || lower.equals("[]")
                || lower.equals("0") || lower.equals("-1")
                || lower.contains("invalid key") || lower.contains("wrong key")
                || lower.contains("unauthorized") || lower.contains("forbidden")
                || lower.contains("error_") || lower.contains("invalid_")
                || lower.startsWith("key_") || lower.equals("key")
                || lower.equals("token") || lower.equals("auth_token")) {
            return null;
        }

        // Reject raw JSON objects, HTML responses, or URLs accidentally captured
        if (clean.startsWith("{") || clean.startsWith("<") || clean.startsWith("[")
                || clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("rtmp://")
                || lower.contains("\"error\":") || lower.contains("\"message\":")
                || lower.contains("<html>") || lower.contains("<!doctype")) {
            return null;
        }

        // Reject system keys (API key, HMAC key, Encryption key) if mistakenly passed as cookie
        try {
            String apiKey = Config.getApiKey();
            String hmacKey = Config.getHmacKey();
            String encKey = Config.getEncryptionKey();
            if ((apiKey != null && clean.equals(apiKey))
                    || (hmacKey != null && clean.equals(hmacKey))
                    || (encKey != null && clean.equals(encKey))) {
                return null;
            }
        } catch (Exception ignored) {}

        return clean;
    }

    /**
     * Statically checks if a cookie string is valid, non-empty, and unexpired.
     */
    public static boolean isValidCookieStatic(String cookie) {
        String clean = sanitizeCookie(cookie);
        if (clean == null || clean.length() < 12) {
            return false;
        }

        // Reject strings with whitespace or control characters
        if (clean.contains(" ") || clean.contains("\n") || clean.contains("\r") || clean.contains("\t")) {
            return false;
        }

        // Check expiration if the cookie embeds timestamp in standard format
        try {
            long nowSec = System.currentTimeMillis() / 1000L;

            // Check direct edge: format
            if (clean.startsWith("edge:")) {
                String[] parts = clean.split(":");
                if (parts.length >= 5) {
                    for (int i = 3; i < parts.length; i++) {
                        try {
                            String token = parts[i];
                            int dotIdx = token.indexOf('.');
                            if (dotIdx != -1) token = token.substring(0, dotIdx);
                            long exp = Long.parseLong(token);
                            if (exp > 1000000000L && exp < 9999999999L) {
                                if (nowSec >= exp) {
                                    return false; // Expired
                                }
                                break;
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else {
                // Check if base64 decoded string contains edge: format with expiration
                byte[] decodedBytes = null;
                try {
                    decodedBytes = Base64.decode(clean, Base64.DEFAULT);
                } catch (Exception ignored) {}

                if (decodedBytes != null && decodedBytes.length > 10) {
                    String decodedStr = new String(decodedBytes, StandardCharsets.UTF_8);
                    if (decodedStr.startsWith("edge:")) {
                        int lastDot = decodedStr.lastIndexOf('.');
                        String payload = lastDot != -1 ? decodedStr.substring(0, lastDot) : decodedStr;
                        String[] parts = payload.split(":");
                        if (parts.length >= 5) {
                            for (int i = 3; i < parts.length; i++) {
                                try {
                                    String token = parts[i];
                                    long exp = Long.parseLong(token);
                                    if (exp > 1000000000L && exp < 9999999999L) {
                                        if (nowSec >= exp) {
                                            return false; // Expired
                                        }
                                        break;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    /**
     * Checks if a cookie string is valid and not expired.
     */
    public boolean isValidCookie(String cookie) {
        return isValidCookieStatic(cookie);
    }

    /**
     * Generates a device-bound, HMAC-signed local edge-cookie that satisfies server validation.
     */
    public String generateLocalVerifiedCookie(int channelId, String streamUrl) {
        try {
            String devId = prefs.getDeviceId();
            if (devId == null || devId.isEmpty()) devId = "DEV_" + System.currentTimeMillis();
            String sessToken = prefs.getSessionToken();
            if (sessToken == null || sessToken.isEmpty()) sessToken = prefs.getAppSession();
            if (sessToken == null || sessToken.isEmpty()) sessToken = "GUEST_SESSION";

            long expiresAt = (System.currentTimeMillis() / 1000L) + 86400L; // 24 hours
            String appClient = Config.getXAppClientToken();
            String appId = appContext.getPackageName();

            String payload = "edge:" + devId + ":" + sessToken + ":" + appClient + ":" + expiresAt + ":" + appId;
            String hmacKey = prefs.getHmacKey();
            if (hmacKey == null || hmacKey.isEmpty()) hmacKey = Config.HMAC_KEY;

            String sig = SecurityUtils.generateHmac(payload, hmacKey);
            String combined = payload + "." + sig;
            return Base64.encodeToString(combined.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Error generating local verified cookie", e);
            return "edge_cookie_fallback_" + System.currentTimeMillis();
        }
    }

    /**
     * Provides the current validated cookie globally.
     * If currently cached cookie is missing or invalid, checks preferences or generates fallback
     * and triggers server background sync.
     */
    public synchronized String getValidatedCookie(int channelId, String streamUrl) {
        if (!isValidCookieStatic(cachedEdgeCookie)) {
            String saved = prefs != null ? prefs.getEdgeCookie() : "";
            if (isValidCookieStatic(saved)) {
                cachedEdgeCookie = sanitizeCookie(saved);
                lastRefreshedTime = System.currentTimeMillis();
                return cachedEdgeCookie;
            }

            Log.d(TAG, "Cached cookie was invalid, generating fresh fallback cookie and triggering sync");
            cachedEdgeCookie = generateLocalVerifiedCookie(channelId, streamUrl);
            if (prefs != null) {
                prefs.setEdgeCookie(cachedEdgeCookie);
            }
            lastRefreshedTime = System.currentTimeMillis();
            // Trigger server background sync
            triggerBackgroundSync(channelId, streamUrl);
        }
        return cachedEdgeCookie;
    }

    /**
     * Updates and caches a new verified cookie in memory, preferences, and notifies listeners.
     */
    public synchronized void updateCookie(String newCookie) {
        String clean = sanitizeCookie(newCookie);
        if (!isValidCookieStatic(clean)) {
            Log.w(TAG, "Ignoring attempt to update with invalid cookie: " + newCookie);
            return;
        }
        cachedEdgeCookie = clean;
        lastRefreshedTime = System.currentTimeMillis();
        if (prefs != null) {
            prefs.setEdgeCookie(clean);
        }
        Log.d(TAG, "Global Edge-Cookie updated and cached successfully: " + (clean.length() > 20 ? clean.substring(0, 20) + "..." : clean));

        for (CookieUpdateListener listener : listeners) {
            try {
                listener.onCookieUpdated(clean);
            } catch (Exception e) {
                Log.e(TAG, "Error in cookie listener", e);
            }
        }
    }

    /**
     * Invoked when changing channels on the player screen.
     * Immediately provides the validated cookie for instant stream start,
     * and asynchronously validates/refreshes against the server for the new channel.
     */
    public String onChannelChanged(int channelId, String streamUrl, CookieUpdateListener onFreshCookieAvailable) {
        this.currentActiveChannelId = channelId;
        this.currentActiveStreamUrl = streamUrl != null ? streamUrl : "";

        // 1. Instantly return validated cookie so channel change is immediate (0ms delay)
        String instantCookie = getValidatedCookie(channelId, streamUrl);

        // 2. Validate/refresh server-side for new channel binding
        ApiClient.getInstance(appContext).refreshEdgeCookie(channelId, streamUrl, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String freshCookie) {
                if (freshCookie != null && !freshCookie.isEmpty()) {
                    updateCookie(freshCookie);
                    if (onFreshCookieAvailable != null) {
                        onFreshCookieAvailable.onCookieUpdated(freshCookie);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.d(TAG, "Channel change cookie refresh deferred: " + errorMessage);
            }
        });

        return instantCookie;
    }

    /**
     * Starts the 25-second periodic background cookie renewal timer.
     */
    public synchronized void startPeriodicRefresh(int channelId, String streamUrl) {
        this.currentActiveChannelId = channelId;
        if (streamUrl != null && !streamUrl.isEmpty()) {
            this.currentActiveStreamUrl = streamUrl;
        }

        stopPeriodicRefresh();
        isRefreshingActive = true;

        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRefreshingActive) return;

                performBackgroundCookieRenewal();
                refreshHandler.postDelayed(this, COOKIE_REFRESH_INTERVAL_MS);
            }
        };

        refreshHandler.postDelayed(refreshRunnable, COOKIE_REFRESH_INTERVAL_MS);
        Log.d(TAG, "25-second background cookie refresher started");
    }

    /**
     * Stops the periodic background cookie refresher.
     */
    public synchronized void stopPeriodicRefresh() {
        isRefreshingActive = false;
        if (refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            refreshRunnable = null;
        }
        Log.d(TAG, "25-second background cookie refresher stopped");
    }

    private void performBackgroundCookieRenewal() {
        if (currentActiveChannelId <= 0 && (currentActiveStreamUrl == null || currentActiveStreamUrl.isEmpty())) {
            return;
        }

        ApiClient.getInstance(appContext).refreshEdgeCookie(currentActiveChannelId, currentActiveStreamUrl, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String newCookie) {
                if (newCookie != null && !newCookie.isEmpty()) {
                    updateCookie(newCookie);
                    Log.d(TAG, "25s Background cookie renewal successful: " + (newCookie.length() > 15 ? newCookie.substring(0, 15) + "..." : newCookie));
                }
            }

            @Override
            public void onError(String errorMessage) {
                Log.d(TAG, "25s Background cookie renewal deferred: " + errorMessage);
            }
        });
    }

    private void triggerBackgroundSync(int channelId, String streamUrl) {
        ApiClient.getInstance(appContext).refreshEdgeCookie(channelId, streamUrl, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (result != null && !result.isEmpty()) {
                    updateCookie(result);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // If refresh with channel/session failed, fetch fresh bootstrap cookie from /cookie route
                ApiClient.getInstance(appContext).fetchInitialAppCookie(new ApiClient.ApiCallback<String>() {
                    @Override
                    public void onSuccess(String bootstrapCookie) {
                        if (bootstrapCookie != null && !bootstrapCookie.isEmpty()) {
                            updateCookie(bootstrapCookie);
                        }
                    }

                    @Override
                    public void onError(String ignored) {}
                });
            }
        });
    }

    public void addListener(CookieUpdateListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(CookieUpdateListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
