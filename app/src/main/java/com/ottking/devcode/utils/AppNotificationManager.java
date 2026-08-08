package com.ottking.devcode.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.preferences.AppPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppNotificationManager {

    private static final String PREF_NAME = "ott_king_notifications";
    private static final String KEY_READ_IDS = "read_notification_ids";
    private static final String KEY_LAST_READ_CHANNEL_COUNT = "last_read_channel_count";

    public interface OnNotificationsUpdatedListener {
        void onNotificationsUpdated(List<NotificationItem> notifications, int unreadCount);
    }

    private final Context context;
    private final SharedPreferences prefs;

    public AppNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isRead(String notificationId) {
        if (notificationId == null || notificationId.trim().isEmpty()) return true;

        if (prefs.getBoolean("key_read_" + notificationId, false)) {
            return true;
        }

        String readStr = prefs.getString("read_notification_ids_str", "");
        if (readStr.contains("," + notificationId + ",")) {
            return true;
        }

        try {
            Set<String> set = prefs.getStringSet(KEY_READ_IDS, null);
            if (set != null && set.contains(notificationId)) {
                return true;
            }
        } catch (Exception ignored) {}

        if (notificationId.startsWith("notif_channel_added")) {
            if (prefs.getBoolean("key_read_notif_channel_added_global", false)) {
                return true;
            }
            int lastReadCount = prefs.getInt(KEY_LAST_READ_CHANNEL_COUNT, 0);
            try {
                String[] parts = notificationId.split("_");
                if (parts.length > 0) {
                    int count = Integer.parseInt(parts[parts.length - 1]);
                    if (count <= lastReadCount) {
                        return true;
                    }
                }
            } catch (Exception ignored) {}
        }

        return false;
    }

    public synchronized void markAsRead(String notificationId, Runnable onDone) {
        if (notificationId != null && !notificationId.trim().isEmpty()) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("key_read_" + notificationId, true);

            String readStr = prefs.getString("read_notification_ids_str", "");
            if (!readStr.contains("," + notificationId + ",")) {
                if (readStr.isEmpty()) {
                    readStr = ",";
                }
                readStr = readStr + notificationId + ",";
                editor.putString("read_notification_ids_str", readStr);
            }

            if (notificationId.startsWith("notif_channel_added")) {
                editor.putBoolean("key_read_notif_channel_added_global", true);
                editor.putString("read_notification_ids_str", readStr + "notif_channel_added_global,");
                try {
                    String[] parts = notificationId.split("_");
                    if (parts.length > 0) {
                        int count = Integer.parseInt(parts[parts.length - 1]);
                        editor.putInt(KEY_LAST_READ_CHANNEL_COUNT, count);
                    }
                } catch (Exception ignored) {}
            }

            Set<String> set = new HashSet<>();
            try {
                Set<String> existing = prefs.getStringSet(KEY_READ_IDS, null);
                if (existing != null) set.addAll(existing);
            } catch (Exception ignored) {}
            set.add(notificationId);
            editor.remove(KEY_READ_IDS);
            editor.putStringSet(KEY_READ_IDS, set);

            editor.commit();
        }

        if (onDone != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
        }
    }

    public synchronized void markAllAsRead(List<NotificationItem> items, Runnable onDone) {
        if (items != null) {
            SharedPreferences.Editor editor = prefs.edit();
            String readStr = prefs.getString("read_notification_ids_str", "");
            if (readStr.isEmpty()) readStr = ",";

            Set<String> set = new HashSet<>();
            try {
                Set<String> existing = prefs.getStringSet(KEY_READ_IDS, null);
                if (existing != null) set.addAll(existing);
            } catch (Exception ignored) {}

            for (NotificationItem item : items) {
                if (item != null && item.getId() != null && !item.getId().trim().isEmpty()) {
                    String id = item.getId();
                    editor.putBoolean("key_read_" + id, true);
                    if (!readStr.contains("," + id + ",")) {
                        readStr = readStr + id + ",";
                    }
                    set.add(id);
                    if (id.startsWith("notif_channel_added")) {
                        editor.putBoolean("key_read_notif_channel_added_global", true);
                        readStr = readStr + "notif_channel_added_global,";
                        try {
                            String[] parts = id.split("_");
                            if (parts.length > 0) {
                                int count = Integer.parseInt(parts[parts.length - 1]);
                                editor.putInt(KEY_LAST_READ_CHANNEL_COUNT, count);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            editor.putString("read_notification_ids_str", readStr);
            editor.remove(KEY_READ_IDS);
            editor.putStringSet(KEY_READ_IDS, set);
            editor.commit();
        }

        if (onDone != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(onDone);
        }
    }

    public static String getChannelsSignature(List<ChannelEntity> channels) {
        if (channels == null || channels.isEmpty()) {
            return "empty";
        }
        return "count_" + channels.size();
    }

    public void loadNotifications(OnNotificationsUpdatedListener listener) {
        new Thread(() -> {
            List<NotificationItem> list = new ArrayList<>();

            // 1. Channel Database Sync Notification ("New Channel Added")
            try {
                AppDatabase db = AppDatabase.getInstance(context);
                List<ChannelEntity> channels = db.channelDao().getAllChannelsSync();
                if (channels != null && !channels.isEmpty()) {
                    int lastReadCount = prefs.getInt(KEY_LAST_READ_CHANNEL_COUNT, 0);
                    String chanNotifId = "notif_channel_added_" + channels.size();
                    if (channels.size() > lastReadCount && !isRead(chanNotifId) && !isRead("notif_channel_added_global")) {
                        list.add(new NotificationItem(
                                chanNotifId,
                                "New Channel Added",
                                "Synced " + channels.size() + " HD & 4K streams (Sports, News, Movies) from OTT KING server.",
                                "Today",
                                R.drawable.ic_tv,
                                "CHANNEL",
                                false,
                                "Watch Live"
                        ));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Deliver initial local notification fast if unread
            List<NotificationItem> initialFiltered = new ArrayList<>();
            for (NotificationItem item : list) {
                if (!isRead(item.getId())) {
                    initialFiltered.add(item);
                }
            }
            if (listener != null) {
                List<NotificationItem> finalInitial = new ArrayList<>(initialFiltered);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        listener.onNotificationsUpdated(finalInitial, finalInitial.size()));
            }

            // 2. Fetch Server Notifications (Global + User/Package targeted)
            AppPreferences appPrefs = AppPreferences.getInstance(context);
            String username = appPrefs.getUsername();
            String userPackage = appPrefs.getUserPackage();

            ApiClient.getInstance(context).fetchServerNotifications(username, userPackage, new ApiClient.ApiCallback<List<NotificationItem>>() {
                @Override
                public void onSuccess(List<NotificationItem> serverNotifs) {
                    List<NotificationItem> combined = new ArrayList<>(initialFiltered);

                    if (serverNotifs != null) {
                        for (NotificationItem sItem : serverNotifs) {
                            if (sItem != null && !isRead(sItem.getId())) {
                                boolean exists = false;
                                for (NotificationItem existing : combined) {
                                    if (existing.getId().equals(sItem.getId())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    combined.add(sItem);
                                }
                            }
                        }
                    }

                    // 3. Check App Update from Server asynchronously
                    ApiClient.getInstance(context).checkAppUpdate(new ApiClient.ApiCallback<UpdateInfo>() {
                        @Override
                        public void onSuccess(UpdateInfo info) {
                            List<NotificationItem> finalList = new ArrayList<>(combined);
                            if (info != null && info.isHasUpdate()) {
                                String updateId = "notif_app_update_v" + info.getVersionName();
                                if (!isRead(updateId)) {
                                    NotificationItem updateItem = new NotificationItem(
                                            updateId,
                                            "New App Update Available (v" + info.getVersionName() + ")",
                                            info.getChangelog() != null && !info.getChangelog().trim().isEmpty()
                                                    ? info.getChangelog()
                                                    : "A new version of OTT KING is available with improved performance and stability.",
                                            "Server Release",
                                            R.drawable.ic_update,
                                            "UPDATE",
                                            false,
                                            "Update Now"
                                    );

                                    boolean exists = false;
                                    for (NotificationItem item : finalList) {
                                        if (item.getId().equals(updateId)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists) {
                                        finalList.add(0, updateItem);
                                    }
                                }
                            }

                            deliverFiltered(finalList, listener);
                        }

                        @Override
                        public void onError(String errorMessage) {
                            deliverFiltered(combined, listener);
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    deliverFiltered(initialFiltered, listener);
                }
            });

        }).start();
    }

    private void deliverFiltered(List<NotificationItem> items, OnNotificationsUpdatedListener listener) {
        List<NotificationItem> filtered = new ArrayList<>();
        if (items != null) {
            for (NotificationItem item : items) {
                if (item != null && !isRead(item.getId())) {
                    filtered.add(item);
                }
            }
        }
        int unreadCount = filtered.size();
        if (listener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    listener.onNotificationsUpdated(filtered, unreadCount));
        }
    }
}
