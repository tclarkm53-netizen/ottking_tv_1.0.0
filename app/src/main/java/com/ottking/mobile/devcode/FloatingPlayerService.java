package com.ottking.mobile.devcode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;
import com.ottking.mobile.devcode.utils.PlayerUtils;
import androidx.media3.exoplayer.source.MediaSource;

public class FloatingPlayerService extends Service {

    public static final String ACTION_START_FLOATING = "com.ottking.mobile.devcode.action.START_FLOATING";
    public static final String ACTION_STOP_FLOATING = "com.ottking.mobile.devcode.action.STOP_FLOATING";

    public static final String EXTRA_CHANNEL_ID = "channel_id";
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_STREAM_TITLE = "stream_title";
    public static final String EXTRA_STREAM_CATEGORY = "stream_category";
    public static final String EXTRA_STREAM_TYPE = "stream_type";
    public static final String EXTRA_LOGO_URL = "logo_url";
    public static final String EXTRA_SEEK_POSITION = "seek_position";

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams layoutParams;

    private ExoPlayer player;
    private MediaSession mediaSession;
    private PlayerView playerView;
    private ImageView btnPlayPause, btnClose, btnExpand;
    private TextView txtTitle;
    private ProgressBar progressBar;
    private boolean isRetryAttempted = false;

    private String channelId;
    private String streamUrl;
    private String streamTitle;
    private String streamCategory;
    private String streamType;
    private String logoUrl;
    private long seekPosition = 0;

