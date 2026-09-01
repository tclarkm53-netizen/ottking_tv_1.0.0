package com.ottking.devcode.ui;

import android.Manifest;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;
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
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.upstream.DefaultAllocator;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
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
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.security.SecurityUtils;
import com.ottking.devcode.utils.UIUtils;

public class PlayerActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "PlayerActivity";

    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private ImageView imgPlayerChannelLogo;
    private TextView txtPlayerChannelName, txtPlayerChannelNumber;
    private RecyclerView recyclerPlayerChannels;
    private ChannelAdapter channelAdapter;
    private AppPreferences prefs;

    private View cardChannelOverlay;
    private View drawerChannelList;
    private List<ChannelEntity> allChannelsList = new ArrayList<>();

    private final Handler uiOverlayHandler = new Handler(Looper.getMainLooper());
    private static final long DRAWER_AUTO_HIDE_DELAY_MS = 6000L; // Auto-hide channel list after 6s of inactivity
    private final Runnable autoHideOverlayRunnable = () -> {
        if (cardChannelOverlay != null && drawerChannelList != null) {
            if (drawerChannelList.getVisibility() != View.VISIBLE) {
                cardChannelOverlay.setVisibility(View.GONE);
            }
        }
    };
    private final Runnable autoHideDrawerRunnable = this::onDrawerAutoHideTriggered;

    private int currentChannelId = 1;
    private int currentChannelNumber = 1;
    private boolean currentIsPremium = false;
    private String currentStreamUrl = "";
    private String currentChannelName = "Select Channel";
    private String currentLogoUrl = "";

    private final Handler retryHandler = new Handler(Looper.getMainLooper());
    private int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 15;

    private final Handler bufferingWatchdogHandler = new Handler(Looper.getMainLooper());
    private Runnable bufferingWatchdogRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    private View cardChannelNumOverlay;
    private TextView txtChannelNumInput;
    private final StringBuilder channelNumBuffer = new StringBuilder();
    private final Handler channelNumHandler = new Handler(Looper.getMainLooper());
    private final Runnable tuneChannelNumRunnable = this::commitChannelNumberInput;

    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private com.ottking.devcode.utils.VoiceSearchHelper voiceSearchHelper;
    private EditText edtPlayerSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_player);

        prefs = AppPreferences.getInstance(this);

        voiceSearchHelper = new com.ottking.devcode.utils.VoiceSearchHelper(this, new com.ottking.devcode.utils.VoiceSearchHelper.VoiceSearchCallback() {
            @Override
            public void onResult(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    String recognizedText = query.trim();
                    if (edtPlayerSearch != null) {
                        edtPlayerSearch.setText(recognizedText);
                        try {
                            edtPlayerSearch.setSelection(recognizedText.length());
                        } catch (Exception ignored) {}
                    }
                    filterPlayerChannels(recognizedText);
                }
            }

            @Override
            public void onCancelled() {
                if (edtPlayerSearch != null) {
                    edtPlayerSearch.requestFocus();
                }
            }
        });

        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                            if (matches != null && !matches.isEmpty()) {
                                String recognizedText = matches.get(0);
                                if (recognizedText != null && !recognizedText.trim().isEmpty()) {
                                    if (edtPlayerSearch != null) {
                                        edtPlayerSearch.setText(recognizedText.trim());
                                        try {
                                            edtPlayerSearch.setSelection(recognizedText.trim().length());
                                        } catch (Exception ignored) {}
                                    }
                                    filterPlayerChannels(recognizedText.trim());
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startVoiceSearch();
                    } else {
                        Toast.makeText(this, R.string.toast_mic_permission_required, Toast.LENGTH_SHORT).show();
                    }
                });

        voiceSearchHelper.setLaunchers(voiceSearchLauncher, requestPermissionLauncher);

        playerView = findViewById(R.id.playerView);
        playerView.setUseController(false);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
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
        }

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
                if (newFocus == null || !isViewInsideDrawer(newFocus)) {
                    getWindow().getDecorView().post(() -> {
                        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
                            View current = getCurrentFocus();
                            if (current == null || !isViewInsideDrawer(current)) {
                                focusPlayingOrFirstChannel();
                            }
                        }
                    });
                }
            }
        });
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
    }

    private void resetDrawerAutoHideTimer() {
        uiOverlayHandler.removeCallbacks(autoHideDrawerRunnable);
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            uiOverlayHandler.postDelayed(autoHideDrawerRunnable, DRAWER_AUTO_HIDE_DELAY_MS);
        }
    }

    private void onDrawerAutoHideTriggered() {
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            if (edtPlayerSearch != null && edtPlayerSearch.isCursorVisible()) {
                resetDrawerAutoHideTimer();
                return;
            }
            hideOverlays();
        }
    }

    private void showChannelDrawer() {
        uiOverlayHandler.removeCallbacks(autoHideOverlayRunnable);
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
                currentStreamUrl = targetChannel.streamUrl;
                currentChannelName = targetChannel.name;
                currentLogoUrl = targetChannel.logoUrl;
                currentIsPremium = targetChannel.isPremium;
                prefs.setLastPlayedChannelId(currentChannelId);

                updateChannelInfoUI();
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
        currentStreamUrl = targetChannel.streamUrl;
        currentChannelName = targetChannel.name;
        currentLogoUrl = targetChannel.logoUrl;
        currentIsPremium = targetChannel.isPremium;
        prefs.setLastPlayedChannelId(currentChannelId);

        updateChannelInfoUI();
        playStream(currentStreamUrl);
        showCardOverlayTemporarily(4000);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            resetDrawerAutoHideTimer();
        }
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

        if (currentLogoUrl != null && !currentLogoUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentLogoUrl)
                    .placeholder(R.drawable.img_splash_bg)
                    .error(R.drawable.img_splash_bg)
                    .into(imgPlayerChannelLogo);
        }
    }

    private static volatile String currentActiveStreamToken = "";
    private static volatile String staticDeviceId = "";
    private static volatile String staticSessionToken = "";
    private static OkHttpClient sharedOkHttpClient;
    private static DefaultBandwidthMeter sharedBandwidthMeter;
    private DataSource.Factory dataSourceFactory;

    private static synchronized DefaultBandwidthMeter getBandwidthMeter(android.content.Context context) {
        if (sharedBandwidthMeter == null) {
            sharedBandwidthMeter = new DefaultBandwidthMeter.Builder(context.getApplicationContext())
                    .setInitialBitrateEstimate(800_000) // 800 kbps: picks light fast initial chunk instantly on mobile/weak network
                    .build();
        }
        return sharedBandwidthMeter;
    }

    private static synchronized OkHttpClient getOkHttpClient() {
        if (sharedOkHttpClient == null) {
            sharedOkHttpClient = new OkHttpClient.Builder()
                    .connectionPool(new ConnectionPool(64, 5, TimeUnit.MINUTES))
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .writeTimeout(3, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                    .addInterceptor(chain -> {
                        okhttp3.Request original = chain.request();
                        okhttp3.Request.Builder builder = original.newBuilder();

                        // 1. Mandatory X-App-Client verification header
                        builder.header(SecurityUtils.HEADER_APP_CLIENT, SecurityUtils.getXAppClientToken());

                        // 2. Dynamic X-Stream-Token header
                        String streamToken = currentActiveStreamToken;
                        if (streamToken != null && !streamToken.isEmpty()) {
                            builder.header(SecurityUtils.HEADER_STREAM_TOKEN, streamToken);
                            builder.header("Authorization", "Bearer " + streamToken);
                        }

                        // 3. Device & Session identification
                        if (staticDeviceId != null && !staticDeviceId.isEmpty()) {
                            builder.header(SecurityUtils.HEADER_DEVICE_ID, staticDeviceId);
                        }
                        if (staticSessionToken != null && !staticSessionToken.isEmpty()) {
                            builder.header(SecurityUtils.HEADER_SESSION_TOKEN, staticSessionToken);
                        }

                        builder.header("User-Agent", "OTT-KING TV 1.0");
                        return chain.proceed(builder.build());
                    })
                    .build();
        }
        return sharedOkHttpClient;
    }

    private DataSource.Factory getDataSourceFactory(android.content.Context context) {
        if (dataSourceFactory == null) {
            DefaultBandwidthMeter bandwidthMeter = getBandwidthMeter(context);
            OkHttpDataSource.Factory okHttpDataSourceFactory = new OkHttpDataSource.Factory(getOkHttpClient())
                    .setUserAgent("OTT-KING TV 1.0")
                    .setTransferListener(bandwidthMeter);
            dataSourceFactory = new DefaultDataSource.Factory(context, okHttpDataSourceFactory)
                    .setTransferListener(bandwidthMeter);
        }
        return dataSourceFactory;
    }

    private void initExoPlayer() {
        if (player == null) {
            android.content.Context playerContext = this;

            DefaultBandwidthMeter bandwidthMeter = getBandwidthMeter(playerContext);
            AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory(
                    500,  // minDurationForQualityIncreaseMs (responds rapidly when bandwidth allows)
                    1200, // maxDurationForQualityDecreaseMs (drops bitrate quickly on packet drop to prevent buffering stall)
                    500,  // minDurationToRetainAfterDiscardMs
                    0.70f // bandwidthFraction (safe threshold to prevent buffer underrun on weak network)
            );
            trackSelector = new DefaultTrackSelector(playerContext, trackSelectionFactory);

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(playerContext);
            boolean hwEnabled = prefs.isHardwareAccelerationEnabled();
            if (hwEnabled) {
                renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
                renderersFactory.setEnableDecoderFallback(true);
            } else {
                renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
                renderersFactory.setEnableDecoderFallback(true);
            }
            renderersFactory.setAllowedVideoJoiningTimeMs(0); // Instant video track rendering without black frame delay

            String buf = prefs.getBufferSettings();
            int minBufferMs = 1200;
            int maxBufferMs = 3500;
            int bufferForPlaybackMs = 25; // Instant ultra-fast startup (< 25ms threshold - plays immediately)
            int bufferForPlaybackAfterRebufferMs = 100; // Ultra-fast rebuffer resume (< 100ms)

            if (buf.contains("1.5 sec") || buf.contains("Fast Start")) {
                minBufferMs = 2000;
                maxBufferMs = 6000;
                bufferForPlaybackMs = 100;
                bufferForPlaybackAfterRebufferMs = 250;
            } else if (buf.contains("3 sec") || buf.contains("Standard")) {
                minBufferMs = 3500;
                maxBufferMs = 10000;
                bufferForPlaybackMs = 250;
                bufferForPlaybackAfterRebufferMs = 500;
            } else if (buf.contains("5 sec") || buf.contains("Smooth")) {
                minBufferMs = 5000;
                maxBufferMs = 15000;
                bufferForPlaybackMs = 500;
                bufferForPlaybackAfterRebufferMs = 1000;
            }

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setAllocator(new DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                    .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBackBuffer(0, false)
                    .build();

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

            player.setSeekParameters(SeekParameters.CLOSEST_SYNC);
            player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build();
            player.setAudioAttributes(audioAttributes, true);
            playerView.setPlayer(player);
            playerView.setKeepScreenOn(true);

            bufferingWatchdogRunnable = () -> {
                if (player != null && (player.getPlaybackState() == Player.STATE_BUFFERING || player.getPlaybackState() == Player.STATE_IDLE || player.getPlayerError() != null)) {
                    retryPlayback("Stream connection stalled, auto reconnecting...");
                }
            };

            player.addListener(new Player.Listener() {
                @Override
                public void onTracksChanged(Tracks tracks) {
                    applySavedPlayerSettings();
                }

                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    bufferingWatchdogHandler.removeCallbacks(bufferingWatchdogRunnable);
                    if (playbackState == Player.STATE_BUFFERING) {
                        bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable, 8000);
                    } else if (playbackState == Player.STATE_READY) {
                        retryCount = 0;
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
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setExceedRendererCapabilitiesIfNecessary(true);

            if (res.contains("1080")) {
                builder.setMaxVideoSize(1920, 1080);
            } else if (res.contains("720")) {
                builder.setMaxVideoSize(1280, 720);
            } else if (res.contains("480")) {
                builder.setMaxVideoSize(854, 480);
            } else if (res.contains("360")) {
                builder.setMaxVideoSize(640, 360);
            } else {
                builder.clearVideoSizeConstraints();
            }
            trackSelector.setParameters(builder);
        }
    }

    private MediaSource buildMediaSource(String url) {
        android.net.Uri uri = android.net.Uri.parse(url);
        DataSource.Factory dsFactory = getDataSourceFactory(this);

        boolean isHls = url.endsWith(".m3u8") || url.contains(".m3u8") || url.contains("m3u8") || url.contains("/hls");
        boolean isDash = url.endsWith(".mpd") || url.contains(".mpd") || url.contains("mpd") || url.contains("/dash");

        if (isHls) {
            // Dedicated HLS Protocol Pipeline (Zero conflict with DASH)
            MediaItem.LiveConfiguration hlsLiveConfig = new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(1000)
                    .setMinOffsetMs(300)
                    .setMaxOffsetMs(3000)
                    .setMaxPlaybackSpeed(1.02f)
                    .setMinPlaybackSpeed(0.98f)
                    .build();

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(hlsLiveConfig)
                    .build();

            boolean chunkless = prefs.isHlsChunklessEnabled();
            DefaultHlsExtractorFactory hlsExtractorFactory = new DefaultHlsExtractorFactory(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                            | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                            | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM,
                    true
            );

            return new HlsMediaSource.Factory(dsFactory)
                    .setAllowChunklessPreparation(chunkless)
                    .setExtractorFactory(hlsExtractorFactory)
                    .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(1))
                    .createMediaSource(mediaItem);

        } else if (isDash) {
            // Dedicated DASH MPD Protocol Pipeline (Zero conflict with HLS)
            MediaItem.LiveConfiguration dashLiveConfig = new MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(1800)
                    .setMinOffsetMs(800)
                    .setMaxOffsetMs(4000)
                    .setMaxPlaybackSpeed(1.03f)
                    .setMinPlaybackSpeed(0.97f)
                    .build();

            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setLiveConfiguration(dashLiveConfig)
                    .build();

            androidx.media3.exoplayer.dash.DefaultDashChunkSource.Factory dashChunkSourceFactory =
                    new androidx.media3.exoplayer.dash.DefaultDashChunkSource.Factory(dsFactory);

            return new DashMediaSource.Factory(dashChunkSourceFactory, dsFactory)
                    .createMediaSource(mediaItem);

        } else {
            // Dedicated Progressive / Direct Stream Pipeline
            MediaItem mediaItem = new MediaItem.Builder()
                    .setUri(uri)
                    .build();

            DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true)
                    .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS);

            return new ProgressiveMediaSource.Factory(dsFactory, extractorsFactory)
                    .createMediaSource(mediaItem);
        }
    }

    private void playStream(String url) {
        if (player == null || url == null || url.trim().isEmpty()) return;

        applySavedPlayerSettings();

        // 1. Instantly generate verified cryptographic stream token
        staticDeviceId = (prefs != null) ? prefs.getDeviceId() : "";
        staticSessionToken = (prefs != null) ? prefs.getSessionToken() : "";
        StreamTokenAuth localToken = ApiClient.getInstance(this).generateLocalStreamToken(currentChannelId, url);
        currentActiveStreamToken = localToken.getStreamToken();

        // 2. Load stream media source with token authorization
        MediaSource mediaSource = buildMediaSource(url.trim());
        player.setMediaSource(mediaSource, true);
        player.prepare();
        player.setPlayWhenReady(true);
        player.play();

        // 3. Asynchronously request/verify stream token from backend endpoint
        ApiClient.getInstance(this).fetchStreamToken(currentChannelId, url, new ApiClient.ApiCallback<StreamTokenAuth>() {
            @Override
            public void onSuccess(StreamTokenAuth result) {
                if (result != null && result.getStreamToken() != null && !result.getStreamToken().isEmpty()) {
                    currentActiveStreamToken = result.getStreamToken();
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
        recyclerPlayerChannels.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                resetDrawerAutoHideTimer();
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dx != 0 || dy != 0) {
                    resetDrawerAutoHideTimer();
                }
            }
        });
        channelAdapter = new ChannelAdapter(true, channel -> {
            if (channel.id == currentChannelId) {
                // If user clicks the currently playing channel, hide list and show info card
                hideOverlays();
                showCardOverlayTemporarily(4000);
                return;
            }
            currentChannelId = channel.id;
            currentStreamUrl = channel.streamUrl;
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
        ImageButton btnPlayerVoiceSearch = findViewById(R.id.btnPlayerVoiceSearch);
        ImageButton btnPlayerSettings = findViewById(R.id.btnPlayerSettings);
        edtPlayerSearch = findViewById(R.id.edtPlayerSearch);

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
                    }
                }
                return false;
            });

            edtPlayerSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterPlayerChannels(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        // Observe channels from database for channel list drawer
        AppDatabase.getInstance(this).channelDao().getAllChannels().observe(this, channels -> {
            if (channels != null && !channels.isEmpty()) {
                allChannelsList = new ArrayList<>(channels);
                channelAdapter.setAllChannelsList(channels);
                if (edtPlayerSearch != null && !edtPlayerSearch.getText().toString().isEmpty()) {
                    filterPlayerChannels(edtPlayerSearch.getText().toString());
                } else {
                    channelAdapter.setChannels(channels);
                }
                updateChannelInfoUI();
                if (getIntent() == null || !getIntent().hasExtra("stream_url")) {
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
                    currentStreamUrl = targetChannel.streamUrl;
                    currentChannelName = targetChannel.name;
                    currentLogoUrl = targetChannel.logoUrl;
                    currentIsPremium = targetChannel.isPremium;
                    prefs.setLastPlayedChannelId(currentChannelId);

                    updateChannelInfoUI();
                    playStream(currentStreamUrl);
                }
            }
        });
    }

    private void startVoiceSearch() {
        try {
            if (voiceSearchHelper != null) {
                voiceSearchHelper.startVoiceSearch();
            }
        } catch (Throwable t) {
            Toast.makeText(this, R.string.toast_voice_search_not_supported, Toast.LENGTH_SHORT).show();
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

        // 4. Buffer Settings
        layout.addView(createSectionHeader("Playback Buffer Settings"));
        String[] buffers = {"Instant / Zero Lag (< 100ms)", "Fast Start (1.5 sec)", "Standard (3 sec)", "Smooth Playback (5 sec)"};
        Spinner spinnerBuffer = new Spinner(this);
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buffers);
        spinnerBuffer.setAdapter(bufferAdapter);
        int currentBufferIndex = 0;
        String curBuf = prefs.getBufferSettings();
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i].equalsIgnoreCase(curBuf)) { currentBufferIndex = i; break; }
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

        // 5. HLS Protocol Settings
        layout.addView(createSectionHeader("HLS Protocol Settings (.m3u8)"));
        String[] hlsModes = {"Ultra Fast (Chunkless)", "Low-Latency HLS (LL-HLS)", "Standard HLS"};
        Spinner spinnerHls = new Spinner(this);
        ArrayAdapter<String> hlsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, hlsModes);
        spinnerHls.setAdapter(hlsAdapter);
        int currentHlsIndex = 0;
        String curHls = prefs.getHlsSettings();
        for (int i = 0; i < hlsModes.length; i++) {
            if (hlsModes[i].equalsIgnoreCase(curHls)) { currentHlsIndex = i; break; }
        }
        spinnerHls.setSelection(currentHlsIndex);
        spinnerHls.setPadding(0, 10, 0, 10);
        spinnerHls.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getColor(R.color.text_primary));
                }
                String newHls = hlsModes[position];
                if (!newHls.equalsIgnoreCase(prefs.getHlsSettings())) {
                    prefs.setHlsSettings(newHls);
                    reinitPlayer();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(spinnerHls);

        SwitchCompat switchHlsChunkless = new SwitchCompat(this);
        switchHlsChunkless.setText("HLS Chunkless Preparation (Fast Start)");
        switchHlsChunkless.setTextColor(getColor(R.color.text_primary));
        switchHlsChunkless.setChecked(prefs.isHlsChunklessEnabled());
        switchHlsChunkless.setPadding(0, 10, 0, 10);
        switchHlsChunkless.setOnCheckedChangeListener((v, isChecked) -> {
            prefs.setHlsChunklessEnabled(isChecked);
            reinitPlayer();
        });
        layout.addView(switchHlsChunkless);

        addDivider(layout);

        // 6. MPD DASH Protocol Settings
        layout.addView(createSectionHeader("MPD (DASH) Protocol Settings (.mpd)"));
        String[] mpdModes = {"Low Latency (Fast Sync)", "Fast Startup (Adaptive)", "Standard DASH"};
        Spinner spinnerMpd = new Spinner(this);
        ArrayAdapter<String> mpdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mpdModes);
        spinnerMpd.setAdapter(mpdAdapter);
        int currentMpdIndex = 0;
        String curMpd = prefs.getMpdSettings();
        for (int i = 0; i < mpdModes.length; i++) {
            if (mpdModes[i].equalsIgnoreCase(curMpd)) { currentMpdIndex = i; break; }
        }
        spinnerMpd.setSelection(currentMpdIndex);
        spinnerMpd.setPadding(0, 10, 0, 10);
        spinnerMpd.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView) {
                    ((TextView) view).setTextColor(getColor(R.color.text_primary));
                }
                String newMpd = mpdModes[position];
                if (!newMpd.equalsIgnoreCase(prefs.getMpdSettings())) {
                    prefs.setMpdSettings(newMpd);
                    reinitPlayer();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        layout.addView(spinnerMpd);

        addDivider(layout);

        // 7. Video Screen Size Settings
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
            if (edtPlayerSearch != null) {
                if (edtPlayerSearch.isCursorVisible()) {
                    edtPlayerSearch.setCursorVisible(false);
                    edtPlayerSearch.setFocusableInTouchMode(false);
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                }
                if (!edtPlayerSearch.getText().toString().isEmpty()) {
                    edtPlayerSearch.setText("");
                }
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
            View customPlayerExitView = getLayoutInflater().inflate(R.layout.layout_exit_dialog_player, null);

            new CustomDialog.Builder(this)
                    .setTitle(getString(R.string.exit_player_title))
                    .setTitleTextColor(0xFFFF4B4B)
                    .setIcon(R.drawable.ic_play)
                    .setMessage(getString(R.string.exit_player_message))
                    .setView(customPlayerExitView)
                    .setWidthPercent(0.50f)
                    .setBackgroundDrawable(R.drawable.bg_exit_dialog_player)
                    .setPositiveButtonDrawable(R.drawable.btn_exit_positive)
                    .setPositiveButton("Exit App", dialog -> finishAffinity())
                    .setNeutralButton("Dashboard", dialog -> {
                        dialog.dismiss();
                        Intent intent = new Intent(PlayerActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton(getString(R.string.btn_keep_watching), dialog -> dialog.dismiss())
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
        UIUtils.hideSystemUI(this);
        applySavedPlayerSettings();
        if (player != null && !player.isPlaying()) {
            player.play();
        }
        if (drawerChannelList != null && drawerChannelList.getVisibility() == View.VISIBLE) {
            if (recyclerPlayerChannels != null) {
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
                if (recyclerPlayerChannels != null) {
                    recyclerPlayerChannels.requestFocus();
                }
            } else if (playerView != null) {
                playerView.requestFocus();
            }
        }
    }

    private void retryPlayback(String reason) {
        if (isFinishing() || isDestroyed()) return;
        retryHandler.removeCallbacksAndMessages(null);

        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            long delay = Math.min(1000L * retryCount, 5000L);
            retryHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (player != null && currentStreamUrl != null && !currentStreamUrl.isEmpty()) {
                    player.setPlayWhenReady(true);
                    player.prepare();
                    player.play();
                } else {
                    playStream(currentStreamUrl);
                }
            }, delay);
        } else {
            retryCount = 0;
            retryHandler.postDelayed(() -> playStream(currentStreamUrl), 6000);
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
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceSearchHelper != null) {
            voiceSearchHelper.destroy();
        }
        retryHandler.removeCallbacksAndMessages(null);
        if (bufferingWatchdogHandler != null) {
            bufferingWatchdogHandler.removeCallbacksAndMessages(null);
        }
        if (channelNumHandler != null) {
            channelNumHandler.removeCallbacksAndMessages(null);
        }
        unregisterNetworkCallback();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
