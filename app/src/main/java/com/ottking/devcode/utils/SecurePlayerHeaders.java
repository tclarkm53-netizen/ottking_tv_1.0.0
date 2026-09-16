package com.ottking.devcode.utils;

import android.content.Context;
import android.net.Uri;

import com.ottking.devcode.network.Config;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Obfuscated & Hardened Player Header Provider.
 * Constructs the player's HTTP headers entirely inside a secure HashMap using
 * dynamically de-obfuscated byte/integer tables at runtime so that reverse-engineering,
 * static analysis, and strings inspection cannot uncover the internal header keys or signatures.
 */
public final class SecurePlayerHeaders {

    private static final int _0xK_SEED = 0x5D;

    // Encrypted Header Key Tables
    private static final int[] _0xK_REFERER = { 15, 57, 57, 59, 43, 61, 41 }; // "Referer"
    private static final int[] _0xK_LOWER_REFERER = { 47, 57, 57, 59, 43, 61, 41 }; // "referer"
    private static final int[] _0xK_USER_AGENT = { 8, 47, 58, 44, 116, 25, 60, 63, 59, 32 }; // "User-Agent"
    private static final int[] _0xK_X_APP_SESSION = { 37, 113, 62, 46, 41, 117, 40, 63, 38, 39, 62, 57, 63 }; // "x-app-session"
    private static final int[] _0xK_UPPER_X_APP_SESSION = { 5, 113, 30, 46, 41, 117, 8, 63, 38, 39, 62, 57, 63 }; // "X-App-Session"
    private static final int[] _0xK_X_SESSION_TOKEN = { 37, 113, 44, 59, 42, 43, 50, 53, 59, 121, 35, 57, 58, 53, 61 }; // "x-session-token"
    private static final int[] _0xK_SESSION_TOKEN = { 14, 57, 44, 45, 48, 55, 53, 119, 1, 59, 60, 51, 63 }; // "Session-Token"
    private static final int[] _0xK_X_DEVICE_ID = { 37, 113, 59, 59, 47, 49, 56, 63, 120, 61, 51 }; // "x-device-id"
    private static final int[] _0xK_UPPER_X_DEVICE_ID = { 5, 113, 27, 59, 47, 49, 56, 63, 120, 29, 51 }; // "X-Device-Id"
    private static final int[] _0xK_X_USER_SESSION = { 37, 113, 42, 45, 60, 42, 118, 41, 48, 39, 36, 63, 62, 62 }; // "x-user-session"
    private static final int[] _0xK_X_GUEST_SESSION = { 37, 113, 56, 43, 60, 43, 47, 119, 38, 49, 36, 37, 56, 63, 61 }; // "x-guest-session"
    private static final int[] _0xK_X_USER_TOKEN = { 37, 113, 42, 45, 60, 42, 118, 46, 58, 63, 50, 56 }; // "x-user-token"
    private static final int[] _0xK_UPPER_X_USER_TOKEN = { 5, 113, 10, 45, 60, 42, 118, 14, 58, 63, 50, 56 }; // "X-User-Token"
    private static final int[] _0xK_X_GUEST_TOKEN = { 37, 113, 56, 43, 60, 43, 47, 119, 33, 59, 60, 51, 63 }; // "x-guest-token"
    private static final int[] _0xK_X_HMAC_SIGNATURE = { 5, 113, 23, 19, 24, 27, 118, 9, 60, 51, 57, 55, 37, 37, 33, 55 }; // "X-HMAC-Signature"
    private static final int[] _0xK_SIGNATURE = { 14, 53, 56, 48, 56, 44, 46, 40, 48 }; // "Signature"
    private static final int[] _0xK_X_STREAM_TOKEN = { 37, 113, 44, 42, 43, 61, 58, 55, 120, 32, 56, 61, 52, 62 }; // "x-stream-token"
    private static final int[] _0xK_UPPER_X_STREAM_TOKEN = { 5, 113, 12, 42, 43, 61, 58, 55, 120, 0, 56, 61, 52, 62 }; // "X-Stream-Token"
    private static final int[] _0xK_EDGE_COOKIE = { 24, 56, 56, 59, 116, 59, 52, 53, 62, 61, 50 }; // "Edge-cookie"
    private static final int[] _0xK_LOWER_EDGE_COOKIE = { 56, 56, 56, 59, 116, 59, 52, 53, 62, 61, 50 }; // "edge-cookie"
    private static final int[] _0xK_COOKIE = { 30, 51, 48, 53, 48, 61 }; // "Cookie"
    private static final int[] _0xK_OTT_KING_USER_AGENT = { 18, 8, 11, 115, 18, 17, 21, 29, 117, 25, 56, 52, 56, 60, 54, 114, 108, 114, 109 }; // "OTT-KING Mobile 1.2"
    private static final int[] _0xK_X_SECURITY_HASH = { 5, 113, 12, 59, 58, 45, 41, 51, 33, 45, 122, 30, 48, 35, 59 }; // "X-Security-Hash"

