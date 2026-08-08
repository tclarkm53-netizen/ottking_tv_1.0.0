package com.ottking.devcode.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.R;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.utils.AppNotificationManager;
import com.ottking.devcode.utils.UpdateManager;

import java.util.ArrayList;
import java.util.List;

public class NotificationPanelDialog {

    public interface OnBadgeUpdateListener {
        void onBadgeUpdated(int unreadCount);
    }

    private final Activity activity;
    private final AppNotificationManager notificationManager;
    private final OnBadgeUpdateListener badgeListener;

    private Dialog dialog;
    private RecyclerView recyclerNotifications;
    private TextView txtEmptyNotifications, txtUnreadCountHeader;
    private NotificationAdapter adapter;
    private final List<NotificationItem> currentItems = new ArrayList<>();

    public NotificationPanelDialog(@NonNull Activity activity, OnBadgeUpdateListener badgeListener) {
        this.activity = activity;
        this.badgeListener = badgeListener;
        this.notificationManager = new AppNotificationManager(activity);
    }

    public void show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_notification_panel, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.END | Gravity.TOP);

            DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
            int panelWidth = Math.max((int) (metrics.widthPixels * 0.38f), (int) (320 * metrics.density));
            window.setLayout(panelWidth, ViewGroup.LayoutParams.MATCH_PARENT);
        }

        ImageButton btnClose = view.findViewById(R.id.btnCloseNotif);
        Button btnMarkAllRead = view.findViewById(R.id.btnMarkAllRead);
        txtUnreadCountHeader = view.findViewById(R.id.txtUnreadCountHeader);
        txtEmptyNotifications = view.findViewById(R.id.txtEmptyNotifications);
        recyclerNotifications = view.findViewById(R.id.recyclerNotifications);

        if (btnClose != null) {
            btnClose.setFocusable(true);
            btnClose.setFocusableInTouchMode(true);
            btnClose.setOnClickListener(v -> dialog.dismiss());
            btnClose.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (btnMarkAllRead != null && btnMarkAllRead.getVisibility() == View.VISIBLE) {
                            btnMarkAllRead.requestFocus();
                            return true;
                        } else if (recyclerNotifications != null && adapter != null && adapter.getItemCount() > 0) {
                            recyclerNotifications.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        if (btnMarkAllRead != null) {
            btnMarkAllRead.setFocusable(true);
            btnMarkAllRead.setFocusableInTouchMode(true);
            btnMarkAllRead.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && btnClose != null) {
                        btnClose.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (recyclerNotifications != null && adapter != null && adapter.getItemCount() > 0) {
                            recyclerNotifications.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        recyclerNotifications.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new NotificationAdapter(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onNotificationClick(NotificationItem item) {
                handleItemClick(item);
            }

            @Override
            public void onActionClick(NotificationItem item) {
                handleItemClick(item);
            }
        });
        recyclerNotifications.setAdapter(adapter);

        if (btnMarkAllRead != null) {
            btnMarkAllRead.setOnClickListener(v -> {
                notificationManager.markAllAsRead(new ArrayList<>(currentItems), () -> {
                    currentItems.clear();
                    adapter.setItems(currentItems);
                    if (txtEmptyNotifications != null) txtEmptyNotifications.setVisibility(View.VISIBLE);
                    if (recyclerNotifications != null) recyclerNotifications.setVisibility(View.GONE);
                    updateHeaderAndBadge();
                });
            });
        }

        loadData();
        dialog.show();

        // Focus first item or close button for DPAD
        if (btnClose != null) {
            btnClose.requestFocus();
        }
    }

    private void loadData() {
        notificationManager.loadNotifications((notifications, unreadCount) -> {
            currentItems.clear();
            if (notifications != null) {
                currentItems.addAll(notifications);
            }

            if (currentItems.isEmpty()) {
                txtEmptyNotifications.setVisibility(View.VISIBLE);
                recyclerNotifications.setVisibility(View.GONE);
            } else {
                txtEmptyNotifications.setVisibility(View.GONE);
                recyclerNotifications.setVisibility(View.VISIBLE);
                adapter.setItems(currentItems);
            }

            updateHeaderAndBadge();
        });
    }

    private void updateHeaderAndBadge() {
        int unread = currentItems.size();

        if (txtUnreadCountHeader != null) {
            if (unread > 0) {
                txtUnreadCountHeader.setText(unread + " Unread");
            } else {
                txtUnreadCountHeader.setText("No Notifications");
            }
        }

        if (badgeListener != null) {
            badgeListener.onBadgeUpdated(unread);
        }
    }

    private void handleItemClick(NotificationItem item) {
        if (item == null || item.getId() == null) return;

        notificationManager.markAsRead(item.getId(), null);

        for (int i = currentItems.size() - 1; i >= 0; i--) {
            if (item.getId().equals(currentItems.get(i).getId())) {
                currentItems.remove(i);
            }
        }

        adapter.setItems(currentItems);
        if (currentItems.isEmpty()) {
            if (txtEmptyNotifications != null) txtEmptyNotifications.setVisibility(View.VISIBLE);
            if (recyclerNotifications != null) recyclerNotifications.setVisibility(View.GONE);
        }
        updateHeaderAndBadge();

        if ("UPDATE".equalsIgnoreCase(item.getType())) {
            if (dialog != null) dialog.dismiss();
            new UpdateManager(activity).checkAndUpdate(true);
        } else if ("CHANNEL".equalsIgnoreCase(item.getType())) {
            if (dialog != null) dialog.dismiss();
            if (activity instanceof MainActivity) {
                ((MainActivity) activity).focusFirstChannel();
            }
        }
    }
}
