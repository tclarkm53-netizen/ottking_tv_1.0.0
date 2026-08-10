package com.example.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.R;
import com.example.model.CategoryModel;
import com.example.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(String category, int position);
    }

    private Context context;
    private List<CategoryModel> categoryModels;
    private int selectedPosition = 0;
    private OnCategoryClickListener listener;

    public CategoryAdapter(Context context, List<String> categories, OnCategoryClickListener listener) {
        this.context = context;
        this.listener = listener;
        updateCategories(categories);
    }

    public void updateCategories(List<String> newCategories) {
        this.categoryModels = new ArrayList<>();
        if (newCategories != null) {
            for (String catName : newCategories) {
                String iconUrl = PreferenceUtils.getCategoryIconUrl(context, catName);
                this.categoryModels.add(new CategoryModel(catName, iconUrl));
            }
        }
        this.selectedPosition = 0;
        notifyDataSetChanged();
    }

    public void updateCategoryModels(List<CategoryModel> models) {
        this.categoryModels = models != null ? models : new ArrayList<>();
        this.selectedPosition = 0;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryModel model = categoryModels.get(position);
        String catName = model.getName();
        String iconUrl = model.getIconUrl();
        if (iconUrl == null || iconUrl.isEmpty()) {
            iconUrl = PreferenceUtils.getCategoryIconUrl(context, catName);
        }

        holder.txtChip.setText(catName);

        if (holder.imgIcon != null) {
            if (iconUrl != null && !iconUrl.trim().isEmpty()) {
                holder.imgIcon.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(iconUrl.trim())
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .placeholder(R.drawable.ic_tv)
                        .error(R.drawable.ic_tv)
                        .circleCrop()
                        .into(holder.imgIcon);
            } else {
                holder.imgIcon.setVisibility(View.GONE);
            }
        }

        if (holder.layoutContainer != null) {
            if (position == selectedPosition) {
                holder.layoutContainer.setBackgroundResource(R.drawable.bg_chip_selected);
                holder.txtChip.setTextColor(ContextCompat.getColor(context, R.color.white));
            } else {
                holder.layoutContainer.setBackgroundResource(R.drawable.bg_chip_unselected);
                holder.txtChip.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
            }
        }

        holder.itemView.setOnClickListener(v -> {
            int prevPos = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(prevPos);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onCategoryClick(catName, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryModels != null ? categoryModels.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        View layoutContainer;
        TextView txtChip;
        ImageView imgIcon;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutContainer = itemView.findViewById(R.id.layoutCategoryChip);
            txtChip = itemView.findViewById(R.id.txtCategoryChip);
            imgIcon = itemView.findViewById(R.id.imgCategoryIcon);
        }
    }
}
