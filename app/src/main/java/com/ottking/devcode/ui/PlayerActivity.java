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
import com.ottking.devcode.utils.InputFocusHelper;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import androidx.media3.common.PlaybackException;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.preferences.AppPreferences;

import com.ottking.devcode.utils.UIUtils;

public class PlayerActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "PlayerActivity";

    private PlayerView playerView;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private ImageView imgPlayerChannelLogo;
    private TextView txtPlayerChannelName, txtPlayerNowPlaying, txtPlayerChannelNumber, txtPlayerChannelBadge;
    private RecyclerView recyclerPlayerChannels;
    private ChannelAdapter channelAdapter;
    private AppPreferences prefs;

    private View cardChannelOverlay;
    private View drawerChannelList;
    private List<ChannelEntity> allChannelsList = new ArrayList<>();

    private final Handler uiOverlayHandler = new Handler(Looper.getMainLooper());
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
    private EditText edtPlayerSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_player);

        prefs = AppPreferences.getInstance(this);

        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            if (edtPlayerSearch != null) {
                                edtPlayerSearch.setText(recognizedText);
                            }
                        }
                    }
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
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        applySavedPlayerSettings();
        imgPlayerChannelLogo = findViewById(R.id.imgPlayerChannelLogo);
        txtPlayerChannelName = findViewById(R.id.txtPlayerChannelName);
        txtPlayerChannelNumber = findViewById(R.id.txtPlayerChannelNumber);
        txtPlayerChannelBadge = findViewById(R.id.txtPlayerChannelBadge);
        txtPlayerNowPlaying = findViewById(R.id.txtPlayerNowPlaying);
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
        registerNetworkCallback();

        FocusManager.getInstance().setupBackPressHandler(this, SCREEN_KEY, this::handlePlayerBackPressInternal);
    }

    private void hideOverlays() {
        uiOverlayHandler.removeCallbacks(autoHideOverlayRunnable);
        channelNumHandler.removeCallbacks(tuneChannelNumRunnable);
        channelNumBuffer.setLength(0);
        if (cardChannelOverlay != null) cardChannelOverlay.setVisibility(View.GONE);
        if (drawerChannelList != null) drawerChannelList.setVisibility(View.GONE);
        if (cardChannelNumOverlay != null) cardChannelNumOverlay.setVisibility(View.GONE);
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
            if (recyclerPlayerChannels != null) {
                recyclerPlayerChannels.requestFocus();
            }
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
    public boolean dispatchKeyEvent(KeyEvent event) {
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
                        changeChannel(false);
                        return true;
                    }
                    break;

                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    if (!isDrawerVisible) {
                        changeChannel(true);
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
        if (txtPlayerChannelBadge != null) {
            if (currentIsPremium) {
                txtPlayerChannelBadge.setText("PAID");
                txtPlayerChannelBadge.setBackgroundResource(R.color.gold_primary);
                txtPlayerChannelBadge.setTextColor(getColor(R.color.black));
            } else {
                txtPlayerChannelBadge.setText("FREE");
                txtPlayerChannelBadge.setBackgroundResource(R.color.accent_green);
                txtPlayerChannelBadge.setTextColor(getColor(R.color.black));
            }
        }
        txtPlayerNowPlaying.setText("• LIVE STREAM 1080p 60fps");

        if (currentLogoUrl != null && !currentLogoUrl.isEmpty()) {
            Glide.with(this)
                    .load(currentLogoUrl)
                    .placeholder(R.drawable.img_splash_bg)
                    .error(R.drawable.img_splash_bg)
                    .into(imgPlayerChannelLogo);
        }
    }

    private void initExoPlayer() {
        if (player == null) {
            android.content.Context playerContext = this;

            trackSelector = new DefaultTrackSelector(playerContext);

            DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(playerContext);
            renderersFactory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);

            String buf = prefs.getBufferSettings();
            int minBufferMs = 3000;
            int maxBufferMs = 15000;
            int bufferForPlaybackMs = 1500;
            int bufferForPlaybackAfterRebufferMs = 3000;

            if (buf.contains("1 sec") || buf.contains("Fast")) {
                minBufferMs = 1500;
                maxBufferMs = 5000;
                bufferForPlaybackMs = 1000;
                bufferForPlaybackAfterRebufferMs = 1500;
            } else if (buf.contains("5 sec") || buf.contains("Smooth")) {
                minBufferMs = 5000;
                maxBufferMs = 20000;
                bufferForPlaybackMs = 3000;
                bufferForPlaybackAfterRebufferMs = 5000;
            } else if (buf.contains("10 sec") || buf.contains("Large")) {
                minBufferMs = 10000;
                maxBufferMs = 30000;
                bufferForPlaybackMs = 5000;
                bufferForPlaybackAfterRebufferMs = 10000;
            }

            DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(minBufferMs, maxBufferMs, bufferForPlaybackMs, bufferForPlaybackAfterRebufferMs)
                    .build();

            DefaultHttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setUserAgent("OTT-KING-Player/1.0 (Linux; Android)");

            DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(playerContext, httpDataSourceFactory);
            DefaultMediaSourceFactory mediaSourceFactory = new DefaultMediaSourceFactory(dataSourceFactory);

            player = new ExoPlayer.Builder(playerContext)
                    .setMediaSourceFactory(mediaSourceFactory)
                    .setRenderersFactory(renderersFactory)
                    .setTrackSelector(trackSelector)
                    .setLoadControl(loadControl)
                    .build();

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
                        bufferingWatchdogHandler.postDelayed(bufferingWatchdogRunnable, 10000);
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
            DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
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

    private void playStream(String url) {
        if (player == null || url == null || url.trim().isEmpty()) return;

        applySavedPlayerSettings();

        MediaItem mediaItem;
        if (url.endsWith(".m3u8")) {
            mediaItem = new MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build();
        } else if (url.endsWith(".mpd")) {
            mediaItem = new MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build();
        } else {
            mediaItem = MediaItem.fromUri(url);
        }

        player.setMediaItem(mediaItem);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void setupChannelDrawer() {
        recyclerPlayerChannels.setLayoutManager(new LinearLayoutManager(this));
        channelAdapter = new ChannelAdapter(true, channel -> {
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
                if (recyclerPlayerChannels != null && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                    recyclerPlayerChannels.scrollToPosition(0);
                    RecyclerView.ViewHolder vh = recyclerPlayerChannels.findViewHolderForAdapterPosition(0);
                    if (vh != null && vh.itemView != null) {
                        vh.itemView.requestFocus();
                    } else {
                        recyclerPlayerChannels.post(() -> {
                            RecyclerView.ViewHolder vh2 = recyclerPlayerChannels.findViewHolderForAdapterPosition(0);
                            if (vh2 != null && vh2.itemView != null) {
                                vh2.itemView.requestFocus();
                            }
                        });
                    }
                }
            }
        });
        recyclerPlayerChannels.setAdapter(channelAdapter);

        if (btnPlayerVoiceSearch != null) {
            btnPlayerVoiceSearch.setOnClickListener(v -> startVoiceSearch());
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
            InputFocusHelper.bind(edtPlayerSearch, this, null, null);

            Runnable activatePlayerSearch = () -> InputFocusHelper.activate(this, edtPlayerSearch, null);

            edtPlayerSearch.setOnClickListener(v -> activatePlayerSearch.run());

            edtPlayerSearch.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtPlayerSearch.getWindowToken(), 0);
                    }
                    edtPlayerSearch.setCursorVisible(false);
                    edtPlayerSearch.setFocusableInTouchMode(false);
                    if (recyclerPlayerChannels != null) {
                        recyclerPlayerChannels.requestFocus();
                    }
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
                        if (recyclerPlayerChannels != null) {
                            recyclerPlayerChannels.requestFocus();
                            return true;
                        }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say channel name (e.g. Sports, News)...");

        try {
            voiceSearchLauncher.launch(intent);
        } catch (Exception ignored) {
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
                    contentFrame.addView(createAccountsTabContent());
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
        String[] buffers = {"Fast Start (1 sec)", "Standard (3 sec)", "Smooth Playback (5 sec)", "Large Buffer (10 sec)"};
        Spinner spinnerBuffer = new Spinner(this);
        ArrayAdapter<String> bufferAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, buffers);
        spinnerBuffer.setAdapter(bufferAdapter);
        int currentBufferIndex = 1;
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

    private View createAccountsTabContent() {
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
            edtU.setHint("Username (admin)");
            edtU.setTextColor(getColor(R.color.white));
            edtU.setHintTextColor(getColor(R.color.text_secondary));
            edtU.setBackgroundResource(R.drawable.bg_card_normal);
            edtU.setPadding(20, 20, 20, 20);

            EditText edtP = new EditText(this);
            edtP.setHint("Password (123456)");
            edtP.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtP.setTextColor(getColor(R.color.white));
            edtP.setHintTextColor(getColor(R.color.text_secondary));
            edtP.setBackgroundResource(R.drawable.bg_card_normal);
            edtP.setPadding(20, 20, 20, 20);

            Button btnLogin = new Button(this);
            btnLogin.setText("Sign In");
            btnLogin.setBackgroundResource(R.drawable.selector_pill_focus);
            btnLogin.setTextColor(getColorStateList(R.color.selector_pill_text));

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
        if (isDrawerVisible || isCardVisible) {
            hideOverlays();
            return true;
        }

        boolean isBootPlayer = prefs.isBootPlayerEnabled();
        new CustomDialog.Builder(this)
                .setTitle(getString(R.string.exit_player_title))
                .setTitleTextColor(0xFFFF4B4B)
                .setIcon(R.drawable.ic_play)
                .setMessage(getString(R.string.exit_player_message))
                .setWidthPercent(0.50f)
                .setBackgroundDrawable(R.drawable.bg_exit_dialog_player)
                .setPositiveButtonDrawable(R.drawable.btn_exit_positive)
                .setPositiveButton(getString(R.string.btn_stop_exit), dialog -> {
                    dialog.dismiss();
                    if (isBootPlayer || isTaskRoot()) {
                        finishAffinity();
                    } else {
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.btn_keep_watching), dialog -> dialog.dismiss())
                .show();
        return true;
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
