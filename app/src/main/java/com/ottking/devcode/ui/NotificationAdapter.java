package com.ottking.devcode.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.R;
import com.ottking.devcode.model.NotificationItem;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationActionListener {
        void onNotificationClick(NotificationItem item);
        void onActionClick(NotificationItem item);
    }

    private final List<NotificationItem> items = new ArrayList<>();
    private final OnNotificationActionListener listener;

    public NotificationAdapter(OnNotificationActionListener listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = items.get(position);

        holder.txtTitle.setText(item.getTitle());
        holder.txtMessage.setText(item.getMessage());
        holder.txtTime.setText(item.getTime());
        holder.imgIcon.setImageResource(item.getIconRes() != 0 ? item.getIconRes() : R.drawable.ic_notifications);

        if (item.isRead()) {
            holder.unreadDot.setVisibility(View.GONE);
        } else {
            holder.unreadDot.setVisibility(View.VISIBLE);
        }

        if (item.getActionText() != null && !item.getActionText().trim().isEmpty()) {
            holder.btnAction.setText(item.getActionText());
            holder.btnAction.setVisibility(View.VISIBLE);
            holder.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onActionClick(item);
            });
            UIUtils.applyFocusAnimation(holder.btnAction, 1.08f, 8f);
        } else {
            holder.btnAction.setVisibility(View.GONE);
        }

        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
        UIUtils.applyFocusAnimation(holder.itemView, 1.04f, 6f);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onNotificationClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtTitle, txtMessage, txtTime;
        View unreadDot;
        Button btnAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgNotifIcon);
            txtTitle = itemView.findViewById(R.id.txtNotifTitle);
            txtMessage = itemView.findViewById(R.id.txtNotifMessage);
            txtTime = itemView.findViewById(R.id.txtNotifTime);
            unreadDot = itemView.findViewById(R.id.unreadDot);
            btnAction = itemView.findViewById(R.id.btnNotifAction);
        }
    }
}
