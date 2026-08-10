package com.ottking.mobile.devcode.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.ottking.mobile.devcode.LandscapeActivity;
import com.ottking.mobile.devcode.PlayerActivity;
import com.ottking.mobile.devcode.R;
import com.ottking.mobile.devcode.database.ChannelEntity;

import java.util.List;

public class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(ChannelEntity channel, int position);
    }

    private Context context;
    private List<ChannelEntity> channelList;
    private boolean isGridMode;
    private OnFavoriteClickListener favoriteClickListener;
    private String playlistFilter;
    private String playlistTitle;
    private boolean isMovieSection = false;

    public ChannelAdapter(Context context, List<ChannelEntity> channelList, boolean isGridMode, OnFavoriteClickListener favoriteClickListener) {
        this.context = context;
        this.channelList = channelList;
        this.isGridMode = isGridMode;
        this.favoriteClickListener = favoriteClickListener;
    }

    public void setMovieSection(boolean isMovieSection) {
        this.isMovieSection = isMovieSection;
    }

    public void setPlaylistInfo(String playlistFilter, String playlistTitle) {
        this.playlistFilter = playlistFilter;
        this.playlistTitle = playlistTitle;
    }

    public void updateList(List<ChannelEntity> newList) {
        this.channelList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChannelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutRes = isGridMode ? R.layout.item_channel_grid : R.layout.item_channel_list;
        View view = LayoutInflater.from(context).inflate(layoutRes, parent, false);
        return new ChannelViewHolder(view, isGridMode);
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelViewHolder holder, int position) {
        if (channelList == null || position >= channelList.size()) return;
        ChannelEntity channel = channelList.get(position);
        if (channel == null) return;

        String title = channel.getTitle() != null ? channel.getTitle() : "Stream";
        String subCat = channel.getSubCategory() != null ? channel.getSubCategory() : "General";
        String res = channel.getResolution() != null ? channel.getResolution() : "HD";
        String streamType = channel.getStreamType() != null ? channel.getStreamType() : "HLS";
        String category = channel.getCategory() != null ? channel.getCategory() : "TV";
        String logoUrl = channel.getLogoUrl();

        boolean isMovie = isMovieSection;
        if (!isMovie && channel.getCategory() != null) {
            String cat = channel.getCategory().toLowerCase();
            if (cat.contains("movie") || cat.contains("vod") || cat.contains("cinema") || cat.contains("series")) {
                isMovie = true;
            }
        }
        if (!isMovie && channel.getSubCategory() != null) {
            String subCategoryLower = channel.getSubCategory().toLowerCase();
            if (subCategoryLower.contains("movie") || subCategoryLower.contains("vod") || subCategoryLower.contains("cinema") || subCategoryLower.contains("series")) {
                isMovie = true;
            }
        }
        if (!isMovie && playlistFilter != null) {
            String filter = playlistFilter.toLowerCase();
            if (filter.contains("movie") || filter.contains("vod") || filter.contains("cinema") || filter.contains("series")) {
                isMovie = true;
            }
        }

        if (holder.txtTitle != null) {
            holder.txtTitle.setText(title);
        }

        if (isGridMode) {
            if (holder.txtCategory != null) {
                holder.txtCategory.setText(subCat + " • " + res);
            }
            if (holder.txtBadge != null) {
                holder.txtBadge.setText(streamType.toUpperCase());
            }
            
            if (holder.imgFav != null) {
                if (channel.isFavorite()) {
                    holder.imgFav.setImageResource(R.drawable.ic_favorite);
                } else {
                    holder.imgFav.setImageResource(R.drawable.ic_favorite_border);
                }

                holder.imgFav.setOnClickListener(v -> {
                    if (favoriteClickListener != null) {
                        favoriteClickListener.onFavoriteClick(channel, position);
                    }
                });
            }

            if (holder.cardLogoFrame != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) holder.cardLogoFrame.getLayoutParams();
                float density = context.getResources().getDisplayMetrics().density;
                if (isMovie) {
                    params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    params.height = (int) (110 * density);
                    holder.cardLogoFrame.setLayoutParams(params);
                    holder.cardLogoFrame.setRadius(10 * density);
                } else {
                    params.width = (int) (66 * density);
                    params.height = (int) (66 * density);
                    holder.cardLogoFrame.setLayoutParams(params);
                    holder.cardLogoFrame.setRadius(33 * density);
                }
            }

            if (holder.imgLogo != null) {
                if (isMovie) {
                    Glide.with(context)
                            .load(logoUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .placeholder(R.drawable.img_hero_banner)
                            .error(R.drawable.img_app_logo)
                            .centerCrop()
                            .into(holder.imgLogo);
                } else {
                    Glide.with(context)
                            .load(logoUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .placeholder(R.drawable.img_app_logo)
                            .error(R.drawable.img_app_logo)
                            .circleCrop()
                            .into(holder.imgLogo);
                }
            }
        } else {
            if (holder.txtCategory != null) {
                holder.txtCategory.setText(category.toUpperCase() + " • " + streamType.toUpperCase());
            }
            if (holder.imgLogo != null) {
                Glide.with(context)
                        .load(logoUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .placeholder(R.drawable.img_app_logo)
                        .error(R.drawable.img_app_logo)
                        .into(holder.imgLogo);
            }
        }

        final boolean finalIsMovie = isMovie;
        holder.itemView.setOnClickListener(v -> {
            if (finalIsMovie) {
                Intent intent = new Intent(context, LandscapeActivity.class);
                intent.putExtra("channel_id", channel.getId());
                intent.putExtra("stream_url", channel.getStreamUrl());
                intent.putExtra("stream_title", channel.getTitle());
                intent.putExtra("stream_category", channel.getCategory());
                intent.putExtra("stream_type", channel.getStreamType());
                intent.putExtra("logo_url", channel.getLogoUrl());
                intent.putExtra("is_favorite", channel.isFavorite());
                context.startActivity(intent);
            } else if (context instanceof PlayerActivity) {
                ((PlayerActivity) context).playChannel(channel);
            } else {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("channel_id", channel.getId());
                intent.putExtra("stream_url", channel.getStreamUrl());
                intent.putExtra("stream_title", channel.getTitle());
                intent.putExtra("stream_category", channel.getCategory());
                intent.putExtra("stream_type", channel.getStreamType());
                intent.putExtra("logo_url", channel.getLogoUrl());
                intent.putExtra("is_favorite", channel.isFavorite());
                if (playlistFilter != null) {
                    intent.putExtra("playlist_filter", playlistFilter);
                }
                if (playlistTitle != null) {
                    intent.putExtra("playlist_title", playlistTitle);
                }
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return channelList != null ? channelList.size() : 0;
    }

    static class ChannelViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtCategory, txtBadge;
        ImageView imgLogo, imgFav;
        androidx.cardview.widget.CardView cardLogoFrame;

        public ChannelViewHolder(@NonNull View itemView, boolean isGrid) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtChannelTitle);
            
            if (isGrid) {
                txtCategory = itemView.findViewById(R.id.txtCategory);
                txtBadge = itemView.findViewById(R.id.txtStreamBadge);
                imgLogo = itemView.findViewById(R.id.imgChannelLogo);
                imgFav = itemView.findViewById(R.id.imgFavIcon);
                cardLogoFrame = itemView.findViewById(R.id.cardLogoFrame);
            } else {
                txtCategory = itemView.findViewById(R.id.txtChannelCategory);
                imgLogo = itemView.findViewById(R.id.imgChannelIcon);
            }
        }
    }
}
