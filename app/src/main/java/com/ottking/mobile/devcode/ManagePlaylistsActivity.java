package com.ottking.mobile.devcode;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ottking.mobile.devcode.adapter.ManagePlaylistAdapter;
import com.ottking.mobile.devcode.database.AppDatabase;
import com.ottking.mobile.devcode.database.ChannelDao;
import com.ottking.mobile.devcode.database.ChannelEntity;
import com.ottking.mobile.devcode.model.PlaylistModel;
import com.ottking.mobile.devcode.utils.M3uParser;
import com.ottking.mobile.devcode.utils.PreferenceUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ManagePlaylistsActivity extends AppCompatActivity {

    private RecyclerView rvCustomPlaylists;
    private LinearLayout layoutEmptyPlaylists;
    private FloatingActionButton fabAddPlaylist;
    private ImageView btnBack;

    private ChannelDao channelDao;
    private ManagePlaylistAdapter playlistAdapter;
    private List<PlaylistModel> customPlaylists = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_playlists);

        channelDao = AppDatabase.getInstance(this).channelDao();

        btnBack = findViewById(R.id.btnBackPlaylistsActivity);
        rvCustomPlaylists = findViewById(R.id.rvCustomPlaylists);
        layoutEmptyPlaylists = findViewById(R.id.layoutEmptyPlaylists);
        fabAddPlaylist = findViewById(R.id.fabAddPlaylist);

        btnBack.setOnClickListener(v -> finish());

        rvCustomPlaylists.setLayoutManager(new LinearLayoutManager(this));

        fabAddPlaylist.setOnClickListener(v -> showAddPlaylistDialog());

        loadCustomPlaylists();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadCustomPlaylists();
    }

    private void loadCustomPlaylists() {
        customPlaylists = PreferenceUtils.getCustomM3uPlaylists(this);
        List<ChannelEntity> allChannels = channelDao != null ? channelDao.getAllChannels() : new ArrayList<>();

        for (PlaylistModel p : customPlaylists) {
            int count = 0;
            for (ChannelEntity c : allChannels) {
                if (c != null && c.getSubCategory() != null && c.getSubCategory().equalsIgnoreCase(p.getTitle())) {
                    count++;
                }
            }
            p.setChannelCount(count > 0 ? count : p.getChannelCount());
        }

        if (customPlaylists.isEmpty()) {
            layoutEmptyPlaylists.setVisibility(View.VISIBLE);
            rvCustomPlaylists.setVisibility(View.GONE);
        } else {
            layoutEmptyPlaylists.setVisibility(View.GONE);
            rvCustomPlaylists.setVisibility(View.VISIBLE);
        }

        final ManagePlaylistAdapter[] adapterHolder = new ManagePlaylistAdapter[1];
        adapterHolder[0] = new ManagePlaylistAdapter(this, customPlaylists, (playlist, position) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Playlist")
                    .setMessage("Are you sure you want to delete playlist '" + playlist.getTitle() + "' and all its channels?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        if (channelDao != null && playlist.getTitle() != null) {
                            channelDao.deleteBySubCategory(playlist.getTitle());
                        }
                        PreferenceUtils.removeCustomM3uPlaylist(this, playlist.getTitle());
                        loadCustomPlaylists();
                        Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        playlistAdapter = adapterHolder[0];
        rvCustomPlaylists.setAdapter(playlistAdapter);
    }

    private void showAddPlaylistDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_playlists, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = view.findViewById(R.id.etPlaylistName);
        EditText etUrl = view.findViewById(R.id.etPlaylistUrl);
        Button btnSample = view.findViewById(R.id.btnLoadSampleM3u);
        Button btnAdd = view.findViewById(R.id.btnAddM3uPlaylist);
        ProgressBar progress = view.findViewById(R.id.progressM3uLoading);
        RecyclerView rvManage = view.findViewById(R.id.rvManagePlaylists);
        ImageView btnClose = view.findViewById(R.id.btnClosePlaylistDialog);

        // Hide bottom list inside dialog as this Activity itself manages the list
        if (rvManage != null) rvManage.setVisibility(View.GONE);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSample.setOnClickListener(v -> {
            etName.setText("IPTV Org Live TV");
            etUrl.setText("https://iptv-org.github.io/iptv/index.m3u");
        });

        btnAdd.setOnClickListener(v -> {
            String title = etName.getText().toString().trim();
            String m3uUrl = etUrl.getText().toString().trim();

            if (title.isEmpty()) {
                etName.setError("Playlist title required");
                return;
            }
            if (m3uUrl.isEmpty() || (!m3uUrl.startsWith("http://") && !m3uUrl.startsWith("https://"))) {
                etUrl.setError("Valid M3U URL required");
                return;
            }

            progress.setVisibility(View.VISIBLE);
            btnAdd.setEnabled(false);

            M3uParser.parseUrlAsync(m3uUrl, title, new M3uParser.OnM3uParseCallback() {
                @Override
                public void onSuccess(List<ChannelEntity> channels) {
                    progress.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);

                    if (channelDao != null) {
                        channelDao.insertAll(channels);
                    }

                    PlaylistModel newPlaylist = new PlaylistModel(
                            "m3u_" + System.currentTimeMillis(),
                            title,
                            "Custom M3U Stream Playlist",
                            title,
                            "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?w=400",
                            channels.size()
                    );

                    PreferenceUtils.addCustomM3uPlaylist(ManagePlaylistsActivity.this, newPlaylist);
                    dialog.dismiss();
                    loadCustomPlaylists();

                    Toast.makeText(ManagePlaylistsActivity.this, "Loaded " + channels.size() + " channels into playlist!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    progress.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);
                    Toast.makeText(ManagePlaylistsActivity.this, "M3U Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }
}
