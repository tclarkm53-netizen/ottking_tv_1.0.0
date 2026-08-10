package com.example;

import com.example.utils.PlayerUtils;
import com.example.utils.PreferenceUtils;
import androidx.media3.exoplayer.source.MediaSource;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.TrafficStats;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Rational;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import com.bumptech.glide.Glide;
import com.example.adapter.ChannelAdapter;
import com.example.database.AppDatabase;
import com.example.database.ChannelDao;
import com.example.database.ChannelEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private View playerControlRoot;
    private View playerContainer;
    private View layoutStreamDetails;
    private View playerTopBar, playerBottomBar, centerControlsLayout;
    private ImageView btnPlayPause, btnRewind, btnForward, btnAspectRatio, btnPiP, btnFullscreen, btnPlayerBack, btnQuality;
    private ImageView imgCurrentLogo, btnFavToggle, btnShare, btnReportLink;
    private TextView txtPlayerChannelTitle, txtCurrentTitle, txtCurrentCategory, txtPosition, txtDuration, txtQualityBadge, txtNetworkSpeed, txtLiveIndicator;
    private int selectedQualityIndex = 0;

    private static class QualityOption {
        String name;
        int width;
        int height;
        TrackGroup trackGroup;
        int trackIndex;

        QualityOption(String name, int width, int height, TrackGroup trackGroup, int trackIndex) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.trackGroup = trackGroup;
            this.trackIndex = trackIndex;
        }
    }
    private long lastRxBytes = 0;
    private long lastTimeStamp = 0;
    private ProgressBar progressBarBuffer;
    private SeekBar seekBarPlayer;
    private RecyclerView rvPlayerChannels;

    private View cardGestureOverlay;
    private ImageView imgGestureIcon;
    private TextView txtGestureTitle;
    private ProgressBar progressGesture;
    private final Handler hideGestureOverlayHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideGestureOverlayRunnable = new Runnable() {
        @Override
        public void run() {
            if (cardGestureOverlay != null) {
                cardGestureOverlay.setVisibility(View.GONE);
            }
        }
    };

    private ChannelDao channelDao;
    private ChannelAdapter channelAdapter;
    private List<ChannelEntity> otherChannels = new ArrayList<>();

    private String streamUrl;
    private String streamTitle;
    private String streamCategory;
    private String streamType;
    private String logoUrl;
    private int channelId = -1;
    private boolean isFavorite = false;
    private String playlistFilter;
    private String playlistTitle;

    private int currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Handler retryHandler = new Handler(Looper.getMainLooper());
    private Handler controlsHideHandler = new Handler(Looper.getMainLooper());
    private static final long CONTROLS_TIMEOUT_MS = 4000;
    private boolean areControlsVisible = true;
    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    private void animateShowControls() {
        if (playerControlRoot == null) return;
        playerControlRoot.animate().cancel();
        if (playerTopBar != null) playerTopBar.animate().cancel();
        if (centerControlsLayout != null) centerControlsLayout.animate().cancel();
        if (playerBottomBar != null) playerBottomBar.animate().cancel();

        playerControlRoot.setVisibility(View.VISIBLE);
        playerControlRoot.setAlpha(0f);
        playerControlRoot.animate()
                .alpha(1f)
                .setDuration(300)
                .setListener(null);

        if (playerTopBar != null) {
            playerTopBar.setTranslationY(-40f);
            playerTopBar.setAlpha(0f);
            playerTopBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null);
        }

        if (centerControlsLayout != null) {
            centerControlsLayout.setScaleX(0.75f);
            centerControlsLayout.setScaleY(0.75f);
            centerControlsLayout.setAlpha(0f);
            centerControlsLayout.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null);
        }

        if (playerBottomBar != null) {
            playerBottomBar.setTranslationY(40f);
            playerBottomBar.setAlpha(0f);
            playerBottomBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null);
        }
    }

    private void animateHideControls() {
        if (playerControlRoot == null || playerControlRoot.getVisibility() != View.VISIBLE) return;

        if (playerTopBar != null) {
            playerTopBar.animate()
                    .translationY(-40f)
                    .alpha(0f)
                    .setDuration(250)
                    .setListener(null);
        }

        if (centerControlsLayout != null) {
            centerControlsLayout.animate()
                    .scaleX(0.75f)
                    .scaleY(0.75f)
                    .alpha(0f)
                    .setDuration(250)
                    .setListener(null);
        }

        if (playerBottomBar != null) {
            playerBottomBar.animate()
                    .translationY(40f)
                    .alpha(0f)
                    .setDuration(250)
                    .setListener(null);
        }

        playerControlRoot.animate()
                .alpha(0f)
                .setDuration(280)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        playerControlRoot.setVisibility(View.GONE);
                        if (playerTopBar != null) {
                            playerTopBar.setTranslationY(0f);
                            playerTopBar.setAlpha(1f);
                        }
                        if (centerControlsLayout != null) {
                            centerControlsLayout.setScaleX(1f);
                            centerControlsLayout.setScaleY(1f);
                            centerControlsLayout.setAlpha(1f);
                        }
                        if (playerBottomBar != null) {
                            playerBottomBar.setTranslationY(0f);
                            playerBottomBar.setAlpha(1f);
                        }
                    }
                });
    }

    private void showControls() {
        if (playerView != null) {
            playerView.showController();
        }
    }

    private void hideControls() {
        if (playerView != null) {
            playerView.hideController();
        }
    }

    private void toggleControlsVisibility() {
        if (playerView != null) {
            if (playerView.isControllerFullyVisible()) {
                playerView.hideController();
            } else {
                playerView.showController();
            }
        }
    }

    private void resetControlsTimeout() {
        if (playerView != null && playerView.isControllerFullyVisible()) {
            playerView.showController();
        }
    }
    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 10;

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateNetworkSpeed();
            if (player != null) {
                long currentPos = player.getCurrentPosition();
                long totalDuration = player.getDuration();
                boolean isLive = player.isCurrentMediaItemLive() || player.isCurrentMediaItemDynamic() || totalDuration <= 0;

                if (isLive) {
                    if (txtLiveIndicator != null) txtLiveIndicator.setVisibility(View.VISIBLE);
                    if (seekBarPlayer != null) seekBarPlayer.setVisibility(View.GONE);
                    if (txtPosition != null) txtPosition.setVisibility(View.GONE);
                    if (txtDuration != null) txtDuration.setVisibility(View.GONE);
                    if (btnRewind != null) btnRewind.setVisibility(View.GONE);
                    if (btnForward != null) btnForward.setVisibility(View.GONE);
                } else {
                    if (txtLiveIndicator != null) txtLiveIndicator.setVisibility(View.GONE);
                    if (seekBarPlayer != null) seekBarPlayer.setVisibility(View.VISIBLE);
                    if (txtPosition != null) txtPosition.setVisibility(View.VISIBLE);
                    if (txtDuration != null) txtDuration.setVisibility(View.VISIBLE);
                    if (btnRewind != null) btnRewind.setVisibility(View.VISIBLE);
                    if (btnForward != null) btnForward.setVisibility(View.VISIBLE);

                    if (totalDuration > 0) {
                        int progress = (int) ((currentPos * 100) / totalDuration);
                        if (seekBarPlayer != null) seekBarPlayer.setProgress(progress);
                        if (txtPosition != null) txtPosition.setText(formatTime(currentPos));
                        if (txtDuration != null) txtDuration.setText(formatTime(totalDuration));
                    }
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        }

        setContentView(R.layout.activity_player);

        channelDao = AppDatabase.getInstance(this).channelDao();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        parseIntent();
        initViews();
        setupCustomControls();
        setupGestureControls();
        setupPlayerChannelsList();
        initializePlayer();
    }

    private void parseIntent() {
        Intent intent = getIntent();
        channelId = intent.getIntExtra("channel_id", -1);
        streamUrl = intent.getStringExtra("stream_url");
        streamTitle = intent.getStringExtra("stream_title");
        streamCategory = intent.getStringExtra("stream_category");
        streamType = intent.getStringExtra("stream_type");
        logoUrl = intent.getStringExtra("logo_url");
        isFavorite = intent.getBooleanExtra("is_favorite", false);
        playlistFilter = intent.getStringExtra("playlist_filter");
        playlistTitle = intent.getStringExtra("playlist_title");

        if (streamUrl == null || streamUrl.isEmpty()) {
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
            streamTitle = "Default Live Stream";
        }
        if (streamTitle == null) streamTitle = "Live TV Channel";
        if (streamType == null) streamType = "hls";
    }

    private void initViews() {
        playerView = findViewById(R.id.playerView);
        playerControlRoot = findViewById(R.id.playerControlRoot);
        playerContainer = findViewById(R.id.playerContainer);
        layoutStreamDetails = findViewById(R.id.layoutStreamDetails);

        playerTopBar = findViewById(R.id.playerTopBar);
        playerBottomBar = findViewById(R.id.playerBottomBar);
        centerControlsLayout = findViewById(R.id.centerControlsLayout);

        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnAspectRatio = findViewById(R.id.btnAspectRatio);
        btnQuality = findViewById(R.id.btnQuality);
        btnPiP = findViewById(R.id.btnPiP);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);

        imgCurrentLogo = findViewById(R.id.imgCurrentLogo);
        btnFavToggle = findViewById(R.id.btnFavToggle);
        btnShare = findViewById(R.id.btnShare);
        btnReportLink = findViewById(R.id.btnReportLink);

        txtPlayerChannelTitle = findViewById(R.id.txtPlayerChannelTitle);
        txtCurrentTitle = findViewById(R.id.txtCurrentTitle);
        txtCurrentCategory = findViewById(R.id.txtCurrentCategory);
        txtPosition = findViewById(R.id.txtPosition);
        txtDuration = findViewById(R.id.txtDuration);
        txtQualityBadge = findViewById(R.id.txtQualityBadge);
        txtNetworkSpeed = findViewById(R.id.txtNetworkSpeed);
        txtLiveIndicator = findViewById(R.id.txtLiveIndicator);
        progressBarBuffer = findViewById(R.id.progressBarBuffer);
        seekBarPlayer = findViewById(R.id.seekBarPlayer);
        rvPlayerChannels = findViewById(R.id.rvPlayerChannels);

        txtPlayerChannelTitle.setText(streamTitle != null ? streamTitle : "Live TV Channel");
        txtCurrentTitle.setText(streamTitle != null ? streamTitle : "Live TV Channel");

        String catStr = (streamCategory != null && !streamCategory.isEmpty()) ? streamCategory.toUpperCase() : "LIVE TV";
        String typeStr = (streamType != null && !streamType.isEmpty()) ? streamType.toUpperCase() : "HLS";
        txtCurrentCategory.setText(catStr + " • " + typeStr);
        txtQualityBadge.setText(typeStr);

        if (isFavorite) {
            btnFavToggle.setImageResource(R.drawable.ic_favorite);
        } else {
            btnFavToggle.setImageResource(R.drawable.ic_favorite_border);
        }

        Glide.with(this)
                .load(logoUrl)
                .placeholder(R.drawable.img_app_logo)
                .error(R.drawable.img_app_logo)
                .into(imgCurrentLogo);

        applyOrientationLayout(getResources().getConfiguration().orientation);

        boolean isFloatingEnabled = PreferenceUtils.isFloatingPlayerEnabled(this);
        if (btnPiP != null) {
            btnPiP.setVisibility(isFloatingEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void setupCustomControls() {
        if (seekBarPlayer != null) {
            seekBarPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        resetControlsTimeout();
                        if (player != null && player.getDuration() > 0) {
                            long newPosition = (player.getDuration() * progress) / 100;
                            if (txtPosition != null) txtPosition.setText(formatTime(newPosition));
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    if (playerView != null) {
                        playerView.setControllerShowTimeoutMs(0);
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    if (player != null && player.getDuration() > 0) {
                        long newPosition = (player.getDuration() * seekBar.getProgress()) / 100;
                        player.seekTo(newPosition);
                    }
                    if (playerView != null) {
                        playerView.setControllerShowTimeoutMs(4000);
                        playerView.showController();
                    }
                }
            });
        }

        if (playerControlRoot != null) {
            playerControlRoot.setOnClickListener(v -> {
                hideControls();
            });
        }

        showControls();

        btnPlayerBack.setOnClickListener(v -> {
            resetControlsTimeout();
            handleBackPress();
        });

        btnPlayPause.setOnClickListener(v -> {
            resetControlsTimeout();
            if (player != null) {
                if (player.getPlayerError() != null || player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED) {
                    retryCount = 0;
                    retryHandler.removeCallbacksAndMessages(null);
                    retryStreamConnection();
                } else if (player.isPlaying()) {
                    player.pause();
                    btnPlayPause.setImageResource(R.drawable.ic_play);
                } else {
                    player.play();
                    btnPlayPause.setImageResource(R.drawable.ic_pause);
                }
            }
        });

        btnRewind.setOnClickListener(v -> {
            resetControlsTimeout();
            if (player != null) {
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
            }
        });

        btnForward.setOnClickListener(v -> {
            resetControlsTimeout();
            if (player != null) {
                player.seekTo(player.getCurrentPosition() + 10000);
            }
        });

        btnAspectRatio.setOnClickListener(v -> {
            resetControlsTimeout();
            if (currentAspectRatioMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                Toast.makeText(this, "Aspect Ratio: Zoom / Crop", Toast.LENGTH_SHORT).show();
            } else if (currentAspectRatioMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                Toast.makeText(this, "Aspect Ratio: Fill Screen", Toast.LENGTH_SHORT).show();
            } else {
                currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                Toast.makeText(this, "Aspect Ratio: Fit", Toast.LENGTH_SHORT).show();
            }
            playerView.setResizeMode(currentAspectRatioMode);
        });

        if (btnQuality != null) {
            btnQuality.setOnClickListener(v -> {
                resetControlsTimeout();
                showQualitySelectionDialog();
            });
        }
        if (txtQualityBadge != null) {
            txtQualityBadge.setOnClickListener(v -> {
                resetControlsTimeout();
                showQualitySelectionDialog();
            });
        }

        btnPiP.setOnClickListener(v -> {
            boolean supportsSystemPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
            if (supportsSystemPip) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        PictureInPictureParams params = new PictureInPictureParams.Builder().build();
                        enterPictureInPictureMode(params);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        enterPictureInPictureMode();
                    }
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            enterCustomFloatingPlayer();
        });

        btnFullscreen.setOnClickListener(v -> toggleFullscreen());

        btnFavToggle.setOnClickListener(v -> {
            isFavorite = !isFavorite;
            if (channelDao != null) {
                List<ChannelEntity> allChannels = channelDao.getAllChannels();
                if (allChannels != null) {
                    for (ChannelEntity c : allChannels) {
                        if ((channelId != -1 && c.getId() == channelId) ||
                                (streamUrl != null && !streamUrl.isEmpty() && streamUrl.equals(c.getStreamUrl())) ||
                                (streamTitle != null && !streamTitle.isEmpty() && streamTitle.equalsIgnoreCase(c.getTitle()))) {
                            c.setFavorite(isFavorite);
                            channelDao.update(c);
                            channelId = c.getId();
                            break;
                        }
                    }
                }
            }
            btnFavToggle.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            Toast.makeText(this, isFavorite ? "Saved to Favorites" : "Removed from Favorites", Toast.LENGTH_SHORT).show();
            if ("favorite".equalsIgnoreCase(playlistFilter)) {
                setupPlayerChannelsList();
            }
        });

        btnShare.setOnClickListener(v -> {
            try {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, streamTitle != null ? streamTitle : "Live Stream");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Watch live stream: " + (streamTitle != null ? streamTitle : "Live Stream") + "\n" + (streamUrl != null ? streamUrl : ""));
                startActivity(Intent.createChooser(shareIntent, "Share Stream via"));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to share stream", Toast.LENGTH_SHORT).show();
            }
        });

        btnReportLink.setOnClickListener(v -> {
            Intent intent = new Intent(PlayerActivity.this, SupportActivity.class);
            startActivity(intent);
        });
    }

    private void setupGestureControls() {
        if (playerView == null) return;

        cardGestureOverlay = findViewById(R.id.cardGestureOverlay);
        imgGestureIcon = findViewById(R.id.imgGestureIcon);
        txtGestureTitle = findViewById(R.id.txtGestureTitle);
        progressGesture = findViewById(R.id.progressGesture);

        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        playerView.setOnTouchListener(new View.OnTouchListener() {
            private float startX = 0f;
            private float startY = 0f;
            private boolean isGestureActive = false;
            private boolean isLeftArea = false;
            private float initialBrightness = 0.5f;
            private int initialVolume = 0;
            private int maxVolume = 1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int viewWidth = v.getWidth();
                int viewHeight = v.getHeight();
                if (viewWidth <= 0 || viewHeight <= 0) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        isGestureActive = false;
                        isLeftArea = startX < (viewWidth / 2f);

                        // Current screen brightness
                        WindowManager.LayoutParams lp = getWindow().getAttributes();
                        if (lp.screenBrightness < 0) {
                            try {
                                int sysBrightness = Settings.System.getInt(
                                        getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                                initialBrightness = sysBrightness / 255f;
                            } catch (Exception e) {
                                initialBrightness = 0.5f;
                            }
                        } else {
                            initialBrightness = lp.screenBrightness;
                        }

                        // Current volume
                        if (audioManager != null) {
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getX() - startX;
                        float deltaY = startY - event.getY(); // Upward swipe increases value

                        if (!isGestureActive) {
                            if (Math.abs(deltaY) > 20 && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                                isGestureActive = true;
                            }
                        }

                        if (isGestureActive) {
                            hideGestureOverlayHandler.removeCallbacks(hideGestureOverlayRunnable);

                            if (isLeftArea) {
                                // Left side -> Brightness
                                float deltaBrightness = deltaY / (viewHeight * 0.75f);
                                float newBrightness = Math.max(0.01f, Math.min(1.0f, initialBrightness + deltaBrightness));

                                WindowManager.LayoutParams windowLp = getWindow().getAttributes();
                                windowLp.screenBrightness = newBrightness;
                                getWindow().setAttributes(windowLp);

                                int percent = Math.round(newBrightness * 100f);
                                showGestureOverlay(R.drawable.ic_brightness, "Brightness: " + percent + "%", percent);
                            } else {
                                // Right side -> Volume
                                if (audioManager != null && maxVolume > 0) {
                                    float deltaVolume = (deltaY / (viewHeight * 0.75f)) * maxVolume;
                                    int newVolume = Math.max(0, Math.min(maxVolume, Math.round(initialVolume + deltaVolume)));

                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);

                                    int percent = Math.round((newVolume * 100f) / maxVolume);
                                    showGestureOverlay(R.drawable.ic_volume, "Volume: " + percent + "%", percent);
                                }
                            }
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isGestureActive) {
                            hideGestureOverlayHandler.postDelayed(hideGestureOverlayRunnable, 1200);
                            return true;
                        } else {
                            float upDeltaX = Math.abs(event.getX() - startX);
                            float upDeltaY = Math.abs(event.getY() - startY);
                            if (upDeltaX < 25 && upDeltaY < 25) {
                                toggleControlsVisibility();
                                return true;
                            }
                        }
                        break;
                }
                return false;
            }
        });
    }

    private void showGestureOverlay(int iconRes, String titleText, int progressPercent) {
        if (cardGestureOverlay != null) {
            if (imgGestureIcon != null) imgGestureIcon.setImageResource(iconRes);
            if (txtGestureTitle != null) txtGestureTitle.setText(titleText);
            if (progressGesture != null) progressGesture.setProgress(progressPercent);
            cardGestureOverlay.setVisibility(View.VISIBLE);
        }
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

    private void setupPlayerChannelsList() {
        List<ChannelEntity> allChannels = null;
        if (channelDao != null) {
            allChannels = channelDao.getAllChannels();
        }
        if (allChannels == null) {
            allChannels = new ArrayList<>();
        }

        boolean isCurrentPlayingMovie = false;
        if (streamCategory != null) {
            String cat = streamCategory.toLowerCase();
            if (cat.contains("movie") || cat.contains("vod") || cat.contains("cinema") || cat.contains("film")) isCurrentPlayingMovie = true;
        }
        if (playlistFilter != null && (playlistFilter.equalsIgnoreCase("movie") || playlistFilter.equalsIgnoreCase("movies"))) {
            isCurrentPlayingMovie = true;
        }

        List<ChannelEntity> filteredList = new ArrayList<>();
        for (ChannelEntity c : allChannels) {
            if (c == null) continue;

            boolean cIsMovie = isMovieChannel(c);
            if (!isCurrentPlayingMovie && cIsMovie) {
                continue;
            }
            if (isCurrentPlayingMovie && !cIsMovie) {
                continue;
            }

            if (playlistFilter != null && !playlistFilter.isEmpty()) {
                String cat = c.getCategory() != null ? c.getCategory() : "";
                String subCat = c.getSubCategory() != null ? c.getSubCategory() : "";
                String type = c.getStreamType() != null ? c.getStreamType() : "";

                if (playlistFilter.equalsIgnoreCase("favorite")) {
                    if (c.isFavorite()) filteredList.add(c);
                } else if (playlistFilter.equalsIgnoreCase("all")) {
                    filteredList.add(c);
                } else if (cat.equalsIgnoreCase(playlistFilter) || subCat.equalsIgnoreCase(playlistFilter) || type.equalsIgnoreCase(playlistFilter)
                        || (playlistTitle != null && (cat.equalsIgnoreCase(playlistTitle) || subCat.equalsIgnoreCase(playlistTitle)))) {
                    filteredList.add(c);
                }
            } else {
                filteredList.add(c);
            }
        }
        otherChannels = filteredList;

        channelAdapter = new ChannelAdapter(this, otherChannels, true, null);
        if (playlistFilter != null) {
            channelAdapter.setPlaylistInfo(playlistFilter, playlistTitle);
        }
        rvPlayerChannels.setLayoutManager(new GridLayoutManager(this, 3));
        rvPlayerChannels.setAdapter(channelAdapter);
    }

    public void playChannel(ChannelEntity channel) {
        if (channel == null) return;
        boolean isMovie = isMovieChannel(channel);
        if (playlistFilter != null && (playlistFilter.equalsIgnoreCase("movie") || playlistFilter.equalsIgnoreCase("movies"))) {
            isMovie = true;
        }

        if (isMovie) {
            Intent intent = new Intent(this, LandscapeActivity.class);
            intent.putExtra("channel_id", channel.getId());
            intent.putExtra("stream_url", channel.getStreamUrl());
            intent.putExtra("stream_title", channel.getTitle());
            intent.putExtra("stream_category", channel.getCategory());
            intent.putExtra("stream_type", channel.getStreamType());
            intent.putExtra("logo_url", channel.getLogoUrl());
            intent.putExtra("is_favorite", channel.isFavorite());
            startActivity(intent);
            finish();
            return;
        }

        boolean isPremium = isPremiumStream(channel.getTitle(), channel.getCategory(), channel.getSubCategory());
        if (isPremium && !PreferenceUtils.isSubscriptionValid(this)) {
            Toast.makeText(this, "🔒 VIP Subscription Required: Please login with an active VIP account to watch this channel.", Toast.LENGTH_LONG).show();
            return;
        }

        retryHandler.removeCallbacksAndMessages(null);
        retryCount = 0;
        this.channelId = channel.getId();
        this.streamUrl = channel.getStreamUrl();
        this.streamTitle = channel.getTitle();
        this.streamCategory = channel.getCategory();
        this.streamType = channel.getStreamType();
        this.logoUrl = channel.getLogoUrl();
        this.isFavorite = channel.isFavorite();

        if (txtPlayerChannelTitle != null) txtPlayerChannelTitle.setText(streamTitle != null ? streamTitle : "Live TV Channel");
        if (txtCurrentTitle != null) txtCurrentTitle.setText(streamTitle != null ? streamTitle : "Live TV Channel");

        String catStr = (streamCategory != null && !streamCategory.isEmpty()) ? streamCategory.toUpperCase() : "LIVE TV";
        String typeStr = (streamType != null && !streamType.isEmpty()) ? streamType.toUpperCase() : "HLS";
        if (txtCurrentCategory != null) txtCurrentCategory.setText(catStr + " • " + typeStr);
        if (txtQualityBadge != null) txtQualityBadge.setText(typeStr);

        if (btnFavToggle != null) {
            btnFavToggle.setImageResource(isFavorite ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        }

        if (imgCurrentLogo != null) {
            Glide.with(this)
                    .load(logoUrl)
                    .placeholder(R.drawable.img_app_logo)
                    .error(R.drawable.img_app_logo)
                    .into(imgCurrentLogo);
        }

        if (player != null && streamUrl != null && !streamUrl.isEmpty()) {
            player.stop();
            MediaSource mediaSource = PlayerUtils.createMediaSource(this, streamUrl, typeStr);
            player.setMediaSource(mediaSource);
            player.prepare();
            player.setPlayWhenReady(true);
            Toast.makeText(this, "Playing: " + streamTitle, Toast.LENGTH_SHORT).show();
        }
    }

    private void retryStreamConnection() {
        if (isFinishing() || isDestroyed()) return;
        if (player == null || streamUrl == null || streamUrl.isEmpty()) return;

        retryCount++;
        if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.VISIBLE);
        Toast.makeText(PlayerActivity.this, "Network fluctuation. Reconnecting... (" + retryCount + "/" + MAX_RETRY_COUNT + ")", Toast.LENGTH_SHORT).show();

        try {
            long currentPos = player.getCurrentPosition();
            player.stop();

            String typeStr = (streamType != null && !streamType.isEmpty()) ? streamType.toUpperCase() : "HLS";
            MediaSource mediaSource = PlayerUtils.createMediaSource(this, streamUrl, typeStr);
            player.setMediaSource(mediaSource);
            if (currentPos > 0) {
                player.seekTo(currentPos);
            }
            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isPremiumStream(String title, String category, String subCat) {
        if (title != null && (title.toUpperCase().contains("[VIP]") || title.toUpperCase().contains("[PREMIUM]"))) return true;
        if (subCat != null && (subCat.toUpperCase().contains("VIP") || subCat.toUpperCase().contains("PREMIUM"))) return true;
        if (category != null && (category.toUpperCase().contains("VIP") || category.toUpperCase().contains("PREMIUM"))) return true;
        return false;
    }

    private void initializePlayer() {
        if (player == null) {
            try {
                player = PlayerUtils.createExoPlayer(this);
                if (mediaSession != null) {
                    try {
                        mediaSession.release();
                    } catch (Exception ignored) {}
                }
                mediaSession = new MediaSession.Builder(this, player).build();
                playerView.setPlayer(player);
                playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
                    if (visibility == View.VISIBLE) {
                        animateShowControls();
                    } else {
                        animateHideControls();
                    }
                });

                if (streamUrl != null && !streamUrl.isEmpty()) {
                    if (isPremiumStream(streamTitle, streamCategory, null) && !PreferenceUtils.isSubscriptionValid(this)) {
                        Toast.makeText(this, "🔒 VIP Subscription Required: Please login with an active VIP account.", Toast.LENGTH_LONG).show();
                    } else {
                        MediaSource mediaSource = PlayerUtils.createMediaSource(this, streamUrl, streamType);
                        player.setMediaSource(mediaSource);
                        player.prepare();
                        player.setPlayWhenReady(true);
                    }
                }

                player.addListener(new Player.Listener() {
                    @Override
                    public void onTracksChanged(@NonNull Tracks tracks) {
                        updateQualityBadgeFromTracks(tracks);
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                    }

                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.VISIBLE);
                            if (btnPlayPause != null) btnPlayPause.setVisibility(View.GONE);
                        } else if (playbackState == Player.STATE_READY) {
                            retryCount = 0;
                            retryHandler.removeCallbacksAndMessages(null);
                            if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.GONE);
                            if (btnPlayPause != null) {
                                btnPlayPause.setVisibility(View.VISIBLE);
                                btnPlayPause.setImageResource(R.drawable.ic_pause);
                            }
                        } else if (playbackState == Player.STATE_ENDED) {
                            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
                        }
                    }

                    @Override
                    public void onPlayerError(@NonNull PlaybackException error) {
                        if (retryCount < MAX_RETRY_COUNT) {
                            if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.VISIBLE);
                            long delayMs = Math.min(8000, 2000L * (retryCount + 1));
                            retryHandler.removeCallbacksAndMessages(null);
                            retryHandler.postDelayed(() -> retryStreamConnection(), delayMs);
                        } else {
                            if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.GONE);
                            if (btnPlayPause != null) {
                                btnPlayPause.setVisibility(View.VISIBLE);
                                btnPlayPause.setImageResource(R.drawable.ic_play);
                            }
                            Toast.makeText(PlayerActivity.this, "Connection lost. Tap Play to reconnect.", Toast.LENGTH_LONG).show();
                        }
                    }
                });

                progressHandler.post(updateProgressRunnable);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to initialize video player", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btnPiP != null) {
            btnPiP.setVisibility(PreferenceUtils.isFloatingPlayerEnabled(this) ? View.VISIBLE : View.GONE);
        }
    }

    private void handleBackPress() {
        if (PreferenceUtils.isFloatingPlayerEnabled(this) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            if (streamUrl != null && !streamUrl.isEmpty()) {
                enterCustomFloatingPlayer();
                return;
            }
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void enterCustomFloatingPlayer() {
        if (!PreferenceUtils.isFloatingPlayerEnabled(this)) {
            Toast.makeText(this, "Floating Player is disabled in Settings.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Overlay Permission Required")
                    .setMessage("Custom Floating Player needs permission to display over other apps so you can keep watching TV while using other applications.")
                    .setPositiveButton("Allow Permission", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        long currentPos = 0;
        if (player != null) {
            try {
                if (!player.isCurrentMediaItemLive() && player.getDuration() > 0) {
                    currentPos = player.getCurrentPosition();
                }
                player.stop();
                player.clearMediaItems();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }

        Intent serviceIntent = new Intent(this, FloatingPlayerService.class);
        serviceIntent.setAction(FloatingPlayerService.ACTION_START_FLOATING);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_CHANNEL_ID, String.valueOf(channelId));
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_URL, streamUrl);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TITLE, streamTitle);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_CATEGORY, streamCategory);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TYPE, streamType);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_LOGO_URL, logoUrl);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_SEEK_POSITION, currentPos);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            finish();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Could not start floating player: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (PreferenceUtils.isFloatingPlayerEnabled(this)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                enterCustomFloatingPlayer();
            } else {
                Toast.makeText(this, "Enable 'Display over other apps' permission for floating player", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateNetworkSpeed() {
        if (txtNetworkSpeed == null) return;
        long currentRxBytes = TrafficStats.getUidRxBytes(getApplicationInfo().uid);
        if (currentRxBytes == TrafficStats.UNSUPPORTED) {
            currentRxBytes = TrafficStats.getTotalRxBytes();
        }
        long currentTime = System.currentTimeMillis();

        if (lastTimeStamp != 0 && lastRxBytes != 0) {
            long timeDelta = currentTime - lastTimeStamp;
            if (timeDelta > 0) {
                long bytesDelta = currentRxBytes - lastRxBytes;
                if (bytesDelta < 0) bytesDelta = 0;
                double speedBytesPerSec = (bytesDelta * 1000.0) / timeDelta;
                String formattedSpeed;
                if (speedBytesPerSec < 1024 * 1024) {
                    formattedSpeed = String.format(Locale.US, "%.1f KB/s", speedBytesPerSec / 1024.0);
                } else {
                    formattedSpeed = String.format(Locale.US, "%.2f MB/s", speedBytesPerSec / (1024.0 * 1024.0));
                }
                txtNetworkSpeed.setText(formattedSpeed);
                txtNetworkSpeed.setVisibility(View.VISIBLE);
            }
        }
        lastRxBytes = currentRxBytes;
        lastTimeStamp = currentTime;
    }

    private void toggleFullscreen() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationLayout(newConfig.orientation);
    }

    private void hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(true);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideSystemUi();
        }
    }

    private void applyOrientationLayout(int orientation) {
        if (playerContainer == null || layoutStreamDetails == null) return;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideSystemUi();
            layoutStreamDetails.setVisibility(View.GONE);
            ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            playerContainer.setLayoutParams(params);
        } else {
            showSystemUi();
            layoutStreamDetails.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams params = playerContainer.getLayoutParams();
            params.height = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 240, getResources().getDisplayMetrics());
            playerContainer.setLayoutParams(params);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        try {
            if (isInPictureInPictureMode) {
                if (playerControlRoot != null) playerControlRoot.setVisibility(View.GONE);
                View channelsView = findViewById(R.id.rvPlayerChannels);
                if (channelsView != null) channelsView.setVisibility(View.GONE);
                if (layoutStreamDetails != null) layoutStreamDetails.setVisibility(View.GONE);
            } else {
                if (playerControlRoot != null) playerControlRoot.setVisibility(View.VISIBLE);
                View channelsView = findViewById(R.id.rvPlayerChannels);
                if (channelsView != null) channelsView.setVisibility(View.VISIBLE);
                if (layoutStreamDetails != null) layoutStreamDetails.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatTime(long millis) {
        long seconds = (millis / 1000) % 60;
        long minutes = (millis / (1000 * 60)) % 60;
        long hours = millis / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void updateQualityBadgeFromTracks(Tracks tracks) {
        if (selectedQualityIndex != 0) return;
        if (tracks == null || txtQualityBadge == null) return;

        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) {
                        Format format = group.getTrackFormat(i);
                        int height = format.height;
                        if (height > 0) {
                            txtQualityBadge.setText("AUTO (" + height + "P)");
                        } else {
                            txtQualityBadge.setText("AUTO");
                        }
                        return;
                    }
                }
            }
        }
        txtQualityBadge.setText("AUTO");
    }

    private void showQualitySelectionDialog() {
        if (player == null) {
            Toast.makeText(this, "Player is not initialized", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<QualityOption> options = new ArrayList<>();
        options.add(new QualityOption("Auto (Adaptive Bitrate)", -1, -1, null, -1));

        Tracks currentTracks = player.getCurrentTracks();
        boolean foundHlsTracks = false;

        if (currentTracks != null) {
            for (Tracks.Group group : currentTracks.getGroups()) {
                if (group.getType() == C.TRACK_TYPE_VIDEO) {
                    TrackGroup mediaTrackGroup = group.getMediaTrackGroup();
                    for (int i = 0; i < group.length; i++) {
                        Format format = group.getTrackFormat(i);
                        int height = format.height;
                        int width = format.width;
                        int bitrate = format.bitrate;

                        if (height > 0 || bitrate > 0) {
                            foundHlsTracks = true;
                            String label;
                            if (height >= 1080) {
                                label = height + "p Full HD";
                            } else if (height >= 720) {
                                label = height + "p HD";
                            } else if (height > 0) {
                                label = height + "p SD";
                            } else {
                                label = (bitrate / 1000) + " kbps";
                            }

                            if (bitrate > 0) {
                                double mbps = bitrate / 1000000.0;
                                if (mbps >= 1.0) {
                                    label += String.format(Locale.US, " (%.1f Mbps)", mbps);
                                } else {
                                    label += String.format(Locale.US, " (%d Kbps)", bitrate / 1000);
                                }
                            }

                            options.add(new QualityOption(label, width, height, mediaTrackGroup, i));
                        }
                    }
                }
            }
        }

        if (!foundHlsTracks) {
            options.add(new QualityOption("1080p Full HD (Force 1080p)", 1920, 1080, null, -1));
            options.add(new QualityOption("720p HD (Force 720p)", 1280, 720, null, -1));
            options.add(new QualityOption("480p Standard (Force 480p)", 854, 480, null, -1));
            options.add(new QualityOption("360p Low (Force 360p)", 640, 360, null, -1));
        }

        String[] itemTitles = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            itemTitles[i] = options.get(i).name;
        }

        if (selectedQualityIndex >= options.size()) {
            selectedQualityIndex = 0;
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Stream Quality")
                .setSingleChoiceItems(itemTitles, selectedQualityIndex, (dialog, which) -> {
                    selectedQualityIndex = which;
                    QualityOption selected = options.get(which);
                    applyVideoQuality(selected);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyVideoQuality(QualityOption option) {
        if (player == null) return;

        try {
            if (option.trackGroup != null && option.trackIndex >= 0) {
                TrackSelectionOverride override = new TrackSelectionOverride(option.trackGroup, option.trackIndex);
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters()
                                .buildUpon()
                                .setOverrideForType(override)
                                .build()
                );
            } else if (option.width > 0 && option.height > 0) {
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters()
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .setMaxVideoSize(option.width, option.height)
                                .build()
                );
            } else {
                player.setTrackSelectionParameters(
                        player.getTrackSelectionParameters()
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
                                .build()
                );
            }

            String badgeText;
            if (option.height > 0) {
                badgeText = option.height + "P";
            } else if (option.name.contains("Auto")) {
                badgeText = "AUTO";
            } else {
                badgeText = "HD";
            }

            if (txtQualityBadge != null) {
                txtQualityBadge.setText(badgeText);
            }

            Toast.makeText(this, "Quality set to: " + option.name, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to change video quality", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null && !isInPictureInPictureMode()) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        controlsHideHandler.removeCallbacksAndMessages(null);
        retryHandler.removeCallbacksAndMessages(null);
        progressHandler.removeCallbacks(updateProgressRunnable);
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
