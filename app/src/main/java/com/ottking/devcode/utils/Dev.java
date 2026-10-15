package com.ottking.devcode.utils;

public class Dev {

    // App Information
    public static final String APP_NAME = "OTT KING";
    public static final String APP_COMPANY = "OTT KING Networks";

    // Contact Information
    public static final String CONTACT_WEBSITE = "https://ottking.com";
    public static final String CONTACT_PHONE = "+880 1700-000000";
    public static final String CONTACT_EMAIL = "ottkingdev@gmail.com";

    // Developer Information
    public static final String DEVELOPED_BY = "AnirbanSumon";
    public static final String DEV_CONTACT = "ottkingdev@gmail.com";
    public static final String DEV_LOCATION = "ottking global development & distribution Network";
    public static final String COPYRIGHT = "Copyright © OTTKING all Right reserved.";

    public static final String ARCHITECTURE = "Java Native + Material Design 3";
    public static final String PLAYBACK_ENGINE = "ExoPlayer Media3 (HLS/DASH/TS)";

    public static String getAppInfo(String versionName, int versionCode) {
        return "App Name: " + APP_NAME + "\n" +
               "Version: " + versionName + " (Build " + versionCode + ")\n" +
               "Company: " + APP_COMPANY + "\n\n" +
               "--- Contact ---\n" +
               "Website: " + CONTACT_WEBSITE + "\n" +
               "Phone: " + CONTACT_PHONE + "\n" +
               "Email: " + CONTACT_EMAIL + "\n\n" +
               "Playback Engine: " + PLAYBACK_ENGINE;
    }

    public static String getDevInfo() {
        return "Developed By: " + DEVELOPED_BY + "\n" +
               "Contract: " + DEV_CONTACT + "\n" +
               "Location: " + DEV_LOCATION + "\n\n" +
               COPYRIGHT;
    }

    public static String getDevInfo(android.content.Context context) {
        String copyrightStr = (context != null) ? context.getString(com.ottking.devcode.R.string.copyright_text) : COPYRIGHT;
        return "Developed By: " + DEVELOPED_BY + "\n" +
               "Contract: " + DEV_CONTACT + "\n" +
               "Location: " + DEV_LOCATION + "\n\n" +
               copyrightStr;
    }
}
