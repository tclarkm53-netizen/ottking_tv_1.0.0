package com.ottking.devcode.utils;

public class Dev {

    public static final String APP_NAME = "OTT KING";
    public static final String APP_TAGLINE = "Ultra HD Live TV & Streaming";
    
    public static final String DEV_NAME = "OTT KING Technical Operations Team";
    public static final String DEV_EMAIL = "dev-support@ottking.com";
    public static final String DEV_LOCATION = "Global Distribution Network";
    public static final String COPYRIGHT = "Copyright © 2026 OTT KING. All Rights Reserved.";

    public static final String ARCHITECTURE = "Java Native + Material Design 3";
    public static final String PLAYBACK_ENGINE = "ExoPlayer Media3 (HLS/DASH/TS)";
    public static final String SECURITY_SPECS = "AES-128 Encrypted API + HMAC Signature";
    public static final String DATABASE_ENGINE = "Local SQLite Room Engine";

    public static String getAppInfo(String versionName, int versionCode) {
        return APP_NAME + " - " + APP_TAGLINE + "\n\n" +
               "Version: " + versionName + " (Build " + versionCode + ")\n" +
               "Architecture: " + ARCHITECTURE + "\n" +
               "Playback Engine: " + PLAYBACK_ENGINE + "\n" +
               "Security: " + SECURITY_SPECS + "\n" +
               "Database: " + DATABASE_ENGINE;
    }

    public static String getDevInfo() {
        return "Developed by: " + DEV_NAME + "\n" +
               "Contact: " + DEV_EMAIL + "\n" +
               "Location: " + DEV_LOCATION + "\n" +
               COPYRIGHT;
    }

    public static String getDevInfo(android.content.Context context) {
        String copyrightStr = (context != null) ? context.getString(com.ottking.devcode.R.string.copyright_text) : COPYRIGHT;
        return "Developed by: " + DEV_NAME + "\n" +
               "Contact: " + DEV_EMAIL + "\n" +
               "Location: " + DEV_LOCATION + "\n" +
               copyrightStr;
    }
}
