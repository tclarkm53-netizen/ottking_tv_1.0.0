package com.ottking.devcode.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import com.ottking.devcode.preferences.AppPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NetworkUtils {

    /**
     * Checks whether the device has an active and valid internet connection (WiFi, Ethernet, Cellular, or VPN).
     */
    public static boolean isNetworkConnected(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork == null) return false;
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                if (caps == null) return false;

                return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                        || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
            } else {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifies if the user's premium subscription is currently active or expired based on server expiry date.
     */
    public static boolean isSubscriptionActive(Context context) {
        if (context == null) return false;
        try {
            AppPreferences prefs = AppPreferences.getInstance(context);
            String sessionToken = prefs.getSessionToken();
            if (sessionToken == null || sessionToken.trim().isEmpty()) {
                return false;
            }

            String expiry = prefs.getUserExpiry();
            if (expiry == null || expiry.trim().isEmpty() || expiry.equalsIgnoreCase("N/A") || expiry.equalsIgnoreCase("Expired")) {
                String pkg = prefs.getUserPackage();
                return pkg != null && !pkg.toLowerCase().contains("free") && !pkg.toLowerCase().contains("expired");
            }

            if (expiry.equalsIgnoreCase("Never") || expiry.equalsIgnoreCase("Lifetime") || expiry.equalsIgnoreCase("Unlimited")) {
                return true;
            }

            Date expiryDate = parseDate(expiry);
            if (expiryDate != null) {
                Date now = new Date();
                boolean isActive = now.before(expiryDate);
                if (!isActive) {
                    // Update user package state locally to reflect expiration
                    prefs.setUserPackage("Expired (Free Tier)");
                }
                return isActive;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String[] formats = new String[]{
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss",
                "dd-MM-yyyy",
                "MM/dd/yyyy HH:mm:ss",
                "MM/dd/yyyy"
        };
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                sdf.setLenient(false);
                return sdf.parse(dateStr.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}
