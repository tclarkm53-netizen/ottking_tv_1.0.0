package com.ottking.devcode.ui.settings;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ottking.devcode.R;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.utils.CustomOrderManager;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomizeChannelAdapter extends RecyclerView.Adapter<CustomizeChannelAdapter.ViewHolder> {

    public interface OnChannelOrderActionListener {
        void onMoveUp(ChannelEntity channel, int position);
        void onMoveDown(ChannelEntity channel, int position);
        void onMoveTop(ChannelEntity channel, int position);
        void onToggleVisibility(ChannelEntity channel, int position);
        void onOrderChanged(int categoryId, List<ChannelEntity> newChannelList);
    }

    private final List<ChannelEntity> channelList = new ArrayList<>();
    private final OnChannelOrderActionListener listener;
    private final CustomOrderManager orderManager;
    private RecyclerView recyclerView;
    private String currentCategoryName = "";
    private int currentCategoryId = 0;

    // Track position currently in active move/reorder mode (-1 if none)
    private int activeMovePosition = -1;

    public CustomizeChannelAdapter(CustomOrderManager orderManager, OnChannelOrderActionListener listener) {
        this.orderManager = orderManager;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < channelList.size()) {
            return channelList.get(position).id;
        }
        return RecyclerView.NO_ID;
    }

    public void setChannels(List<ChannelEntity> channels, int categoryId, String categoryName) {
        this.currentCategoryId = categoryId;
        this.currentCategoryName = categoryName != null ? categoryName : "";
        this.activeMovePosition = -1;
        this.channelList.clear();
        if (channels != null) {
            this.channelList.addAll(channels);
        }
        notifyDataSetChanged();
    }

    public List<ChannelEntity> getChannels() {
        return channelList;
    }

    public int getActiveMovePosition() {
        return activeMovePosition;
    }

    public void exitMoveMode() {
        if (activeMovePosition != -1) {
            int oldActive = activeMovePosition;
            activeMovePosition = -1;
            notifyItemChanged(oldActive, "PAYLOAD_MOVE");
        }
    }

    private void requestChildFocusOnPos(int pos, int targetViewId) {
        if (recyclerView == null) return;
        recyclerView.scrollToPosition(pos);
        recyclerView.post(() -> {
            if (recyclerView == null) return;
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(pos);
            if (vh != null) {
                View target = (targetViewId != 0 && targetViewId != vh.itemView.getId())
                        ? vh.itemView.findViewById(targetViewId) : null;
                if (target == null || !target.isFocusable() || !target.isEnabled()) {
                    target = vh.itemView;
                }
                if (target != null) {
                    target.requestFocus();
                }
            } else {
                recyclerView.post(() -> {
                    if (recyclerView == null) return;
                    RecyclerView.ViewHolder vh2 = recyclerView.findViewHolderForAdapterPosition(pos);
                    if (vh2 != null) {
                        View target = (targetViewId != 0 && targetViewId != vh2.itemView.getId())
                                ? vh2.itemView.findViewById(targetViewId) : null;
                        if (target == null || !target.isFocusable() || !target.isEnabled()) {
                            target = vh2.itemView;
                        }
                        if (target != null) {
                            target.requestFocus();
                        }
                    }
                });
            }
        });
    }

    private void saveOrder() {
        List<Integer> newOrder = new ArrayList<>();
        for (ChannelEntity ch : channelList) {
            newOrder.add(ch.id);
        }
        orderManager.setCustomChannelOrder(currentCategoryId, newOrder);
        if (listener != null) {
            listener.onOrderChanged(currentCategoryId, new ArrayList<>(channelList));
        }
    }

    public void moveChannelUp(int pos, int targetFocusViewId) {
        if (pos <= 0 || pos >= channelList.size()) return;

        int targetPos = pos - 1;
        Collections.swap(channelList, pos, targetPos);
        if (activeMovePosition == pos) {
            activeMovePosition = targetPos;
        }

        notifyItemMoved(pos, targetPos);
        notifyItemChanged(pos, "PAYLOAD_MOVE");
        notifyItemChanged(targetPos, "PAYLOAD_MOVE");

        saveOrder();
        requestChildFocusOnPos(targetPos, targetFocusViewId);
    }

    public void moveChannelDown(int pos, int targetFocusViewId) {
        if (pos < 0 || pos >= channelList.size() - 1) return;

        int targetPos = pos + 1;
        Collections.swap(channelList, pos, targetPos);
        if (activeMovePosition == pos) {
            activeMovePosition = targetPos;
        }

        notifyItemMoved(pos, targetPos);
        notifyItemChanged(pos, "PAYLOAD_MOVE");
        notifyItemChanged(targetPos, "PAYLOAD_MOVE");

        saveOrder();
        requestChildFocusOnPos(targetPos, targetFocusViewId);
    }

    public void moveChannelToTop(int pos, int targetFocusViewId) {
        if (pos <= 0 || pos >= channelList.size()) return;

        ChannelEntity item = channelList.remove(pos);
        channelList.add(0, item);
        if (activeMovePosition == pos) {
            activeMovePosition = 0;
        }

        notifyItemMoved(pos, 0);
        notifyItemRangeChanged(0, pos + 1, "PAYLOAD_MOVE");

        saveOrder();
        requestChildFocusOnPos(0, targetFocusViewId);
    }

    public void toggleVisibility(int pos) {
        if (pos < 0 || pos >= channelList.size()) return;
        ChannelEntity channel = channelList.get(pos);

        orderManager.toggleChannelHidden(channel.id);
        notifyItemChanged(pos, "PAYLOAD_VISIBILITY");
        requestChildFocusOnPos(pos, R.id.btnChannelVisibility);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customize_channel, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            ChannelEntity channel = channelList.get(position);
            boolean isHidden = orderManager.isChannelHidden(channel.id);
            boolean isMovingThisItem = (position == activeMovePosition);

            if (payloads.contains("PAYLOAD_MOVE")) {
                if (isMovingThisItem) {
                    holder.itemView.setSelected(true);
                    holder.txtPosition.setText("↕ MOVE");
                    holder.txtPosition.setTextColor(0xFF000000);
                    holder.txtPosition.setBackgroundColor(0xFFFFD700);
                    holder.txtStatus.setText("Active! Press ▲/▼ to move, OK to save");
                    holder.txtStatus.setTextColor(0xFFFFD700);
                    holder.imgReorderHandle.setColorFilter(0xFFFFD700);
                } else {
                    holder.itemView.setSelected(false);
                    holder.txtPosition.setText(String.format("#%d", position + 1));
                    holder.txtPosition.setTextColor(0xFF2979FF);
                    holder.txtPosition.setBackgroundResource(R.drawable.bg_pill_normal);
                    holder.imgReorderHandle.setColorFilter(0xFF776F8E);
                    holder.txtStatus.setText(isHidden ? "Hidden from Lists & Player" : "Visible • Long press to reorder");
                    holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
                }

                boolean canMoveUp = position > 0;
                holder.btnMoveUp.setEnabled(canMoveUp);
                holder.btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.3f);
                holder.btnMoveTop.setEnabled(canMoveUp);
                holder.btnMoveTop.setAlpha(canMoveUp ? 1.0f : 0.3f);

                boolean canMoveDown = position < channelList.size() - 1;
                holder.btnMoveDown.setEnabled(canMoveDown);
                holder.btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.3f);
            }

            if (payloads.contains("PAYLOAD_VISIBILITY")) {
                holder.btnVisibility.setImageResource(isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
                holder.txtStatus.setText(isHidden ? "Hidden from Lists & Player" : "Visible • Long press to reorder");
                holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
            }
            return;
        }

        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChannelEntity channel = channelList.get(position);
        boolean isHidden = orderManager.isChannelHidden(channel.id);
        boolean isMovingThisItem = (position == activeMovePosition);

        holder.txtName.setText(channel.name != null ? channel.name : "Channel " + channel.id);
        holder.txtCategory.setText(currentCategoryName);

        if (isMovingThisItem) {
            holder.itemView.setSelected(true);
            holder.txtPosition.setText("↕ MOVE");
            holder.txtPosition.setTextColor(0xFF000000);
            holder.txtPosition.setBackgroundColor(0xFFFFD700);
            holder.txtStatus.setText("Active! Press ▲/▼ to move, OK to save");
            holder.txtStatus.setTextColor(0xFFFFD700);
            holder.imgReorderHandle.setColorFilter(0xFFFFD700);
        } else {
            holder.itemView.setSelected(false);
            holder.txtPosition.setText(String.format("#%d", position + 1));
            holder.txtPosition.setTextColor(0xFF2979FF);
            holder.txtPosition.setBackgroundResource(R.drawable.bg_pill_normal);
            holder.imgReorderHandle.setColorFilter(0xFF776F8E);
            holder.txtStatus.setText(isHidden ? "Hidden from Lists & Player" : "Visible • Long press to reorder");
            holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
        }

        holder.btnVisibility.setImageResource(isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);

        // Load channel logo
        if (channel.logoUrl != null && !channel.logoUrl.trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(channel.logoUrl)
                    .placeholder(R.drawable.img_app_icon)
                    .error(R.drawable.img_app_icon)
                    .into(holder.imgLogo);
        } else {
            holder.imgLogo.setImageResource(R.drawable.img_app_icon);
        }

        // Enable/Disable move buttons
        boolean canMoveUp = position > 0;
        holder.btnMoveUp.setEnabled(canMoveUp);
        holder.btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.3f);
        holder.btnMoveTop.setEnabled(canMoveUp);
        holder.btnMoveTop.setAlpha(canMoveUp ? 1.0f : 0.3f);

        boolean canMoveDown = position < channelList.size() - 1;
        holder.btnMoveDown.setEnabled(canMoveDown);
        holder.btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.3f);

        // Long Press on row to activate Reordering mode
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            if (activeMovePosition == pos) {
                activeMovePosition = -1;
                notifyItemChanged(pos, "PAYLOAD_MOVE");
                Toast.makeText(v.getContext(), "Reorder mode closed", Toast.LENGTH_SHORT).show();
            } else {
                int oldActive = activeMovePosition;
                activeMovePosition = pos;
                if (oldActive != -1) {
                    notifyItemChanged(oldActive, "PAYLOAD_MOVE");
                }
                notifyItemChanged(pos, "PAYLOAD_MOVE");
                Toast.makeText(v.getContext(), "Reorder active! Press ▲ or ▼ to move anywhere, then press OK to save.", Toast.LENGTH_LONG).show();
            }
            return true;
        });

        // Click on item
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;

            if (activeMovePosition == pos) {
                activeMovePosition = -1;
                notifyItemChanged(pos, "PAYLOAD_MOVE");
                Toast.makeText(v.getContext(), "New order saved", Toast.LENGTH_SHORT).show();
            } else if (activeMovePosition != -1) {
                int oldActive = activeMovePosition;
                activeMovePosition = -1;
                notifyItemChanged(oldActive, "PAYLOAD_MOVE");
            }
        });

        // Remote D-Pad Key Listener while in active move mode
        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (activeMovePosition == pos) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        moveChannelUp(pos, R.id.layoutChannelRow);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        moveChannelDown(pos, R.id.layoutChannelRow);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                        activeMovePosition = -1;
                        notifyItemChanged(pos, "PAYLOAD_MOVE");
                        Toast.makeText(v.getContext(), "New order saved", Toast.LENGTH_SHORT).show();
                        requestChildFocusOnPos(pos, R.id.layoutChannelRow);
                        return true;
                    }
                }
            }
            return false;
        });

        // Button Click Listeners with in-place move and focus retention
        holder.btnMoveTop.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                moveChannelToTop(pos, R.id.btnChannelMoveTop);
            }
        });

        holder.btnMoveUp.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                moveChannelUp(pos, R.id.btnChannelMoveUp);
            }
        });

        holder.btnMoveDown.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                moveChannelDown(pos, R.id.btnChannelMoveDown);
            }
        });

        holder.btnVisibility.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                toggleVisibility(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return channelList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgReorderHandle;
        final TextView txtPosition;
        final ImageView imgLogo;
        final TextView txtName;
        final TextView txtCategory;
        final TextView txtStatus;
        final ImageButton btnMoveTop;
        final ImageButton btnMoveUp;
        final ImageButton btnMoveDown;
        final ImageButton btnVisibility;

        ViewHolder(View itemView) {
            super(itemView);
            imgReorderHandle = itemView.findViewById(R.id.imgChannelReorderHandle);
            txtPosition = itemView.findViewById(R.id.txtChannelPosition);
            imgLogo = itemView.findViewById(R.id.imgChannelLogo);
            txtName = itemView.findViewById(R.id.txtChannelName);
            txtCategory = itemView.findViewById(R.id.txtChannelCategory);
            txtStatus = itemView.findViewById(R.id.txtChannelStatus);
            btnMoveTop = itemView.findViewById(R.id.btnChannelMoveTop);
            btnMoveUp = itemView.findViewById(R.id.btnChannelMoveUp);
            btnMoveDown = itemView.findViewById(R.id.btnChannelMoveDown);
            btnVisibility = itemView.findViewById(R.id.btnChannelVisibility);

            UIUtils.applyFocusAnimation(itemView, 1.02f, 4f);
            UIUtils.applyFocusAnimation(btnMoveTop, 1.15f, 6f);
            UIUtils.applyFocusAnimation(btnMoveUp, 1.15f, 6f);
            UIUtils.applyFocusAnimation(btnMoveDown, 1.15f, 6f);
            UIUtils.applyFocusAnimation(btnVisibility, 1.15f, 6f);
        }
    }
}
