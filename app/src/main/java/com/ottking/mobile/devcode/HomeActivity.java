package com.ottking.mobile.devcode;

import java.util.Locale;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.facebook.shimmer.ShimmerFrameLayout;
import androidx.transition.TransitionSet;

import android.graphics.Color;
import com.ottking.mobile.devcode.adapter.CategoryAdapter;
import com.ottking.mobile.devcode.adapter.ChannelAdapter;
import com.ottking.mobile.devcode.adapter.ManagePlaylistAdapter;
import com.ottking.mobile.devcode.adapter.NotificationAdapter;
import com.ottking.mobile.devcode.adapter.PlaylistAdapter;
import com.ottking.mobile.devcode.config.Config;
import com.ottking.mobile.devcode.database.AppDatabase;
import com.ottking.mobile.devcode.database.ChannelDao;
import com.ottking.mobile.devcode.database.ChannelEntity;
import com.ottking.mobile.devcode.model.AppUpdateInfo;
import com.ottking.mobile.devcode.model.NotificationModel;
import com.ottking.mobile.devcode.model.PlaylistModel;
import com.ottking.mobile.devcode.utils.M3uParser;
import com.ottking.mobile.devcode.utils.PreferenceUtils;
import com.ottking.mobile.devcode.utils.SampleData;
import com.ottking.mobile.devcode.utils.ServerApiManager;
import com.ottking.mobile.devcode.utils.CryptoUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HomeActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomNavigationView bottomNavigationView;
    private NestedScrollView nestedScrollView;
    private RecyclerView rvCategories, rvChannels, rvPlaylists, rvNotifications;
    private LinearLayout layoutSearchInput, layoutPlaylistHeader, layoutLoadingSpinner, layoutEmptyState, layoutFullscreenStatus, layoutAppUpdate;
    private View layoutShimmer;
    private ShimmerFrameLayout shimmerFrameLayout;
    private EditText etSearchQuery;
    private TextView txtAppName, txtSectionTitle, txtFeaturedTitle, txtPlaylistHeaderTitle, txtPlaylistHeaderCount, txtLoadingText, txtEmptyTitle, txtEmptySubtitle, btnEmptyAction;
    private TextView txtStatusBadge, txtStatusTitle, txtStatusMessage, txtStatusNotice, btnStatusRetry, btnStatusExit;
    private TextView txtUpdateBadge, txtUpdateTitle, txtUpdateMessage, txtUpdateReleaseNotes, txtUpdateForceNotice, btnUpdateNow, btnUpdateLater;
    private ImageView btnNavDrawer, btnSearch, btnReload, btnFavorites, btnNotification, btnBackToPlaylists, imgEmptyState, imgStatusIcon, imgUpdateIcon;
    private ProgressBar progressBarLoading;
    private View cardFeatured;
    private View cardResumeStream;
    private ImageView imgResumeLogo, btnResumePlay;
    private TextView txtResumeBadge, txtResumeTitle, txtResumeSubtitle;
    private AppUpdateInfo activeAppUpdateInfo = null;

    private ChannelDao channelDao;
    private ChannelAdapter channelAdapter;
    private CategoryAdapter categoryAdapter;
    private PlaylistAdapter playlistAdapter;
    private NotificationAdapter notificationAdapter;

    private List<ChannelEntity> currentChannels = new ArrayList<>();
    private List<ChannelEntity> activePlaylistChannels = new ArrayList<>();
    private List<PlaylistModel> playlistList = new ArrayList<>();
    private List<NotificationModel> notificationList = new ArrayList<>();
    private PlaylistModel activeSelectedPlaylist = null;
    private String currentMainTab = "tv"; // "tv", "playlist", "favorites", "notifications"
    private String previousMainTab = "tv";
    private int previousNavId = R.id.nav_live_tv;
    private PlaylistModel previousActivePlaylist = null;
    private String currentFilterCategory = "All";
    private boolean isShowingFavoritesOnly = false;
    private static boolean isInitialDataLoaded = false;

    private void checkAndResetDefaultFavorites() {
        android.content.SharedPreferences prefs = getSharedPreferences("app_settings", MODE_PRIVATE);
        if (!prefs.getBoolean("favorites_default_cleared_v1", false)) {
            if (channelDao != null) {
                channelDao.clearAllFavorites();
            }
            prefs.edit().putBoolean("favorites_default_cleared_v1", true).apply();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        channelDao = AppDatabase.getInstance(this).channelDao();
        checkAndResetDefaultFavorites();

        initViews();
        setupToolbarActions();
        setupCategoryList();
        setupRecyclerView();
        setupPlaylistsList();
        setupNotificationsList();
        setupBottomNavigation();
        setupNavigationDrawer();
        setupBackPressedHandler();
        loadChannels();
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        bottomNavigationView = findViewById(R.id.bottomNavigation);
        nestedScrollView = findViewById(R.id.nestedScrollView);
        rvCategories = findViewById(R.id.rvCategories);
        rvChannels = findViewById(R.id.rvChannels);
        rvPlaylists = findViewById(R.id.rvPlaylists);
        rvNotifications = findViewById(R.id.rvNotifications);
        layoutSearchInput = findViewById(R.id.layoutSearchInput);
        layoutPlaylistHeader = findViewById(R.id.layoutPlaylistHeader);
        layoutLoadingSpinner = findViewById(R.id.layoutLoadingSpinner);
        layoutShimmer = findViewById(R.id.layoutShimmer);
        shimmerFrameLayout = findViewById(R.id.shimmerFrameLayout);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        progressBarLoading = findViewById(R.id.progressBarLoading);
        txtLoadingText = findViewById(R.id.txtLoadingText);
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle);
        txtEmptySubtitle = findViewById(R.id.txtEmptySubtitle);
        btnEmptyAction = findViewById(R.id.btnEmptyAction);
        imgEmptyState = findViewById(R.id.imgEmptyState);

        txtAppName = findViewById(R.id.txtAppName);
        etSearchQuery = findViewById(R.id.etSearchQuery);
        txtSectionTitle = findViewById(R.id.txtSectionTitle);
        txtFeaturedTitle = findViewById(R.id.txtFeaturedTitle);
        txtPlaylistHeaderTitle = findViewById(R.id.txtPlaylistHeaderTitle);
        txtPlaylistHeaderCount = findViewById(R.id.txtPlaylistHeaderCount);
        cardFeatured = findViewById(R.id.cardFeatured);

        cardResumeStream = findViewById(R.id.cardResumeStream);
        imgResumeLogo = findViewById(R.id.imgResumeLogo);
        btnResumePlay = findViewById(R.id.btnResumePlay);
        txtResumeBadge = findViewById(R.id.txtResumeBadge);
        txtResumeTitle = findViewById(R.id.txtResumeTitle);
        txtResumeSubtitle = findViewById(R.id.txtResumeSubtitle);

        btnNavDrawer = findViewById(R.id.btnNavDrawer);
        btnSearch = findViewById(R.id.btnSearch);
        btnReload = findViewById(R.id.btnReload);
        btnFavorites = findViewById(R.id.btnFavorites);
        btnNotification = findViewById(R.id.btnNotification);
        btnBackToPlaylists = findViewById(R.id.btnBackToPlaylists);

        layoutFullscreenStatus = findViewById(R.id.layoutFullscreenStatus);
        imgStatusIcon = findViewById(R.id.imgStatusIcon);
        txtStatusBadge = findViewById(R.id.txtStatusBadge);
        txtStatusTitle = findViewById(R.id.txtStatusTitle);
        txtStatusMessage = findViewById(R.id.txtStatusMessage);
        txtStatusNotice = findViewById(R.id.txtStatusNotice);
        btnStatusRetry = findViewById(R.id.btnStatusRetry);
        btnStatusExit = findViewById(R.id.btnStatusExit);

        layoutAppUpdate = findViewById(R.id.layoutAppUpdate);
        imgUpdateIcon = findViewById(R.id.imgUpdateIcon);
        txtUpdateBadge = findViewById(R.id.txtUpdateBadge);
        txtUpdateTitle = findViewById(R.id.txtUpdateTitle);
        txtUpdateMessage = findViewById(R.id.txtUpdateMessage);
        txtUpdateReleaseNotes = findViewById(R.id.txtUpdateReleaseNotes);
        txtUpdateForceNotice = findViewById(R.id.txtUpdateForceNotice);
        btnUpdateNow = findViewById(R.id.btnUpdateNow);
        btnUpdateLater = findViewById(R.id.btnUpdateLater);

        if (btnStatusRetry != null) {
            btnStatusRetry.setOnClickListener(v -> syncChannelsFromServer());
        }
        if (btnStatusExit != null) {
            btnStatusExit.setOnClickListener(v -> finishAffinity());
        }

        if (btnUpdateNow != null) {
            btnUpdateNow.setOnClickListener(v -> {
                if (activeAppUpdateInfo != null) {
                    downloadAndInstallApk(activeAppUpdateInfo.getDownloadUrl());
                } else {
                    downloadAndInstallApk("https://verify-app.alwaysdata.net/new/mobile/app-release.apk");
                }
            });
        }
        if (btnUpdateLater != null) {
            btnUpdateLater.setOnClickListener(v -> hideAppUpdateUI());
        }
    }

    private void setupToolbarActions() {
        btnNavDrawer.setOnClickListener(v -> {
            if ("favorites".equals(currentMainTab) || "notifications".equals(currentMainTab) || activeSelectedPlaylist != null) {
                onBackPressed();
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        btnSearch.setOnClickListener(v -> {
            if (layoutSearchInput.getVisibility() == View.VISIBLE) {
                layoutSearchInput.setVisibility(View.GONE);
                etSearchQuery.setText("");
                filterChannels();
            } else {
                layoutSearchInput.setVisibility(View.VISIBLE);
                etSearchQuery.requestFocus();
            }
        });

        etSearchQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChannels();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnReload.setOnClickListener(v -> {
            reloadActiveStateData();
        });

        btnFavorites.setOnClickListener(v -> {
            showFavoritesScreen();
        });

        btnNotification.setOnClickListener(v -> {
            showNotificationsScreen();
        });

        cardFeatured.setOnClickListener(v -> {
            if (!currentChannels.isEmpty()) {
                ChannelEntity channel = currentChannels.get(0);
                if (FloatingPlayerService.isRunning()) {
                    Intent serviceIntent = new Intent(HomeActivity.this, FloatingPlayerService.class);
                    serviceIntent.setAction(FloatingPlayerService.ACTION_START_FLOATING);
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_CHANNEL_ID, String.valueOf(channel.getId()));
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_URL, channel.getStreamUrl());
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TITLE, channel.getTitle());
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_CATEGORY, channel.getCategory());
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TYPE, channel.getStreamType());
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_LOGO_URL, channel.getLogoUrl());
                    serviceIntent.putExtra(FloatingPlayerService.EXTRA_SEEK_POSITION, 0L);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }
                    Toast.makeText(HomeActivity.this, "Playing on Floating Player: " + channel.getTitle(), Toast.LENGTH_SHORT).show();
                    return;
                }
                boolean isMovie = "movie".equalsIgnoreCase(currentMainTab) || isMovieChannel(channel);
                Intent intent;
                if (isMovie) {
                    intent = new Intent(HomeActivity.this, LandscapeActivity.class);
                } else {
                    intent = new Intent(HomeActivity.this, PlayerActivity.class);
                }
                intent.putExtra("channel_id", channel.getId());
                intent.putExtra("stream_url", channel.getStreamUrl());
                intent.putExtra("stream_title", channel.getTitle());
                intent.putExtra("stream_category", channel.getCategory());
                intent.putExtra("stream_type", channel.getStreamType());
                intent.putExtra("logo_url", channel.getLogoUrl());
                intent.putExtra("is_favorite", channel.isFavorite());
                startActivity(intent);
            }
        });
    }

    private void setupCategoryList() {
        categoryAdapter = new CategoryAdapter(this, new ArrayList<>(), (category, position) -> {
            currentFilterCategory = category;
            filterChannels();
        });
        rvCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvCategories.setAdapter(categoryAdapter);
        updateCategoriesFromChannels();
    }

    private boolean isMovieChannel(ChannelEntity channel) {
        if (channel == null) return false;
        String cat = channel.getCategory() != null ? channel.getCategory().toLowerCase().trim() : "";
        String subCat = channel.getSubCategory() != null ? channel.getSubCategory().toLowerCase().trim() : "";
        String title = channel.getTitle() != null ? channel.getTitle().toLowerCase().trim() : "";
        String url = channel.getStreamUrl() != null ? channel.getStreamUrl().toLowerCase().trim() : "";
        String type = channel.getStreamType() != null ? channel.getStreamType().toLowerCase().trim() : "";

        if (cat.equalsIgnoreCase("movie") || cat.equalsIgnoreCase("movies") || cat.equalsIgnoreCase("vod") || cat.equalsIgnoreCase("cinema") || cat.equalsIgnoreCase("film")) return true;
        if (subCat.equalsIgnoreCase("movie") || subCat.equalsIgnoreCase("movies") || subCat.equalsIgnoreCase("vod") || subCat.equalsIgnoreCase("cinema") || subCat.equalsIgnoreCase("film")) return true;

        if (cat.contains("movie") || cat.contains("movies") || cat.contains("vod") || cat.contains("cinema") || cat.contains("film")) return true;
        if (subCat.contains("movie") || subCat.contains("movies") || subCat.contains("vod") || subCat.contains("cinema") || subCat.contains("film")) return true;

        if ("movie".equals(type) || "vod".equals(type)) return true;
        if (url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") || url.contains("/movie/") || url.contains("/movies/") || url.contains("/vod/")) return true;

        if (title.contains("[movie]") || title.contains("(movie)") || title.contains("[vod]") || title.contains("[cinema]") || title.contains("[film]")) return true;

        return false;
    }

    private boolean isMovieCategoryName(String name) {
        if (name == null) return false;
        String lower = name.trim().toLowerCase();
        return lower.contains("movie") || lower.contains("movies") || lower.contains("vod") || lower.contains("cinema") || lower.contains("film");
    }

    private void updateCategoriesFromChannels() {
        List<ChannelEntity> all = channelDao != null ? channelDao.getAllChannels() : new ArrayList<>();
        Set<String> categoriesSet = new LinkedHashSet<>();

        boolean isMovieTab = "movie".equalsIgnoreCase(currentMainTab);

        for (ChannelEntity channel : all) {
            if (channel == null) continue;
            boolean isMovie = isMovieChannel(channel);
            if (isMovieTab) {
                if (!isMovie) continue;
            } else {
                if (isMovie) continue;
            }

            String cat = channel.getCategory() != null ? channel.getCategory().trim() : "";
            String subCat = channel.getSubCategory() != null ? channel.getSubCategory().trim() : "";

            if (isMovieTab) {
                if (!subCat.isEmpty() && !subCat.equalsIgnoreCase("General")) {
                    categoriesSet.add(subCat);
                } else if (!cat.isEmpty() && !cat.equalsIgnoreCase("General")) {
                    categoriesSet.add(cat);
                }
            } else {
                if (isMovieCategoryName(cat) || isMovieCategoryName(subCat)) continue;

                if (!subCat.isEmpty() && !subCat.equalsIgnoreCase("General") && !isMovieCategoryName(subCat)) {
                    categoriesSet.add(subCat);
                } else if (!cat.isEmpty() && !cat.equalsIgnoreCase("tv") && !cat.equalsIgnoreCase("General") && !isMovieCategoryName(cat)) {
                    categoriesSet.add(cat);
                }
            }
        }

        if (!isMovieTab) {
            // Also add custom M3U playlists as category chips if any exist
            List<PlaylistModel> customM3uList = PreferenceUtils.getCustomM3uPlaylists(this);
            for (PlaylistModel custom : customM3uList) {
                if (custom != null && custom.getTitle() != null && !custom.getTitle().trim().isEmpty()) {
                    categoriesSet.add(custom.getTitle().trim());
                }
            }
        }

        if (categoriesSet.isEmpty()) {
            if (rvCategories != null) rvCategories.setVisibility(View.GONE);
            if (categoryAdapter != null) {
                categoryAdapter.updateCategories(new ArrayList<>());
            }
            return;
        }

        List<String> dynamicCategories = new ArrayList<>();
        dynamicCategories.add("All");
        dynamicCategories.addAll(categoriesSet);
        if (rvCategories != null) rvCategories.setVisibility(View.GONE);
        if (categoryAdapter != null) {
            categoryAdapter.updateCategories(dynamicCategories);
        }
    }

    private void setupRecyclerView() {
        rvChannels.setLayoutManager(new GridLayoutManager(this, 3));
        channelAdapter = new ChannelAdapter(this, currentChannels, true, (channel, position) -> {
            boolean newFavStatus = !channel.isFavorite();
            channel.setFavorite(newFavStatus);
            if (channelDao != null) {
                channelDao.update(channel);
            }
            if ("favorites".equalsIgnoreCase(currentMainTab)) {
                showFavoritesScreen();
            } else {
                channelAdapter.notifyItemChanged(position);
            }
            playlistList = generatePlaylists();
            if (playlistAdapter != null) {
                playlistAdapter.updateList(playlistList);
            }
            Toast.makeText(this, newFavStatus ? "Added to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
        });
        rvChannels.setAdapter(channelAdapter);
    }

    private void setupNotificationsList() {
        if (rvNotifications != null) {
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            notificationAdapter = new NotificationAdapter(this, notificationList, (notification, position) -> {
                if (notification != null && notification.getId() != null && notification.getId().startsWith("update_")) {
                    if (activeAppUpdateInfo != null) {
                        showAppUpdateUI(activeAppUpdateInfo);
                    } else {
                        checkAppUpdateFromApi();
                    }
                }
            });
            rvNotifications.setAdapter(notificationAdapter);
        }
    }

    private List<NotificationModel> generateNotifications() {
        return new ArrayList<>(notificationList);
    }

    private void showLoadingSpinner(String message) {
        if (layoutShimmer != null && shimmerFrameLayout != null) {
            layoutShimmer.setVisibility(View.VISIBLE);
            shimmerFrameLayout.startShimmer();
        } else if (layoutLoadingSpinner != null) {
            if (txtLoadingText != null) txtLoadingText.setText(message != null ? message : "Loading data...");
            layoutLoadingSpinner.setVisibility(View.VISIBLE);
        }
        if (layoutEmptyState != null) layoutEmptyState.setVisibility(View.GONE);
        if (rvCategories != null) rvCategories.setVisibility(View.GONE);
        if (rvChannels != null) rvChannels.setVisibility(View.GONE);
        if (rvPlaylists != null) rvPlaylists.setVisibility(View.GONE);
        if (rvNotifications != null) rvNotifications.setVisibility(View.GONE);
    }

    private void hideLoadingSpinner() {
        if (layoutShimmer != null && shimmerFrameLayout != null) {
            shimmerFrameLayout.stopShimmer();
            layoutShimmer.setVisibility(View.GONE);
        }
        if (layoutLoadingSpinner != null) {
            layoutLoadingSpinner.setVisibility(View.GONE);
        }
    }

    private void showEmptyState(int iconRes, String title, String subtitle, String actionBtnText, Runnable onActionClick) {
        hideLoadingSpinner();
        if (layoutEmptyState == null) return;
        if (imgEmptyState != null) imgEmptyState.setImageResource(iconRes);
        if (txtEmptyTitle != null) txtEmptyTitle.setText(title);
        if (txtEmptySubtitle != null) txtEmptySubtitle.setText(subtitle);
        if (btnEmptyAction != null) {
            btnEmptyAction.setText(actionBtnText);
            btnEmptyAction.setOnClickListener(v -> {
                if (onActionClick != null) onActionClick.run();
            });
        }
        layoutEmptyState.setVisibility(View.VISIBLE);
        if (rvChannels != null) rvChannels.setVisibility(View.GONE);
        if (rvPlaylists != null) rvPlaylists.setVisibility(View.GONE);
        if (rvNotifications != null) rvNotifications.setVisibility(View.GONE);
    }

    private void hideEmptyState() {
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    private void updateToolbarHeaderUI() {
        if (btnNavDrawer != null) {
            if ("favorites".equals(currentMainTab) || "notifications".equals(currentMainTab) || activeSelectedPlaylist != null) {
                btnNavDrawer.setImageResource(R.drawable.ic_arrow_back);
            } else {
                btnNavDrawer.setImageResource(R.drawable.ic_menu);
            }
        }
        if (txtAppName == null) return;
        if ("favorites".equals(currentMainTab)) {
            txtAppName.setText("Favorites");
        } else if ("notifications".equals(currentMainTab)) {
            txtAppName.setText("Notifications");
        } else if (activeSelectedPlaylist != null) {
            txtAppName.setText(activeSelectedPlaylist.getTitle());
        } else if ("playlist".equals(currentMainTab)) {
            txtAppName.setText("IPTV Categories");
        } else if ("movie".equals(currentMainTab)) {
            txtAppName.setText("Movies & Cinema");
        } else {
            txtAppName.setText("Live TV Channels");
        }
    }

    private void reloadActiveStateData() {
        Toast.makeText(this, "Reloading channels from server...", Toast.LENGTH_SHORT).show();
        isInitialDataLoaded = false;
        syncChannelsFromServer();
    }

    public void showFavoritesScreen() {
        if (!"favorites".equalsIgnoreCase(currentMainTab) && !"notifications".equalsIgnoreCase(currentMainTab)) {
            previousMainTab = currentMainTab;
            previousNavId = (bottomNavigationView != null) ? bottomNavigationView.getSelectedItemId() : R.id.nav_live_tv;
            previousActivePlaylist = activeSelectedPlaylist;
        }
        currentMainTab = "favorites";
        updateToolbarHeaderUI();
        applyLayoutTransition();
        isShowingFavoritesOnly = true;
        btnFavorites.setColorFilter(getColor(R.color.accent_gold));

        List<ChannelEntity> favs = channelDao != null ? channelDao.getFavoriteChannels() : new ArrayList<>();
        activeSelectedPlaylist = null;
        activePlaylistChannels.clear();

        layoutPlaylistHeader.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        rvPlaylists.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);

        txtSectionTitle.setVisibility(View.VISIBLE);
        txtSectionTitle.setText("⭐ Favorite Channels (" + favs.size() + ")");

        if (favs.isEmpty()) {
            rvChannels.setVisibility(View.GONE);
            showEmptyState(R.drawable.ic_favorite_border, "No Favorites Added Yet", "Tap the star icon on any channel to save it to your favorites list.", "Explore Live TV", this::showPlaylistsView);
        } else {
            hideEmptyState();
            rvChannels.setVisibility(View.VISIBLE);
            currentChannels = favs;
            if (channelAdapter != null) {
                channelAdapter.setPlaylistInfo("favorite", "Favorites");
                channelAdapter.updateList(favs);
            }
        }
    }

    public void showNotificationsScreen() {
        if ("notifications".equalsIgnoreCase(currentMainTab)) return;
        if (!"favorites".equalsIgnoreCase(currentMainTab)) {
            previousMainTab = currentMainTab;
            previousNavId = (bottomNavigationView != null) ? bottomNavigationView.getSelectedItemId() : R.id.nav_live_tv;
            previousActivePlaylist = activeSelectedPlaylist;
        }
        currentMainTab = "notifications";
        updateToolbarHeaderUI();
        applyLayoutTransition();
        btnFavorites.setColorFilter(getColor(R.color.text_primary));

        activeSelectedPlaylist = null;
        activePlaylistChannels.clear();

        layoutPlaylistHeader.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        rvPlaylists.setVisibility(View.GONE);
        rvChannels.setVisibility(View.GONE);

        txtSectionTitle.setVisibility(View.VISIBLE);
        txtSectionTitle.setText("🔔 Notifications & System Updates");

        if (activeAppUpdateInfo != null) {
            addAppUpdateNotification(activeAppUpdateInfo);
        }
        notificationList = generateNotifications();
        if (notificationList.isEmpty()) {
            showEmptyState(R.drawable.ic_notification, "No Notifications", "You have no new notifications right now.", "Explore Live TV", this::showPlaylistsView);
        } else {
            hideEmptyState();
            rvNotifications.setVisibility(View.VISIBLE);
            if (notificationAdapter != null) {
                notificationAdapter.updateList(notificationList);
            }
        }
    }

    private void setupPlaylistsList() {
        playlistList = generatePlaylists();
        playlistAdapter = new PlaylistAdapter(this, playlistList, (playlist, position) -> {
            openPlaylistDetails(playlist);
        });
        rvPlaylists.setLayoutManager(new GridLayoutManager(this, 2));
        rvPlaylists.setAdapter(playlistAdapter);

        btnBackToPlaylists.setOnClickListener(v -> {
            showPlaylistsView();
        });
    }

    private List<PlaylistModel> generatePlaylists() {
        List<PlaylistModel> list = new ArrayList<>();
        List<ChannelEntity> all = channelDao != null ? channelDao.getAllChannels() : new ArrayList<>();

        Map<String, Integer> serverCategories = new LinkedHashMap<>();

        for (ChannelEntity c : all) {
            if (c == null) continue;
            if (isMovieChannel(c)) continue;

            String subCat = c.getSubCategory() != null ? c.getSubCategory().trim() : "";
            String cat = c.getCategory() != null ? c.getCategory().trim() : "";

            if (isMovieCategoryName(subCat) || isMovieCategoryName(cat)) continue;

            String categoryKey = !subCat.isEmpty() && !subCat.equalsIgnoreCase("General") ? subCat : cat;
            if (!categoryKey.isEmpty() && !categoryKey.equalsIgnoreCase("General") && !categoryKey.equalsIgnoreCase("tv") && !isMovieCategoryName(categoryKey)) {
                serverCategories.put(categoryKey, serverCategories.getOrDefault(categoryKey, 0) + 1);
            }
        }

        for (Map.Entry<String, Integer> entry : serverCategories.entrySet()) {
            String catName = entry.getKey();
            int count = entry.getValue();
            if (count > 0) {
                String img = PreferenceUtils.getCategoryIconUrl(this, catName);
                if (img == null || img.trim().isEmpty()) {
                    for (ChannelEntity c : all) {
                        if (c != null) {
                            String sub = c.getSubCategory() != null ? c.getSubCategory().trim() : "";
                            String cat = c.getCategory() != null ? c.getCategory().trim() : "";
                            if (catName.equalsIgnoreCase(sub) || catName.equalsIgnoreCase(cat)) {
                                if (c.getLogoUrl() != null && !c.getLogoUrl().trim().isEmpty()) {
                                    img = c.getLogoUrl().trim();
                                    break;
                                }
                            }
                        }
                    }
                }
                list.add(new PlaylistModel("p_cat_" + catName, catName, count + " Live TV Channels", catName, img != null ? img.trim() : "", count));
            }
        }

        // Custom M3U Playlists added by user
        List<PlaylistModel> customM3uList = PreferenceUtils.getCustomM3uPlaylists(this);
        for (PlaylistModel custom : customM3uList) {
            int count = 0;
            for (ChannelEntity c : all) {
                if (c != null && c.getSubCategory() != null && c.getSubCategory().equalsIgnoreCase(custom.getTitle())) {
                    count++;
                }
            }
            custom.setChannelCount(count > 0 ? count : custom.getChannelCount());
            list.add(custom);
        }

        return list;
    }

    private void applyLayoutTransition() {
        if (nestedScrollView != null) {
            nestedScrollView.smoothScrollTo(0, 0);
            TransitionSet transitionSet = new TransitionSet();
            transitionSet.addTransition(new Fade(Fade.IN).setDuration(150));
            transitionSet.addTransition(new AutoTransition().setDuration(150));
            transitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
            TransitionManager.beginDelayedTransition(nestedScrollView, transitionSet);
        }
    }

    private void showPlaylistsView() {
        currentMainTab = "playlist";
        previousMainTab = "playlist";
        previousNavId = R.id.nav_category;
        previousActivePlaylist = null;
        activeSelectedPlaylist = null;
        if (bottomNavigationView != null && bottomNavigationView.getSelectedItemId() != R.id.nav_category) {
            bottomNavigationView.getMenu().findItem(R.id.nav_category).setChecked(true);
        }
        updateToolbarHeaderUI();
        applyLayoutTransition();
        activePlaylistChannels.clear();
        btnFavorites.setColorFilter(getColor(R.color.text_primary));
        hideEmptyState();

        layoutPlaylistHeader.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        rvChannels.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        txtSectionTitle.setVisibility(View.GONE);

        playlistList = generatePlaylists();
        if (playlistList.isEmpty()) {
            showEmptyState(R.drawable.ic_playlist, "No Playlists Found", "Create or add M3U playlists to get started.", "Add Stream", this::showAddStreamDialog);
        } else {
            hideEmptyState();
            rvPlaylists.setVisibility(View.VISIBLE);
            if (playlistAdapter != null) {
                playlistAdapter.updateList(playlistList);
            }
        }
    }

    private void showMainChannelsView() {
        currentMainTab = "tv";
        previousMainTab = "tv";
        previousNavId = R.id.nav_live_tv;
        previousActivePlaylist = null;
        activeSelectedPlaylist = null;
        if (bottomNavigationView != null && bottomNavigationView.getSelectedItemId() != R.id.nav_live_tv) {
            bottomNavigationView.getMenu().findItem(R.id.nav_live_tv).setChecked(true);
        }
        updateToolbarHeaderUI();
        applyLayoutTransition();
        activePlaylistChannels.clear();
        btnFavorites.setColorFilter(getColor(R.color.text_primary));
        hideEmptyState();

        updateCategoriesFromChannels();
        rvPlaylists.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        layoutPlaylistHeader.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        txtSectionTitle.setVisibility(View.VISIBLE);
        txtSectionTitle.setText("Live TV Channels");
        if (channelAdapter != null) {
            channelAdapter.setPlaylistInfo(null, null);
        }
        filterChannels();
    }

    private void openPlaylistDetails(PlaylistModel playlist) {
        activeSelectedPlaylist = playlist;
        currentMainTab = "playlist";
        previousMainTab = "playlist";
        previousNavId = R.id.nav_category;
        previousActivePlaylist = playlist;
        if (bottomNavigationView != null && bottomNavigationView.getSelectedItemId() != R.id.nav_category) {
            bottomNavigationView.getMenu().findItem(R.id.nav_category).setChecked(true);
        }
        updateToolbarHeaderUI();
        applyLayoutTransition();
        btnFavorites.setColorFilter(getColor(R.color.text_primary));
        hideEmptyState();

        rvPlaylists.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        layoutPlaylistHeader.setVisibility(View.GONE);

        txtSectionTitle.setVisibility(View.VISIBLE);
        txtSectionTitle.setText(playlist.getTitle() + " Channels");

        List<ChannelEntity> filtered = new ArrayList<>();
        List<ChannelEntity> all = channelDao != null ? channelDao.getAllChannels() : new ArrayList<>();
        String filter = playlist.getCategoryFilter();

        for (ChannelEntity c : all) {
            if (c == null) continue;
            String cat = c.getCategory() != null ? c.getCategory() : "";
            String subCat = c.getSubCategory() != null ? c.getSubCategory() : "";
            String type = c.getStreamType() != null ? c.getStreamType() : "";

            if (filter.equalsIgnoreCase("favorite")) {
                if (c.isFavorite()) filtered.add(c);
            } else if (filter.equalsIgnoreCase("all")) {
                filtered.add(c);
            } else if (cat.equalsIgnoreCase(filter) || subCat.equalsIgnoreCase(filter) || type.equalsIgnoreCase(filter)) {
                filtered.add(c);
            }
        }

        activePlaylistChannels = new ArrayList<>(filtered);
        currentChannels = filtered;

        if (filtered.isEmpty()) {
            showEmptyState(R.drawable.ic_tv, "No Channels In Playlist", "This playlist does not have any stream channels yet.", "Back to Playlists", this::showPlaylistsView);
        } else {
            hideEmptyState();
            rvChannels.setVisibility(View.VISIBLE);

            // Build dynamic category list for this playlist
            List<String> dynamicCats = new ArrayList<>();
            dynamicCats.add("All");
            Set<String> subCats = new LinkedHashSet<>();
            for (ChannelEntity c : filtered) {
                if (c.getSubCategory() != null && !c.getSubCategory().trim().isEmpty()
                        && !c.getSubCategory().equalsIgnoreCase(playlist.getTitle())) {
                    subCats.add(c.getSubCategory().trim());
                } else if (c.getCategory() != null && !c.getCategory().trim().isEmpty()) {
                    subCats.add(c.getCategory().trim());
                }
            }
            dynamicCats.addAll(subCats);

            if (dynamicCats.size() > 1) {
                rvCategories.setVisibility(View.GONE);
                if (categoryAdapter != null) {
                    categoryAdapter.updateCategories(dynamicCats);
                }
            } else {
                rvCategories.setVisibility(View.GONE);
            }

            if (channelAdapter != null) {
                channelAdapter.setPlaylistInfo(filter, playlist.getTitle());
                channelAdapter.updateList(filtered);
            }
        }
    }

    private void showMoviesView() {
        currentMainTab = "movie";
        previousMainTab = "movie";
        previousNavId = R.id.nav_movies;
        previousActivePlaylist = null;
        activeSelectedPlaylist = null;
        if (bottomNavigationView != null && bottomNavigationView.getSelectedItemId() != R.id.nav_movies) {
            bottomNavigationView.getMenu().findItem(R.id.nav_movies).setChecked(true);
        }
        updateToolbarHeaderUI();
        applyLayoutTransition();
        activePlaylistChannels.clear();
        btnFavorites.setColorFilter(getColor(R.color.text_primary));
        hideEmptyState();

        layoutPlaylistHeader.setVisibility(View.GONE);
        rvPlaylists.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.GONE);
        rvCategories.setVisibility(View.GONE);
        cardFeatured.setVisibility(View.GONE);
        txtSectionTitle.setVisibility(View.VISIBLE);
        txtSectionTitle.setText("Blockbuster Movies");
        txtFeaturedTitle.setText("Featured 4K Movies & Cinema");

        if (channelAdapter != null) {
            channelAdapter.setPlaylistInfo("movie", "Movies");
        }

        List<ChannelEntity> all = channelDao != null ? channelDao.getAllChannels() : new ArrayList<>();
        boolean hasMovies = false;
        for (ChannelEntity c : all) {
            if (c != null && isMovieChannel(c)) {
                hasMovies = true;
                break;
            }
        }
        if (!hasMovies && !isInitialDataLoaded) {
            syncMoviesFromServer();
        } else {
            updateCategoriesFromChannels();
            filterChannels();
        }
    }

    private void syncMoviesFromServer() {
        currentMainTab = "movie";
        showLoadingSpinner("Loading Movies from Server Route...");
        ServerApiManager.syncChannelsFromRoute(this, channelDao, Config.ROUTE_MOVIES, new ServerApiManager.SyncCallback() {
            @Override
            public void onSuccess(int channelsSyncedCount) {
                isInitialDataLoaded = true;
                hideLoadingSpinner();
                hideFullscreenStatusUI();
                if (channelDao != null) {
                    currentChannels = channelDao.getAllChannels();
                }
                updateCategoriesFromChannels();
                filterChannels();
            }

            @Override
            public void onError(String errorMessage) {
                hideLoadingSpinner();
                if (channelDao != null) {
                    currentChannels = channelDao.getAllChannels();
                }
                if (currentChannels != null && !currentChannels.isEmpty()) {
                    isInitialDataLoaded = true;
                    hideFullscreenStatusUI();
                    updateCategoriesFromChannels();
                    filterChannels();
                    Toast.makeText(HomeActivity.this, "Offline Mode: Using cached movies.", Toast.LENGTH_SHORT).show();
                } else if (errorMessage != null && errorMessage.startsWith(Config.MAINTENANCE_STATUS_CODE)) {
                    showFullscreenStatusUI(errorMessage);
                } else {
                    showFullscreenStatusUI(errorMessage);
                }
            }
        });
    }

    private void setupBottomNavigation() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setLabelVisibilityMode(NavigationBarView.LABEL_VISIBILITY_SELECTED);
            bottomNavigationView.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_live_tv && "tv".equalsIgnoreCase(currentMainTab) && activeSelectedPlaylist == null) {
                    return true;
                }
                if (id == R.id.nav_category && "playlist".equalsIgnoreCase(currentMainTab) && activeSelectedPlaylist == null) {
                    return true;
                }
                if (id == R.id.nav_movies && "movie".equalsIgnoreCase(currentMainTab) && activeSelectedPlaylist == null) {
                    return true;
                }

                applyLayoutTransition();
                animateBottomNavItemSelection(id);
                if (id == R.id.nav_live_tv) {
                    showMainChannelsView();
                    txtSectionTitle.setText("Live TV Channels");
                    txtFeaturedTitle.setText("Featured Live Broadcast");
                    updateToolbarHeaderUI();
                } else if (id == R.id.nav_category) {
                    showPlaylistsView();
                } else if (id == R.id.nav_movies) {
                    showMoviesView();
                }
                return true;
            });
            bottomNavigationView.post(() -> animateBottomNavItemSelection(bottomNavigationView.getSelectedItemId()));
        }
    }

    private void animateBottomNavItemSelection(int selectedItemId) {
        if (bottomNavigationView == null) return;
        ViewGroup menuView = (ViewGroup) bottomNavigationView.getChildAt(0);
        if (menuView == null) return;

        for (int i = 0; i < menuView.getChildCount(); i++) {
            View itemView = menuView.getChildAt(i);
            if (itemView == null) continue;

            boolean isSelected = (itemView.getId() == selectedItemId);
            if (isSelected) {
                itemView.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .translationY(-3f)
                        .setDuration(220)
                        .setInterpolator(new OvershootInterpolator(1.5f))
                        .start();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    itemView.setTranslationZ(12f);
                }
            } else {
                itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationY(0f)
                        .setDuration(200)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    itemView.setTranslationZ(0f);
                }
            }
        }
    }

    private void setupNavigationDrawer() {
        if (navigationView != null) {
            navigationView.setNavigationItemSelectedListener(this);
            updateNavHeaderUI();
        }
    }

    private void updateNavHeaderUI() {
        if (navigationView == null) return;
        View headerView = navigationView.getHeaderView(0);
        if (headerView == null) return;

        TextView txtName = headerView.findViewById(R.id.txtHeaderUserName);
        TextView txtStatus = headerView.findViewById(R.id.txtHeaderUserStatus);
        TextView btnAction = headerView.findViewById(R.id.btnHeaderLoginAction);
        View container = headerView.findViewById(R.id.headerUserContainer);

        boolean loggedIn = PreferenceUtils.isLoggedIn(this);
        if (loggedIn) {
            if (txtName != null) txtName.setText(PreferenceUtils.getUserName(this));
            if (txtStatus != null) txtStatus.setText(PreferenceUtils.getSubscriptionExpiryFormatted(this));
            if (btnAction != null) btnAction.setText("ACCOUNT / LOGOUT");
        } else {
            if (txtName != null) txtName.setText("Guest User");
            if (txtStatus != null) txtStatus.setText("Tap to Login / Sign In");
            if (btnAction != null) btnAction.setText("LOGIN");
        }

        if (container != null) {
            container.setOnClickListener(v -> {
                drawerLayout.closeDrawer(GravityCompat.START);
                showLoginDialog();
            });
        }
    }

    private void showFloatingPlayerSettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_floating_player, null);
        androidx.appcompat.widget.SwitchCompat switchPip = view.findViewById(R.id.switchFloatingPlayer);
        TextView txtDetail = view.findViewById(R.id.txtPipStatusDetail);

        boolean currentSetting = PreferenceUtils.isFloatingPlayerEnabled(this);
        boolean hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);

        if (switchPip != null) {
            switchPip.setChecked(currentSetting && hasOverlayPermission);
            updatePipDetailText(txtDetail, currentSetting && hasOverlayPermission, hasOverlayPermission);

            switchPip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(HomeActivity.this);
                    if (!overlayGranted) {
                        buttonView.setChecked(false);
                        updatePipDetailText(txtDetail, false, false);
                        promptOverlayAndNotificationPermission();
                    } else {
                        PreferenceUtils.setFloatingPlayerEnabled(HomeActivity.this, true);
                        updatePipDetailText(txtDetail, true, true);
                        Toast.makeText(HomeActivity.this, "Floating Player Enabled", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    PreferenceUtils.setFloatingPlayerEnabled(HomeActivity.this, false);
                    boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(HomeActivity.this);
                    updatePipDetailText(txtDetail, false, overlayGranted);
                    Toast.makeText(HomeActivity.this, "Floating Player Disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Done", (dialog, which) -> {
                    boolean enabled = switchPip != null && switchPip.isChecked();
                    Toast.makeText(this, enabled ? "Floating Player Enabled" : "Floating Player Disabled", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasOverlayPermission) {
            builder.setNeutralButton("Grant Permission", (dialog, which) -> {
                promptOverlayAndNotificationPermission();
            });
        }

        builder.show();
    }

    private void promptOverlayAndNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Required")
                    .setMessage("Floating Player requires 'Display over other apps' permission so you can watch videos while using other apps. Please turn it ON in settings.")
                    .setPositiveButton("Open Settings", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            try {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                                startActivity(intent);
                            } catch (Exception ignored) {}
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    private void updatePipDetailText(TextView txtDetail, boolean enabled, boolean hasPermission) {
        if (txtDetail == null) return;
        if (!enabled) {
            txtDetail.setText("Status: Disabled • Floating player mode is turned off.");
        } else if (!hasPermission) {
            txtDetail.setText("Status: Overlay permission required! Tap 'Grant Permission' below to allow floating on all apps.");
        } else {
            txtDetail.setText("Status: Active • Custom floating window player will float on top of all apps.");
        }
    }

    private void showLowQualitySettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_low_quality, null);
        androidx.appcompat.widget.SwitchCompat switchQuality = view.findViewById(R.id.switchLowQuality);
        TextView txtDetail = view.findViewById(R.id.txtQualityStatusDetail);

        boolean currentSetting = PreferenceUtils.isLowQualityEnabled(this);
        if (switchQuality != null) {
            switchQuality.setChecked(currentSetting);
            if (txtDetail != null) {
                txtDetail.setText(currentSetting ? "Status: Low Quality (Data Saver) Active." : "Status: Standard High Quality Active.");
            }
            switchQuality.setOnCheckedChangeListener((buttonView, isChecked) -> {
                PreferenceUtils.setLowQualityEnabled(HomeActivity.this, isChecked);
                if (txtDetail != null) {
                    txtDetail.setText(isChecked ? "Status: Low Quality (Data Saver) Active." : "Status: Standard High Quality Active.");
                }
            });
        }

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Save", (dialog, which) -> {
                    boolean enabled = switchQuality != null && switchQuality.isChecked();
                    Toast.makeText(this, enabled ? "Low Quality Mode Enabled" : "Low Quality Mode Disabled", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showLoginDialog() {
        if (PreferenceUtils.isLoggedIn(this)) {
            String infoMsg = "👤 User Account: " + PreferenceUtils.getUserName(this) + "\n" +
                    "📧 Email: " + PreferenceUtils.getUserEmail(this) + "\n" +
                    "⭐ Plan: " + PreferenceUtils.getPlanName(this) + "\n" +
                    "📱 Device ID: " + PreferenceUtils.getDeviceId(this) + "\n" +
                    "🌐 Server URL: " + PreferenceUtils.getApiUrl(this) + "\n\n" +
                    "📅 Subscription Status:\n" + PreferenceUtils.getSubscriptionExpiryFormatted(this);

            new AlertDialog.Builder(this)
                    .setTitle("Account & Subscription Details")
                    .setMessage(infoMsg)
                    .setPositiveButton("Verify Status", (dialog, which) -> {
                        showLoadingSpinner("Verifying session with server...");
                        ServerApiManager.validateUserSession(this, new ServerApiManager.SessionCallback() {
                            @Override
                            public void onValid(String user, String subscriptionStatus, long expiryTimestamp, String expiryFormatted, String planName) {
                                hideLoadingSpinner();
                                updateNavHeaderUI();
                                Toast.makeText(HomeActivity.this, "✅ Server Verified: " + planName + " (" + subscriptionStatus + ")\nExpires: " + expiryFormatted, Toast.LENGTH_LONG).show();
                                syncChannelsFromServer();
                            }

                            @Override
                            public void onInvalid(String reason) {
                                hideLoadingSpinner();
                                updateNavHeaderUI();
                                ServerApiManager.purgeExpiredPremiumChannels(channelDao);
                                Toast.makeText(HomeActivity.this, "⚠️ Server Verification: " + reason, Toast.LENGTH_LONG).show();
                                syncChannelsFromServer();
                            }
                        });
                    })
                    .setNegativeButton("Logout Account", (dialog, which) -> {
                        showLoadingSpinner("Logging out from Server...");
                        ServerApiManager.logoutUser(this, () -> {
                            hideLoadingSpinner();
                            ServerApiManager.purgeExpiredPremiumChannels(channelDao);
                            updateNavHeaderUI();
                            Toast.makeText(HomeActivity.this, "Logged out successfully. Device unbound.", Toast.LENGTH_LONG).show();
                            syncChannelsFromServer();
                        });
                    })
                    .show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_login, null);
        EditText etUser = view.findViewById(R.id.etLoginUsername);
        EditText etPass = view.findViewById(R.id.etLoginPassword);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Login Account", (dialog, which) -> {
                    String username = etUser.getText().toString().trim();
                    String password = etPass.getText().toString().trim();

                    if (username.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this, "Error: Username and Password are required!", Toast.LENGTH_LONG).show();
                        return;
                    }

                    showLoadingSpinner("Authenticating with Server...");

                    ServerApiManager.loginUser(this, username, password, new ServerApiManager.AuthCallback() {
                        @Override
                        public void onSuccess(String user, String subscriptionStatus, long expiryTimestamp, String expiryFormatted, String planName) {
                            hideLoadingSpinner();
                            updateNavHeaderUI();
                            if ("ACTIVE".equalsIgnoreCase(subscriptionStatus)) {
                                Toast.makeText(HomeActivity.this, "🎉 Login Successful! Welcome " + user + ".\n" + planName + " Active till " + expiryFormatted, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(HomeActivity.this, "⚠️ Account authenticated, but subscription is EXPIRED (" + expiryFormatted + "). Premium content locked.", Toast.LENGTH_LONG).show();
                                ServerApiManager.purgeExpiredPremiumChannels(channelDao);
                            }
                            syncChannelsFromServer();
                            checkServerNotifications();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hideLoadingSpinner();
                            updateNavHeaderUI();
                            Toast.makeText(HomeActivity.this, "❌ Login Failed: " + errorMessage, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadChannels() {
        currentMainTab = "tv";
        showMainChannelsView();
        syncChannelsFromServer();
        checkAppUpdateFromApi();
    }

    private void syncChannelsFromServer() {
        showLoadingSpinner("Connecting & Syncing Server Data...");

        if (PreferenceUtils.isLoggedIn(this) && !PreferenceUtils.isSubscriptionValid(this)) {
            ServerApiManager.purgeExpiredPremiumChannels(channelDao);
        }

        // Sync categories route in parallel
        ServerApiManager.syncCategoriesFromServer(this, new ServerApiManager.SyncCallback() {
            @Override
            public void onSuccess(int count) {
                updateCategoriesFromChannels();
            }
            @Override
            public void onError(String errorMessage) {}
        });

        ServerApiManager.syncAllContentFromServer(this, channelDao, new ServerApiManager.SyncCallback() {
            @Override
            public void onSuccess(int channelsSyncedCount) {
                isInitialDataLoaded = true;
                hideLoadingSpinner();
                hideFullscreenStatusUI();
                if (channelDao != null) {
                    currentChannels = channelDao.getAllChannels();
                } else {
                    currentChannels = new ArrayList<>();
                }
                updateCategoriesFromChannels();
                if (channelsSyncedCount > 0) {
                    Toast.makeText(HomeActivity.this, "Synced " + channelsSyncedCount + " channels & movies from server!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(HomeActivity.this, "Content loaded.", Toast.LENGTH_SHORT).show();
                }
                filterChannels();
                updateResumeStreamUI();

                AppUpdateInfo updateInfo = ServerApiManager.getLatestAppUpdateInfo();
                if (updateInfo != null) {
                    activeAppUpdateInfo = updateInfo;
                    addAppUpdateNotification(updateInfo);
                }

                checkServerNotifications();
            }

            @Override
            public void onError(String errorMessage) {
                hideLoadingSpinner();
                isInitialDataLoaded = false;
                currentChannels = new ArrayList<>();
                if (channelAdapter != null) {
                    channelAdapter.updateList(new ArrayList<>());
                }
                PreferenceUtils.clearLastPlayedStream(HomeActivity.this);
                updateResumeStreamUI();

                String displayError = (errorMessage != null && !errorMessage.isEmpty()) ? errorMessage : "SERVER ERROR: Server response not received.";
                showFullscreenStatusUI(displayError);
            }
        });
    }

    private void checkServerNotifications() {
        ServerApiManager.syncNotificationsFromServer(this, new ServerApiManager.NotificationSyncCallback() {
            @Override
            public void onSuccess(List<NotificationModel> serverNotifs, int newCount) {
                if (serverNotifs != null && !serverNotifs.isEmpty()) {
                    for (NotificationModel sm : serverNotifs) {
                        boolean exists = false;
                        for (NotificationModel existing : notificationList) {
                            if (existing.getId() != null && existing.getId().equals(sm.getId())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            notificationList.add(0, sm);
                        }
                    }
                    if (notificationAdapter != null) {
                        notificationAdapter.updateList(notificationList);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void showFullscreenStatusUI(String errorMessage) {
        if (layoutFullscreenStatus == null) return;
        layoutFullscreenStatus.setVisibility(View.VISIBLE);

        if (errorMessage != null && errorMessage.contains(Config.MAINTENANCE_STATUS_CODE)) {
            String cleanMsg = errorMessage.replace(Config.MAINTENANCE_STATUS_CODE, "").trim();
            if (cleanMsg.isEmpty()) {
                cleanMsg = "Server is currently under maintenance. Please try again later.";
            }
            if (imgStatusIcon != null) {
                imgStatusIcon.setImageResource(R.drawable.ic_maintenance);
                imgStatusIcon.setColorFilter(Color.parseColor("#F59E0B"));
            }
            if (txtStatusBadge != null) {
                txtStatusBadge.setText("MAINTENANCE MODE");
                txtStatusBadge.setBackgroundResource(R.drawable.bg_chip_selected);
            }
            if (txtStatusTitle != null) {
                txtStatusTitle.setText("App Under Maintenance");
            }
            if (txtStatusMessage != null) {
                txtStatusMessage.setText("Notice: " + cleanMsg + "\n\nApplication access and data sync are temporarily disabled during maintenance.");
            }
            if (txtStatusNotice != null) {
                txtStatusNotice.setText("⚠️ App in Maintenance Mode. No server data loaded.");
            }
        } else if (errorMessage != null && errorMessage.contains("ROUTE_NOT_FOUND")) {
            if (imgStatusIcon != null) {
                imgStatusIcon.setImageResource(R.drawable.ic_report);
                imgStatusIcon.setColorFilter(Color.parseColor("#EF4444"));
            }
            if (txtStatusBadge != null) {
                txtStatusBadge.setText("404 ROUTE ERROR");
                txtStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
            if (txtStatusTitle != null) {
                txtStatusTitle.setText("Server Route Not Found (404)");
            }
            if (txtStatusMessage != null) {
                txtStatusMessage.setText("The server endpoint or route path is missing or invalid. Please check your Config.java API_URL settings.");
            }
            if (txtStatusNotice != null) {
                txtStatusNotice.setText("⚠️ Invalid Endpoint / Route Path Error");
            }
        } else if (errorMessage != null && errorMessage.contains("HANDSHAKE_FAILED")) {
            if (imgStatusIcon != null) {
                imgStatusIcon.setImageResource(R.drawable.ic_settings);
                imgStatusIcon.setColorFilter(Color.parseColor("#EF4444"));
            }
            if (txtStatusBadge != null) {
                txtStatusBadge.setText("HANDSHAKE FAILED");
                txtStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
            if (txtStatusTitle != null) {
                txtStatusTitle.setText("Security Handshake Failed");
            }
            if (txtStatusMessage != null) {
                txtStatusMessage.setText("Security handshake mismatch (401/403). Mismatched API key or HMAC signature between app and server.");
            }
            if (txtStatusNotice != null) {
                txtStatusNotice.setText("⚠️ Authentication / Security Key Mismatch");
            }
        } else if (errorMessage != null && errorMessage.contains("NO_NETWORK")) {
            if (imgStatusIcon != null) {
                imgStatusIcon.setImageResource(R.drawable.ic_no_network);
                imgStatusIcon.setColorFilter(Color.parseColor("#EF4444"));
            }
            if (txtStatusBadge != null) {
                txtStatusBadge.setText("NO INTERNET");
                txtStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
            if (txtStatusTitle != null) {
                txtStatusTitle.setText("No Network Connection");
            }
            if (txtStatusMessage != null) {
                txtStatusMessage.setText("Unable to establish network connection. Please check your internet connection and try again.");
            }
            if (txtStatusNotice != null) {
                txtStatusNotice.setText("⚠️ Internet Connection Required to Load App Data");
            }
        } else {
            if (imgStatusIcon != null) {
                imgStatusIcon.setImageResource(R.drawable.ic_report);
                imgStatusIcon.setColorFilter(Color.parseColor("#EF4444"));
            }
            if (txtStatusBadge != null) {
                txtStatusBadge.setText("SERVER ERROR");
                txtStatusBadge.setBackgroundResource(R.drawable.bg_chip_unselected);
            }
            if (txtStatusTitle != null) {
                txtStatusTitle.setText("Server Response Error");
            }
            if (txtStatusMessage != null) {
                txtStatusMessage.setText(errorMessage != null ? errorMessage : "An unexpected server error occurred.");
            }
            if (txtStatusNotice != null) {
                txtStatusNotice.setText("⚠️ Failed to load server data.");
            }
        }
    }

    private void hideFullscreenStatusUI() {
        if (layoutFullscreenStatus != null) {
            layoutFullscreenStatus.setVisibility(View.GONE);
        }
    }

    private void checkAppUpdateFromApi() {
        ServerApiManager.checkAppUpdate(this, new ServerApiManager.AppUpdateCallback() {
            @Override
            public void onUpdateInfoReceived(AppUpdateInfo updateInfo, boolean isUpdateAvailable) {
                if (updateInfo != null) {
                    activeAppUpdateInfo = updateInfo;
                    if (isUpdateAvailable) {
                        addAppUpdateNotification(updateInfo);
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Ignore silent update check failures
            }
        });
    }

    private void showAppUpdateUI(AppUpdateInfo updateInfo) {
        if (layoutAppUpdate == null || updateInfo == null) return;
        activeAppUpdateInfo = updateInfo;
        layoutAppUpdate.setVisibility(View.VISIBLE);

        if (txtUpdateBadge != null) {
            txtUpdateBadge.setText("VERSION " + updateInfo.getVersionName().toUpperCase() + " AVAILABLE");
        }
        if (txtUpdateTitle != null) {
            txtUpdateTitle.setText(updateInfo.getTitle());
        }
        if (txtUpdateMessage != null) {
            txtUpdateMessage.setText(updateInfo.getMessage());
        }
        if (txtUpdateReleaseNotes != null && !updateInfo.getReleaseNotes().isEmpty()) {
            txtUpdateReleaseNotes.setText(updateInfo.getReleaseNotes());
        }

        int installedCode = 1;
        try {
            android.content.pm.PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                installedCode = (int) pInfo.getLongVersionCode();
            } else {
                installedCode = pInfo.versionCode;
            }
        } catch (Exception ignored) {}

        boolean forceRequired = updateInfo.isForceUpdateRequired(installedCode);
        if (txtUpdateForceNotice != null) {
            txtUpdateForceNotice.setVisibility(forceRequired ? View.VISIBLE : View.GONE);
        }
        if (btnUpdateLater != null) {
            btnUpdateLater.setVisibility(forceRequired ? View.GONE : View.VISIBLE);
        }
    }

    private void hideAppUpdateUI() {
        if (layoutAppUpdate != null) {
            layoutAppUpdate.setVisibility(View.GONE);
        }
    }

    private void downloadAndInstallApk(String apkUrl) {
        if (apkUrl == null || apkUrl.trim().isEmpty()) {
            apkUrl = "https://verify-app.alwaysdata.net/new/mobile/app-release.apk";
        }

        final String finalUrl = apkUrl;

        // Custom Progress Dialog for APK download
        final android.app.Dialog progressDialog = new android.app.Dialog(this);
        progressDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        progressDialog.setCancelable(false);

        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(60, 50, 60, 50);
        dialogLayout.setBackgroundColor(Color.parseColor("#1E1E2E"));

        TextView txtTitle = new TextView(this);
        txtTitle.setText("Downloading App Update...");
        txtTitle.setTextSize(18);
        txtTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        txtTitle.setTextColor(Color.WHITE);
        txtTitle.setPadding(0, 0, 0, 24);

        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView txtProgress = new TextView(this);
        txtProgress.setText("Starting download 0% (0 MB / 0 MB)...");
        txtProgress.setTextColor(Color.parseColor("#9CA3AF"));
        txtProgress.setTextSize(13);
        txtProgress.setPadding(0, 16, 0, 0);

        dialogLayout.addView(txtTitle);
        dialogLayout.addView(progressBar);
        dialogLayout.addView(txtProgress);

        progressDialog.setContentView(dialogLayout);
        if (progressDialog.getWindow() != null) {
            progressDialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        progressDialog.show();

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(finalUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int fileLength = conn.getContentLength();

                java.io.File downloadsDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && !downloadsDir.exists()) {
                    downloadsDir.mkdirs();
                }

                java.io.File outputFile = new java.io.File(downloadsDir, "update.apk");
                if (outputFile.exists()) {
                    outputFile.delete();
                }

                java.io.InputStream input = conn.getInputStream();
                java.io.FileOutputStream output = new java.io.FileOutputStream(outputFile);

                byte[] data = new byte[8192];
                int count;
                long total = 0;
                long lastProgressUpdate = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    long now = System.currentTimeMillis();
                    if (now - lastProgressUpdate > 100 || total == fileLength) {
                        lastProgressUpdate = now;
                        final long currentTotal = total;
                        final int percent = (fileLength > 0) ? (int) ((currentTotal * 100) / fileLength) : 0;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            progressBar.setProgress(percent);
                            float mbDownloaded = currentTotal / (1024f * 1024f);
                            if (fileLength > 0) {
                                float mbTotal = fileLength / (1024f * 1024f);
                                txtProgress.setText(String.format(Locale.US, "Downloading... %d%% (%.2f MB / %.2f MB)", percent, mbDownloaded, mbTotal));
                            } else {
                                txtProgress.setText(String.format(Locale.US, "Downloaded %.2f MB", mbDownloaded));
                            }
                        });
                    }
                }

                output.flush();
                output.close();
                input.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(HomeActivity.this, "Download complete! Opening installer...", Toast.LENGTH_SHORT).show();
                    installDownloadedApk(outputFile);
                });

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.dismiss();
                    }
                    Toast.makeText(HomeActivity.this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installDownloadedApk(java.io.File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(this, "APK file not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        getPackageName() + ".provider",
                        apkFile
                );
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!getPackageManager().canRequestPackageInstalls()) {
                    Toast.makeText(this, "Please allow permission to install updates", Toast.LENGTH_LONG).show();
                    Intent permIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permIntent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(permIntent);
                    return;
                }
            }

            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not launch package installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void addAppUpdateNotification(AppUpdateInfo updateInfo) {
        if (updateInfo == null) return;
        String notifId = "update_" + updateInfo.getVersionCode();
        for (NotificationModel n : notificationList) {
            if (notifId.equals(n.getId())) {
                return;
            }
        }
        String time = updateInfo.getUpdateTime();
        if (time == null || time.isEmpty()) time = "Just Now";

        NotificationModel updateNotif = new NotificationModel(
                notifId,
                "🚀 " + updateInfo.getTitle(),
                updateInfo.getMessage() + "\n\n(Tap here to view update details & download v" + updateInfo.getVersionName() + ")",
                time,
                R.drawable.ic_reload,
                false
        );
        notificationList.add(0, updateNotif);
        if (notificationAdapter != null) {
            notificationAdapter.notifyDataSetChanged();
        }
    }

    private void filterChannels() {
        List<ChannelEntity> filtered = new ArrayList<>();
        String query = etSearchQuery.getText() != null ? etSearchQuery.getText().toString().trim().toLowerCase() : "";

        if (activeSelectedPlaylist != null) {
            for (ChannelEntity channel : activePlaylistChannels) {
                if (channel == null) continue;
                String cat = channel.getCategory() != null ? channel.getCategory() : "";
                String subCat = channel.getSubCategory() != null ? channel.getSubCategory() : "";
                String title = channel.getTitle() != null ? channel.getTitle() : "";

                boolean matchesCategory = currentFilterCategory.equalsIgnoreCase("All")
                        || subCat.equalsIgnoreCase(currentFilterCategory)
                        || cat.equalsIgnoreCase(currentFilterCategory);
                boolean matchesSearch = query.isEmpty() || title.toLowerCase().contains(query) || subCat.toLowerCase().contains(query);
                boolean matchesFavorites = !isShowingFavoritesOnly || channel.isFavorite();

                if (matchesCategory && matchesSearch && matchesFavorites) {
                    filtered.add(channel);
                }
            }
        } else {
            List<ChannelEntity> allChannels = channelDao != null ? channelDao.getAllChannels() : null;
            if (allChannels != null) {
                for (ChannelEntity channel : allChannels) {
                    if (channel == null) continue;
                    String cat = channel.getCategory() != null ? channel.getCategory() : "";
                    String subCat = channel.getSubCategory() != null ? channel.getSubCategory() : "";
                    String title = channel.getTitle() != null ? channel.getTitle() : "";

                    boolean matchesTab = false;
                    if ("movie".equalsIgnoreCase(currentMainTab)) {
                        matchesTab = isMovieChannel(channel);
                    } else if ("tv".equalsIgnoreCase(currentMainTab)) {
                        matchesTab = !isMovieChannel(channel);
                    } else {
                        matchesTab = true;
                    }

                    boolean matchesCategory = currentFilterCategory.equalsIgnoreCase("All")
                            || subCat.equalsIgnoreCase(currentFilterCategory)
                            || cat.equalsIgnoreCase(currentFilterCategory);
                    boolean matchesSearch = query.isEmpty() || title.toLowerCase().contains(query) || subCat.toLowerCase().contains(query);
                    boolean matchesFavorites = !isShowingFavoritesOnly || channel.isFavorite();

                    if (matchesTab && matchesCategory && matchesSearch && matchesFavorites) {
                        filtered.add(channel);
                    }
                }
            }
        }

        currentChannels = filtered;
        if (filtered.isEmpty()) {
            if (channelDao != null && channelDao.getChannelCount() == 0) {
                showEmptyState(R.drawable.ic_tv, "No Channels Available", "No stream channels loaded from server yet. Tap below to sync channels from server.", "Sync From Server", this::syncChannelsFromServer);
            } else if (!currentFilterCategory.equalsIgnoreCase("All")) {
                showEmptyState(R.drawable.ic_playlist, "Category Is Empty", "No channels found in '" + currentFilterCategory + "' category.", "Clear Category Filter", () -> {
                    currentFilterCategory = "All";
                    etSearchQuery.setText("");
                    filterChannels();
                });
            } else if (!query.isEmpty()) {
                showEmptyState(R.drawable.ic_search, "No Search Results", "No channels matched '" + query + "'.", "Clear Search", () -> {
                    etSearchQuery.setText("");
                    filterChannels();
                });
            } else if (isShowingFavoritesOnly) {
                showEmptyState(R.drawable.ic_favorite_border, "No Favorites Yet", "You haven't added any channels to favorites.", "Explore All Channels", () -> {
                    isShowingFavoritesOnly = false;
                    btnFavorites.setColorFilter(getColor(R.color.text_primary));
                    filterChannels();
                });
            } else {
                showEmptyState(R.drawable.ic_tv, "No Channels Found", "No stream channels available for this category or view.", "Sync Server Data", this::syncChannelsFromServer);
            }
        } else {
            hideEmptyState();
            if (rvChannels.getVisibility() != View.VISIBLE && !currentMainTab.equals("playlist") && !currentMainTab.equals("notifications")) {
                rvChannels.setVisibility(View.VISIBLE);
            }
            if (channelAdapter != null) {
                channelAdapter.setMovieSection("movie".equalsIgnoreCase(currentMainTab));
                channelAdapter.updateList(filtered);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shimmerFrameLayout != null && layoutShimmer != null && layoutShimmer.getVisibility() == View.VISIBLE) {
            shimmerFrameLayout.startShimmer();
        }
        updateNavHeaderUI();
        updateResumeStreamUI();
        if (PreferenceUtils.isLoggedIn(this) && !PreferenceUtils.isSubscriptionValid(this)) {
            ServerApiManager.purgeExpiredPremiumChannels(channelDao);
        }
        if (channelDao != null) {
            currentChannels = channelDao.getAllChannels();
            playlistList = generatePlaylists();
            if (playlistAdapter != null) {
                playlistAdapter.updateList(playlistList);
            }
        }
        if ("favorites".equalsIgnoreCase(currentMainTab)) {
            showFavoritesScreen();
        } else if (activeSelectedPlaylist != null) {
            openPlaylistDetails(activeSelectedPlaylist);
        }
        checkServerNotifications();

        // Start real-time sync polling
        performSilentRealTimeSync();
        realTimeSyncHandler.removeCallbacks(realTimeSyncRunnable);
        realTimeSyncHandler.postDelayed(realTimeSyncRunnable, 15000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (shimmerFrameLayout != null) {
            shimmerFrameLayout.stopShimmer();
        }
        realTimeSyncHandler.removeCallbacks(realTimeSyncRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realTimeSyncHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        drawerLayout.closeDrawer(GravityCompat.START);
        int id = item.getItemId();

        if (id == R.id.nav_floating_player) {
            showFloatingPlayerSettingsDialog();
        } else if (id == R.id.nav_login) {
            showLoginDialog();
        } else if (id == R.id.nav_low_quality) {
            showLowQualitySettingsDialog();
        } else if (id == R.id.nav_copyright) {
            showCopyrightDialog();
        } else if (id == R.id.nav_dev) {
            Intent intent = new Intent(this, SupportActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_telegram) {
            openDirectExternalUrl(PreferenceUtils.getTelegramUrl(this));
        } else if (id == R.id.nav_whatsapp) {
            openDirectExternalUrl(PreferenceUtils.getWhatsAppUrl(this));
        } else if (id == R.id.nav_exit) {
            showExitConfirmationDialog();
        }
        return true;
    }

    private void openDirectExternalUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Link not available", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showAddStreamDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_stream, null);
        EditText etName = view.findViewById(R.id.etStreamName);
        EditText etUrl = view.findViewById(R.id.etStreamUrl);

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Add & Play", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String url = etUrl.getText().toString().trim();
                    if (!name.isEmpty() && !url.isEmpty()) {
                        ChannelEntity entity = new ChannelEntity(
                                name,
                                url,
                                "",
                                (currentMainTab != null && !currentMainTab.isEmpty()) ? currentMainTab : "tv",
                                "Custom Stream",
                                false,
                                url.contains(".mpd") ? "dash" : (url.contains(".ts") ? "ts" : "hls"),
                                "Full HD"
                        );
                        if (channelDao != null) {
                            channelDao.insert(entity);
                        }
                        loadChannels();
                        playlistList = generatePlaylists();
                        if (playlistAdapter != null) {
                            playlistAdapter.updateList(playlistList);
                        }
                        Toast.makeText(this, "Playing Network Stream...", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(HomeActivity.this, PlayerActivity.class);
                        intent.putExtra("stream_url", entity.getStreamUrl());
                        intent.putExtra("stream_title", entity.getTitle());
                        intent.putExtra("stream_category", entity.getCategory());
                        intent.putExtra("stream_type", entity.getStreamType());
                        intent.putExtra("logo_url", entity.getLogoUrl());
                        intent.putExtra("is_favorite", entity.isFavorite());
                        startActivity(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCopyrightDialog() {
        String verName = BuildConfig.VERSION_NAME;
        int verCode = BuildConfig.VERSION_CODE;
        new AlertDialog.Builder(this)
                .setTitle("About & Legal (v" + verName + ")")
                .setMessage("Live TV & Movies Player v" + verName + " (Build " + verCode + ")\n\n" +
                        "Live TV Player does not host or store any video media streams on its servers. All stream links provided are freely available on the open web and public IPTV channels under Fair Use guidelines.")
                .setPositiveButton("I Understand", null)
                .show();
    }

    private void showSettingsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null);
        com.google.android.material.switchmaterial.SwitchMaterial switchFloating = view.findViewById(R.id.switchSettingsFloatingPlayer);
        if (switchFloating != null) {
            boolean hasOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
            boolean currentEnabled = PreferenceUtils.isFloatingPlayerEnabled(this) && hasOverlay;
            switchFloating.setChecked(currentEnabled);

            switchFloating.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(HomeActivity.this);
                    if (!overlayGranted) {
                        buttonView.setChecked(false);
                        promptOverlayAndNotificationPermission();
                    } else {
                        PreferenceUtils.setFloatingPlayerEnabled(HomeActivity.this, true);
                        Toast.makeText(HomeActivity.this, "Floating Player Enabled", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    PreferenceUtils.setFloatingPlayerEnabled(HomeActivity.this, false);
                    Toast.makeText(HomeActivity.this, "Floating Player Disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        new AlertDialog.Builder(this)
                .setView(view)
                .setPositiveButton("Save Settings", (dialog, which) -> {
                    Toast.makeText(this, "Player settings updated", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Server API Settings", (dialog, which) -> showServerApiConfigDialog())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showServerApiConfigDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 20);

        TextView lblInfo = new TextView(this);
        lblInfo.setText("Configure Secure Server API details (URL, API Key, HMAC Secret Key, AES Encryption Key):");
        lblInfo.setTextColor(0xFFE2E8F0);
        lblInfo.setTextSize(13);
        layout.addView(lblInfo);

        final EditText etUrl = new EditText(this);
        etUrl.setHint("API Endpoint URL");
        etUrl.setText(PreferenceUtils.getApiUrl(this));
        etUrl.setTextColor(0xFFFFFFFF);
        etUrl.setHintTextColor(0xFF94A3B8);
        layout.addView(etUrl);

        final EditText etApiKey = new EditText(this);
        etApiKey.setHint("API Key");
        etApiKey.setText(PreferenceUtils.getApiKey(this));
        etApiKey.setTextColor(0xFFFFFFFF);
        etApiKey.setHintTextColor(0xFF94A3B8);
        layout.addView(etApiKey);

        final EditText etHmacKey = new EditText(this);
        etHmacKey.setHint("HMAC Secret Key");
        etHmacKey.setText(PreferenceUtils.getHmacKey(this));
        etHmacKey.setTextColor(0xFFFFFFFF);
        etHmacKey.setHintTextColor(0xFF94A3B8);
        layout.addView(etHmacKey);

        final EditText etEncKey = new EditText(this);
        etEncKey.setHint("AES Encryption Key");
        etEncKey.setText(PreferenceUtils.getEncryptionKey(this));
        etEncKey.setTextColor(0xFFFFFFFF);
        etEncKey.setHintTextColor(0xFF94A3B8);
        layout.addView(etEncKey);

        new AlertDialog.Builder(this)
                .setTitle("Secure Server API Settings")
                .setView(layout)
                .setPositiveButton("Save & Sync", (dialog, which) -> {
                    String url = etUrl.getText().toString();
                    String apiKey = etApiKey.getText().toString();
                    String hmacKey = etHmacKey.getText().toString();
                    String encKey = etEncKey.getText().toString();

                    PreferenceUtils.setServerApiConfig(this, url, apiKey, hmacKey, encKey);
                    Toast.makeText(this, "API Credentials Saved! Syncing...", Toast.LENGTH_SHORT).show();
                    syncChannelsFromServer();
                })
                .setNeutralButton("Reset Defaults", (dialog, which) -> {
                    PreferenceUtils.setServerApiConfig(this, PreferenceUtils.DEFAULT_API_URL, PreferenceUtils.DEFAULT_API_KEY, PreferenceUtils.DEFAULT_HMAC_KEY, PreferenceUtils.DEFAULT_ENCRYPTION_KEY);
                    Toast.makeText(this, "Reset to Default Server Credentials", Toast.LENGTH_SHORT).show();
                    syncChannelsFromServer();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Application?")
                .setMessage("Are you sure you want to exit Live TV Player?")
                .setPositiveButton("Exit", (dialog, which) -> finishAffinity())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManagePlaylistsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_manage_playlists, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = view.findViewById(R.id.etPlaylistName);
        EditText etUrl = view.findViewById(R.id.etPlaylistUrl);
        android.widget.Button btnSample = view.findViewById(R.id.btnLoadSampleM3u);
        android.widget.Button btnAdd = view.findViewById(R.id.btnAddM3uPlaylist);
        ProgressBar progress = view.findViewById(R.id.progressM3uLoading);
        RecyclerView rvManage = view.findViewById(R.id.rvManagePlaylists);
        ImageView btnClose = view.findViewById(R.id.btnClosePlaylistDialog);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSample.setOnClickListener(v -> {
            etName.setText("IPTV Org Live TV");
            etUrl.setText("https://iptv-org.github.io/iptv/index.m3u");
        });

        List<PlaylistModel> customPlaylists = PreferenceUtils.getCustomM3uPlaylists(this);
        final ManagePlaylistAdapter[] manageAdapterHolder = new ManagePlaylistAdapter[1];
        manageAdapterHolder[0] = new ManagePlaylistAdapter(this, customPlaylists, (playlist, position) -> {
            if (channelDao != null && playlist.getTitle() != null) {
                channelDao.deleteBySubCategory(playlist.getTitle());
            }
            PreferenceUtils.removeCustomM3uPlaylist(this, playlist.getTitle());
            List<PlaylistModel> updated = PreferenceUtils.getCustomM3uPlaylists(this);
            if (manageAdapterHolder[0] != null) {
                manageAdapterHolder[0].updateList(updated);
            }
            playlistList = generatePlaylists();
            if (playlistAdapter != null) {
                playlistAdapter.updateList(playlistList);
            }
            Toast.makeText(this, "Playlist " + playlist.getTitle() + " deleted", Toast.LENGTH_SHORT).show();
        });

        ManagePlaylistAdapter manageAdapter = manageAdapterHolder[0];

        rvManage.setLayoutManager(new LinearLayoutManager(this));
        rvManage.setAdapter(manageAdapter);

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
                            "",
                            channels.size()
                    );

                    PreferenceUtils.addCustomM3uPlaylist(HomeActivity.this, newPlaylist);
                    List<PlaylistModel> updatedList = PreferenceUtils.getCustomM3uPlaylists(HomeActivity.this);
                    manageAdapter.updateList(updatedList);

                    playlistList = generatePlaylists();
                    if (playlistAdapter != null) {
                        playlistAdapter.updateList(playlistList);
                    }

                    etName.setText("");
                    etUrl.setText("");
                    Toast.makeText(HomeActivity.this, "Loaded " + channels.size() + " channels into playlist!", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onError(String errorMessage) {
                    progress.setVisibility(View.GONE);
                    btnAdd.setEnabled(true);
                    Toast.makeText(HomeActivity.this, "M3U Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if ("favorites".equals(currentMainTab) || "notifications".equals(currentMainTab)) {
            if (previousActivePlaylist != null) {
                openPlaylistDetails(previousActivePlaylist);
                if (bottomNavigationView != null) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_category).setChecked(true);
                    animateBottomNavItemSelection(R.id.nav_category);
                }
            } else if ("movie".equalsIgnoreCase(previousMainTab) || previousNavId == R.id.nav_movies) {
                showMoviesView();
                if (bottomNavigationView != null) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_movies).setChecked(true);
                    animateBottomNavItemSelection(R.id.nav_movies);
                }
            } else if ("playlist".equalsIgnoreCase(previousMainTab) || previousNavId == R.id.nav_category) {
                showPlaylistsView();
                if (bottomNavigationView != null) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_category).setChecked(true);
                    animateBottomNavItemSelection(R.id.nav_category);
                }
            } else {
                showMainChannelsView();
                if (bottomNavigationView != null) {
                    bottomNavigationView.getMenu().findItem(R.id.nav_live_tv).setChecked(true);
                    animateBottomNavItemSelection(R.id.nav_live_tv);
                }
            }
        } else if (activeSelectedPlaylist != null) {
            showPlaylistsView();
            if (bottomNavigationView != null) {
                bottomNavigationView.getMenu().findItem(R.id.nav_category).setChecked(true);
                animateBottomNavItemSelection(R.id.nav_category);
            }
        } else {
            showExitDialog();
        }
    }

    private final Handler realTimeSyncHandler = new Handler(Looper.getMainLooper());
    private final Runnable realTimeSyncRunnable = new Runnable() {
        @Override
        public void run() {
            performSilentRealTimeSync();
            realTimeSyncHandler.postDelayed(this, 15000);
        }
    };

    private void performSilentRealTimeSync() {
        if (!isInitialDataLoaded) return;
        ServerApiManager.syncAllContentFromServer(this, channelDao, new ServerApiManager.SyncCallback() {
            @Override
            public void onSuccess(int channelsSyncedCount) {
                runOnUiThread(() -> {
                    if (channelDao != null) {
                        currentChannels = channelDao.getAllChannels();
                        playlistList = generatePlaylists();
                        if (playlistAdapter != null) {
                            playlistAdapter.updateList(playlistList);
                        }
                        updateCategoriesFromChannels();
                        filterChannels();
                        updateResumeStreamUI();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                // Keep running silently without blocking UI
            }
        });
    }

    private boolean isStreamPremium(String cat, String title) {
        if (cat == null) cat = "";
        if (title == null) title = "";
        String lower = (cat + " " + title).toLowerCase();
        return lower.contains("vip") || lower.contains("premium") || lower.contains("paid") || lower.contains("subscription");
    }

    private void updateResumeStreamUI() {
        if (cardResumeStream != null) {
            cardResumeStream.setVisibility(View.GONE);
        }
    }

    private void showExitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit the application?")
                .setPositiveButton("Yes, Exit", (dialog, which) -> finish())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}
