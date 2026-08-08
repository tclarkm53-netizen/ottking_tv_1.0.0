package com.ottking.devcode.ui;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.devcode.R;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryEntity category);
    }

    public interface OnCategoryNavigationListener {
        void onNavigateToChannels();
        void onNavigateToHeader();
    }

    public interface OnCategoryFocusListener {
        void onCategoryFocused(int position, View view);
    }

    private final List<CategoryEntity> categoryList = new ArrayList<>();
    private final OnCategoryClickListener listener;
    private OnCategoryNavigationListener navigationListener;
    private OnCategoryFocusListener focusListener;
    private int selectedPosition = 0;
    private boolean isCollapsed = false;

    public CategoryAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setNavigationListener(OnCategoryNavigationListener navigationListener) {
        this.navigationListener = navigationListener;
    }

    public void setFocusListener(OnCategoryFocusListener focusListener) {
        this.focusListener = focusListener;
    }

    public void setCollapsed(boolean collapsed) {
        if (this.isCollapsed != collapsed) {
            this.isCollapsed = collapsed;
            notifyDataSetChanged();
        }
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setCategories(List<CategoryEntity> categories) {
        this.categoryList.clear();
        if (categories != null) {
            this.categoryList.addAll(categories);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryEntity category = categoryList.get(position);
        holder.txtCatName.setText(category.name);

        if ("ic_play".equals(category.icon)) {
            holder.imgCatIcon.setImageResource(R.drawable.ic_play);
        } else if ("ic_info".equals(category.icon)) {
            holder.imgCatIcon.setImageResource(R.drawable.ic_info);
        } else {
            holder.imgCatIcon.setImageResource(R.drawable.ic_tv);
        }

        if (isCollapsed) {
            holder.txtCatName.setVisibility(View.GONE);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) holder.imgCatIcon.getLayoutParams();
            if (lp != null) {
                lp.setMarginEnd(0);
                holder.imgCatIcon.setLayoutParams(lp);
            }
            if (holder.layoutCategoryItem != null) {
                holder.layoutCategoryItem.setGravity(Gravity.CENTER);
                int pHorizontal = UIUtils.dpToPx(holder.itemView.getContext(), 8);
                int pVertical = UIUtils.dpToPx(holder.itemView.getContext(), 12);
                holder.layoutCategoryItem.setPadding(pHorizontal, pVertical, pHorizontal, pVertical);
            }
        } else {
            holder.txtCatName.setVisibility(View.VISIBLE);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) holder.imgCatIcon.getLayoutParams();
            if (lp != null) {
                lp.setMarginEnd(UIUtils.dpToPx(holder.itemView.getContext(), 10));
                holder.imgCatIcon.setLayoutParams(lp);
            }
            if (holder.layoutCategoryItem != null) {
                holder.layoutCategoryItem.setGravity(Gravity.CENTER_VERTICAL);
                int pHorizontal = UIUtils.dpToPx(holder.itemView.getContext(), 16);
                int pVertical = UIUtils.dpToPx(holder.itemView.getContext(), 12);
                holder.layoutCategoryItem.setPadding(pHorizontal, pVertical, pHorizontal, pVertical);
            }
        }

        holder.itemView.setFocusable(true);
        holder.itemView.setFocusableInTouchMode(true);
        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.06f, 8f);
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                if (hasFocus && !v.isInTouchMode()) {
                    if (focusListener != null) {
                        focusListener.onCategoryFocused(pos, v);
                    }
                    if (pos != selectedPosition) {
                        selectedPosition = pos;
                        if (listener != null) {
                            listener.onCategoryClick(category);
                        }
                    }
                }
                boolean shouldShowSelection = (hasFocus && !v.isInTouchMode()) || pos == selectedPosition;
                v.setSelected(shouldShowSelection);
            }
        });

        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;

                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (navigationListener != null) {
                        navigationListener.onNavigateToChannels();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (pos == 0) {
                        if (navigationListener != null) {
                            navigationListener.onNavigateToHeader();
                            return true;
                        }
                    } else {
                        RecyclerView parent = (RecyclerView) holder.itemView.getParent();
                        if (parent != null) {
                            RecyclerView.ViewHolder targetVh = parent.findViewHolderForAdapterPosition(pos - 1);
                            if (targetVh != null && targetVh.itemView != null) {
                                targetVh.itemView.requestFocus();
                                return true;
                            }
                        }
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (pos == getItemCount() - 1) {
                        return true;
                    } else {
                        RecyclerView parent = (RecyclerView) holder.itemView.getParent();
                        if (parent != null) {
                            RecyclerView.ViewHolder targetVh = parent.findViewHolderForAdapterPosition(pos + 1);
                            if (targetVh != null && targetVh.itemView != null) {
                                targetVh.itemView.requestFocus();
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                int prev = selectedPosition;
                selectedPosition = pos;
                notifyItemChanged(prev);
                notifyItemChanged(selectedPosition);
                if (listener != null) {
                    listener.onCategoryClick(category);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList.size();
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutCategoryItem;
        ImageView imgCatIcon;
        TextView txtCatName;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutCategoryItem = itemView.findViewById(R.id.layoutCategoryItem);
            imgCatIcon = itemView.findViewById(R.id.imgCatIcon);
            txtCatName = itemView.findViewById(R.id.txtCatName);
        }
    }
}
