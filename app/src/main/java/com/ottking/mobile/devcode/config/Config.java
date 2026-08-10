package com.ottking.mobile.devcode.config;

/**
 * Global App Configuration Manager
 * Centralized store for API URLs, security credentials, route definitions, and app settings.
 */
public class Config {

    // Primary API URL & Base Endpoint
    public static final String BASE_URL = "https://verify-app.alwaysdata.net/new/mobile/v1/";
    public static final String API_URL = "https://verify-app.alwaysdata.net/new/mobile/v1/";

    // Security & Authentication Credentials
    public static final String API_KEY = "iptv_sec_api_key_2026";
    public static final String HMAC_KEY = "iptv_hmac_secret_key_889900";
    public static final String ENCRYPTION_KEY = "12345678901234567890123456789012";

    // Header Keys
    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_HMAC_SIGNATURE = "X-HMAC-Signature";

    // Dedicated Server API Routes for Easy Management
    public static final String ROUTE_LIVE_TV = "live_tv";          // Live TV Channels Route ("live_tv")
    public static final String ROUTE_CATEGORIES = "categories";    // Categories & Icons Route ("categories")
    public static final String ROUTE_MOVIES = "movies";            // Movies / VOD Route ("movies")
    public static final String ROUTE_LOGIN = "login";             // User Login Authentication Route
    public static final String ROUTE_SESSION_CHECK = "session_check"; // Session Validation & Subscription Status Route
    public static final String ROUTE_LOGOUT = "logout";           // User Logout Route
    public static final String ROUTE_APP_DATA = "app_data";       // Master App Data Sync Route
    public static final String ROUTE_APP_CONFIG = "app_config";   // Server App Configuration & Category Icons Route
    public static final String ROUTE_UPDATE = "update";           // App Software Update Check Route
    public static final String ROUTE_CHANNELS = "channels";       // General Channels Alias Route
    public static final String ROUTE_TV = "tv";                   // TV Alias Route
    public static final String ROUTE_SERIES = "series";           // Series Route
    public static final String ROUTE_HEALTH = "health";           // Server Health Check Route

    // Network Timeouts (in seconds)
    public static final int CONNECT_TIMEOUT_SECONDS = 15;
    public static final int READ_TIMEOUT_SECONDS = 15;
    public static final int WRITE_TIMEOUT_SECONDS = 15;

    // App Preferences Keys
    public static final String PREF_NAME = "iptv_preferences";

    // Maintenance Settings
    public static final boolean REQUIRE_SERVER_DATA_TO_RUN = true;
    public static final String MAINTENANCE_STATUS_CODE = "MAINTENANCE:";
}