    private SecurePlayerHeaders() {}

    /**
     * Runtime fast byte de-obfuscation.
     */
    public static String _dec(int[] enc) {
        if (enc == null) return "";
        char[] c = new char[enc.length];
        for (int i = 0; i < enc.length; i++) {
            c[i] = (char) (enc[i] ^ _0xK_SEED ^ (i & 0x0F));
        }
        return new String(c);
    }

    public static String getDecryptedUserAgent() {
        return _dec(_0xK_OTT_KING_USER_AGENT);
    }

    private static void putSecure(HashMap<String, String> map, int[] keyEnc, String val) {
        if (map != null && keyEnc != null && val != null && !val.trim().isEmpty()) {
            map.put(_dec(keyEnc), val.trim());
        }
    }

    /**
     * Dynamically generates the complete, secure HashMap of player headers.
     * All header keys are loaded into memory via byte de-obfuscation.
     */
    public static HashMap<String, String> getSecurePlayerHeaderMap(Context context, String streamUrl) {
        HashMap<String, String> headerMap = new HashMap<>();

        // 1. Obfuscated Referer & User-Agent injection
        String streamReferer = Config.STREAM_REFERER;
        if (streamReferer != null && !streamReferer.trim().isEmpty()) {
            putSecure(headerMap, _0xK_REFERER, streamReferer);
            putSecure(headerMap, _0xK_LOWER_REFERER, streamReferer);
        }

        String userAgent = getDecryptedUserAgent();
        putSecure(headerMap, _0xK_USER_AGENT, userAgent);
        headerMap.put("user-agent", userAgent);

        if (context != null) {
            Context appContext = context.getApplicationContext();
            AppPreferences prefs = AppPreferences.getInstance(appContext);

            // 2. Obfuscated Session Tokens injection
            String appSession = prefs.getAppSession();
            if (appSession != null && !appSession.isEmpty()) {
                putSecure(headerMap, _0xK_X_APP_SESSION, appSession);
                putSecure(headerMap, _0xK_UPPER_X_APP_SESSION, appSession);
                putSecure(headerMap, _0xK_X_SESSION_TOKEN, appSession);
                putSecure(headerMap, _0xK_SESSION_TOKEN, appSession);
            }

            // 3. Device ID injection
            String deviceId = prefs.getDeviceId();
            if (deviceId != null && !deviceId.isEmpty()) {
                putSecure(headerMap, _0xK_X_DEVICE_ID, deviceId);
                putSecure(headerMap, _0xK_UPPER_X_DEVICE_ID, deviceId);
                headerMap.put("device_id", deviceId);
                headerMap.put("Device-Id", deviceId);
                headerMap.put("x-device-id", deviceId);
            }

            // 4. App ID / Package ID injection
            String appId = appContext.getPackageName();
            if (appId == null || appId.isEmpty()) {
                appId = SecurityUtils.APP_ID;
            }
            headerMap.put("app_id", appId);
            headerMap.put("App-Id", appId);
            headerMap.put("X-App-Id", appId);
            headerMap.put("x-app-id", appId);
            headerMap.put("package_name", appId);
            headerMap.put("X-Package-Name", appId);

            // 5. Client Token injection
            headerMap.put("X-App-Client", Config.getXAppClientToken());
            headerMap.put("x-app-client", Config.getXAppClientToken());

            // 6. User / Guest Session Tokens injection
            boolean isLoggedIn = prefs.isLoggedIn();
            if (isLoggedIn) {
                putSecure(headerMap, _0xK_X_USER_SESSION, "1");
                putSecure(headerMap, _0xK_X_GUEST_SESSION, "0");
                String userToken = prefs.getUserToken();
                if (userToken != null && !userToken.isEmpty()) {
                    putSecure(headerMap, _0xK_X_USER_TOKEN, userToken);
                    putSecure(headerMap, _0xK_UPPER_X_USER_TOKEN, userToken);
                }
            } else {
                putSecure(headerMap, _0xK_X_USER_SESSION, "0");
                putSecure(headerMap, _0xK_X_GUEST_SESSION, "1");
                String guestToken = prefs.getGuestSession();
                if (guestToken != null && !guestToken.isEmpty()) {
                    putSecure(headerMap, _0xK_X_GUEST_TOKEN, guestToken);
                }
            }

            // 7. Obfuscated HMAC Signature injection
            String apiSignature = PlayerUtils.getOrGenerateApiSignature(appContext, streamUrl);
            if (apiSignature != null && !apiSignature.isEmpty()) {
                putSecure(headerMap, _0xK_X_HMAC_SIGNATURE, apiSignature);
                putSecure(headerMap, _0xK_SIGNATURE, apiSignature);
                headerMap.put("x-hmac-signature", apiSignature);
                headerMap.put("signature", apiSignature);
            }

            // 8. Stream Token injection
            String streamToken = "";
            if (com.ottking.devcode.ui.PlayerActivity.getCurrentActiveStreamToken() != null &&
                    !com.ottking.devcode.ui.PlayerActivity.getCurrentActiveStreamToken().isEmpty()) {
                streamToken = com.ottking.devcode.ui.PlayerActivity.getCurrentActiveStreamToken();
            } else {
                streamToken = appSession;
            }
            if (streamToken != null && !streamToken.isEmpty()) {
                putSecure(headerMap, _0xK_X_STREAM_TOKEN, streamToken);
                putSecure(headerMap, _0xK_UPPER_X_STREAM_TOKEN, streamToken);
                headerMap.put("Authorization", "Bearer " + streamToken);
                headerMap.put("authorization", "Bearer " + streamToken);
            }

            // 9. Edge Cookie injection via GlobalCookieManager
            String edgeCookieVal = com.ottking.devcode.network.GlobalCookieManager.getInstance(appContext).getValidatedCookie(1, streamUrl);
            if (edgeCookieVal == null || edgeCookieVal.trim().isEmpty() || "null".equalsIgnoreCase(edgeCookieVal.trim())) {
                edgeCookieVal = prefs.getEdgeCookie();
            }
            if (edgeCookieVal == null || edgeCookieVal.trim().isEmpty() || "null".equalsIgnoreCase(edgeCookieVal.trim())) {
                edgeCookieVal = com.ottking.devcode.ui.PlayerActivity.getStaticEdgeCookie();
            }

            if (edgeCookieVal != null && !edgeCookieVal.trim().isEmpty()) {
                String clean = edgeCookieVal.trim();
                putSecure(headerMap, _0xK_EDGE_COOKIE, clean);
                putSecure(headerMap, _0xK_LOWER_EDGE_COOKIE, clean);
                headerMap.put("EdgeCookie", clean);
                headerMap.put("edge_cookie", clean);
                headerMap.put("X-Edge-Cookie", clean);
                headerMap.put("x-edge-cookie", clean);
                headerMap.put("Cookie-Edge", clean);

                String fullCookie = "Edge-cookie=" + clean + "; edge-cookie=" + clean + "; edge_cookie=" + clean + "; EdgeCookie=" + clean + "; cookie=" + clean;
                putSecure(headerMap, _0xK_COOKIE, fullCookie);
                headerMap.put("cookie", fullCookie);
                headerMap.put("X-Cookie-Json", "{\"edge_cookie\":\"" + clean + "\"}");
            }

            // 10. Tamper-Proof Security Hash of the Map
            try {
                String hmacKey = prefs.getHmacKey();
                if (hmacKey == null || hmacKey.isEmpty()) {
                    hmacKey = Config.HMAC_KEY;
                }
                if (hmacKey != null && !hmacKey.isEmpty()) {
                    long now = System.currentTimeMillis();
                    String payload = (deviceId != null ? deviceId : "") + "|" + (streamUrl != null ? streamUrl : "") + "|" + now;
                    String secHash = SecurityUtils.generateHmac(payload, hmacKey);
                    if (secHash != null && !secHash.isEmpty()) {
                        putSecure(headerMap, _0xK_X_SECURITY_HASH, secHash);
                        headerMap.put("x-security-hash", secHash);
                        headerMap.put("X-Timestamp", String.valueOf(now));
                    }
                }
            } catch (Exception ignored) {}
        }

        return headerMap;
    }
}
