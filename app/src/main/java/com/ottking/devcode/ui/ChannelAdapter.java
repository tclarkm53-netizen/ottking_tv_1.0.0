package com.ottking.devcode.ui;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ottking.devcode.R;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(ChannelEntity channel);
    }

    public interface OnChannelNavigationListener {
        void onNavigateToCategories();
        default void onNavigateToHeader() {}
        default void onNavigateToHeader(int position) {
            onNavigateToHeader();
        }
        default void onNavigateToStart() {}
    }

    public interface OnChannelFocusListener {
        void onChannelFocused(int position, View view);
    }

    private final List<ChannelEntity> channelList = new ArrayList<>();
    private final List<ChannelEntity> masterAllChannels = new ArrayList<>();
    private final OnChannelClickListener listener;
    private OnChannelNavigationListener navigationListener;
    private OnChannelFocusListener focusListener;
    private final boolean isListViewMode;
    private int playingChannelId = -1;
    private int spanCount = 5;

    public ChannelAdapter(OnChannelClickListener listener) {
        this(false, listener);
    }

    public ChannelAdapter(boolean isListViewMode, OnChannelClickListener listener) {
        this.isListViewMode = isListViewMode;
        if (isListViewMode) {
            this.spanCount = 1;
        }
        this.listener = listener;
    }

    public void setNavigationListener(OnChannelNavigationListener navigationListener) {
        this.navigationListener = navigationListener;
    }

    public void setFocusListener(OnChannelFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    public void setSpanCount(int spanCount) {
        this.spanCount = Math.max(1, spanCount);
    }

    public void setPlayingChannelId(int channelId) {
        this.playingChannelId = channelId;
        notifyDataSetChanged();
    }

    public void setAllChannelsList(List<ChannelEntity> all) {
        this.masterAllChannels.clear();
        if (all != null) {
            this.masterAllChannels.addAll(all);
        }
        notifyDataSetChanged();
    }

    public void setChannels(List<ChannelEntity> channels) {
        this.channelList.clear();
        if (channels != null) {
            this.channelList.addAll(channels);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isListViewMode ? R.layout.item_channel_list : R.layout.item_channel;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ChannelViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        ChannelEntity channel = channelList.get(position);
        holder.txtChannelName.setText(channel.name);

        boolean isPlaying = (channel.id == playingChannelId);

        if (holder.txtChannelNumber != null) {
            int channelNum = position + 1;
            if (!masterAllChannels.isEmpty()) {
                int globalIndex = masterAllChannels.indexOf(channel);
                if (globalIndex != -1) {
                    channelNum = globalIndex + 1;
                }
            }
            holder.txtChannelNumber.setText("CH " + channelNum);
        }

        if (holder.txtStreamType != null) {
            if (isPlaying) {
                holder.txtStreamType.setText("▶ PLAYING");
                holder.txtStreamType.setTextColor(holder.itemView.getContext().getColor(R.color.gold_primary));
            } else if (channel.streamType != null) {
                if (isListViewMode) {
                    holder.txtStreamType.setText("• " + channel.streamType.toUpperCase());
                    holder.txtStreamType.setTextColor(holder.itemView.getContext().getColor(R.color.text_muted));
                } else {
                    holder.txtStreamType.setText(channel.streamType.toUpperCase());
                    holder.txtStreamType.setTextColor(holder.itemView.getContext().getColor(R.color.white));
                }
            }
        }

        if (isPlaying) {
            holder.txtChannelName.setTextColor(holder.itemView.getContext().getColor(R.color.gold_primary));
            holder.itemView.setSelected(true);
        } else {
            holder.txtChannelName.setTextColor(holder.itemView.getContext().getColor(R.color.text_primary));
            holder.itemView.setSelected(false);
        }

        if (channel.isPremium) {
            holder.txtBadge.setText("PAID");
            holder.txtBadge.setBackgroundResource(R.color.gold_primary);
            holder.txtBadge.setTextColor(holder.itemView.getContext().getColor(R.color.black));
        } else {
            holder.txtBadge.setText("FREE");
            holder.txtBadge.setBackgroundResource(R.color.accent_green);
            holder.txtBadge.setTextColor(holder.itemView.getContext().getColor(R.color.black));
        }

        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);

        Glide.with(holder.itemView.getContext())
                .load(channel.logoUrl)
                .placeholder(R.drawable.img_splash_bg)
                .error(R.drawable.img_splash_bg)
                .into(holder.imgLogo);

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.07f, 12f);
            if (hasFocus) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && focusListener != null) {
                    focusListener.onChannelFocused(pos, v);
                }
            }
        });

        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && !channelList.isEmpty()) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && (pos % spanCount == 0 || channelList.size() == 1)) {
                        if (navigationListener != null) {
                            navigationListener.onNavigateToCategories();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos < spanCount) {
                        if (navigationListener != null) {
                            navigationListener.onNavigateToHeader(pos);
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && pos >= channelList.size() - spanCount) {
                        if (navigationListener != null) {
                            navigationListener.onNavigateToStart();
                            return true;
                        }
                        return true; // Keep focus on bottom row if no footer action
                    }
                }
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelClick(channel);
            }
        });
    }

    @Override
    public int getItemCount() {
        return channelList.size();
    }

    public static class ChannelViewHolder extends RecyclerView.ViewHolder {
        ImageView imgLogo;
        TextView txtChannelName, txtChannelNumber, txtCategory, txtBadge, txtStreamType;

        public ChannelViewHolder(@NonNull View itemView) {
            super(itemView);
            imgLogo = itemView.findViewById(R.id.imgLogo);
            txtChannelName = itemView.findViewById(R.id.txtChannelName);
            txtChannelNumber = itemView.findViewById(R.id.txtChannelNumber);
            txtCategory = itemView.findViewById(R.id.txtCategory);
            txtBadge = itemView.findViewById(R.id.txtBadge);
            txtStreamType = itemView.findViewById(R.id.txtStreamType);
        }
    }
}
