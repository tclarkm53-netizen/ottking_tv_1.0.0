package com.ottking.devcode.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.network.ApiClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppNotificationManager {

    private static final String PREF_NAME = "ott_king_notifications";
    private static final String KEY_READ_IDS = "read_notification_ids";

    public interface OnNotificationsUpdatedListener {
        void onNotificationsUpdated(List<NotificationItem> notifications, int unreadCount);
    }

    private final Context context;
    private final SharedPreferences prefs;

    public AppNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public Set<String> getReadNotificationIds() {
        return new HashSet<>(prefs.getStringSet(KEY_READ_IDS, new HashSet<>()));
    }

    public void markAsRead(String notificationId, Runnable onDone) {
        Set<String> readIds = getReadNotificationIds();
        readIds.add(notificationId);
        prefs.edit().putStringSet(KEY_READ_IDS, readIds).apply();
        if (onDone != null) onDone.run();
    }

    public void markAllAsRead(List<NotificationItem> items, Runnable onDone) {
        Set<String> readIds = getReadNotificationIds();
        for (NotificationItem item : items) {
            readIds.add(item.getId());
        }
        prefs.edit().putStringSet(KEY_READ_IDS, readIds).apply();
        if (onDone != null) onDone.run();
    }

    public void loadNotifications(OnNotificationsUpdatedListener listener) {
        new Thread(() -> {
            List<NotificationItem> list = new ArrayList<>();
            Set<String> readIds = getReadNotificationIds();

            // 1. Channel Database Sync Notification
            AppDatabase db = AppDatabase.getInstance(context);
            List<com.ottking.devcode.db.ChannelEntity> channels = db.channelDao().getAllChannelsSync();
            int channelCount = channels != null ? channels.size() : 0;
            if (channelCount > 0) {
                int maxId = 0;
                for (com.ottking.devcode.db.ChannelEntity c : channels) {
                    if (c.id > maxId) maxId = c.id;
                }
                String chanId = "notif_channel_sync_" + channelCount + "_max_" + maxId;
                if (!readIds.contains(chanId)) {
                    list.add(new NotificationItem(
                            chanId,
                            "New Channel Added",
                            "Synced " + channelCount + " HD & 4K streams (Sports, News, Movies) from OTT KING server.",
                            "Today",
                            R.drawable.ic_tv,
                            "CHANNEL",
                            false,
                            "Watch Live"
                    ));
                }
            }

            // Calculate current unread count before async update check
            int initialUnread = list.size();
            List<NotificationItem> initialList = new ArrayList<>(list);

            // Notify initial list on UI
            if (listener != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        listener.onNotificationsUpdated(initialList, initialUnread));
            }

            // 2. Check App Update from Server asynchronously
            ApiClient.getInstance(context).checkAppUpdate(new ApiClient.ApiCallback<UpdateInfo>() {
                @Override
                public void onSuccess(UpdateInfo info) {
                    if (info != null && info.isHasUpdate()) {
                        String updateId = "notif_app_update_v" + info.getVersionName();
                        if (!readIds.contains(updateId)) {
                            NotificationItem updateItem = new NotificationItem(
                                    updateId,
                                    "New App Update Available (v" + info.getVersionName() + ")",
                                    info.getChangelog(),
                                    "Server Release",
                                    R.drawable.ic_update,
                                    "UPDATE",
                                    false,
                                    "Update Now"
                            );
                            list.add(0, updateItem);

                            int finalUnread = list.size();
                            if (listener != null) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                                        listener.onNotificationsUpdated(new ArrayList<>(list), finalUnread));
                            }
                        }
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    // Ignore error on background check
                }
            });

        }).start();
    }
}
