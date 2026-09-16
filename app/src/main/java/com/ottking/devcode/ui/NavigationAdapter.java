package com.ottking.devcode.ui;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.R;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class NavigationAdapter extends RecyclerView.Adapter<NavigationAdapter.NavViewHolder> {

    public interface OnNavClickListener {
        void onNavClick(String navItem);
    }

    public interface OnNavNavigationListener {
        void onNavigateToContent();
        void onNavigateToHeader();
    }

    private final List<String> navList = new ArrayList<>();
    private final OnNavClickListener listener;
    private OnNavNavigationListener navigationListener;
    private int selectedPosition = 0;

    public NavigationAdapter(List<String> items, OnNavClickListener listener) {
        if (items != null) {
            this.navList.addAll(items);
        }
        this.listener = listener;
    }

    public void setNavigationListener(OnNavNavigationListener navigationListener) {
        this.navigationListener = navigationListener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public NavViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_settings_nav, parent, false);
        return new NavViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NavViewHolder holder, int position) {
        String item = navList.get(position);
        if ("Account".equals(item)) {
            holder.txtNavTitle.setText("Account");
            holder.imgNavIcon.setImageResource(R.drawable.ic_account);
        } else if ("PlayerSettings".equals(item) || "Player".equals(item)) {
            holder.txtNavTitle.setText("Player Settings");
            holder.imgNavIcon.setImageResource(R.drawable.ic_play);
        } else if ("TvSettings".equals(item) || "Tv".equals(item)) {
            holder.txtNavTitle.setText("TV Settings");
            holder.imgNavIcon.setImageResource(R.drawable.ic_tv);
        } else if ("System".equals(item)) {
            holder.txtNavTitle.setText("System Info");
            holder.imgNavIcon.setImageResource(R.drawable.ic_info);
        } else {
            holder.txtNavTitle.setText(item);
            holder.imgNavIcon.setImageResource(R.drawable.ic_settings);
        }

        boolean isSelected = (position == selectedPosition);
        holder.itemView.setSelected(isSelected);
        holder.imgNavIcon.setSelected(isSelected);
        holder.txtNavTitle.setSelected(isSelected);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
                int prev = selectedPosition;
                selectedPosition = pos;
                if (holder.itemView.getParent() instanceof RecyclerView) {
                    RecyclerView rv = (RecyclerView) holder.itemView.getParent();
                    RecyclerView.ViewHolder prevHolder = rv.findViewHolderForAdapterPosition(prev);
                    if (prevHolder != null && prevHolder.itemView != null) {
                        prevHolder.itemView.setSelected(false);
                        View prevIcon = prevHolder.itemView.findViewById(R.id.imgNavIcon);
                        if (prevIcon != null) prevIcon.setSelected(false);
                        View prevTitle = prevHolder.itemView.findViewById(R.id.txtNavTitle);
                        if (prevTitle != null) prevTitle.setSelected(false);
                    }
                }
                holder.itemView.setSelected(true);
                holder.imgNavIcon.setSelected(true);
                holder.txtNavTitle.setSelected(true);
                if (listener != null) {
                    listener.onNavClick(item);
                }
            }
            if (navigationListener != null) {
                navigationListener.onNavigateToContent();
            }
        });

        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = holder.getAdapterPosition();
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (navigationListener != null) {
                        navigationListener.onNavigateToContent();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP && pos == 0) {
                    if (navigationListener != null) {
                        navigationListener.onNavigateToHeader();
                        return true;
                    }
                }
            }
            return false;
        });

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.05f, 8f);
            if (hasFocus) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
                    int prev = selectedPosition;
                    selectedPosition = pos;
                    if (holder.itemView.getParent() instanceof RecyclerView) {
                        RecyclerView rv = (RecyclerView) holder.itemView.getParent();
                        RecyclerView.ViewHolder prevHolder = rv.findViewHolderForAdapterPosition(prev);
                        if (prevHolder != null && prevHolder.itemView != null) {
                            prevHolder.itemView.setSelected(false);
                            View prevIcon = prevHolder.itemView.findViewById(R.id.imgNavIcon);
                            if (prevIcon != null) prevIcon.setSelected(false);
                            View prevTitle = prevHolder.itemView.findViewById(R.id.txtNavTitle);
                            if (prevTitle != null) prevTitle.setSelected(false);
                        }
                    }
                    holder.itemView.setSelected(true);
                    holder.imgNavIcon.setSelected(true);
                    holder.txtNavTitle.setSelected(true);
                    if (listener != null) {
                        listener.onNavClick(item);
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return navList.size();
    }

    public static class NavViewHolder extends RecyclerView.ViewHolder {
        ImageView imgNavIcon;
        TextView txtNavTitle;

        public NavViewHolder(@NonNull View itemView) {
            super(itemView);
            imgNavIcon = itemView.findViewById(R.id.imgNavIcon);
            txtNavTitle = itemView.findViewById(R.id.txtNavTitle);
        }
    }
}
