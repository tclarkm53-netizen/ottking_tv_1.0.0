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

    public void setCollapsed(boolean collapsed, RecyclerView recyclerView) {
        if (this.isCollapsed != collapsed) {
            this.isCollapsed = collapsed;
            if (recyclerView != null) {
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder vh = recyclerView.getChildViewHolder(child);
                    if (vh instanceof CategoryViewHolder) {
                        applyCollapsedState((CategoryViewHolder) vh, collapsed, true);
                    }
                }
            } else {
                notifyDataSetChanged();
            }
        }
    }

    public void setCollapsed(boolean collapsed) {
        setCollapsed(collapsed, null);
    }

    private void applyCollapsedState(CategoryViewHolder holder, boolean collapsed, boolean animated) {
        if (collapsed) {
            if (animated) {
                holder.txtCatName.animate()
                        .alpha(0f)
                        .setDuration(160)
                        .withEndAction(() -> holder.txtCatName.setVisibility(View.GONE))
                        .start();
            } else {
                holder.txtCatName.animate().cancel();
                holder.txtCatName.setAlpha(0f);
                holder.txtCatName.setVisibility(View.GONE);
            }
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
            if (animated) {
                holder.txtCatName.setAlpha(0f);
                holder.txtCatName.animate()
                        .alpha(1f)
                        .setDuration(220)
                        .start();
            } else {
                holder.txtCatName.animate().cancel();
                holder.txtCatName.setAlpha(1f);
            }
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
    }

    public CategoryEntity getCategoryAt(int pos) {
        if (pos >= 0 && pos < categoryList.size()) {
            return categoryList.get(pos);
        }
        return null;
    }

    public List<CategoryEntity> getCategoryList() {
        return new ArrayList<>(categoryList);
    }

    public int getPositionByCategoryId(int categoryId) {
        for (int i = 0; i < categoryList.size(); i++) {
            if (categoryList.get(i).id == categoryId) {
                return i;
            }
        }
        return -1;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        if (position >= 0 && position < categoryList.size()) {
            this.selectedPosition = position;
        }
    }

    public void setCategories(List<CategoryEntity> categories) {
        if (categories == null) return;
        if (this.categoryList.size() == categories.size()) {
            boolean same = true;
            for (int i = 0; i < categories.size(); i++) {
                CategoryEntity oldItem = this.categoryList.get(i);
                CategoryEntity newItem = categories.get(i);
                if (oldItem.id != newItem.id || (oldItem.name != null && !oldItem.name.equals(newItem.name))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }
        this.categoryList.clear();
        this.categoryList.addAll(categories);
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

        applyCollapsedState(holder, isCollapsed, false);

        holder.itemView.setSelected(position == selectedPosition);

        holder.itemView.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.04f, 6f);
            if (hasFocus) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    if (focusListener != null) {
                        focusListener.onCategoryFocused(pos, v);
                    }
                    if (pos != selectedPosition) {
                        int prev = selectedPosition;
                        selectedPosition = pos;
                        // Update visual selection directly on views without calling notifyItemChanged to avoid losing focus
                        if (holder.itemView.getParent() instanceof RecyclerView) {
                            RecyclerView rv = (RecyclerView) holder.itemView.getParent();
                            RecyclerView.ViewHolder prevHolder = rv.findViewHolderForAdapterPosition(prev);
                            if (prevHolder != null && prevHolder.itemView != null) {
                                prevHolder.itemView.setSelected(false);
                            }
                        }
                        holder.itemView.setSelected(true);
                        if (listener != null) {
                            listener.onCategoryClick(category);
                        }
                    }
                }
            }
        });

        holder.itemView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;

                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (pos >= categoryList.size() - 1) {
                        // Reached bottom of categories - consume event to avoid jumping
                        return true;
                    } else {
                        focusCategoryItem(holder.itemView, pos + 1);
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (pos == 0) {
                        if (navigationListener != null) {
                            navigationListener.onNavigateToHeader();
                            return true;
                        }
                    } else {
                        focusCategoryItem(holder.itemView, pos - 1);
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    // Stay inside category section
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (navigationListener != null) {
                        navigationListener.onNavigateToChannels();
                        return true;
                    }
                }
            }
            return false;
        });

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
                    }
                }
                holder.itemView.setSelected(true);
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

    private void focusCategoryItem(View currentView, int targetPos) {
        if (targetPos < 0 || targetPos >= categoryList.size()) return;
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
