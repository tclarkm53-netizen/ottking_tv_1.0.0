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
        default void onNavigateToCategories() {}
        default void onNavigateToHeader(int channelPosition) {
            onNavigateToHeader();
        }
        default void onNavigateToHeader() {}
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
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < channelList.size()) {
            return channelList.get(position).id;
        }
        return RecyclerView.NO_ID;
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
        if (all == null) return;
        this.masterAllChannels.clear();
        this.masterAllChannels.addAll(all);
    }

    public void setChannels(List<ChannelEntity> channels) {
        if (channels == null) return;
        List<ChannelEntity> oldList = new ArrayList<>(this.channelList);
        List<ChannelEntity> newList = new ArrayList<>(channels);
        androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldList.size();
            }

            @Override
            public int getNewListSize() {
                return newList.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldList.get(oldItemPosition).id == newList.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                ChannelEntity oldItem = oldList.get(oldItemPosition);
                ChannelEntity newItem = newList.get(newItemPosition);
                return oldItem.equals(newItem);
            }
        });

        this.channelList.clear();
        this.channelList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    public ChannelEntity getChannelAt(int pos) {
        if (pos >= 0 && pos < channelList.size()) {
            return channelList.get(pos);
        }
        return null;
    }

    public List<ChannelEntity> getChannelList() {
        return new ArrayList<>(channelList);
    }

    public int getChannelPositionById(int channelId) {
        for (int i = 0; i < channelList.size(); i++) {
            if (channelList.get(i).id == channelId) {
                return i;
            }
        }
        return -1;
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

        if (UIUtils.isValidImageUrl(channel.logoUrl)) {
            Glide.with(holder.itemView.getContext())
                    .load(channel.logoUrl.trim())
                    .placeholder(R.drawable.img_app_icon)
                    .error(R.drawable.img_app_icon)
                    .into(holder.imgLogo);
        } else {
            Glide.with(holder.itemView.getContext()).clear(holder.imgLogo);
            holder.imgLogo.setImageResource(R.drawable.img_app_icon);
        }

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.04f, 8f);
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
                    if (isListViewMode) {
                        // Player drawer single-column list mode
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            return true; // Maintain focus inside drawer
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (pos == 0) {
                                if (navigationListener != null) {
                                    navigationListener.onNavigateToHeader(0);
                                    return true;
                                }
                            } else {
                                focusChannelItem(holder.itemView, pos - 1);
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (pos >= channelList.size() - 1) {
                                // Already at the end of the channel list: clamp focus to the last item
                                focusChannelItem(holder.itemView, channelList.size() - 1);
                                return true;
                            } else {
                                focusChannelItem(holder.itemView, pos + 1);
                                return true;
                            }
                        }
                    } else {
                        // Home grid mode
                        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            if (pos % spanCount == 0 || channelList.size() == 1) {
                                if (navigationListener != null) {
                                    navigationListener.onNavigateToCategories();
                                    return true;
                                }
                            } else {
                                focusChannelItem(holder.itemView, pos - 1);
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                            if ((pos + 1) % spanCount == 0 || pos >= channelList.size() - 1) {
                                // Rightmost edge of grid - keep focus stable
                                return true;
                            } else {
                                focusChannelItem(holder.itemView, pos + 1);
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (pos < spanCount) {
                                if (navigationListener != null) {
                                    navigationListener.onNavigateToHeader(pos);
                                    return true;
                                }
                            } else {
                                focusChannelItem(holder.itemView, pos - spanCount);
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            int targetPos = pos + spanCount;
                            if (targetPos < channelList.size()) {
                                focusChannelItem(holder.itemView, targetPos);
                                return true;
                            } else if (pos < (channelList.size() / spanCount) * spanCount && channelList.size() > 0) {
                                focusChannelItem(holder.itemView, channelList.size() - 1);
                                return true;
                            } else {
                                // Bottom row reached - keep focus stable
                                return true;
                            }
                        }
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

    @Override
    public void onViewRecycled(@NonNull ChannelViewHolder holder) {
        super.onViewRecycled(holder);
        if (holder.imgLogo != null) {
            Glide.with(holder.itemView.getContext()).clear(holder.imgLogo);
        }
    }

    private void focusChannelItem(View currentView, int targetPos) {
        if (targetPos < 0 || targetPos >= channelList.size()) return;
        RecyclerView rv = null;
        if (currentView.getParent() instanceof RecyclerView) {
            rv = (RecyclerView) currentView.getParent();
        } else if (currentView instanceof RecyclerView) {
            rv = (RecyclerView) currentView;
        }
        if (rv != null) {
            final RecyclerView finalRv = rv;
            RecyclerView.ViewHolder targetHolder = rv.findViewHolderForAdapterPosition(targetPos);
            if (targetHolder != null && targetHolder.itemView != null) {
                targetHolder.itemView.requestFocus();
                finalRv.smoothScrollToPosition(targetPos);
            } else {
                finalRv.scrollToPosition(targetPos);
                finalRv.post(() -> {
                    RecyclerView.ViewHolder targetHolder2 = finalRv.findViewHolderForAdapterPosition(targetPos);
                    if (targetHolder2 != null && targetHolder2.itemView != null) {
                        targetHolder2.itemView.requestFocus();
                    } else {
                        finalRv.postDelayed(() -> {
                            RecyclerView.ViewHolder targetHolder3 = finalRv.findViewHolderForAdapterPosition(targetPos);
                            if (targetHolder3 != null && targetHolder3.itemView != null) {
                                targetHolder3.itemView.requestFocus();
                            }
                        }, 25);
                    }
                });
            }
        }
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
