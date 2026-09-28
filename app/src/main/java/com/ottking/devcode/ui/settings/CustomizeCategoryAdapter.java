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

import com.ottking.devcode.R;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.utils.CustomOrderManager;
import com.ottking.devcode.utils.UIUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CustomizeCategoryAdapter extends RecyclerView.Adapter<CustomizeCategoryAdapter.ViewHolder> {

    public interface OnCategoryOrderActionListener {
        void onMoveUp(CategoryEntity category, int position);
        void onMoveDown(CategoryEntity category, int position);
        void onToggleVisibility(CategoryEntity category, int position);
        void onOrderChanged(List<CategoryEntity> newCategoryList);
    }

    private final List<CategoryEntity> categoryList = new ArrayList<>();
    private final OnCategoryOrderActionListener listener;
    private final CustomOrderManager orderManager;
    private RecyclerView recyclerView;

    // Track position currently in active move/reorder mode (-1 if none)
    private int activeMovePosition = -1;

    public CustomizeCategoryAdapter(CustomOrderManager orderManager, OnCategoryOrderActionListener listener) {
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
        if (position >= 0 && position < categoryList.size()) {
            return categoryList.get(position).id;
        }
        return RecyclerView.NO_ID;
    }

    public void setCategories(List<CategoryEntity> categories) {
        this.categoryList.clear();
        this.activeMovePosition = -1;
        if (categories != null) {
            this.categoryList.addAll(categories);
        }
        notifyDataSetChanged();
    }

    public List<CategoryEntity> getCategories() {
        return categoryList;
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
        for (CategoryEntity c : categoryList) {
            if (!CustomOrderManager.isAllCategory(c)) {
                newOrder.add(c.id);
            }
        }
        orderManager.setCustomCategoryOrder(newOrder);
        if (listener != null) {
            listener.onOrderChanged(new ArrayList<>(categoryList));
        }
    }

    public void moveCategoryUp(int pos, int targetFocusViewId) {
        if (pos <= 0 || pos >= categoryList.size()) return;
        if (pos == 1 && CustomOrderManager.isAllCategory(categoryList.get(0))) {
            if (recyclerView != null) {
                Toast.makeText(recyclerView.getContext(), "Cannot move above 'All' category", Toast.LENGTH_SHORT).show();
            }
            requestChildFocusOnPos(pos, targetFocusViewId);
            return;
        }

        int targetPos = pos - 1;
        Collections.swap(categoryList, pos, targetPos);
        if (activeMovePosition == pos) {
            activeMovePosition = targetPos;
        }

        notifyItemMoved(pos, targetPos);
        notifyItemChanged(pos, "PAYLOAD_MOVE");
        notifyItemChanged(targetPos, "PAYLOAD_MOVE");

        saveOrder();
        requestChildFocusOnPos(targetPos, targetFocusViewId);
    }

    public void moveCategoryDown(int pos, int targetFocusViewId) {
        if (pos < 0 || pos >= categoryList.size() - 1) return;
        if (pos == 0 && CustomOrderManager.isAllCategory(categoryList.get(0))) {
            if (recyclerView != null) {
                Toast.makeText(recyclerView.getContext(), "'All' category is fixed and cannot be moved", Toast.LENGTH_SHORT).show();
            }
            requestChildFocusOnPos(pos, targetFocusViewId);
            return;
        }

        int targetPos = pos + 1;
        Collections.swap(categoryList, pos, targetPos);
        if (activeMovePosition == pos) {
            activeMovePosition = targetPos;
        }

        notifyItemMoved(pos, targetPos);
        notifyItemChanged(pos, "PAYLOAD_MOVE");
        notifyItemChanged(targetPos, "PAYLOAD_MOVE");

        saveOrder();
        requestChildFocusOnPos(targetPos, targetFocusViewId);
    }

    public void toggleVisibility(int pos) {
        if (pos < 0 || pos >= categoryList.size()) return;
        CategoryEntity category = categoryList.get(pos);
        if (CustomOrderManager.isAllCategory(category)) return;

        orderManager.toggleCategoryHidden(category.id);
        notifyItemChanged(pos, "PAYLOAD_VISIBILITY");
        requestChildFocusOnPos(pos, R.id.btnCatVisibility);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_customize_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            CategoryEntity category = categoryList.get(position);
            boolean isAll = CustomOrderManager.isAllCategory(category);
            boolean isHidden = orderManager.isCategoryHidden(category.id);
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

                    if (isAll) {
                        holder.txtStatus.setText("Default (Fixed at Top • Cannot Move)");
                        holder.txtStatus.setTextColor(0xFF82B1FF);
                    } else {
                        holder.txtStatus.setText(isHidden ? "Hidden from Home & Drawer" : "Visible • Long press to reorder");
                        holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
                    }
                }

                if (!isAll) {
                    boolean canMoveUp = position > 0 && !(position == 1 && CustomOrderManager.isAllCategory(categoryList.get(0)));
                    holder.btnMoveUp.setEnabled(canMoveUp);
                    holder.btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.3f);

                    boolean canMoveDown = position < categoryList.size() - 1;
                    holder.btnMoveDown.setEnabled(canMoveDown);
                    holder.btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.3f);
                }
            }

            if (payloads.contains("PAYLOAD_VISIBILITY")) {
                if (!isAll) {
                    holder.btnVisibility.setImageResource(isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
                    holder.txtStatus.setText(isHidden ? "Hidden from Home & Drawer" : "Visible • Long press to reorder");
                    holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
                }
            }
            return;
        }

        onBindViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        CategoryEntity category = categoryList.get(position);
        boolean isAll = CustomOrderManager.isAllCategory(category);
        boolean isHidden = orderManager.isCategoryHidden(category.id);
        boolean isMovingThisItem = (position == activeMovePosition);

        holder.txtName.setText(category.name != null ? category.name : "Category " + category.id);

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

            if (isAll) {
                holder.txtStatus.setText("Default (Fixed at Top • Cannot Move)");
                holder.txtStatus.setTextColor(0xFF82B1FF);
            } else {
                holder.txtStatus.setText(isHidden ? "Hidden from Home & Drawer" : "Visible • Long press to reorder");
                holder.txtStatus.setTextColor(isHidden ? 0xFFFF5252 : 0xFF00E676);
            }
        }

        if (isAll) {
            holder.btnVisibility.setEnabled(false);
            holder.btnVisibility.setAlpha(0.25f);
            holder.btnMoveUp.setEnabled(false);
            holder.btnMoveUp.setAlpha(0.25f);
            holder.btnMoveDown.setEnabled(false);
            holder.btnMoveDown.setAlpha(0.25f);
            holder.imgReorderHandle.setVisibility(View.GONE);
        } else {
            holder.imgReorderHandle.setVisibility(View.VISIBLE);
            holder.btnVisibility.setEnabled(true);
            holder.btnVisibility.setAlpha(1.0f);
            holder.btnVisibility.setImageResource(isHidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);

            boolean canMoveUp = position > 0 && !(position == 1 && CustomOrderManager.isAllCategory(categoryList.get(0)));
            holder.btnMoveUp.setEnabled(canMoveUp);
            holder.btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.3f);

            boolean canMoveDown = position < categoryList.size() - 1;
            holder.btnMoveDown.setEnabled(canMoveDown);
            holder.btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.3f);
        }

        // Long Press on row to activate Reordering mode
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;

            CategoryEntity cat = categoryList.get(pos);
            if (CustomOrderManager.isAllCategory(cat)) {
                Toast.makeText(v.getContext(), "'All' category is fixed and cannot be moved", Toast.LENGTH_SHORT).show();
                return true;
            }

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
                Toast.makeText(v.getContext(), "Reorder active! Press ▲ or ▼ to move, then press OK to save.", Toast.LENGTH_LONG).show();
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
                        moveCategoryUp(pos, R.id.layoutCategoryRow);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        moveCategoryDown(pos, R.id.layoutCategoryRow);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BACK) {
                        activeMovePosition = -1;
                        notifyItemChanged(pos, "PAYLOAD_MOVE");
                        Toast.makeText(v.getContext(), "New order saved", Toast.LENGTH_SHORT).show();
                        requestChildFocusOnPos(pos, R.id.layoutCategoryRow);
                        return true;
                    }
                }
            }
            return false;
        });

        // Button Click Listeners with in-place move and focus retention
        holder.btnMoveUp.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                moveCategoryUp(pos, R.id.btnCatMoveUp);
            }
        });

        holder.btnMoveDown.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                moveCategoryDown(pos, R.id.btnCatMoveDown);
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
        return categoryList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgReorderHandle;
        final TextView txtPosition;
        final ImageView imgIcon;
        final TextView txtName;
        final TextView txtStatus;
        final ImageButton btnMoveUp;
        final ImageButton btnMoveDown;
        final ImageButton btnVisibility;

        ViewHolder(View itemView) {
            super(itemView);
            imgReorderHandle = itemView.findViewById(R.id.imgCatReorderHandle);
            txtPosition = itemView.findViewById(R.id.txtCatPosition);
            imgIcon = itemView.findViewById(R.id.imgCatIcon);
            txtName = itemView.findViewById(R.id.txtCatName);
            txtStatus = itemView.findViewById(R.id.txtCatStatus);
            btnMoveUp = itemView.findViewById(R.id.btnCatMoveUp);
            btnMoveDown = itemView.findViewById(R.id.btnCatMoveDown);
            btnVisibility = itemView.findViewById(R.id.btnCatVisibility);

            UIUtils.applyFocusAnimation(itemView, 1.02f, 4f);
            UIUtils.applyFocusAnimation(btnMoveUp, 1.15f, 6f);
            UIUtils.applyFocusAnimation(btnMoveDown, 1.15f, 6f);
            UIUtils.applyFocusAnimation(btnVisibility, 1.15f, 6f);
        }
    }
}
