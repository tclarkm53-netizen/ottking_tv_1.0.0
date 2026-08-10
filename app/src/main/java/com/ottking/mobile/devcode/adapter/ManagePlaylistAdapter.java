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

import com.ottking.mobile.devcode.PlayerActivity;
import com.ottking.mobile.devcode.R;
import com.ottking.mobile.devcode.model.PlaylistModel;

import java.util.List;

public class ManagePlaylistAdapter extends RecyclerView.Adapter<ManagePlaylistAdapter.ManageViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(PlaylistModel playlist, int position);
    }

    private Context context;
    private List<PlaylistModel> playlistList;
    private OnDeleteClickListener deleteClickListener;

    public ManagePlaylistAdapter(Context context, List<PlaylistModel> playlistList, OnDeleteClickListener deleteClickListener) {
        this.context = context;
        this.playlistList = playlistList;
        this.deleteClickListener = deleteClickListener;
    }

    public void updateList(List<PlaylistModel> newList) {
        this.playlistList = newList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ManageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manage_playlist, parent, false);
        return new ManageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ManageViewHolder holder, int position) {
        if (playlistList == null || position >= playlistList.size()) return;
        PlaylistModel playlist = playlistList.get(position);
        if (playlist == null) return;

        holder.txtTitle.setText(playlist.getTitle());
        holder.txtInfo.setText(playlist.getChannelCount() + " Channels • Tap to Play");

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("playlist_filter", playlist.getTitle());
            intent.putExtra("playlist_title", playlist.getTitle());
            context.startActivity(intent);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteClickListener != null) {
                deleteClickListener.onDeleteClick(playlist, holder.getAdapterPosition());
            }
        });
    }

    @Override
    public int getItemCount() {
        return playlistList != null ? playlistList.size() : 0;
    }

    static class ManageViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtInfo;
        ImageView btnDelete;

        public ManageViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtManagePlaylistTitle);
            txtInfo = itemView.findViewById(R.id.txtManagePlaylistInfo);
            btnDelete = itemView.findViewById(R.id.btnDeletePlaylist);
        }
    }
}
