package com.ottking.mobile.devcode.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ottking.mobile.devcode.R;
import com.ottking.mobile.devcode.model.PlaylistModel;

import java.util.List;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder> {

    public interface OnPlaylistClickListener {
        void onPlaylistClick(PlaylistModel playlist, int position);
    }

    private Context context;
    private List<PlaylistModel> playlistList;
    private OnPlaylistClickListener listener;

    public PlaylistAdapter(Context context, List<PlaylistModel> playlistList, OnPlaylistClickListener listener) {
        this.context = context;
        this.playlistList = playlistList;
        this.listener = listener;
    }

    public void updateList(List<PlaylistModel> newList) {
        this.playlistList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        if (playlistList == null || position >= playlistList.size()) return;
        PlaylistModel playlist = playlistList.get(position);
        if (playlist == null) return;

        if (holder.txtTitle != null) {
            holder.txtTitle.setText(playlist.getTitle());
        }
        if (holder.txtDesc != null) {
            holder.txtDesc.setText(playlist.getChannelCount() + " Channels");
        }
        if (holder.txtCountBadge != null) {
            holder.txtCountBadge.setText(playlist.getChannelCount() + " Channels");
        }

        if (holder.imgCover != null) {
            String iconUrl = playlist.getIconUrl();
            if (iconUrl != null && !iconUrl.trim().isEmpty()) {
                Glide.with(context)
                        .load(iconUrl.trim())
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .skipMemoryCache(false)
                        .placeholder(R.drawable.img_app_logo)
                        .error(R.drawable.img_app_logo)
                        .into(holder.imgCover);
            } else {
                Glide.with(context)
                        .load(R.drawable.img_app_logo)
                        .into(holder.imgCover);
            }
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaylistClick(playlist, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlistList != null ? playlistList.size() : 0;
    }

    static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtDesc, txtCountBadge;
        ImageView imgCover;

        public PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtPlaylistTitle);
            txtDesc = itemView.findViewById(R.id.txtPlaylistDesc);
            imgCover = itemView.findViewById(R.id.imgPlaylistCover);
        }
    }
}
