package com.example.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.HomeActivity;
import com.example.R;

import java.util.HashSet;
import java.util.Set;

public class NotificationHelper {

    public static final String CHANNEL_ID = "server_announcements_channel";
    public static final String CHANNEL_NAME = "Server Announcements & Updates";
    private static final String PREF_NOTIFICATIONS = "shown_server_notifications";
    private static final String KEY_SHOWN_IDS = "shown_ids";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications, stream updates, and announcements from the server.");
            channel.enableLights(true);
            channel.enableVibration(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void showNotificationIfNew(Context context, String notificationId, String title, String message) {
        if (context == null || notificationId == null || notificationId.trim().isEmpty()) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREF_NOTIFICATIONS, Context.MODE_PRIVATE);
        Set<String> shownIds = prefs.getStringSet(KEY_SHOWN_IDS, new HashSet<>());
        if (shownIds.contains(notificationId)) {
            return; // Already notified the user for this notification
        }

        // Mark as shown
        Set<String> updated = new HashSet<>(shownIds);
        updated.add(notificationId);
        prefs.edit().putStringSet(KEY_SHOWN_IDS, updated).apply();

        createNotificationChannel(context);

        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("open_tab", "notifications");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId.hashCode(),
                intent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : "IPTV Announcement")
                .setContentText(message != null ? message : "New update from server.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
            notificationManager.notify(Math.abs(notificationId.hashCode()), builder.build());
        } catch (SecurityException ignored) {
            // Permission not granted on Android 13+
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
