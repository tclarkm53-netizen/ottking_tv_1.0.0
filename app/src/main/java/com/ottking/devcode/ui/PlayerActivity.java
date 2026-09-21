package com.ottking.devcode.ui;

import android.Manifest;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import com.ottking.devcode.utils.FocusManager;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;

import com.bumptech.glide.Glide;
import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.model.StreamTokenAuth;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.utils.PlayerUtils;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.network.DataPollingManager;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;
import com.ottking.devcode.utils.UIUtils;

public class PlayerActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "PlayerActivity";

    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private Dialog currentPlayerExitDialog;
    private ImageView imgPlayerChannelLogo;
    private TextView txtPlayerChannelName, txtPlayerChannelNumber;
    private RecyclerView recyclerPlayerChannels;
    private ChannelAdapter channelAdapter;
    private AppPreferences prefs;

    private View cardChannelOverlay;
    private View drawerChannelList;
    private EditText edtPlayerSearch;
    private ImageView btnClearPlayerSearch;
    private ImageButton btnPlayerVoiceSearch;
    private boolean isAwaitingVoiceResult = false;
    private List<ChannelEntity> allChannelsList = new ArrayList<>();

    private final Handler uiOverlayHandler = new Handler(Looper.getMainLooper());
    private static final long DRAWER_AUTO_HIDE_TIMEOUT_MS = 5000L;
    private static final long DRAWER_UNFOCUSED_AUTO_HIDE_TIMEOUT_MS = 2500L;

    private final Runnable autoHideDrawerRunnable = this::handleAutoHideDrawer;

    private final Runnable autoHideOverlayRunnable = () -> {
        if (cardChannelOverlay != null && drawerChannelList != null) {
            if (drawerChannelList.getVisibility() != View.VISIBLE) {
                cardChannelOverlay.setVisibility(View.GONE);
            }
        }
    };

    private int currentChannelId = 1;
    private int currentChannelNumber = 1;
    private boolean currentIsPremium = false;
    private String currentStreamUrl = "";
    private String currentChannelName = "Select Channel";
    private String currentLogoUrl = "";

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 15;

    // --- Stream Auto-Polling & Freeze Recovery Watchdog ---
    private final Handler streamAutoPollHandler = new Handler(Looper.getMainLooper());
    private long lastObservedPosition = -1;
    private long lastFrameRenderTimeMs = 0;
    private long lastBufferedPositionMs = -1;
    private long positionStallStartTime = 0;
    private long continuousBufferStartTime = 0;
    private int consecutiveStallRecoveries = 0;
    private boolean isPlayerResumed = false;
    private static final long AUTO_POLL_INTERVAL_MS = 1000; // Poll every 1.0 second for proactive stream health & buffer monitoring
    private static final long MAX_ALLOWED_BUFFER_MS = 6500; // 6.5s continuous buffer stall = auto-jump to live edge or refresh stream
    private long currentLiveTargetOffsetMs = C.TIME_UNSET;
    private long currentLiveMinOffsetMs = C.TIME_UNSET;
    private long currentLiveMaxOffsetMs = C.TIME_UNSET;
    private boolean isProactiveRecoveryActive = false;

    // --- Periodic Edge-Cookie Refresher ---
    private final Handler cookieRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable cookieRefreshRunnable;
    private static final long COOKIE_REFRESH_INTERVAL_MS = 25000L; // 25 seconds auto-refresh interval as requested

    private final Handler bufferingWatchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable bufferingWatchdogRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    private View cardChannelNumOverlay;
    private TextView txtChannelNumInput;
    private final StringBuilder channelNumBuffer = new StringBuilder();
    private final Handler channelNumHandler = new Handler(Looper.getMainLooper());
    private final Runnable tuneChannelNumRunnable = this::commitChannelNumberInput;

    private final Handler channelSwitchDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingChannelSwitchRunnable = null;

    private boolean initialPlaybackStarted = false;

    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        staticAppContext = getApplicationContext();
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_player);

        prefs = AppPreferences.getInstance(this);
        staticAppVersionName = ApiClient.getAppVersionName(this);
        staticAppVersionCode = ApiClient.getAppVersionCode(this);

        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    isAwaitingVoiceResult = false;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            if (edtPlayerSearch != null) {
                                edtPlayerSearch.setText(recognizedText);
                            }
                        }
                    }
                    // Reliably restore focus after returning from Voice Search dialog
                    restoreVoiceSearchReturnFocus();
                });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startVoiceSearch();
                    }
                });

        playerView = findViewById(R.id.playerView);
        playerView.setUseController(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
        playerView.setKeepContentOnPlayerReset(true);
        dismissBufferingView();
        applySavedPlayerSettings();
        imgPlayerChannelLogo = findViewById(R.id.imgPlayerChannelLogo);
        txtPlayerChannelName = findViewById(R.id.txtPlayerChannelName);
        txtPlayerChannelNumber = findViewById(R.id.txtPlayerChannelNumber);
        recyclerPlayerChannels = findViewById(R.id.recyclerPlayerChannels);
        cardChannelOverlay = findViewById(R.id.cardChannelOverlay);
        drawerChannelList = findViewById(R.id.drawerChannelList);
        cardChannelNumOverlay = findViewById(R.id.cardChannelNumOverlay);
        txtChannelNumInput = findViewById(R.id.txtChannelNumInput);
        ImageButton btnPlayerSettings = findViewById(R.id.btnPlayerSettings);

        // Hide overlays by default
        hideOverlays();

        // Screen single-click shows channel card, long-click shows channel list drawer
        playerView.setOnClickListener(v -> {
            if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
                hideOverlays();
            } else {
                showCardOverlayTemporarily(4000);
            }
        });
        playerView.setOnLongClickListener(v -> {
            showChannelDrawer();
            return true;
        });

        // Get Intent Extras if launched from Home
        if (getIntent() != null && getIntent().hasExtra("stream_url")) {
            currentChannelId = getIntent().getIntExtra("channel_id", 1);
            currentChannelNumber = getIntent().getIntExtra("channel_number", 1);
            currentStreamUrl = getIntent().getStringExtra("stream_url");
            currentChannelName = getIntent().getStringExtra("channel_name");
            currentLogoUrl = getIntent().getStringExtra("logo_url");
            currentIsPremium = getIntent().getBooleanExtra("is_premium", false);
            prefs.setLastPlayedChannelId(currentChannelId);
            initialPlaybackStarted = true;
        } else {
            initialPlaybackStarted = false;
        }

        if (currentStreamUrl != null) {
            currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, currentStreamUrl);
        }

        if (getIntent() != null) {
            String passedCookie = getIntent().getStringExtra("edge_cookie");
            if (passedCookie != null && !passedCookie.trim().isEmpty()) {
                staticEdgeCookie = passedCookie.trim();
                com.ottking.devcode.network.GlobalCookieManager.getInstance(this).updateCookie(passedCookie.trim());
            }
        }
        staticEdgeCookie = com.ottking.devcode.network.GlobalCookieManager.getInstance(this).getValidatedCookie(currentChannelId, currentStreamUrl);

        // Register Global Cookie Listener
        com.ottking.devcode.network.GlobalCookieManager.getInstance(this).addListener(newCookie -> {
            staticEdgeCookie = newCookie;
            android.util.Log.d("PlayerActivity", "Player received global cookie update");
        });

        updateChannelInfoUI();
        initExoPlayer();
        setupChannelDrawer();
        setupFocusGuard();
        registerNetworkCallback();

        FocusManager.getInstance().setupBackPressHandler(this, SCREEN_KEY, this::handlePlayerBackPressInternal);
    }

    private void setupFocusGuard() {
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
                resetDrawerAutoHideTimer();
            }
        });
    }

    private void handleAutoHideDrawer() {
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            // Keep drawer open if user has keyboard/cursor active in search
            if (edtPlayerSearch != null && edtPlayerSearch.isCursorVisible()) {
                resetDrawerAutoHideTimer();
                return;
            }
            hideOverlays();
        }
    }

    private void resetDrawerAutoHideTimer() {
        uiOverlayHandler.removeCallbacks(autoHideDrawerRunnable);
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            View currentFocus = getCurrentFocus();
            boolean hasFocusInsideDrawer = isViewInsideDrawer(currentFocus);
            long timeout = hasFocusInsideDrawer ? DRAWER_AUTO_HIDE_TIMEOUT_MS : DRAWER_UNFOCUSED_AUTO_HIDE_TIMEOUT_MS;
            uiOverlayHandler.postDelayed(autoHideDrawerRunnable, timeout);
        }
    }

    private boolean isViewInsideDrawer(View view) {
        if (view == null || drawerChannelList == null) return false;
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent == drawerChannelList) return true;
            parent = parent.getParent();
        }
        return view == drawerChannelList;
    }

    private void focusPlayingOrFirstChannel() {
        if (recyclerPlayerChannels == null || channelAdapter == null || channelAdapter.getItemCount() == 0) {
            if (edtPlayerSearch != null) edtPlayerSearch.requestFocus();
            return;
        }
        int targetPos = channelAdapter.getChannelPositionById(currentChannelId);
        if (targetPos < 0) {
            targetPos = 0;
        }
        final int finalPos = Math.min(targetPos, channelAdapter.getItemCount() - 1);
        focusPlayerChannelAtPosition(finalPos);
    }

    private void focusPlayerChannelAtPosition(int position) {
        if (recyclerPlayerChannels == null || channelAdapter == null || channelAdapter.getItemCount() == 0) return;
        final int targetPos = Math.max(0, Math.min(position, channelAdapter.getItemCount() - 1));
        recyclerPlayerChannels.scrollToPosition(targetPos);
        recyclerPlayerChannels.post(() -> {
            RecyclerView.ViewHolder holder = recyclerPlayerChannels.findViewHolderForAdapterPosition(targetPos);
            if (holder != null && holder.itemView != null) {
                holder.itemView.requestFocus();
            } else {
                recyclerPlayerChannels.postDelayed(() -> {
                    RecyclerView.ViewHolder holder2 = recyclerPlayerChannels.findViewHolderForAdapterPosition(targetPos);
                    if (holder2 != null && holder2.itemView != null) {
                        holder2.itemView.requestFocus();
                    } else if (recyclerPlayerChannels.getChildCount() > 0) {
                        recyclerPlayerChannels.getChildAt(0).requestFocus();
                    } else if (edtPlayerSearch != null) {
                        edtPlayerSearch.requestFocus();
                    }
                }, 30);
            }
        });
    }

    private void hideOverlays() {
        uiOverlayHandler.removeCallbacks(autoHideOverlayRunnable);
        uiOverlayHandler.removeCallbacks(autoHideDrawerRunnable);
        channelNumHandler.removeCallbacks(tuneChannelNumRunnable);
        channelNumBuffer.setLength(0);
        if (cardChannelOverlay != null) cardChannelOverlay.setVisibility(View.GONE);
        if (drawerChannelList != null) drawerChannelList.setVisibility(View.GONE);
        if (cardChannelNumOverlay != null) cardChannelNumOverlay.setVisibility(View.GONE);
        if (playerView != null) {
            playerView.requestFocus();
        }
    }

    private void showChannelDrawer() {
        uiOverlayHandler.removeCallbacks(autoHideOverlayRunnable);
        uiOverlayHandler.removeCallbacks(autoHideDrawerRunnable);
        if (cardChannelOverlay != null) cardChannelOverlay.setVisibility(View.GONE);
        if (cardChannelNumOverlay != null) cardChannelNumOverlay.setVisibility(View.GONE);
        if (drawerChannelList != null) {
            drawerChannelList.setVisibility(View.VISIBLE);
            if (channelAdapter != null) {
                channelAdapter.setPlayingChannelId(currentChannelId);
            }
            focusPlayingOrFirstChannel();
            resetDrawerAutoHideTimer();
        }
    }

    private void toggleChannelDrawer() {
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            hideOverlays();
        } else {
            showChannelDrawer();
        }
    }

    private void showCardOverlayTemporarily(long durationMs) {
        uiOverlayHandler.removeCallbacks(autoHideOverlayRunnable);
        if (drawerChannelList != null) drawerChannelList.setVisibility(View.GONE);
        if (cardChannelOverlay != null) {
            cardChannelOverlay.setVisibility(View.VISIBLE);
        }
        uiOverlayHandler.postDelayed(autoHideOverlayRunnable, durationMs);
    }

    private void commitChannelNumberInput() {
        if (channelNumBuffer.length() == 0) return;
        try {
            int targetNum = Integer.parseInt(channelNumBuffer.toString());
            channelNumBuffer.setLength(0);
            if (cardChannelNumOverlay != null) {
                cardChannelNumOverlay.setVisibility(View.GONE);
            }

            if (allChannelsList != null && !allChannelsList.isEmpty()) {
                int targetIndex = targetNum - 1; // 1-based channel numbering
                if (targetIndex < 0) {
                    targetIndex = 0;
                } else if (targetIndex >= allChannelsList.size()) {
                    targetIndex = allChannelsList.size() - 1;
                }

                ChannelEntity targetChannel = allChannelsList.get(targetIndex);
                currentChannelId = targetChannel.id;
                currentChannelNumber = targetIndex + 1;
                currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, targetChannel.streamUrl);
                currentChannelName = targetChannel.name;
                currentLogoUrl = targetChannel.logoUrl;
                currentIsPremium = targetChannel.isPremium;
                prefs.setLastPlayedChannelId(currentChannelId);

                updateChannelInfoUI();
                stopAndFlushPreviousStream();
                playStream(currentStreamUrl);
                showCardOverlayTemporarily(4000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            channelNumBuffer.setLength(0);
            if (cardChannelNumOverlay != null) {
                cardChannelNumOverlay.setVisibility(View.GONE);
            }
        }
    }

    private void dismissBufferingView() {
        if (playerView != null) {
            try {
                playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER);
                View buffering = playerView.findViewById(androidx.media3.ui.R.id.exo_buffering);
                if (buffering != null) {
                    buffering.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {}
        }
    }

    private void stopAndFlushPreviousStream() {
        if (channelSwitchDebounceHandler != null) {
            channelSwitchDebounceHandler.removeCallbacksAndMessages(null);
        }
        if (retryHandler != null) {
            retryHandler.removeCallbacksAndMessages(null);
        }
        if (bufferingWatchdogHandler != null && bufferingWatchdogRunnable != null) {
            bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable);
        }
        stopStreamAutoPolling();

        // Immediately abort previous stream network downloads & purge buffers so old picture doesn't freeze
        if (sharedOkHttpClient != null) {
            try {
                sharedOkHttpClient.dispatcher().cancelAll();
            } catch (Exception ignored) {}
        }
        if (player != null) {
            try {
                player.stop();
                player.clearMediaItems();
            } catch (Exception ignored) {}
        }

        // Reset all playback tracking metrics
        lastObservedPosition = -1;
        lastBufferedPositionMs = -1;
        lastFrameRenderTimeMs = 0;
        positionStallStartTime = 0;
        continuousBufferStartTime = 0;
        consecutiveStallRecoveries = 0;
        isProactiveRecoveryActive = false;
        retryCount = 0;
    }

    private void changeChannel(boolean next) {
        if (allChannelsList == null || allChannelsList.isEmpty()) return;

        int currentIndex = -1;
        for (int i = 0; i < allChannelsList.size(); i++) {
            if (allChannelsList.get(i).id == currentChannelId) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) currentIndex = 0;

        int targetIndex;
        if (next) {
            targetIndex = (currentIndex + 1) % allChannelsList.size();
        } else {
            targetIndex = (currentIndex - 1 + allChannelsList.size()) % allChannelsList.size();
        }

        ChannelEntity targetChannel = allChannelsList.get(targetIndex);
        currentChannelId = targetChannel.id;
        currentChannelNumber = targetIndex + 1;
        currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, targetChannel.streamUrl);
        currentChannelName = targetChannel.name;
        currentLogoUrl = targetChannel.logoUrl;
        currentIsPremium = targetChannel.isPremium;
        prefs.setLastPlayedChannelId(currentChannelId);

        updateChannelInfoUI();
        showCardOverlayTemporarily(4000);

        // Immediately abort previous stream network downloads & purge buffers so old picture doesn't freeze
        stopAndFlushPreviousStream();

        // Load new channel immediately with zero delay for ultra-responsive TV experience
        playStream(currentStreamUrl);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            resetDrawerAutoHideTimer();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            resetDrawerAutoHideTimer();
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            boolean isDrawerVisible = drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE;

            // Handle Number Keys (0-9 & Numpad 0-9)
            boolean isDigit = (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                           || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9);

            if (isDigit) {
                int digit = (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
                          ? (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                          : (keyCode - KeyEvent.KEYCODE_0);

                if (channelNumBuffer.length() < 4) {
                    channelNumBuffer.append(digit);
                }

                if (cardChannelNumOverlay != null && txtChannelNumInput != null) {
                    txtChannelNumInput.setText(channelNumBuffer.toString());
                    cardChannelNumOverlay.setVisibility(View.VISIBLE);
                }

                channelNumHandler.removeCallbacks(tuneChannelNumRunnable);
                channelNumHandler.postDelayed(tuneChannelNumRunnable, 1000);
                return true;
            }

            // If user is currently typing a channel number:
            if (channelNumBuffer.length() > 0) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    channelNumHandler.removeCallbacks(tuneChannelNumRunnable);
                    commitChannelNumberInput();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    channelNumHandler.removeCallbacks(tuneChannelNumRunnable);
                    channelNumBuffer.setLength(0);
                    if (cardChannelNumOverlay != null) cardChannelNumOverlay.setVisibility(View.GONE);
                    return true;
                }
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_GUIDE:
                    if (!isDrawerVisible) {
                        showChannelDrawer();
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    if (!isDrawerVisible) {
                        changeChannel(true);
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    if (!isDrawerVisible) {
                        changeChannel(false);
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_BACK:
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        handleBackPress();
                    }
                    return true;

                case KeyEvent.KEYCODE_INFO:
                case KeyEvent.KEYCODE_TV:
                case KeyEvent.KEYCODE_WINDOW:
                case KeyEvent.KEYCODE_CAPTIONS:
                case KeyEvent.KEYCODE_SETTINGS:
                case KeyEvent.KEYCODE_PROG_RED:
                case KeyEvent.KEYCODE_PROG_GREEN:
                case KeyEvent.KEYCODE_PROG_YELLOW:
                case KeyEvent.KEYCODE_PROG_BLUE:
                    if (!isDrawerVisible) {
                        showCardOverlayTemporarily(4000);
                        return true;
                    }
                    break;

                default:
                    // Show channel card overlay on any undefined key press when drawer is closed
                    if (!isDrawerVisible && keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_MUTE) {
                        showCardOverlayTemporarily(4000);
                    }
                    break;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateChannelInfoUI() {
        txtPlayerChannelName.setText(currentChannelName);
        if (txtPlayerChannelNumber != null) {
            int displayChannelNum = currentChannelNumber;
            if (allChannelsList != null && !allChannelsList.isEmpty()) {
                for (int i = 0; i < allChannelsList.size(); i++) {
                    if (allChannelsList.get(i).id == currentChannelId) {
                        displayChannelNum = i + 1;
                        break;
                    }
                }
            }
            txtPlayerChannelNumber.setText("CH " + displayChannelNum);
        }
        if (channelAdapter != null) {
            channelAdapter.setPlayingChannelId(currentChannelId);
        }

        if (imgPlayerChannelLogo != null && !isFinishing() && !isDestroyed()) {
            if (UIUtils.isValidImageUrl(currentLogoUrl)) {
                Glide.with(this)
                        .load(currentLogoUrl.trim())
                        .placeholder(R.drawable.img_app_icon)
                        .error(R.drawable.img_app_icon)
                        .into(imgPlayerChannelLogo);
            } else {
                Glide.with(this).clear(imgPlayerChannelLogo);
                imgPlayerChannelLogo.setImageResource(R.drawable.img_app_icon);
            }
        }
    }

    private static volatile String currentActiveStreamToken = "";
    private static volatile String staticDeviceId = "";
    private static volatile String staticAppId = SecurityUtils.APP_ID;
    private static volatile String staticSessionToken = "";
    private static volatile String staticEdgeCookie = "";
    private static volatile int staticCurrentChannelId = 1;
    private static volatile String staticAppVersionName = com.ottking.devcode.BuildConfig.VERSION_NAME;
    private static volatile int staticAppVersionCode = com.ottking.devcode.BuildConfig.VERSION_CODE;
    private static Context staticAppContext;
    private static OkHttpClient sharedOkHttpClient;
    private static DefaultBandwidthMeter bandwidthMeter;
    private DataSource.Factory dataSourceFactory;

    public static String getCurrentActiveStreamToken() {
        return currentActiveStreamToken != null ? currentActiveStreamToken : "";
    }

    public static String getStaticEdgeCookie() {
        return staticEdgeCookie != null ? staticEdgeCookie : "";
    }

    public static OkHttpClient getSharedOkHttpClient() {
        return getOkHttpClient();
    }

    private static synchronized OkHttpClient getOkHttpClient() {
        if (sharedOkHttpClient == null) {
            okhttp3.Dispatcher dispatcher = new okhttp3.Dispatcher();
            dispatcher.setMaxRequests(64);
            dispatcher.setMaxRequestsPerHost(16);

            sharedOkHttpClient = new OkHttpClient.Builder()
                    .dispatcher(dispatcher)
                    .connectionPool(new ConnectionPool(32, 10, TimeUnit.MINUTES))
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .writeTimeout(8, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    .addInterceptor(chain -> {
                        okhttp3.Request original = chain.request();
                        okhttp3.Request.Builder builder = original.newBuilder();
                        String reqUrl = original.url().toString();

                        // 1. Mandatory X-App-Client verification header
                        builder.header(SecurityUtils.HEADER_APP_CLIENT, SecurityUtils.getXAppClientToken());

                        // 2. Comprehensive obfuscated & hardened player headers from SecurePlayerHeaders
                        if (staticAppContext != null) {
                            Map<String, String> secureMap = com.ottking.devcode.utils.SecurePlayerHeaders.getSecurePlayerHeaderMap(staticAppContext, reqUrl);
                            for (Map.Entry<String, String> entry : secureMap.entrySet()) {
                                if (entry.getKey() != null && entry.getValue() != null) {
                                    builder.header(entry.getKey(), entry.getValue());
                                }
                            }
                        }

                        // 3. Fallback explicit headers to guarantee validation
                        String appId = (staticAppId != null && !staticAppId.isEmpty()) ? staticAppId : SecurityUtils.APP_ID;
                        builder.header("app_id", appId);
                        builder.header("X-App-Id", appId);
                        builder.header("App-Id", appId);
                        builder.header("X-Package-Name", appId);
                        builder.header("package_name", appId);

                        String devId = (staticDeviceId != null && !staticDeviceId.isEmpty()) ? staticDeviceId : "";
                        if (!devId.isEmpty()) {
                            builder.header("device_id", devId);
                            builder.header(SecurityUtils.HEADER_DEVICE_ID, devId);
                            builder.header("Device-Id", devId);
                            builder.header("X-Device-Id", devId);
                        }

                        String sessToken = (staticSessionToken != null && !staticSessionToken.isEmpty()) ? staticSessionToken : "";
                        if (!sessToken.isEmpty()) {
                            builder.header("Session-Token", sessToken);
                            builder.header(SecurityUtils.HEADER_SESSION_TOKEN, sessToken);
                            builder.header("x-app-session", sessToken);
                            builder.header("X-App-Session", sessToken);
                        }

                        String streamToken = currentActiveStreamToken;
                        if (streamToken != null && !streamToken.isEmpty()) {
                            builder.header("Authorization", "Bearer " + streamToken);
                            builder.header(SecurityUtils.HEADER_STREAM_TOKEN, streamToken);
                            builder.header("x-stream-token", streamToken);
                        }

                        String edgeCookie = (staticEdgeCookie != null && !staticEdgeCookie.isEmpty()) ? staticEdgeCookie.trim() : "";
                        if (edgeCookie.isEmpty() && staticAppContext != null) {
                            edgeCookie = com.ottking.devcode.network.GlobalCookieManager.getInstance(staticAppContext).getValidatedCookie(staticCurrentChannelId, reqUrl);
                        }
                        if (!edgeCookie.isEmpty()) {
                            String rawToken = edgeCookie;
                            String lowerToken = rawToken.toLowerCase(Locale.US);
                            if (lowerToken.contains("edge_cookie=")) {
                                int idx = lowerToken.indexOf("edge_cookie=") + "edge_cookie=".length();
                                int end = rawToken.indexOf(';', idx);
                                rawToken = (end != -1) ? rawToken.substring(idx, end).trim() : rawToken.substring(idx).trim();
                            } else if (lowerToken.contains("edge-cookie=")) {
                                int idx = lowerToken.indexOf("edge-cookie=") + "edge-cookie=".length();
                                int end = rawToken.indexOf(';', idx);
                                rawToken = (end != -1) ? rawToken.substring(idx, end).trim() : rawToken.substring(idx).trim();
                            }

                            if (!rawToken.isEmpty()) {
                                builder.header("Edge-Cookie", rawToken);
                                builder.header("edge-cookie", rawToken);
                                builder.header("EdgeCookie", rawToken);
                                builder.header("edge_cookie", rawToken);
                                builder.header("X-Edge-Cookie", rawToken);
                                builder.header("x-edge-cookie", rawToken);
                                builder.header("Cookie-Edge", rawToken);
                                String fullCookie = "Edge-cookie=" + rawToken + "; edge-cookie=" + rawToken + "; edge_cookie=" + rawToken + "; EdgeCookie=" + rawToken;
                                builder.header("Cookie", fullCookie);
                                builder.header("cookie", fullCookie);

                                try {
                                    org.json.JSONObject cJson = new org.json.JSONObject();
                                    cJson.put("app_id", appId);
                                    cJson.put("device_id", devId);
                                    cJson.put("edge_cookie", rawToken);
                                    cJson.put("cookie", rawToken);
                                    cJson.put("channel_id", staticCurrentChannelId);
                                    cJson.put("timestamp", System.currentTimeMillis());
                                    String cJsonStr = cJson.toString();
                                    builder.header("X-Cookie-Json", cJsonStr);
                                    builder.header("X-Edge-Cookie-Json", cJsonStr);
                                } catch (Exception ignored) {}
                            }
                        }

                        builder.header("X-App-Version", staticAppVersionName);
                        builder.header("App-Version", staticAppVersionName);
                        builder.header("X-Version-Name", staticAppVersionName);
                        builder.header("X-Version-Code", String.valueOf(staticAppVersionCode));
                        builder.header("Version-Code", String.valueOf(staticAppVersionCode));

                        return chain.proceed(builder.build());
                    })
                    .build();
        }
        return sharedOkHttpClient;
    }


    private boolean isLowEndDevice() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null && am.isLowRamDevice()) {
                return true;
            }
        } catch (Exception ignored) {}
        int cores = Runtime.getRuntime().availableProcessors();
        return cores <= 2;
    }

    private DataSource.Factory getDataSourceFactory(android.content.Context context) {
        if (dataSourceFactory == null) {
            if (bandwidthMeter == null) {
                bandwidthMeter = new DefaultBandwidthMeter.Builder(context)
                        .setResetOnNetworkTypeChange(true)
                        .setInitialBitrateEstimate(1_200_000)
                        .build();
            }
            OkHttpDataSource.Factory okHttpDataSourceFactory = new OkHttpDataSource.Factory(getOkHttpClient())
                    .setUserAgent(com.ottking.devcode.utils.SecurePlayerHeaders.getDecryptedUserAgent())
                    .setTransferListener(bandwidthMeter);
            dataSourceFactory = new DefaultDataSource.Factory(context, okHttpDataSourceFactory);
        }
        return dataSourceFactory;
    }

    private void initExoPlayer() {
        if (player == null) {
            android.content.Context playerContext = this;

            if (bandwidthMeter == null) {
                bandwidthMeter = new DefaultBandwidthMeter.Builder(playerContext)
                        .setResetOnNetworkTypeChange(true)
                        .setInitialBitrateEstimate(800_000) // 800 kbps downloads initial chunk in ~20-40ms for lightning startup
                        .build();
            }

            AdaptiveTrackSelection.Factory adaptiveTrackSelectionFactory = new AdaptiveTrackSelection.Factory(
                    1500,   // minDurationForQualityIncreaseMs: 1.5s fast step-up to Full HD
                    1000,   // maxDurationForQualityDecreaseMs: Downgrade quickly within 1.0s if bandwidth drops
                    10000,  // minDurationToRetainAfterDiscardMs
                    0.80f   // bandwidthFraction
            );
            trackSelector = new DefaultTrackSelector(playerContext, adaptiveTrackSelectionFactory);

            DefaultRenderersFactory renderersFactory = PlayerUtils.createOptimizedRenderersFactory(
                    playerContext,
                    prefs.isHardwareAccelerationEnabled()
            );

            String buf = prefs.getBufferSettings();
            boolean isLowRam = isLowEndDevice();

            int minBufferMs;
            int maxBufferMs;
            // ABSOLUTE MINIMUM LATENCY STARTUP (<=20ms target) & ZERO BUFFERING:
            // bufferForPlaybackMs = 20ms ensures playback begins immediately within 20ms
            // while background loading maintains continuous segments to prevent freezing.
            int bufferForPlaybackMs = 20;
            int bufferForPlaybackAfterRebufferMs = 100;

            if (buf.contains("Fast") || buf.contains("1 sec") || buf.contains("2s")) {
                minBufferMs = isLowRam ? 15000 : 25000;
                maxBufferMs = isLowRam ? 30000 : 50000;
                bufferForPlaybackMs = 20;
                bufferForPlaybackAfterRebufferMs = 100;
                currentLiveTargetOffsetMs = C.TIME_UNSET;
                currentLiveMinOffsetMs = C.TIME_UNSET;
                currentLiveMaxOffsetMs = C.TIME_UNSET;
            } else if (buf.contains("Standard") || buf.contains("3 sec") || buf.contains("60s")) {
                minBufferMs = isLowRam ? 25000 : 35000;
                maxBufferMs = isLowRam ? 50000 : 70000;
                bufferForPlaybackMs = 20;
                bufferForPlaybackAfterRebufferMs = 100;
                currentLiveTargetOffsetMs = C.TIME_UNSET;
                currentLiveMinOffsetMs = C.TIME_UNSET;
                currentLiveMaxOffsetMs = C.TIME_UNSET;
            } else if (buf.contains("Smooth") || buf.contains("5 sec") || buf.contains("90s")) {
                minBufferMs = isLowRam ? 30000 : 50000;
                maxBufferMs = isLowRam ? 60000 : 90000;
                bufferForPlaybackMs = 20;
                bufferForPlaybackAfterRebufferMs = 100;
                currentLiveTargetOffsetMs = C.TIME_UNSET;
                currentLiveMinOffsetMs = C.TIME_UNSET;
                currentLiveMaxOffsetMs = C.TIME_UNSET;
            } else if (buf.contains("Ultra") || buf.contains("180s") || buf.contains("Shield")) {
                minBufferMs = isLowRam ? 40000 : 60000;
                maxBufferMs = isLowRam ? 80000 : 120000;
                bufferForPlaybackMs = 20;
                bufferForPlaybackAfterRebufferMs = 100;
                currentLiveTargetOffsetMs = C.TIME_UNSET;
                currentLiveMinOffsetMs = C.TIME_UNSET;
                currentLiveMaxOffsetMs = C.TIME_UNSET;
            } else {
                // Default: Immediate 20ms startup + continuous robust forward buffer
                minBufferMs = isLowRam ? 25000 : 35000;
                maxBufferMs = isLowRam ? 50000 : 70000;
                bufferForPlaybackMs = 20;
                bufferForPlaybackAfterRebufferMs = 100;
                currentLiveTargetOffsetMs = C.TIME_UNSET;
                currentLiveMinOffsetMs = C.TIME_UNSET;
                currentLiveMaxOffsetMs = C.TIME_UNSET;
            }

            // High capacity LoadControl pre-downloads advance segments aggressively
            DefaultLoadControl loadControl = PlayerUtils.createOptimizedLoadControl(
                    minBufferMs,
                    maxBufferMs,
                    bufferForPlaybackMs,
                    bufferForPlaybackAfterRebufferMs,
                    isLowRam
            );

            DataSource.Factory dsFactory = getDataSourceFactory(playerContext);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dsFactory);

            player = new ExoPlayer.Builder(playerContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setRenderersFactory(renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .setBandwidthMeter(bandwidthMeter)
                    .setWakeMode(C.WAKE_MODE_NETWORK)
                    .setHandleAudioBecomingNoisy(true)
                    .build();

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();
            player.setAudioAttributes(audioAttributes, true);
            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            playerView.setPlayer(player);
            playerView.setKeepScreenOn(true);

            bufferingWatchdogRunnable = () -> {
                if (player != null && (player.getPlaybackState() == Player.STATE_BUFFERING || player.getPlaybackState() == Player.STATE_IDLE || player.getPlayerError() != null)) {
                    retryPlayback("Stream connection stalled, auto reconnecting...");
                }
            };

            player.addListener(new Player.Listener() {
                @Override
                public void onRenderedFirstFrame() {
                    lastFrameRenderTimeMs = System.currentTimeMillis();
                    positionStallStartTime = 0;
                    continuousBufferStartTime = 0;
                    consecutiveStallRecoveries = 0;
                    dismissBufferingView();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        lastFrameRenderTimeMs = System.currentTimeMillis();
                        positionStallStartTime = 0;
                        continuousBufferStartTime = 0;
                        consecutiveStallRecoveries = 0;
                        dismissBufferingView();
                    }
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable);
                    dismissBufferingView();
                    if (playbackState == Player.STATE_BUFFERING) {
                        bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable, MAX_ALLOWED_BUFFER_MS);
                    } else if (playbackState == Player.STATE_READY) {
                        retryCount = 0;
                        continuousBufferStartTime = 0;
                        dismissBufferingView();
                        if (lastFrameRenderTimeMs == 0) {
                            lastFrameRenderTimeMs = System.currentTimeMillis();
                        }
                        if (player != null && !player.isPlaying()) {
                            player.play();
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        retryPlayback("Stream disconnected, reconnecting...");
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable);
                    int httpCode = com.ottking.devcode.utils.PlayerUtils.getHttpErrorCode(error);
                    if (httpCode == 404 || httpCode == 410) {
                        Toast.makeText(PlayerActivity.this, "This channel link is currently unavailable (Error " + httpCode + ")", Toast.LENGTH_SHORT).show();
                        return;
                    } else if (httpCode == 401 || httpCode == 403) {
                        if (retryCount < 4) {
                            retryCount++;
                            android.util.Log.w("PlayerActivity", "Stream rejected with " + httpCode + ", refreshing session & cookies... Attempt: " + retryCount);
                            ApiClient.getInstance(PlayerActivity.this).refreshEdgeCookie(currentChannelId, currentStreamUrl, new ApiClient.ApiCallback<String>() {
                                @Override
                                public void onSuccess(String newCookie) {
                                    if (newCookie != null && !newCookie.isEmpty()) {
                                        staticEdgeCookie = newCookie;
                                        if (prefs != null) prefs.setEdgeCookie(newCookie);
                                    }
                                    ApiClient.getInstance(PlayerActivity.this).fetchStreamToken(currentChannelId, currentStreamUrl, new ApiClient.ApiCallback<StreamTokenAuth>() {
                                        @Override
                                        public void onSuccess(StreamTokenAuth tokenAuth) {
                                            if (tokenAuth != null && tokenAuth.getStreamToken() != null && !tokenAuth.getStreamToken().isEmpty()) {
                                                currentActiveStreamToken = tokenAuth.getStreamToken();
                                            }
                                            retryPlayback("Session & Edge-Cookie refreshed, reconnecting...");
                                        }

                                        @Override
                                        public void onError(String err) {
                                            retryPlayback("Reconnecting stream...");
                                        }
                                    });
                                }

                                @Override
                                public void onError(String err) {
                                    retryPlayback("Reconnecting stream...");
                                }
                            });
                            return;
                        }
                    }

                    boolean isMalformed = (error != null && (
                            error.getCause() instanceof androidx.media3.common.ParserException
                            || error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                            || error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                    ));
                    if (isMalformed) {
                        android.util.Log.w("PlayerActivity", "Stream payload malformed or unsupported format");
                        if (retryCount >= 2) {
                            Toast.makeText(PlayerActivity.this, "This channel is currently offline or format is not supported", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        retryCount++;
                        retryPlayback("Re-checking stream source...");
                        return;
                    }

                    if (error != null && error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        if (player != null) {
                            player.seekToDefaultPosition();
                            player.prepare();
                            player.play();
                        }
                    } else {
                        retryPlayback("Network lag / server error, retrying...");
                    }
                }
            });
        }

        applySavedPlayerSettings();
        playStream(currentStreamUrl);
    }

    private void reinitPlayer() {
        long currentPosition = 0;
        boolean playWhenReady = true;
        if (player != null) {
            currentPosition = player.getCurrentPosition();
            playWhenReady = player.getPlayWhenReady();
            player.release();
            player = null;
        }
        initExoPlayer();
        if (player != null) {
            player.seekTo(currentPosition);
            player.setPlayWhenReady(playWhenReady);
        }
    }

    private void applySavedPlayerSettings() {
        if (playerView != null) {
            int screenSize = prefs.getVideoScreenSize();
            switch (screenSize) {
                case 1:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                    break;
                case 2:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    break;
                case 3:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                    break;
                case 0:
                default:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    break;
            }
        }

        if (trackSelector != null) {
            String res = prefs.getVideoResolution();
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters()
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(false)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setTunnelingEnabled(false)
                    .setViewportSizeToPhysicalDisplaySize(this, true);

            if (res.contains("1080")) {
                builder.setMaxVideoSize(1920, 1080);
            } else if (res.contains("720")) {
                builder.setMaxVideoSize(1280, 720);
            } else if (res.contains("480")) {
                builder.setMaxVideoSize(854, 480);
            } else if (res.contains("360")) {
                builder.setMaxVideoSize(640, 360);
            } else {
                if (isLowEndDevice()) {
                    builder.setMaxVideoSize(1280, 720);
                    builder.setMaxVideoFrameRate(30);
                } else {
                    builder.clearVideoSizeConstraints();
                }
            }
            trackSelector.setParameters(builder);
        }
    }

    private static class LiveStreamLoadErrorHandlingPolicy extends DefaultLoadErrorHandlingPolicy {
        public LiveStreamLoadErrorHandlingPolicy() {
            super(15);
        }

        @Override
        public long getRetryDelayMsFor(LoadErrorInfo loadErrorInfo) {
            // Immediate fast-retry (250ms - 1200ms) prevents live buffer starvation and freeze
            return Math.min(250L * Math.max(1, loadErrorInfo.errorCount), 1200L);
        }

        @Override
        public int getMinimumLoadableRetryCount(int dataType) {
            return 15;
        }
    }

    private MediaSource buildMediaSource(String url) {
        String streamType = "hls";
        if (url != null) {
            String lower = url.toLowerCase(Locale.US);
            if (lower.contains(".mpd") || lower.contains("mpd")) {
                streamType = "dash";
            } else if (lower.contains(".ts")) {
                streamType = "ts";
            }
        }
        return com.ottking.devcode.utils.PlayerUtils.createMediaSource(this, url, streamType, retryCount);
    }

    private void playStream(String url) {
        if (player == null || url == null || url.trim().isEmpty()) return;

        // Completely flush old buffers, abort old downloads, and stop previous stream
        stopAndFlushPreviousStream();

        // Decrypt stream URL if encrypted with hardware key
        url = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, url);

        // VPN & Proxy Security Guard: Immediately stop playback and block if VPN is active
        if (com.ottking.devcode.security.VpnDetectionManager.isVpnOrProxyActive(this)) {
            player.stop();
            com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(this, () -> playStream(currentStreamUrl));
            return;
        }

        // Reset auto-polling stall metrics for clean stream start
        lastObservedPosition = -1;
        lastBufferedPositionMs = -1;
        lastFrameRenderTimeMs = System.currentTimeMillis();
        positionStallStartTime = 0;
        continuousBufferStartTime = 0;
        consecutiveStallRecoveries = 0;

        startStreamAutoPolling();
        applySavedPlayerSettings();

        // 1. Instantly generate verified cryptographic stream token and provide validated Edge-Cookie
        staticCurrentChannelId = currentChannelId;
        staticAppId = getPackageName();
        staticDeviceId = (prefs != null) ? prefs.getDeviceId() : "";
        staticSessionToken = (prefs != null) ? prefs.getSessionToken() : "";

        // Provide validated edge cookie globally with zero delay for channel switch
        staticEdgeCookie = com.ottking.devcode.network.GlobalCookieManager.getInstance(this)
                .onChannelChanged(currentChannelId, url, freshCookie -> {
                    staticEdgeCookie = freshCookie;
                });

        // Start continuous 25-second background cookie renewal
        com.ottking.devcode.network.GlobalCookieManager.getInstance(this)
                .startPeriodicRefresh(currentChannelId, url);

        StreamTokenAuth localToken = ApiClient.getInstance(this).generateLocalStreamToken(currentChannelId, url);
        currentActiveStreamToken = localToken.getStreamToken();

        // 2. Load stream media source with token authorization
        dismissBufferingView();
        MediaSource mediaSource = buildMediaSource(url.trim());
        player.setMediaSource(mediaSource, true);
        player.prepare();
        player.setPlayWhenReady(true);
        player.play();
        dismissBufferingView();

        // 3. Request realtime edge-cookie from channel / check-session route
        ApiClient.getInstance(this).refreshEdgeCookie(currentChannelId, url, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String realtimeCookie) {
                if (realtimeCookie != null && !realtimeCookie.isEmpty()) {
                    staticEdgeCookie = realtimeCookie;
                    com.ottking.devcode.network.GlobalCookieManager.getInstance(PlayerActivity.this).updateCookie(realtimeCookie);
                    android.util.Log.d("PlayerActivity", "Realtime cookie received via channel/check-session route: " + realtimeCookie);
                }
            }

            @Override
            public void onError(String errorMessage) {}
        });

        // 4. Asynchronously request/verify stream token from backend endpoint
        ApiClient.getInstance(this).fetchStreamToken(currentChannelId, url, new ApiClient.ApiCallback<StreamTokenAuth>() {
            @Override
            public void onSuccess(StreamTokenAuth result) {
                if (result != null) {
                    if (result.getStreamToken() != null && !result.getStreamToken().isEmpty()) {
                        currentActiveStreamToken = result.getStreamToken();
                    }
                    if (result.getEdgeCookie() != null && !result.getEdgeCookie().isEmpty()) {
                        staticEdgeCookie = result.getEdgeCookie();
                        com.ottking.devcode.network.GlobalCookieManager.getInstance(PlayerActivity.this).updateCookie(result.getEdgeCookie());
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Keep playing seamlessly with local verified token
            }
        });
    }

    private void setupChannelDrawer() {
        recyclerPlayerChannels.setLayoutManager(new LinearLayoutManager(this));
        channelAdapter = new ChannelAdapter(true, channel -> {
            if (channel == null) return;
            if (channel.id == currentChannelId || (currentStreamUrl != null && currentStreamUrl.equals(channel.streamUrl))) {
                hideOverlays();
                return;
            }
            currentChannelId = channel.id;
            currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, channel.streamUrl);
            currentChannelName = channel.name;
            currentLogoUrl = channel.logoUrl;
            currentIsPremium = channel.isPremium;
            if (allChannelsList != null && !allChannelsList.isEmpty()) {
                int pos = allChannelsList.indexOf(channel);
                if (pos != -1) {
                    currentChannelNumber = pos + 1;
                }
            }
            prefs.setLastPlayedChannelId(currentChannelId);
            updateChannelInfoUI();
            playStream(currentStreamUrl);
            hideOverlays();
            showCardOverlayTemporarily(4000);
        });
        btnPlayerVoiceSearch = findViewById(R.id.btnPlayerVoiceSearch);
        ImageButton btnPlayerSettings = findViewById(R.id.btnPlayerSettings);
        edtPlayerSearch = findViewById(R.id.edtPlayerSearch);
        btnClearPlayerSearch = findViewById(R.id.btnClearPlayerSearch);

        channelAdapter.setNavigationListener(new ChannelAdapter.OnChannelNavigationListener() {
            @Override
            public void onNavigateToCategories() {}

            @Override
            public void onNavigateToHeader(int channelPosition) {
                if (edtPlayerSearch != null) {
                    edtPlayerSearch.requestFocus();
                } else if (btnPlayerVoiceSearch != null) {
                    btnPlayerVoiceSearch.requestFocus();
                } else if (btnPlayerSettings != null) {
                    btnPlayerSettings.requestFocus();
                }
            }

            @Override
            public void onNavigateToHeader() {
                if (edtPlayerSearch != null) {
                    edtPlayerSearch.requestFocus();
                } else if (btnPlayerVoiceSearch != null) {
                    btnPlayerVoiceSearch.requestFocus();
                } else if (btnPlayerSettings != null) {
                    btnPlayerSettings.requestFocus();
                }
            }

            @Override
            public void onNavigateToStart() {
                focusPlayerChannelAtPosition(0);
            }
        });
        channelAdapter.setFocusListener((pos, view) -> {
            resetDrawerAutoHideTimer();
        });
        recyclerPlayerChannels.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                resetDrawerAutoHideTimer();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                resetDrawerAutoHideTimer();
            }
        });
        recyclerPlayerChannels.setAdapter(channelAdapter);

        if (btnPlayerVoiceSearch != null) {
            btnPlayerVoiceSearch.setOnClickListener(v -> startVoiceSearch());
            btnPlayerVoiceSearch.setOnFocusChangeListener((v, hasFocus) -> {
                UIUtils.animateFocus(v, hasFocus, 1.15f, 10f);
            });
            btnPlayerVoiceSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (btnPlayerSettings != null) {
                            btnPlayerSettings.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (edtPlayerSearch != null) {
                            edtPlayerSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        if (btnPlayerSettings != null) {
            btnPlayerSettings.setOnClickListener(v -> showPlayerSettingsDialog());
            btnPlayerSettings.setOnFocusChangeListener((v, hasFocus) -> {
                UIUtils.animateFocus(v, hasFocus, 1.15f, 10f);
            });
            btnPlayerSettings.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (btnPlayerVoiceSearch != null) {
                            btnPlayerVoiceSearch.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (edtPlayerSearch != null) {
                            edtPlayerSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        if (edtPlayerSearch != null) {
            edtPlayerSearch.setFocusable(true);
            edtPlayerSearch.setFocusableInTouchMode(false);
            edtPlayerSearch.setCursorVisible(false);

            edtPlayerSearch.setOnFocusChangeListener((v, hasFocus) -> {
                UIUtils.animateFocus(v, hasFocus, 1.04f, 8f);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (!hasFocus) {
                    edtPlayerSearch.setCursorVisible(false);
                    edtPlayerSearch.setFocusableInTouchMode(false);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                } else {
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                }
            });

            Runnable activatePlayerSearch = () -> {
                edtPlayerSearch.setFocusableInTouchMode(true);
                edtPlayerSearch.setCursorVisible(true);
                edtPlayerSearch.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtPlayerSearch, InputMethodManager.SHOW_IMPLICIT);
                }
            };

            edtPlayerSearch.setOnClickListener(v -> activatePlayerSearch.run());

            edtPlayerSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                    edtPlayerSearch.setCursorVisible(false);
                    edtPlayerSearch.setFocusableInTouchMode(false);
                    focusPlayerChannelAtPosition(0);
                    return true;
                }
                return false;
            });

            edtPlayerSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        if (!edtPlayerSearch.isCursorVisible()) {
                            activatePlayerSearch.run();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                        }
                        edtPlayerSearch.setCursorVisible(false);
                        edtPlayerSearch.setFocusableInTouchMode(false);
                        if (btnPlayerVoiceSearch != null) {
                            btnPlayerVoiceSearch.requestFocus();
                            return true;
                        } else if (btnPlayerSettings != null) {
                            btnPlayerSettings.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                        }
                        edtPlayerSearch.setCursorVisible(false);
                        edtPlayerSearch.setFocusableInTouchMode(false);
                        focusPlayerChannelAtPosition(0);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (edtPlayerSearch.isCursorVisible() && edtPlayerSearch.getSelectionStart() < edtPlayerSearch.getText().length()) {
                            return false;
                        }
                        if (btnClearPlayerSearch != null && btnClearPlayerSearch.getVisibility() == View.VISIBLE) {
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                            }
                            edtPlayerSearch.setCursorVisible(false);
                            edtPlayerSearch.setFocusableInTouchMode(false);
                            btnClearPlayerSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });

            if (btnClearPlayerSearch != null) {
                UIUtils.applyFocusAnimation(btnClearPlayerSearch, 1.15f, 6f);
                btnClearPlayerSearch.setOnClickListener(v -> {
                    resetDrawerAutoHideTimer();
                    edtPlayerSearch.setText("");
                    filterPlayerChannels("");
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                    edtPlayerSearch.setCursorVisible(false);
                    edtPlayerSearch.setFocusableInTouchMode(false);
                    edtPlayerSearch.requestFocus();
                });

                btnClearPlayerSearch.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        resetDrawerAutoHideTimer();
                        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                            btnClearPlayerSearch.performClick();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                            edtPlayerSearch.requestFocus();
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            focusPlayerChannelAtPosition(0);
                            return true;
                        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            if (btnPlayerVoiceSearch != null) {
                                btnPlayerVoiceSearch.requestFocus();
                                return true;
                            } else if (btnPlayerSettings != null) {
                                btnPlayerSettings.requestFocus();
                                return true;
                            }
                        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                            focusPlayingOrFirstChannel();
                            return true;
                        }
                    }
                    return false;
                });
            }

            edtPlayerSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    resetDrawerAutoHideTimer();
                    if (btnClearPlayerSearch != null) {
                        btnClearPlayerSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                    }
                    filterPlayerChannels(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Observe channels from database for channel list drawer and real-time updates
        AppDatabase.getInstance(this).channelDao().getAllChannels().observe(this, channels -> {
            if (channels != null && !channels.isEmpty()) {
                allChannelsList = new ArrayList<>(channels);
                channelAdapter.setAllChannelsList(channels);
                if (edtPlayerSearch != null && !edtPlayerSearch.getText().toString().isEmpty()) {
                    filterPlayerChannels(edtPlayerSearch.getText().toString());
                } else {
                    channelAdapter.setChannels(channels);
                }

                if (!initialPlaybackStarted) {
                    // Initial startup (e.g. Boot Player directly launched into PlayerActivity)
                    initialPlaybackStarted = true;
                    int lastPlayedId = prefs.getLastPlayedChannelId();
                    ChannelEntity targetChannel = null;
                    if (lastPlayedId != -1) {
                        for (ChannelEntity c : channels) {
                            if (c.id == lastPlayedId) {
                                targetChannel = c;
                                break;
                            }
                        }
                    }
                    if (targetChannel == null) {
                        targetChannel = channels.get(0);
                    }

                    int pos = channels.indexOf(targetChannel);
                    currentChannelId = targetChannel.id;
                    currentChannelNumber = (pos != -1) ? (pos + 1) : 1;
                    currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, targetChannel.streamUrl);
                    currentChannelName = targetChannel.name;
                    currentLogoUrl = targetChannel.logoUrl;
                    currentIsPremium = targetChannel.isPremium;
                    prefs.setLastPlayedChannelId(currentChannelId);

                    updateChannelInfoUI();
                    playStream(currentStreamUrl);
                } else {
                    // Real-time server sync update while user is actively watching
                    ChannelEntity targetChannel = null;
                    int targetPos = -1;
                    for (int i = 0; i < channels.size(); i++) {
                        if (channels.get(i).id == currentChannelId) {
                            targetChannel = channels.get(i);
                            targetPos = i;
                            break;
                        }
                    }

                    if (targetChannel != null) {
                        currentChannelNumber = targetPos + 1;
                        currentChannelName = targetChannel.name;
                        currentLogoUrl = targetChannel.logoUrl;
                        currentIsPremium = targetChannel.isPremium;

                        String updatedStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, targetChannel.streamUrl);
                        if (updatedStreamUrl != null && !updatedStreamUrl.isEmpty() && !updatedStreamUrl.equals(currentStreamUrl)) {
                            android.util.Log.d("PlayerActivity", "Stream URL updated in real-time from server for: " + currentChannelName);
                            currentStreamUrl = updatedStreamUrl;
                            playStream(currentStreamUrl);
                        }
                        updateChannelInfoUI();
                    } else {
                        // Current channel was deleted on server; smoothly fallback to channel 1
                        ChannelEntity fallback = channels.get(0);
                        currentChannelId = fallback.id;
                        currentChannelNumber = 1;
                        currentStreamUrl = com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(this, fallback.streamUrl);
                        currentChannelName = fallback.name;
                        currentLogoUrl = fallback.logoUrl;
                        currentIsPremium = fallback.isPremium;
                        prefs.setLastPlayedChannelId(currentChannelId);
                        updateChannelInfoUI();
                        playStream(currentStreamUrl);
                    }
                }
            }
        });
    }

    private void startVoiceSearch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say channel name (e.g. Sports, News)...");

        try {
            isAwaitingVoiceResult = true;
            voiceSearchLauncher.launch(intent);
        } catch (Exception ignored) {
            isAwaitingVoiceResult = false;
        }
    }

    private void filterPlayerChannels(String query) {
        if (allChannelsList == null || channelAdapter == null) return;
        if (query == null || query.trim().isEmpty()) {
            channelAdapter.setChannels(allChannelsList);
        } else {
            List<ChannelEntity> filtered = new ArrayList<>();
            String lower = query.trim().toLowerCase();
            for (ChannelEntity c : allChannelsList) {
                if (c.name != null && c.name.toLowerCase().contains(lower)) {
                    filtered.add(c);
                }
            }
            channelAdapter.setChannels(filtered);
        }
    }    private void showPlayerSettingsDialog() {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(10, 10, 10, 10);

        // Top Tabs Bar
        LinearLayout tabsBar = new LinearLayout(this);
        tabsBar.setOrientation(LinearLayout.HORIZONTAL);
        tabsBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnTabPlayer = new Button(this);
        btnTabPlayer.setText("Player");
        btnTabPlayer.setFocusable(true);
        btnTabPlayer.setClickable(true);

        Button btnTabTv = new Button(this);
        btnTabTv.setText("TV Settings");
        btnTabTv.setFocusable(true);
        btnTabTv.setClickable(true);

        Button btnTabAccounts = new Button(this);
        btnTabAccounts.setText("Account");
        btnTabAccounts.setFocusable(true);
        btnTabAccounts.setClickable(true);

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        tabParams.setMargins(4, 0, 4, 10);

        tabsBar.addView(btnTabPlayer, tabParams);
        tabsBar.addView(btnTabTv, tabParams);
        tabsBar.addView(btnTabAccounts, tabParams);

        // Content Frame
        FrameLayout contentFrame = new FrameLayout(this);
        contentFrame.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        contentFrame.setPadding(0, 10, 0, 10);

        mainLayout.addView(tabsBar);
        mainLayout.addView(contentFrame);

        final int[] activeTab = {0};

        Runnable loadTabContent = new Runnable() {
            @Override
            public void run() {
                contentFrame.removeAllViews();

                styleTabButton(btnTabPlayer, activeTab[0] == 0);
                styleTabButton(btnTabTv, activeTab[0] == 1);
                styleTabButton(btnTabAccounts, activeTab[0] == 2);

                if (activeTab[0] == 0) {
                    contentFrame.addView(createPlayerTabContent());
                } else if (activeTab[0] == 1) {
                    contentFrame.addView(createTvSettingsTabContent());
                } else {
                    contentFrame.addView(createAccountsTabContent(btnTabAccounts));
                }
            }
        };

        btnTabPlayer.setOnClickListener(v -> {
            activeTab[0] = 0;
            loadTabContent.run();
        });
        btnTabPlayer.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeTab[0] = 0;
                loadTabContent.run();
            }
        });

        btnTabTv.setOnClickListener(v -> {
            activeTab[0] = 1;
            loadTabContent.run();
        });
        btnTabTv.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeTab[0] = 1;
                loadTabContent.run();
            }
        });

        btnTabAccounts.setOnClickListener(v -> {
            activeTab[0] = 2;
            loadTabContent.run();
        });
        btnTabAccounts.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                activeTab[0] = 2;
                loadTabContent.run();
            }
        });

        loadTabContent.run();

        new CustomDialog.Builder(this)
                .setTitle(getString(R.string.title_player_settings))
                .setIcon(R.drawable.ic_settings)
                .setView(mainLayout)
                .setWidthPercent(0.95f)
                .show();
    }

    private void styleTabButton(Button btn, boolean isActive) {
        if (isActive) {
            btn.setBackgroundResource(R.drawable.selector_pill_focus);
            btn.setTextColor(getColorStateList(R.color.selector_pill_text));
            btn.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            btn.setBackgroundResource(R.drawable.bg_card_normal);
            btn.setTextColor(getColor(R.color.text_secondary));
            btn.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        btn.setPadding(10, 10, 10, 10);
    }

    private View createPlayerTabContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 10, 10, 10);

        // 1. Video Resolution Settings
        layout.addView(createSectionHeader("Video Resolution Settings"));
        String[] resolutions = {"Auto (Adaptive)", "1080p Full HD", "720p HD", "480p SD", "360p Low"};
        Spinner spinnerRes = new Spinner(this);
        ArrayAdapter<String> resAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, resolutions);
        spinnerRes.setAdapter(resAdapter);
        int currentResIndex = 0;
        String curRes = prefs.getVideoResolution();
        for (int i = 0; i < resolutions.length; i++) {
            if (resolutions[i].equalsIgnoreCase(curRes)) { currentResIndex = i; break; }
        }
        spinnerRes.setSelection(currentResIndex);
        spinnerRes.setPadding(0, 10, 0, 10);
        spinnerRes.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getColor(R.color.text_primary));
                }
                prefs.setVideoResolution(resolutions[position]);
                applySavedPlayerSettings();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(spinnerRes);

        addDivider(layout);

        // 2. Hardware Acceleration Settings
        layout.addView(createSectionHeader("Hardware Acceleration"));
        SwitchCompat switchHw = new SwitchCompat(this);
        switchHw.setText("Enable HW / HW+ Decoder");
        switchHw.setTextColor(getColor(R.color.text_primary));
        switchHw.setChecked(prefs.isHardwareAccelerationEnabled());
        switchHw.setPadding(0, 10, 0, 10);
        switchHw.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.setHardwareAccelerationEnabled(isChecked);
            reinitPlayer();
        });
        layout.addView(switchHw);

        addDivider(layout);

        // 3. Retry Settings
        layout.addView(createSectionHeader("Stream Retry Settings"));
        String[] retries = {"Auto (3 Retries)", "5 Retries", "10 Retries", "Unlimited Retries"};
        Spinner spinnerRetry = new Spinner(this);
        ArrayAdapter<String> retryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, retries);
        spinnerRetry.setAdapter(retryAdapter);
        int currentRetryIndex = 0;
        String curRetry = prefs.getRetrySettings();
        for (int i = 0; i < retries.length; i++) {
            if (retries[i].equalsIgnoreCase(curRetry)) { currentRetryIndex = i; break; }
        }
        spinnerRetry.setSelection(currentRetryIndex);
        spinnerRetry.setPadding(0, 10, 0, 10);
        spinnerRetry.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getColor(R.color.text_primary));
                }
                prefs.setRetrySettings(retries[position]);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(spinnerRetry);

        addDivider(layout);

        // 4. Buffer Settings (Advance Preload & Network Recovery Options)
        layout.addView(createSectionHeader("Playback Buffer Settings"));
        String[] buffers = {
                "Instant Live (20ms startup, 60s buffer - Zero Buffering)",
                "Fast Start (20ms startup, 30s preload)",
                "Standard (20ms startup, 60s preload)",
                "Smooth Playback (20ms startup, 90s preload)",
                "Ultra Preload Buffer (20ms startup, 180s preload - Network Shield)"
        };
        Spinner spinnerBuffer = new Spinner(this);
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buffers);
        spinnerBuffer.setAdapter(bufferAdapter);
        int currentBufferIndex = 0; // Default to Instant Live
        String curBuf = prefs.getBufferSettings();
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i].equalsIgnoreCase(curBuf)) {
                currentBufferIndex = i;
                break;
            }
        }
        if (curBuf != null) {
            if (curBuf.contains("Instant") || curBuf.contains("Zero")) currentBufferIndex = 0;
            else if (curBuf.contains("Fast") || curBuf.contains("30s")) currentBufferIndex = 1;
            else if (curBuf.contains("Standard") || curBuf.contains("60s")) currentBufferIndex = 2;
            else if (curBuf.contains("Smooth") || curBuf.contains("90s")) currentBufferIndex = 3;
            else if (curBuf.contains("Ultra") || curBuf.contains("Shield") || curBuf.contains("180s")) currentBufferIndex = 4;
        }
        spinnerBuffer.setSelection(currentBufferIndex);
        spinnerBuffer.setPadding(0, 10, 0, 10);
        spinnerBuffer.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getColor(R.color.text_primary));
                }
                String newBuf = buffers[position];
                if (!newBuf.equalsIgnoreCase(prefs.getBufferSettings())) {
                    prefs.setBufferSettings(newBuf);
                    reinitPlayer();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(spinnerBuffer);

        addDivider(layout);

        // 5. Video Screen Size Settings
        layout.addView(createSectionHeader("Video Screen Size Settings"));
        String[] aspectRatios = {"Fit to Screen", "Stretch (Full Screen)", "Zoom 16:9", "Original (4:3)"};
        RadioGroup radioGroupScreen = new RadioGroup(this);
        radioGroupScreen.setOrientation(RadioGroup.VERTICAL);

        int curScreenSize = prefs.getVideoScreenSize();
        for (int i = 0; i < aspectRatios.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(aspectRatios[i]);
            rb.setTextColor(getColor(R.color.text_primary));
            int rbId = 1000 + i;
            rb.setId(rbId);
            if (i == curScreenSize) rb.setChecked(true);
            radioGroupScreen.addView(rb);
        }

        radioGroupScreen.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId >= 1000) {
                int sizeIndex = checkedId - 1000;
                if (sizeIndex >= 0 && sizeIndex < aspectRatios.length) {
                    prefs.setVideoScreenSize(sizeIndex);
                    applySavedPlayerSettings();
                }
            }
        });

        layout.addView(radioGroupScreen);

        scrollView.addView(layout);
        return scrollView;
    }

    private TextView createSectionHeader(String title) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(getColor(R.color.gold_primary));
        header.setTextSize(13);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(0, 10, 0, 6);
        return header;
    }

    private void addDivider(LinearLayout layout) {
        View divider = new View(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(0, 12, 0, 12);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(getColor(R.color.card_bg_stroke));
        layout.addView(divider);
    }

    private View createTvSettingsTabContent() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 10, 10, 10);

        // 1. Boot Player Mode
        layout.addView(createSectionHeader("Boot Player Settings"));
        
        LinearLayout cardBoot = new LinearLayout(this);
        cardBoot.setOrientation(LinearLayout.HORIZONTAL);
        cardBoot.setGravity(Gravity.CENTER_VERTICAL);
        cardBoot.setPadding(16, 12, 16, 12);
        cardBoot.setBackgroundResource(R.drawable.bg_card_normal);
        cardBoot.setFocusable(true);

        TextView txtBootLabel = new TextView(this);
        txtBootLabel.setText("Boot Directly Into Player Mode");
        txtBootLabel.setTextColor(getColor(R.color.text_primary));
        txtBootLabel.setTextSize(14f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        txtBootLabel.setLayoutParams(labelParams);

        SwitchCompat switchBoot = new SwitchCompat(this);
        switchBoot.setChecked(prefs.isBootPlayerEnabled());
        switchBoot.setFocusable(false);
        switchBoot.setClickable(false);

        cardBoot.addView(txtBootLabel);
        cardBoot.addView(switchBoot);

        cardBoot.setOnClickListener(v -> {
            boolean newState = !prefs.isBootPlayerEnabled();
            prefs.setBootPlayerEnabled(newState);
            switchBoot.setChecked(newState);
            Toast.makeText(this, "Boot Player Mode " + (newState ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
        });
        UIUtils.applyFocusAnimation(cardBoot, 1.02f, 4f);

        layout.addView(cardBoot);

        addDivider(layout);

        // 2. Data Sync Settings
        layout.addView(createSectionHeader("Sync & Background Data"));
        
        LinearLayout cardSync = new LinearLayout(this);
        cardSync.setOrientation(LinearLayout.HORIZONTAL);
        cardSync.setGravity(Gravity.CENTER_VERTICAL);
        cardSync.setPadding(16, 12, 16, 12);
        cardSync.setBackgroundResource(R.drawable.bg_card_normal);
        cardSync.setFocusable(true);

        TextView txtSyncLabel = new TextView(this);
        txtSyncLabel.setText("Real-Time Channel & EPG Polling");
        txtSyncLabel.setTextColor(getColor(R.color.text_primary));
        txtSyncLabel.setTextSize(14f);
        LinearLayout.LayoutParams syncLabelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        txtSyncLabel.setLayoutParams(syncLabelParams);

        SwitchCompat switchSync = new SwitchCompat(this);
        switchSync.setChecked(prefs.isAutoSyncEnabled());
        switchSync.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.setAutoSyncEnabled(isChecked);
        });

        cardSync.addView(txtSyncLabel);
        cardSync.addView(switchSync);

        cardSync.setOnClickListener(v -> {
            switchSync.toggle();
            Toast.makeText(this, "Auto Sync " + (switchSync.isChecked() ? "ENABLED" : "DISABLED"), Toast.LENGTH_SHORT).show();
        });
        UIUtils.applyFocusAnimation(cardSync, 1.02f, 4f);

        layout.addView(cardSync);

        scrollView.addView(layout);
        return scrollView;
    }

    private View createAccountsTabContent(Button tabButton) {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(10, 10, 10, 10);

        String session = prefs.getSessionToken();
        if (!session.isEmpty()) {
            layout.addView(createSectionHeader("Active Account Overview"));

            LinearLayout infoCard = new LinearLayout(this);
            infoCard.setOrientation(LinearLayout.VERTICAL);
            infoCard.setBackgroundResource(R.drawable.bg_card_normal);
            infoCard.setPadding(24, 24, 24, 24);

            TextView txtUsername = new TextView(this);
            txtUsername.setText("User: " + prefs.getUsername());
            txtUsername.setTextColor(getColor(R.color.gold_primary));
            txtUsername.setTextSize(15);
            txtUsername.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView txtPackage = new TextView(this);
            txtPackage.setText("Package: " + prefs.getUserPackage());
            txtPackage.setTextColor(getColor(R.color.text_primary));
            txtPackage.setTextSize(14);
            txtPackage.setPadding(0, 8, 0, 4);

            TextView txtExpiry = new TextView(this);
            txtExpiry.setText("Expiration: " + prefs.getUserExpiry());
            txtExpiry.setTextColor(getColor(R.color.text_secondary));
            txtExpiry.setTextSize(13);

            infoCard.addView(txtUsername);
            infoCard.addView(txtPackage);
            infoCard.addView(txtExpiry);

            layout.addView(infoCard);

            addDivider(layout);

            Button btnLogout = new Button(this);
            btnLogout.setText("Logout Account");
            btnLogout.setBackgroundResource(R.drawable.selector_pill_focus);
            btnLogout.setTextColor(getColorStateList(R.color.selector_pill_text));
            btnLogout.setFocusable(true);
            btnLogout.setClickable(true);
            UIUtils.applyFocusAnimation(btnLogout, 1.04f, 6f);
            btnLogout.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (tabButton != null) {
                        tabButton.requestFocus();
                        return true;
                    }
                }
                return false;
            });
            btnLogout.setOnClickListener(v -> {
                prefs.logout();
                ApiClient.getInstance(this).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {}
                    @Override
                    public void onError(String errorMessage) {}
                });
                showPlayerSettingsDialog();
            });

            layout.addView(btnLogout);
        } else {
            layout.addView(createSectionHeader("Quick Subscriber Login"));

            EditText edtU = new EditText(this);
            edtU.setHint("Username");
            edtU.setTextColor(getColor(R.color.white));
            edtU.setHintTextColor(getColor(R.color.text_secondary));
            edtU.setBackgroundResource(R.drawable.selector_card_focus);
            edtU.setPadding(20, 20, 20, 20);
            edtU.setFocusable(true);
            edtU.setFocusableInTouchMode(false);
            edtU.setCursorVisible(false);

            EditText edtP = new EditText(this);
            edtP.setHint("Password");
            edtP.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtP.setTextColor(getColor(R.color.white));
            edtP.setHintTextColor(getColor(R.color.text_secondary));
            edtP.setBackgroundResource(R.drawable.selector_card_focus);
            edtP.setPadding(20, 20, 20, 20);
            edtP.setFocusable(true);
            edtP.setFocusableInTouchMode(false);
            edtP.setCursorVisible(false);

            Button btnLogin = new Button(this);
            btnLogin.setText("Sign In");
            btnLogin.setBackgroundResource(R.drawable.selector_pill_focus);
            btnLogin.setTextColor(getColorStateList(R.color.selector_pill_text));
            btnLogin.setFocusable(true);
            btnLogin.setClickable(true);
            UIUtils.applyFocusAnimation(btnLogin, 1.04f, 6f);

            edtU.setOnFocusChangeListener((v, hasFocus) -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (!hasFocus) {
                    edtU.setCursorVisible(false);
                    edtU.setFocusableInTouchMode(false);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtU.getWindowToken(), 0);
                    }
                }
            });

            edtU.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        edtU.setFocusableInTouchMode(true);
                        edtU.setCursorVisible(true);
                        edtU.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(edtU, InputMethodManager.SHOW_IMPLICIT);
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        edtP.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        if (tabButton != null) {
                            tabButton.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
            edtU.setOnClickListener(v -> {
                edtU.setFocusableInTouchMode(true);
                edtU.setCursorVisible(true);
                edtU.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtU, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            edtP.setOnFocusChangeListener((v, hasFocus) -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (!hasFocus) {
                    edtP.setCursorVisible(false);
                    edtP.setFocusableInTouchMode(false);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtP.getWindowToken(), 0);
                    }
                }
            });

            edtP.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        edtP.setFocusableInTouchMode(true);
                        edtP.setCursorVisible(true);
                        edtP.requestFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.showSoftInput(edtP, InputMethodManager.SHOW_IMPLICIT);
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        edtU.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        btnLogin.requestFocus();
                        return true;
                    }
                }
                return false;
            });
            edtP.setOnClickListener(v -> {
                edtP.setFocusableInTouchMode(true);
                edtP.setCursorVisible(true);
                edtP.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(edtP, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            btnLogin.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    edtP.requestFocus();
                    return true;
                }
                return false;
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 8, 0, 8);

            layout.addView(edtU, lp);
            layout.addView(edtP, lp);
            layout.addView(btnLogin, lp);

            btnLogin.setOnClickListener(v -> {
                String u = edtU.getText().toString().trim();
                String p = edtP.getText().toString().trim();
                ApiClient.getInstance(this).login(u, p, new ApiClient.ApiCallback<UserInfo>() {
                    @Override
                    public void onSuccess(UserInfo result) {
                        showPlayerSettingsDialog();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        new CustomDialog.Builder(PlayerActivity.this)
                                .setTitle("Login Error")
                                .setMessage(errorMessage)
                                .setPositiveButton("OK", d -> d.dismiss())
                                .show();
                    }
                });
            });
        }

        scrollView.addView(layout);
        return scrollView;
    }

    private boolean handlePlayerBackPressInternal() {
        boolean isDrawerVisible = drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE;
        boolean isCardVisible = cardChannelOverlay != null && cardChannelOverlay.getVisibility() == View.VISIBLE;

        if (isDrawerVisible) {
            if (edtPlayerSearch != null && (edtPlayerSearch.isCursorVisible() || !edtPlayerSearch.getText().toString().isEmpty())) {
                edtPlayerSearch.setText("");
                edtPlayerSearch.setCursorVisible(false);
                edtPlayerSearch.setFocusableInTouchMode(false);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                }
                focusPlayingOrFirstChannel();
                return true;
            }
            hideOverlays();
            return true;
        }

        if (isCardVisible) {
            hideOverlays();
            return true;
        }

        boolean isBootPlayer = prefs.isBootPlayerEnabled();
        if (isBootPlayer || isTaskRoot()) {
            if (isFinishing() || isDestroyed()) return true;
            if (currentPlayerExitDialog != null && currentPlayerExitDialog.isShowing()) return true;

            View customPlayerExitView = getLayoutInflater().inflate(R.layout.layout_exit_dialog_player, null);

            currentPlayerExitDialog = new CustomDialog.Builder(this)
                    .setTitle(getString(R.string.exit_player_title))
                    .setTitleTextColor(0xFFFF4B4B)
                    .setIcon(R.drawable.ic_play)
                    .setMessage(getString(R.string.exit_player_message))
                    .setView(customPlayerExitView)
                    .setWidthPercent(0.50f)
                    .setBackgroundDrawable(R.drawable.bg_exit_dialog_player)
                    .setPositiveButtonDrawable(R.drawable.btn_exit_positive)
                    .setPositiveButton("Exit App", dialog -> {
                        if (dialog != null) {
                            try {
                                dialog.dismiss();
                            } catch (Exception ignored) {}
                        }
                        currentPlayerExitDialog = null;
                        finishAffinity();
                    })
                    .setNeutralButton("Dashboard", dialog -> {
                        if (dialog != null) {
                            try {
                                dialog.dismiss();
                            } catch (Exception ignored) {}
                        }
                        currentPlayerExitDialog = null;
                        Intent intent = new Intent(PlayerActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(getString(R.string.btn_keep_watching), dialog -> {
                        if (dialog != null) {
                            try {
                                dialog.dismiss();
                            } catch (Exception ignored) {}
                        }
                        currentPlayerExitDialog = null;
                    })
                    .show();
            return true;
        }
        return false;
    }

    private void handleBackPress() {
        FocusManager.getInstance().handleBackPress(this, SCREEN_KEY, this::handlePlayerBackPressInternal, null);
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isPlayerResumed = true;
        UIUtils.hideSystemUI(this);

        // VPN & Proxy Security Guard
        if (com.ottking.devcode.security.VpnDetectionManager.isVpnOrProxyActive(this)) {
            if (player != null) {
                player.stop();
            }
            com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(this, () -> playStream(currentStreamUrl));
            return;
        }

        // Register Real-time VPN Monitoring
        com.ottking.devcode.security.VpnDetectionManager.getInstance().startMonitoring(this, new com.ottking.devcode.security.VpnDetectionManager.VpnStateListener() {
            @Override
            public void onVpnDetected() {
                runOnUiThread(() -> {
                    if (player != null) {
                        player.stop();
                    }
                    com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(PlayerActivity.this, () -> playStream(currentStreamUrl));
                });
            }

            @Override
            public void onVpnDisconnected() {
                runOnUiThread(() -> {
                    com.ottking.devcode.security.VpnDetectionManager.getInstance().dismissDialog();
                    if (player != null && currentStreamUrl != null && !currentStreamUrl.isEmpty()) {
                        playStream(currentStreamUrl);
                    }
                });
            }
        });

        applySavedPlayerSettings();
        if (player != null && !player.isPlaying()) {
            player.play();
        }
        startStreamAutoPolling();
        DataPollingManager.getInstance(this).startPolling();
        DataPollingManager.getInstance(this).triggerSyncNow();
        if (currentChannelId > 0 && currentStreamUrl != null && !currentStreamUrl.isEmpty()) {
            com.ottking.devcode.network.GlobalCookieManager.getInstance(this).startPeriodicRefresh(currentChannelId, currentStreamUrl);
        }

        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            if (isAwaitingVoiceResult) {
                restoreVoiceSearchReturnFocus();
            } else if (recyclerPlayerChannels != null) {
                recyclerPlayerChannels.requestFocus();
            }
        } else if (playerView != null) {
            playerView.requestFocus();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            UIUtils.hideSystemUI(this);
            if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
                if (isAwaitingVoiceResult) {
                    restoreVoiceSearchReturnFocus();
                } else if (recyclerPlayerChannels != null) {
                    recyclerPlayerChannels.requestFocus();
                }
            } else if (playerView != null) {
                playerView.requestFocus();
            }
        }
    }

    private void restoreVoiceSearchReturnFocus() {
        if (drawerChannelList == null || drawerChannelList.getVisibility() != View.VISIBLE) {
            return;
        }
        resetDrawerAutoHideTimer();
        runOnUiThread(() -> {
            drawerChannelList.post(() -> {
                if (btnPlayerVoiceSearch != null) {
                    btnPlayerVoiceSearch.requestFocus();
                } else if (edtPlayerSearch != null) {
                    edtPlayerSearch.requestFocus();
                } else if (recyclerPlayerChannels != null) {
                    recyclerPlayerChannels.requestFocus();
                }
            });
        });
    }

    private void retryPlayback(String reason) {
        if (isFinishing() || isDestroyed()) return;
        retryHandler.removeCallbacksAndMessages(null);

        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            long delay = Math.min(300L * retryCount, 1500L);
            retryHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                playStream(currentStreamUrl);
            }, delay);
        } else {
            retryCount = 0;
            retryHandler.postDelayed(() -> playStream(currentStreamUrl), 2000);
        }
    }

    private void registerNetworkCallback() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        runOnUiThread(() -> {
                            if (player != null) {
                                int state = player.getPlaybackState();
                                if (state == Player.STATE_IDLE || state == Player.STATE_BUFFERING || player.getPlayerError() != null || !player.isPlaying()) {
                                    retryCount = 0;
                                    retryPlayback("Network connected, resuming stream...");
                                }
                            }
                        });
                    }
                };
                cm.registerDefaultNetworkCallback(networkCallback);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void unregisterNetworkCallback() {
        try {
            if (networkCallback != null) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(networkCallback);
                }
                networkCallback = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isPlayerResumed = false;
        uiOverlayHandler.removeCallbacks(autoHideDrawerRunnable);
        stopStreamAutoPolling();
        DataPollingManager.getInstance(this).stopPolling();
        stopCookieRefreshTimer();
        com.ottking.devcode.network.GlobalCookieManager.getInstance(this).stopPeriodicRefresh();
        com.ottking.devcode.security.VpnDetectionManager.getInstance().stopMonitoring(this);
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        com.ottking.devcode.security.VpnDetectionManager.getInstance().stopMonitoring(this);
        com.ottking.devcode.security.VpnDetectionManager.getInstance().dismissDialog();
        com.ottking.devcode.network.GlobalCookieManager.getInstance(this).stopPeriodicRefresh();
        if (currentPlayerExitDialog != null) {
            try {
                if (currentPlayerExitDialog.isShowing()) {
                    currentPlayerExitDialog.dismiss();
                }
            } catch (Exception ignored) {}
            currentPlayerExitDialog = null;
        }
        super.onDestroy();
        isPlayerResumed = false;
        uiOverlayHandler.removeCallbacksAndMessages(null);
        stopStreamAutoPolling();
        DataPollingManager.getInstance(this).stopPolling();
        stopCookieRefreshTimer();
        if (cookieRefreshHandler != null) {
            cookieRefreshHandler.removeCallbacksAndMessages(null);
        }
        retryHandler.removeCallbacksAndMessages(null);
        if (bufferingWatchdogHandler != null) {
            bufferingWatchdogHandler.removeCallbacksAndMessages(null);
        }
        if (channelNumHandler != null) {
            channelNumHandler.removeCallbacksAndMessages(null);
        }
        if (channelSwitchDebounceHandler != null) {
            channelSwitchDebounceHandler.removeCallbacksAndMessages(null);
        }
        unregisterNetworkCallback();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    // Auto-polling watchdog runnable
    private final Runnable streamAutoPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPlayerResumed || isFinishing() || isDestroyed()) {
                return;
            }

            checkAndRecoverStreamIfFrozen();

            streamAutoPollHandler.postDelayed(this, AUTO_POLL_INTERVAL_MS);
        }
    };

    /**
     * Proactively monitors stream health, forward segment buffer depth, and playback progression.
     * Keeps segments pre-loaded in advance and initiates proactive data recovery before the player
     * drops into a hard buffering state.
     */
    private void checkAndRecoverStreamIfFrozen() {
        if (player == null || currentStreamUrl == null || currentStreamUrl.trim().isEmpty()) {
            return;
        }

        if (isFinishing() || isDestroyed()) return;

        int state = player.getPlaybackState();
        long now = System.currentTimeMillis();

        // 1. Live stream unexpectedly stopped (IDLE or ENDED)
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            if (player != null && player.getPlayerError() != null) {
                // An active player error is already being handled by onPlayerError
                return;
            }
            android.util.Log.w("PlayerWatchdog", "Live stream stopped (State=" + state + "). Auto-recovering stream...");
            positionStallStartTime = 0;
            continuousBufferStartTime = 0;
            retryPlayback("Stream stopped, auto-recovering...");
            return;
        }

        // 2. Stream buffering stalled - allow safe natural loading without false double-buffer interruption
        if (state == Player.STATE_BUFFERING) {
            dismissBufferingView();
            positionStallStartTime = 0;
            lastObservedPosition = -1;
            if (continuousBufferStartTime == 0) {
                continuousBufferStartTime = now;
                // Proactively trigger token & edge-cookie renewal in background immediately
                com.ottking.devcode.network.GlobalCookieManager.getInstance(this).getValidatedCookie(currentChannelId, currentStreamUrl);
            } else {
                long bufferStallDuration = now - continuousBufferStartTime;

                if (bufferStallDuration >= MAX_ALLOWED_BUFFER_MS) {
                    android.util.Log.w("PlayerWatchdog", "Buffering timeout (" + bufferStallDuration + "ms). Executing stream data recovery...");
                    continuousBufferStartTime = 0;
                    consecutiveStallRecoveries++;
                    if (consecutiveStallRecoveries <= 2) {
                        try {
                            if (player.isCurrentMediaItemLive()) {
                                player.seekToDefaultPosition();
                            }
                            player.prepare();
                            player.setPlayWhenReady(true);
                            player.play();
                        } catch (Exception e) {
                            playStream(currentStreamUrl);
                        }
                    } else {
                        consecutiveStallRecoveries = 0;
                        retryPlayback("Buffering recovery, refreshing stream...");
                    }
                    return;
                }
            }
        } else {
            continuousBufferStartTime = 0;
        }

        // 3. Player is in STATE_READY -> verify stream health & permanently dismiss buffering indicators
        if (state == Player.STATE_READY) {
            if (!player.getPlayWhenReady()) {
                player.setPlayWhenReady(true);
            }

            // Immediately dismiss any buffering spinner once stream is ready
            dismissBufferingView();

            // When player is actively rendering (isPlaying() is true)
            if (player.isPlaying()) {
                continuousBufferStartTime = 0;
                positionStallStartTime = 0;
                lastFrameRenderTimeMs = now;
                consecutiveStallRecoveries = 0;
                return;
            }

            // If player is in STATE_READY and playWhenReady is true, BUT isPlaying() is false
            // (e.g. audio sink block, decoder freeze or paused pipeline)
            player.play();
            if (positionStallStartTime == 0) {
                positionStallStartTime = now;
            } else if (now - positionStallStartTime >= 3500) {
                positionStallStartTime = 0;
                android.util.Log.w("PlayerWatchdog", "Player in STATE_READY but not playing for 3.5s. Auto-refreshing stream...");
                try {
                    player.prepare();
                    player.setPlayWhenReady(true);
                    player.play();
                } catch (Exception e) {
                    playStream(currentStreamUrl);
                }
            }
        }
    }

    private void startStreamAutoPolling() {
        streamAutoPollHandler.removeCallbacks(streamAutoPollRunnable);
        streamAutoPollHandler.postDelayed(streamAutoPollRunnable, AUTO_POLL_INTERVAL_MS);
    }

    private void stopStreamAutoPolling() {
        streamAutoPollHandler.removeCallbacks(streamAutoPollRunnable);
    }

    private void startCookieRefreshTimer() {
        stopCookieRefreshTimer();
        cookieRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed() && player != null && isPlayerResumed) {
                    refreshActiveCookie();
                }
                cookieRefreshHandler.postDelayed(this, COOKIE_REFRESH_INTERVAL_MS);
            }
        };
        cookieRefreshHandler.postDelayed(cookieRefreshRunnable, COOKIE_REFRESH_INTERVAL_MS);
    }

    private void stopCookieRefreshTimer() {
        if (cookieRefreshRunnable != null) {
            cookieRefreshHandler.removeCallbacks(cookieRefreshRunnable);
        }
    }

    private void refreshActiveCookie() {
        if (currentChannelId <= 0 && (currentStreamUrl == null || currentStreamUrl.isEmpty())) {
            return;
        }
        ApiClient.getInstance(this).refreshEdgeCookie(currentChannelId, currentStreamUrl, new ApiClient.ApiCallback<String>() {
            @Override
            public void onSuccess(String newCookie) {
                if (newCookie != null && !newCookie.isEmpty()) {
                    staticEdgeCookie = newCookie;
                    if (prefs != null) {
                        prefs.setEdgeCookie(newCookie);
                    }
                    android.util.Log.d("PlayerActivity", "Edge-Cookie auto-refreshed successfully");
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Keep playing seamlessly with existing token without flooding API
                android.util.Log.d("PlayerActivity", "Edge-Cookie refresh deferred: " + errorMessage);
            }
        });
    }
}
