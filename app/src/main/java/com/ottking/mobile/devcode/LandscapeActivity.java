package com.ottking.mobile.devcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.ottking.mobile.devcode.utils.PlayerUtils;
import com.ottking.mobile.devcode.utils.PreferenceUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LandscapeActivity extends AppCompatActivity {

    private PlayerView playerViewLandscape;
    private View playerControlRoot;
    private View playerTopBar, playerBottomBar, centerControlsLayout;
    private TextView txtLiveIndicator, txtPosition, txtDuration, txtPlayerChannelTitle;
    private TextView txtNetworkSpeed, txtQualityBadge;
    private SeekBar seekBarPlayer;
    private ImageView btnPlayPause, btnRewind, btnForward, btnAspectRatio, btnPiP, btnFullscreen, btnPlayerBack, btnQuality;
    private ProgressBar progressBarBuffer;
    private ExoPlayer player;
    private MediaSession mediaSession;

    private String streamUrl;
    private String streamType;
    private String streamTitle;
    private String streamCategory;
    private int selectedQualityIndex = 0;
    private int currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;

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

    private long lastRxBytes = 0;
    private long lastTimeStamp = 0;
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 5;
    private boolean isUserSeeking = false;
    private boolean wasPlayingBeforePause = true;
    private BroadcastReceiver screenStateReceiver = null;

    private final Handler controlsHideHandler = new Handler(Looper.getMainLooper());
    private static final long CONTROLS_TIMEOUT_MS = 4000;
    private boolean areControlsVisible = true;

    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };

    private void animateShowControls() {
        showControls();
    }

    private void animateHideControls() {
        hideControls();
    }

    private void showControls() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        if (playerControlRoot == null) return;

        areControlsVisible = true;
        playerControlRoot.animate().cancel();
        if (playerTopBar != null) playerTopBar.animate().cancel();
        if (centerControlsLayout != null) centerControlsLayout.animate().cancel();
        if (playerBottomBar != null) playerBottomBar.animate().cancel();

        playerControlRoot.setVisibility(View.VISIBLE);
        playerControlRoot.setAlpha(0f);
        playerControlRoot.animate()
                .alpha(1f)
                .setDuration(220)
                .setListener(null);

        if (playerTopBar != null) {
            playerTopBar.setTranslationY(-30f);
            playerTopBar.setAlpha(0f);
            playerTopBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setListener(null);
        }

        if (centerControlsLayout != null) {
            centerControlsLayout.setScaleX(0.85f);
            centerControlsLayout.setScaleY(0.85f);
            centerControlsLayout.setAlpha(0f);
            centerControlsLayout.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(220)
                    .setListener(null);
        }

        if (playerBottomBar != null) {
            playerBottomBar.setTranslationY(30f);
            playerBottomBar.setAlpha(0f);
            playerBottomBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(220)
                    .setListener(null);
        }

        if (playerViewLandscape != null) {
            playerViewLandscape.showController();
        }

        controlsHideHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS);
    }

    private void hideControls() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        if (playerControlRoot == null || !areControlsVisible) return;

        areControlsVisible = false;
        playerControlRoot.animate().cancel();
        if (playerTopBar != null) playerTopBar.animate().cancel();
        if (centerControlsLayout != null) centerControlsLayout.animate().cancel();
        if (playerBottomBar != null) playerBottomBar.animate().cancel();

        if (playerTopBar != null) {
            playerTopBar.animate()
                    .translationY(-30f)
                    .alpha(0f)
                    .setDuration(200)
                    .setListener(null);
        }

        if (centerControlsLayout != null) {
            centerControlsLayout.animate()
                    .scaleX(0.85f)
                    .scaleY(0.85f)
                    .alpha(0f)
                    .setDuration(200)
                    .setListener(null);
        }

        if (playerBottomBar != null) {
            playerBottomBar.animate()
                    .translationY(30f)
                    .alpha(0f)
                    .setDuration(200)
                    .setListener(null);
        }

        playerControlRoot.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!areControlsVisible && playerControlRoot != null) {
                            playerControlRoot.setVisibility(View.GONE);
                        }
                    }
                });
    }

    private void toggleControlsVisibility() {
        if (areControlsVisible) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void resetControlsTimeout() {
        controlsHideHandler.removeCallbacks(hideControlsRunnable);
        if (areControlsVisible) {
            controlsHideHandler.postDelayed(hideControlsRunnable, CONTROLS_TIMEOUT_MS);
        }
    }

    private final Runnable updateProgressRunnable = new Runnable() {
        @Override
        public void run() {
            updateNetworkSpeed();
            if (player != null && !isUserSeeking) {
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
                        int progress = (int) ((currentPos * 1000) / totalDuration);
                        if (seekBarPlayer != null) seekBarPlayer.setProgress(progress);
                        if (txtPosition != null) txtPosition.setText(formatTime(currentPos));
                        if (txtDuration != null) txtDuration.setText(formatTime(totalDuration));
                    }
                }
            }
            progressHandler.postDelayed(this, 1000);
        }
    };

    private void updateNetworkSpeed() {
        if (txtNetworkSpeed == null) return;
        long currentRxBytes = TrafficStats.getUidRxBytes(getApplicationInfo().uid);
        if (currentRxBytes == TrafficStats.UNSUPPORTED || currentRxBytes < 0) {
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

    private String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long seconds = (ms / 1000) % 60;
        long minutes = (ms / (1000 * 60)) % 60;
        long hours = ms / (1000 * 60 * 60);
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void retryStreamConnection() {
        if (isFinishing() || isDestroyed()) return;
        if (player == null || streamUrl == null || streamUrl.isEmpty()) return;

        retryCount++;
        if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Reconnecting stream... (" + retryCount + "/" + MAX_RETRY_COUNT + ")", Toast.LENGTH_SHORT).show();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (Exception ignored) {}

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_landscape);

        hideSystemUi();

        Intent intent = getIntent();
        streamUrl = intent.getStringExtra("stream_url");
        streamType = intent.getStringExtra("stream_type");
        streamTitle = intent.getStringExtra("stream_title");
        streamCategory = intent.getStringExtra("stream_category");

        if (streamUrl == null || streamUrl.isEmpty()) {
            if (PreferenceUtils.hasLastPlayedStream(this)) {
                streamUrl = PreferenceUtils.getLastPlayedStreamUrl(this);
                streamTitle = PreferenceUtils.getLastPlayedTitle(this);
                streamCategory = PreferenceUtils.getLastPlayedCategory(this);
                streamType = PreferenceUtils.getLastPlayedType(this);
            } else {
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8";
                streamTitle = "Blockbuster Movie Stream";
            }
        }
        if (streamTitle == null) streamTitle = "Movie Player";
        if (streamType == null) streamType = "hls";

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        initViews();
        setupCustomControls();
        setupGestureControls();
        initializePlayer();
        progressHandler.post(updateProgressRunnable);
    }

    private void initViews() {
        playerViewLandscape = findViewById(R.id.playerViewLandscape);
        playerControlRoot = findViewById(R.id.playerControlRoot);
        playerTopBar = findViewById(R.id.playerTopBar);
        playerBottomBar = findViewById(R.id.playerBottomBar);
        centerControlsLayout = findViewById(R.id.centerControlsLayout);

        txtLiveIndicator = findViewById(R.id.txtLiveIndicator);
        txtPosition = findViewById(R.id.txtPosition);
        txtDuration = findViewById(R.id.txtDuration);
        txtNetworkSpeed = findViewById(R.id.txtNetworkSpeed);
        txtQualityBadge = findViewById(R.id.txtQualityBadge);
        txtPlayerChannelTitle = findViewById(R.id.txtPlayerChannelTitle);

        seekBarPlayer = findViewById(R.id.seekBarPlayer);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnRewind = findViewById(R.id.btnRewind);
        btnForward = findViewById(R.id.btnForward);
        btnAspectRatio = findViewById(R.id.btnAspectRatio);
        btnPiP = findViewById(R.id.btnPiP);
        btnFullscreen = findViewById(R.id.btnFullscreen);
        btnPlayerBack = findViewById(R.id.btnPlayerBack);
        btnQuality = findViewById(R.id.btnQuality);
        progressBarBuffer = findViewById(R.id.progressBarBuffer);

        cardGestureOverlay = findViewById(R.id.cardGestureOverlay);
        imgGestureIcon = findViewById(R.id.imgGestureIcon);
        txtGestureTitle = findViewById(R.id.txtGestureTitle);
        progressGesture = findViewById(R.id.progressGesture);

        if (txtPlayerChannelTitle != null && streamTitle != null && !streamTitle.isEmpty()) {
            txtPlayerChannelTitle.setText(streamTitle);
        }

        String typeStr = (streamType != null && !streamType.isEmpty()) ? streamType.toUpperCase() : "HD";
        if (txtQualityBadge != null) {
            txtQualityBadge.setText(typeStr);
        }
        if (btnFullscreen != null) {
            btnFullscreen.setVisibility(View.GONE);
        }

        boolean isFloatingEnabled = PreferenceUtils.isFloatingPlayerEnabled(this);
        if (btnPiP != null) {
            btnPiP.setVisibility(isFloatingEnabled ? View.VISIBLE : View.GONE);
        }
    }

    private void setupCustomControls() {
        if (seekBarPlayer != null) {
            seekBarPlayer.setMax(1000);
            seekBarPlayer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        resetControlsTimeout();
                        if (player != null && player.getDuration() > 0) {
                            long newPosition = (player.getDuration() * progress) / 1000;
                            if (txtPosition != null) txtPosition.setText(formatTime(newPosition));
                        }
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = true;
                    if (playerViewLandscape != null) {
                        playerViewLandscape.setControllerShowTimeoutMs(0);
                    }
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    isUserSeeking = false;
                    if (player != null && player.getDuration() > 0) {
                        long newPosition = (player.getDuration() * seekBar.getProgress()) / 1000;
                        player.seekTo(newPosition);
                    }
                    if (playerViewLandscape != null) {
                        playerViewLandscape.setControllerShowTimeoutMs(4000);
                        playerViewLandscape.showController();
                    }
                }
            });
        }

        if (btnPlayerBack != null) {
            btnPlayerBack.setOnClickListener(v -> {
                resetControlsTimeout();
                handleBackPress();
            });
        }

        if (btnPlayPause != null) {
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
        }

        if (btnRewind != null) {
            btnRewind.setOnClickListener(v -> {
                resetControlsTimeout();
                if (player != null) {
                    long newPos = Math.max(0, player.getCurrentPosition() - 10000);
                    player.seekTo(newPos);
                    Toast.makeText(this, "⏪ 10s Rewind", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnForward != null) {
            btnForward.setOnClickListener(v -> {
                resetControlsTimeout();
                if (player != null) {
                    long maxDur = player.getDuration() > 0 ? player.getDuration() : Long.MAX_VALUE;
                    long newPos = Math.min(maxDur, player.getCurrentPosition() + 10000);
                    player.seekTo(newPos);
                    Toast.makeText(this, "⏩ 10s Forward", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnAspectRatio != null) {
            btnAspectRatio.setOnClickListener(v -> {
                resetControlsTimeout();
                if (currentAspectRatioMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                    Toast.makeText(this, "Aspect Ratio: Zoom / Crop (16:9)", Toast.LENGTH_SHORT).show();
                } else if (currentAspectRatioMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                    currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                    Toast.makeText(this, "Aspect Ratio: Fill Screen", Toast.LENGTH_SHORT).show();
                } else {
                    currentAspectRatioMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                    Toast.makeText(this, "Aspect Ratio: Fit (Standard)", Toast.LENGTH_SHORT).show();
                }
                if (playerViewLandscape != null) {
                    playerViewLandscape.setResizeMode(currentAspectRatioMode);
                }
            });
        }

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

        if (btnPiP != null) {
            btnPiP.setOnClickListener(v -> {
                if (!PreferenceUtils.isFloatingPlayerEnabled(this)) {
                    Toast.makeText(this, "Floating Player is disabled in Settings", Toast.LENGTH_SHORT).show();
                    return;
                }
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
        }

        if (playerViewLandscape != null) {
            playerViewLandscape.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
                if (visibility == View.VISIBLE) {
                    animateShowControls();
                } else {
                    animateHideControls();
                }
            });
        }

        showControls();
    }

    private boolean isPremiumStream(String title, String category) {
        if (title != null && (title.toUpperCase().contains("[VIP]") || title.toUpperCase().contains("[PREMIUM]"))) return true;
        if (category != null && (category.toUpperCase().contains("VIP") || category.toUpperCase().contains("PREMIUM"))) return true;
        return false;
    }

    private void initializePlayer() {
        try {
            if (player != null) {
                player.release();
                player = null;
            }
            player = PlayerUtils.createExoPlayer(this);
            if (mediaSession != null) {
                try {
                    mediaSession.release();
                } catch (Exception ignored) {}
            }
            try {
                mediaSession = new MediaSession.Builder(this, player).build();
            } catch (Exception ignored) {}

            playerViewLandscape.setPlayer(player);

            if (streamUrl != null && !streamUrl.isEmpty()) {
                if (isPremiumStream(streamTitle, streamCategory) && !PreferenceUtils.isSubscriptionValid(this)) {
                    Toast.makeText(this, "🔒 VIP Subscription Required: Please login with an active VIP account to watch this movie/series.", Toast.LENGTH_LONG).show();
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
                    if (btnPlayPause != null) {
                        btnPlayPause.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
                    }
                    if (isPlaying) {
                        wasPlayingBeforePause = true;
                        saveCurrentPlaybackState();
                    }
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
                            btnPlayPause.setImageResource(player != null && player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        if (progressBarBuffer != null) progressBarBuffer.setVisibility(View.GONE);
                        if (btnPlayPause != null) {
                            btnPlayPause.setVisibility(View.VISIBLE);
                            btnPlayPause.setImageResource(R.drawable.ic_play);
                        }
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
                        Toast.makeText(LandscapeActivity.this, "Stream connection failed. Tap Play to reconnect.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to initialize video player", Toast.LENGTH_SHORT).show();
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
        boolean foundTracks = false;

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
                            foundTracks = true;
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

        if (!foundTracks) {
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

    private void setupGestureControls() {
        if (playerViewLandscape == null) return;

        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        final GestureDetector gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                toggleControlsVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(@NonNull MotionEvent e) {
                int viewWidth = playerViewLandscape.getWidth();
                if (viewWidth > 0 && player != null) {
                    if (e.getX() < viewWidth / 2f) {
                        long newPos = Math.max(0, player.getCurrentPosition() - 10000);
                        player.seekTo(newPos);
                        Toast.makeText(LandscapeActivity.this, "⏪ 10s Rewind", Toast.LENGTH_SHORT).show();
                    } else {
                        long maxDur = player.getDuration() > 0 ? player.getDuration() : Long.MAX_VALUE;
                        long newPos = Math.min(maxDur, player.getCurrentPosition() + 10000);
                        player.seekTo(newPos);
                        Toast.makeText(LandscapeActivity.this, "⏩ 10s Forward", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return super.onDoubleTap(e);
            }
        });

        View.OnTouchListener touchListener = new View.OnTouchListener() {
            private float startX = 0f;
            private float startY = 0f;
            private long touchDownTime = 0;
            private boolean isGestureActive = false;
            private boolean isLeftArea = false;
            private float initialBrightness = 0.5f;
            private int initialVolume = 0;
            private int maxVolume = 1;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);

                int viewWidth = v.getWidth();
                int viewHeight = v.getHeight();
                if (viewWidth <= 0 || viewHeight <= 0) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        touchDownTime = System.currentTimeMillis();
                        isGestureActive = false;
                        isLeftArea = startX < (viewWidth / 2f);

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

                        if (audioManager != null) {
                            maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                            initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        }
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getX() - startX;
                        float deltaY = startY - event.getY();

                        if (!isGestureActive) {
                            if (Math.abs(deltaY) > 25 && Math.abs(deltaY) > Math.abs(deltaX) * 1.2f) {
                                isGestureActive = true;
                            }
                        }

                        if (isGestureActive) {
                            hideGestureOverlayHandler.removeCallbacks(hideGestureOverlayRunnable);

                            if (isLeftArea) {
                                float deltaBrightness = deltaY / (viewHeight * 0.75f);
                                float newBrightness = Math.max(0.01f, Math.min(1.0f, initialBrightness + deltaBrightness));

                                WindowManager.LayoutParams windowLp = getWindow().getAttributes();
                                windowLp.screenBrightness = newBrightness;
                                getWindow().setAttributes(windowLp);

                                int percent = Math.round(newBrightness * 100f);
                                showGestureOverlay(R.drawable.ic_brightness, "Brightness: " + percent + "%", percent);
                            } else {
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
                            long duration = System.currentTimeMillis() - touchDownTime;
                            if (upDeltaX < 30 && upDeltaY < 30 && duration < 500) {
                                toggleControlsVisibility();
                                return true;
                            }
                        }
                        break;
                }
                return false;
            }
        };

        playerViewLandscape.setOnTouchListener(touchListener);
        if (playerControlRoot != null) {
            playerControlRoot.setOnTouchListener(touchListener);
        }
        if (playerTopBar != null) {
            playerTopBar.setOnClickListener(v -> resetControlsTimeout());
        }
        if (centerControlsLayout != null) {
            centerControlsLayout.setOnClickListener(v -> resetControlsTimeout());
        }
        if (playerBottomBar != null) {
            playerBottomBar.setOnClickListener(v -> resetControlsTimeout());
        }
    }

    private void showGestureOverlay(int iconRes, String titleText, int progressPercent) {
        if (cardGestureOverlay != null) {
            if (imgGestureIcon != null) imgGestureIcon.setImageResource(iconRes);
            if (txtGestureTitle != null) txtGestureTitle.setText(titleText);
            if (progressGesture != null) progressGesture.setProgress(progressPercent);
            cardGestureOverlay.setVisibility(View.VISIBLE);
        }
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        try {
            if (isInPictureInPictureMode) {
                if (playerControlRoot != null) playerControlRoot.setVisibility(View.GONE);
            } else {
                if (playerControlRoot != null) playerControlRoot.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleBackPress() {
        if (PreferenceUtils.isFloatingPlayerEnabled(this) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            if (streamUrl != null && !streamUrl.isEmpty()) {
                enterCustomFloatingPlayer();
                return;
            }
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (PreferenceUtils.isFloatingPlayerEnabled(this) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            if (streamUrl != null && !streamUrl.isEmpty()) {
                enterCustomFloatingPlayer();
            }
        }
    }

    private void enterCustomFloatingPlayer() {
        if (!PreferenceUtils.isFloatingPlayerEnabled(this)) {
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
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
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_CHANNEL_ID, "");
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_URL, streamUrl);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TITLE, streamTitle);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_CATEGORY, "Movie");
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_STREAM_TYPE, streamType);
        serviceIntent.putExtra(FloatingPlayerService.EXTRA_LOGO_URL, "");
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

    private void saveCurrentPlaybackState() {
        if (player != null && streamUrl != null && !streamUrl.isEmpty()) {
            long pos = (!player.isCurrentMediaItemLive() && player.getDuration() > 0) ? player.getCurrentPosition() : 0;
            PreferenceUtils.saveLastPlayedStream(this, 0, streamUrl, streamTitle, streamType, streamCategory, "", pos);
        }
    }

    private void resumeOrRestartPlayback() {
        try {
            if (player == null) {
                initializePlayer();
            } else {
                if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED || player.getPlayerError() != null) {
                    retryStreamConnection();
                } else if (!player.isPlaying()) {
                    player.play();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiver == null) {
            screenStateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent == null || intent.getAction() == null) return;
                    String action = intent.getAction();
                    if (Intent.ACTION_SCREEN_ON.equals(action) || Intent.ACTION_USER_PRESENT.equals(action)) {
                        if (PreferenceUtils.isAutoPlayOnScreenOn(LandscapeActivity.this)) {
                            new Handler(Looper.getMainLooper()).postDelayed(() -> resumeOrRestartPlayback(), 250);
                        }
                    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                        if (player != null) {
                            wasPlayingBeforePause = player.isPlaying();
                            saveCurrentPlaybackState();
                        }
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(screenStateReceiver, filter, Context.RECEIVER_EXPORTED);
                } else {
                    registerReceiver(screenStateReceiver, filter);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void unregisterScreenStateReceiver() {
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
            } catch (Exception ignored) {}
            screenStateReceiver = null;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (PreferenceUtils.isAutoPlayOnScreenOn(this)) {
            resumeOrRestartPlayback();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (btnPiP != null) {
            btnPiP.setVisibility(PreferenceUtils.isFloatingPlayerEnabled(this) ? View.VISIBLE : View.GONE);
        }
        registerScreenStateReceiver();
        if (PreferenceUtils.isAutoPlayOnScreenOn(this)) {
            resumeOrRestartPlayback();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterScreenStateReceiver();
        if (player != null) {
            wasPlayingBeforePause = player.isPlaying();
            saveCurrentPlaybackState();
            if (!isInPictureInPictureMode()) {
                player.pause();
            }
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
        unregisterScreenStateReceiver();
        hideGestureOverlayHandler.removeCallbacksAndMessages(null);
        progressHandler.removeCallbacksAndMessages(null);
        retryHandler.removeCallbacksAndMessages(null);
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Exception ignored) {}
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
