package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.model.NotificationModel;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationModel notification, int position);
    }

    private final Context context;
    private List<NotificationModel> notificationList;
    private OnNotificationClickListener clickListener;

    public NotificationAdapter(Context context, List<NotificationModel> notificationList) {
        this(context, notificationList, null);
    }

    public NotificationAdapter(Context context, List<NotificationModel> notificationList, OnNotificationClickListener listener) {
        this.context = context;
        this.notificationList = notificationList;
        this.clickListener = listener;
    }

    public void updateList(List<NotificationModel> list) {
        this.notificationList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel item = notificationList.get(position);
        if (item == null) return;

        holder.txtTitle.setText(item.getTitle());
        holder.txtMessage.setText(item.getMessage());
        holder.txtTime.setText(item.getTimestamp());
        holder.imgIcon.setImageResource(item.getIconRes());

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onNotificationClick(item, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notificationList != null ? notificationList.size() : 0;
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtMessage, txtTime;
        ImageView imgIcon;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtNotificationTitle);
            txtMessage = itemView.findViewById(R.id.txtNotificationMessage);
            txtTime = itemView.findViewById(R.id.txtNotificationTime);
            imgIcon = itemView.findViewById(R.id.imgNotificationIcon);
        }
    }
}