    private static final String CHANNEL_ID = "floating_player_channel";
    private static final int NOTIF_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_FLOATING.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null) {
            channelId = intent.getStringExtra(EXTRA_CHANNEL_ID);
            streamUrl = intent.getStringExtra(EXTRA_STREAM_URL);
            streamTitle = intent.getStringExtra(EXTRA_STREAM_TITLE);
            streamCategory = intent.getStringExtra(EXTRA_STREAM_CATEGORY);
            streamType = intent.getStringExtra(EXTRA_STREAM_TYPE);
            logoUrl = intent.getStringExtra(EXTRA_LOGO_URL);
            seekPosition = intent.getLongExtra(EXTRA_SEEK_POSITION, 0);
        }

        try {
            Notification notification = createNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(NOTIF_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } catch (Exception e) {
                    startForeground(NOTIF_ID, notification);
                }
            } else {
                startForeground(NOTIF_ID, notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(this, "No stream URL provided for Floating Player", Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        if (floatingView == null) {
            initFloatingWindow();
        } else {
            if (txtTitle != null) {
                txtTitle.setText(streamTitle != null ? streamTitle : "Live Stream");
            }
            playStream();
        }

        return START_NOT_STICKY;
    }

    private View floatingHeader;
    private View floatingControls;
    private boolean areFloatingControlsVisible = true;
    private final Handler floatingHideHandler = new Handler(Looper.getMainLooper());
    private static final long FLOATING_CONTROLS_TIMEOUT_MS = 4000;

    private final Runnable hideFloatingControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideFloatingControls();
        }
    };

    private void showFloatingControls() {
        floatingHideHandler.removeCallbacks(hideFloatingControlsRunnable);
        areFloatingControlsVisible = true;

        if (floatingHeader != null) {
            floatingHeader.animate().cancel();
            floatingHeader.setVisibility(View.VISIBLE);
            floatingHeader.setAlpha(0f);
            floatingHeader.setTranslationY(-20f);
            floatingHeader.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setListener(null);
        }

        if (floatingControls != null) {
            floatingControls.animate().cancel();
            floatingControls.setVisibility(View.VISIBLE);
            floatingControls.setAlpha(0f);
            floatingControls.setTranslationY(20f);
            floatingControls.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setListener(null);
        }

        floatingHideHandler.postDelayed(hideFloatingControlsRunnable, FLOATING_CONTROLS_TIMEOUT_MS);
    }

    private void hideFloatingControls() {
        floatingHideHandler.removeCallbacks(hideFloatingControlsRunnable);
        if (!areFloatingControlsVisible) return;
        areFloatingControlsVisible = false;

        if (floatingHeader != null) {
            floatingHeader.animate().cancel();
            floatingHeader.animate()
                    .alpha(0f)
                    .translationY(-20f)
                    .setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!areFloatingControlsVisible && floatingHeader != null) {
                                floatingHeader.setVisibility(View.GONE);
                            }
                        }
                    });
        }

        if (floatingControls != null) {
            floatingControls.animate().cancel();
            floatingControls.animate()
                    .alpha(0f)
                    .translationY(20f)
                    .setDuration(200)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            if (!areFloatingControlsVisible && floatingControls != null) {
                                floatingControls.setVisibility(View.GONE);
                            }
                        }
                    });
        }
    }

    private void toggleFloatingControls() {
        if (areFloatingControlsVisible) {
            hideFloatingControls();
        } else {
            showFloatingControls();
        }
    }

    private void resetFloatingControlsTimeout() {
        floatingHideHandler.removeCallbacks(hideFloatingControlsRunnable);
        if (areFloatingControlsVisible) {
            floatingHideHandler.postDelayed(hideFloatingControlsRunnable, FLOATING_CONTROLS_TIMEOUT_MS);
        }
    }

    private void initFloatingWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Display over other apps permission required", Toast.LENGTH_LONG).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return;
        }

        try {
            LayoutInflater inflater = LayoutInflater.from(new androidx.appcompat.view.ContextThemeWrapper(this, R.style.Theme_LiveTVPlayer));
            floatingView = inflater.inflate(R.layout.layout_floating_player_window, null);

            int layoutType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            int widthPx = (int) (280 * getResources().getDisplayMetrics().density);
            int heightPx = (int) (170 * getResources().getDisplayMetrics().density);

            layoutParams = new WindowManager.LayoutParams(
                    widthPx,
                    heightPx,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
            );

            layoutParams.gravity = Gravity.TOP | Gravity.START;
            layoutParams.x = 100;
            layoutParams.y = 300;

            windowManager.addView(floatingView, layoutParams);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Unable to show floating window: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
            return;
        }

        try {
            playerView = floatingView.findViewById(R.id.floatingPlayerView);
            btnPlayPause = floatingView.findViewById(R.id.btnFloatingPlayPause);
            btnClose = floatingView.findViewById(R.id.btnFloatingClose);
            btnExpand = floatingView.findViewById(R.id.btnFloatingExpand);
            txtTitle = floatingView.findViewById(R.id.txtFloatingTitle);
            progressBar = floatingView.findViewById(R.id.floatingProgress);
            floatingHeader = floatingView.findViewById(R.id.floatingHeader);
            floatingControls = floatingView.findViewById(R.id.floatingControls);

            if (txtTitle != null) {
                txtTitle.setText(streamTitle != null ? streamTitle : "Live Stream");
            }

            if (btnClose != null) {
                btnClose.setOnClickListener(v -> {
                    resetFloatingControlsTimeout();
                    stopSelf();
                });
            }

            if (btnExpand != null) {
                btnExpand.setOnClickListener(v -> {
                    resetFloatingControlsTimeout();
                    long currentPos = (player != null) ? player.getCurrentPosition() : 0;
                    boolean isMovie = false;
                    if (streamCategory != null) {
                        String cat = streamCategory.toLowerCase();
                        if (cat.contains("movie") || cat.contains("vod") || cat.contains("cinema") || cat.contains("film")) {
                            isMovie = true;
                        }
                    }
                    if (streamType != null && (streamType.equalsIgnoreCase("vod") || streamType.equalsIgnoreCase("movie"))) {
                        isMovie = true;
                    }

                    Class<?> targetActivity = isMovie ? LandscapeActivity.class : PlayerActivity.class;
                    Intent intent = new Intent(this, targetActivity);
                    int chId = -1;
                    try {
                        if (channelId != null && !channelId.trim().isEmpty()) {
                            chId = Integer.parseInt(channelId.trim());
                        }
                    } catch (Exception ignored) {}

                    intent.putExtra("channel_id", chId);
                    intent.putExtra("stream_url", streamUrl);
                    intent.putExtra("stream_title", streamTitle);
                    intent.putExtra("stream_category", streamCategory);
                    intent.putExtra("stream_type", streamType);
                    intent.putExtra("logo_url", logoUrl);
                    intent.putExtra("seek_position", currentPos);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    stopSelf();
                });
            }

            if (btnPlayPause != null) {
                btnPlayPause.setOnClickListener(v -> {
                    resetFloatingControlsTimeout();
                    if (player != null) {
                        if (player.isPlaying()) {
                            player.pause();
                            btnPlayPause.setImageResource(R.drawable.ic_play);
                        } else {
                            player.play();
                            btnPlayPause.setImageResource(R.drawable.ic_pause);
                        }
                    }
                });
            }

            setupDragListener();
            showFloatingControls();
            playStream();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error in floating window setup: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
        }
    }

    private void setupDragListener() {
        if (floatingView == null) return;

        View.OnTouchListener touchListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchDownTime = 0;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchDownTime = System.currentTimeMillis();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) (event.getRawX() - initialTouchX);
                        int deltaY = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(deltaX) > 12 || Math.abs(deltaY) > 12) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            layoutParams.x = initialX + deltaX;
                            layoutParams.y = initialY + deltaY;
                            if (windowManager != null && floatingView != null) {
                                try {
                                    windowManager.updateViewLayout(floatingView, layoutParams);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!isDragging) {
                            long duration = System.currentTimeMillis() - touchDownTime;
                            if (duration < 500) {
                                toggleFloatingControls();
                                return true;
                            }
                        }
                        break;
                }
                return false;
            }
        };

        floatingView.setOnTouchListener(touchListener);
        if (playerView != null) {
            playerView.setOnTouchListener(touchListener);
        }
        if (floatingHeader != null) {
            floatingHeader.setOnTouchListener(touchListener);
        }
        if (floatingControls != null) {
            floatingControls.setOnClickListener(v -> resetFloatingControlsTimeout());
        }
    }

    private void playStream() {
        if (streamUrl == null || streamUrl.trim().isEmpty()) {
            Toast.makeText(this, "Stream URL is empty", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            isRetryAttempted = false;
            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

            if (player == null) {
                player = PlayerUtils.createExoPlayer(this);
                if (mediaSession != null) {
                    try {
                        mediaSession.release();
                    } catch (Exception ignored) {}
                }
                try {
                    mediaSession = new MediaSession.Builder(this, player).build();
                } catch (Exception ignored) {}

                if (playerView != null) {
                    playerView.setPlayer(player);
                }

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int playbackState) {
                        if (playbackState == Player.STATE_BUFFERING) {
                            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                        } else if (playbackState == Player.STATE_READY) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_pause);
                        } else if (playbackState == Player.STATE_ENDED) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
                        }
                    }

                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        if (isPlaying) {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_pause);
                        } else if (player != null && !player.isPlaying() && player.getPlaybackState() != Player.STATE_BUFFERING) {
                            if (btnPlayPause != null) btnPlayPause.setImageResource(R.drawable.ic_play);
                        }
                    }

                    @Override
                    public void onRenderedFirstFrame() {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        // Retry stream once if network glitch
                        if (player != null && streamUrl != null && !isRetryAttempted) {
                            isRetryAttempted = true;
                            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
                            try {
                                MediaSource ms = PlayerUtils.createMediaSource(FloatingPlayerService.this, streamUrl, "HLS");
                                player.setMediaSource(ms);
                                player.prepare();
                                player.setPlayWhenReady(true);
                            } catch (Exception ignored) {}
                        }
                    }
                });
            } else {
                if (playerView != null) {
                    playerView.setPlayer(player);
                }
            }

            String typeStr = (streamType != null && !streamType.isEmpty()) ? streamType.toUpperCase() : "HLS";
            MediaSource mediaSource = PlayerUtils.createMediaSource(this, streamUrl, typeStr);
            player.setMediaSource(mediaSource);

            boolean isVod = false;
            if (streamCategory != null) {
                String cat = streamCategory.toLowerCase();
                if (cat.contains("movie") || cat.contains("vod") || cat.contains("cinema") || cat.contains("film")) {
                    isVod = true;
                }
            }
            if (streamType != null && (streamType.equalsIgnoreCase("vod") || streamType.equalsIgnoreCase("movie"))) {
                isVod = true;
            }
            if (streamUrl.endsWith(".mp4") || streamUrl.endsWith(".mkv") || streamUrl.endsWith(".webm") || streamUrl.endsWith(".avi")) {
                isVod = true;
            }
            if (streamUrl.toLowerCase().contains(".m3u8")) {
                isVod = false;
            }

            if (isVod && seekPosition > 0) {
                try {
                    player.seekTo(seekPosition);
                } catch (Exception ignored) {}
            }

            player.prepare();
            player.setPlayWhenReady(true);
        } catch (Exception e) {
            e.printStackTrace();
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to start floating stream: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Notification createNotification() {
        Intent stopIntent = new Intent(this, FloatingPlayerService.class);
        stopIntent.setAction(ACTION_STOP_FLOATING);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent openIntent = new Intent(this, PlayerActivity.class);
        openIntent.putExtra("channel_id", channelId);
        openIntent.putExtra("stream_url", streamUrl);
        openIntent.putExtra("stream_title", streamTitle);
        openIntent.putExtra("stream_category", streamCategory);
        openIntent.putExtra("stream_type", streamType);
        openIntent.putExtra("logo_url", logoUrl);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 1, openIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(streamTitle != null ? streamTitle : "Floating TV Player")
                .setContentText("Tap to return to player")
                .setSmallIcon(R.drawable.ic_stream)
                .setContentIntent(openPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating Player Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSession != null) {
            try {
                mediaSession.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaSession = null;
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        if (windowManager != null && floatingView != null) {
            try {
                windowManager.removeView(floatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
