package com.ottking.devcode.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.animation.ValueAnimator;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.graphics.Color;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.Button;

import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;
import com.ottking.devcode.network.DataPollingManager;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.utils.NetworkUtils;

import java.util.ArrayList;
import java.util.List;

import com.ottking.devcode.utils.FocusManager;
import com.ottking.devcode.utils.UIUtils;
import com.ottking.devcode.viewmodel.FocusViewModel;

public class MainActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "MainActivity";
    public static final String GROUP_CATEGORIES = "categories";
    public static final String GROUP_CHANNELS = "channels";
    public static final String GROUP_HEADER = "header";

    private String currentActiveSection = GROUP_CATEGORIES;

    private RecyclerView recyclerCategories, recyclerChannels;
    private View categoryContainer;
    private TextView txtCategoryHeader;
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private TextView txtSelectedCategoryTitle, txtChannelCount, txtNotificationBadge;
    private EditText edtSearch;
    private ImageView btnClearSearch;
    private com.ottking.devcode.utils.AppNotificationManager notificationManager;
    private boolean isCategoryExpanded = true;

    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private final List<CategoryEntity> rawCategoriesFromDb = new ArrayList<>();
    private boolean channelsLoadedFromDb = false;

    private int lastFocusedChannelPosition = 0;
    private boolean cameToSearchFromChannel = false;

    private final List<ChannelEntity> allChannels = new ArrayList<>();
    private final List<ChannelEntity> filteredChannels = new ArrayList<>();
    public static final int DEFAULT_ALL_CATEGORY_ID = 1;
    private int selectedCategoryId = DEFAULT_ALL_CATEGORY_ID; // 1 = All
    private int allCategoryId = DEFAULT_ALL_CATEGORY_ID;
    private boolean isInitialLaunch = true;
    private boolean isServerSyncCompleted = false;
    private boolean hasShownEmptyModal = false;
    private final DataPollingManager.SyncListener syncListener = new DataPollingManager.SyncListener() {
        @Override
        public void onSyncStarted() {}

        @Override
        public void onSyncCompleted(boolean success, String errorMessage) {
            isServerSyncCompleted = true;
            runOnUiThread(() -> {
                filterChannels();
                updateCategoriesAndSelection();
                if (allChannels.isEmpty() && !hasShownEmptyModal) {
                    hasShownEmptyModal = true;
                    showNoChannelsModal();
                }
            });
        }
    };

    private View layoutNetworkStatus, viewNetworkDot, bannerNoInternet;
    private View layoutNoChannels;
    private Button btnRetrySyncChannels;
    private TextView txtNetworkStatus;
    private Button btnBannerRetry;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Dialog currentExitDialog;
    private NotificationPanelDialog notificationPanelDialog;
    private Dialog noChannelsModalDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_main);

        categoryContainer = findViewById(R.id.categoryContainer);
        txtCategoryHeader = findViewById(R.id.txtCategoryHeader);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        recyclerChannels = findViewById(R.id.recyclerChannels);
        txtSelectedCategoryTitle = findViewById(R.id.txtSelectedCategoryTitle);
        txtChannelCount = findViewById(R.id.txtChannelCount);
        txtNotificationBadge = findViewById(R.id.txtNotificationBadge);
        edtSearch = findViewById(R.id.edtSearch);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        layoutNetworkStatus = findViewById(R.id.layoutNetworkStatus);
        viewNetworkDot = findViewById(R.id.viewNetworkDot);
        txtNetworkStatus = findViewById(R.id.txtNetworkStatus);
        bannerNoInternet = findViewById(R.id.bannerNoInternet);
        btnBannerRetry = findViewById(R.id.btnBannerRetry);
        layoutNoChannels = findViewById(R.id.layoutNoChannels);
        btnRetrySyncChannels = findViewById(R.id.btnRetrySyncChannels);

        if (btnRetrySyncChannels != null) {
            UIUtils.applyFocusAnimation(btnRetrySyncChannels, 1.06f, 8f);
            btnRetrySyncChannels.setOnClickListener(v -> {
                hasShownEmptyModal = false;
                Toast.makeText(this, "Refreshing channels from server...", Toast.LENGTH_SHORT).show();
                DataPollingManager.getInstance(this).triggerSyncNow();
            });
        }

        if (btnBannerRetry != null) {
            btnBannerRetry.setOnFocusChangeListener((v, hasFocus) -> UIUtils.animateFocus(v, hasFocus, 1.05f, 6f));
            btnBannerRetry.setOnClickListener(v -> {
                Toast.makeText(this, "Re-syncing with server...", Toast.LENGTH_SHORT).show();
                DataPollingManager.getInstance(this).triggerSyncNow();
                updateNetworkStatusUI(checkIsConnected());
            });
        }

        if (layoutNetworkStatus != null) {
            UIUtils.applyFocusAnimation(layoutNetworkStatus, 1.08f, 8f);
            layoutNetworkStatus.setOnClickListener(v -> {
                boolean isOnline = checkIsConnected();
                Toast.makeText(this, "Network Status: " + (isOnline ? "Connected (Online)" : "No Connection (Downtime/Buffering)"), Toast.LENGTH_SHORT).show();
            });
        }

        setupNetworkMonitoring();

        notificationManager = new com.ottking.devcode.utils.AppNotificationManager(this);

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        ImageButton btnNotification = findViewById(R.id.btnNotification);
        ImageButton btnVoiceSearch = findViewById(R.id.btnVoiceSearch);

        voiceSearchLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String recognizedText = matches.get(0);
                            edtSearch.setText(recognizedText);
                            Toast.makeText(this, "Searching: " + recognizedText, Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startVoiceSearch();
                    } else {
                        Toast.makeText(this, "Microphone permission is required for Voice Search", Toast.LENGTH_SHORT).show();
                    }
                });

        if (btnVoiceSearch != null) {
            btnVoiceSearch.setOnClickListener(v -> startVoiceSearch());
        }

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnNotification.setOnClickListener(v -> {
            if (isFinishing() || isDestroyed()) return;
            if (notificationPanelDialog != null && notificationPanelDialog.isShowing()) return;
            notificationPanelDialog = new NotificationPanelDialog(this, unreadCount -> {
                updateBadgeUI(unreadCount);
            });
            notificationPanelDialog.show();
        });

        updateNotificationBadge();

        setupAdapters();
        observeRoomDatabase();
        setupSearch();
        setupHeaderNavigation();
        setupFocusGuard();

        FocusManager.getInstance().setupBackPressHandler(this, SCREEN_KEY, this::handleMainBackPressInternal);
        recyclerCategories.post(this::focusFirstCategoryItem);
    }

    private void setupFocusGuard() {
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus == null) {
                getWindow().getDecorView().post(this::restoreActiveFocus);
                return;
            }
            if (recyclerCategories != null && recyclerCategories.findContainingItemView(newFocus) != null) {
                currentActiveSection = GROUP_CATEGORIES;
                isInitialLaunch = false;
                setCategoryPanelExpanded(true);
            } else if (recyclerChannels != null && recyclerChannels.findContainingItemView(newFocus) != null) {
                currentActiveSection = GROUP_CHANNELS;
                isInitialLaunch = false;
                setCategoryPanelExpanded(false);
            } else if (isHeaderViewFocused(newFocus)) {
                if (!isInitialLaunch) {
                    currentActiveSection = GROUP_HEADER;
                    setCategoryPanelExpanded(true);
                } else {
                    recyclerCategories.post(this::focusFirstCategoryItem);
                }
            }
        });
    }

    private boolean isHeaderViewFocused(View currentFocus) {
        if (currentFocus == null) return false;
        View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        View btnNotification = findViewById(R.id.btnNotification);
        View btnSettings = findViewById(R.id.btnSettings);
        return currentFocus == edtSearch
                || (edtSearch != null && edtSearch.hasFocus())
                || currentFocus == btnClearSearch
                || currentFocus == btnVoiceSearch
                || currentFocus == btnNotification
                || currentFocus == btnSettings;
    }

    private boolean handleMainBackPressInternal() {
        View currentFocus = getCurrentFocus();
        if (isHeaderViewFocused(currentFocus)) {
            if (edtSearch != null) {
                if (edtSearch.getText().length() > 0) {
                    edtSearch.setText("");
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
                edtSearch.setCursorVisible(false);
                edtSearch.setFocusableInTouchMode(false);
            }
            if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                cameToSearchFromChannel = false;
                focusChannelAtPosition(lastFocusedChannelPosition);
                return true;
            } else {
                focusSelectedCategory();
                return true;
            }
        }

        if (currentFocus != null && recyclerChannels != null && recyclerChannels.findContainingItemView(currentFocus) != null) {
            focusSelectedCategory();
            return true;
        }

        showExitDialog();
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (handleMainBackPressInternal()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
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
        } catch (Exception e) {
            Toast.makeText(this, "Voice search is not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private int calculateSpanCount() {
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        // Minimum 6 cards per row, increasing based on screen size
        int cols = screenWidthDp / 130;
        return Math.max(6, cols);
    }

    private void setupAdapters() {
        // Categories List Layout
        recyclerCategories.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryAdapter(category -> {
            selectedCategoryId = category.id;
            lastFocusedChannelPosition = 0;
            txtSelectedCategoryTitle.setText(category.name);
            filterChannels();
        });
        categoryAdapter.setNavigationListener(new CategoryAdapter.OnCategoryNavigationListener() {
            @Override
            public void onNavigateToChannels() {
                setCategoryPanelExpanded(false);
                focusChannelAtPosition(lastFocusedChannelPosition);
            }

            @Override
            public void onNavigateToHeader() {
                cameToSearchFromChannel = false;
                edtSearch.requestFocus();
            }
        });
        categoryAdapter.setFocusListener((pos, view) -> {
            setCategoryPanelExpanded(true);
            isInitialLaunch = false;
            currentActiveSection = GROUP_CATEGORIES;
            CategoryEntity cat = categoryAdapter.getCategoryAt(pos);
            int catId = (cat != null) ? cat.id : 0;
            FocusManager.getInstance().savePosition(this, SCREEN_KEY, GROUP_CATEGORIES, pos);
            FocusManager.getInstance().saveFocusedItemId(this, SCREEN_KEY, GROUP_CATEGORIES, catId);
            FocusManager.getInstance().saveHomeCategoryFocus(catId, pos, view);
            FocusManager.getInstance().saveFocus(this, SCREEN_KEY, view);
        });
        categoryAdapter.setCategories(prepareCategoriesList(new ArrayList<>()));
        recyclerCategories.setAdapter(categoryAdapter);

        // Channels Grid Layout (exactly 5 columns)
        int spanCount = 5;
        recyclerChannels.setLayoutManager(new GridLayoutManager(this, spanCount));
        channelAdapter = new ChannelAdapter(channel -> {
            if (com.ottking.devcode.security.VpnDetectionManager.isVpnOrProxyActive(MainActivity.this)) {
                com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(MainActivity.this, null);
                return;
            }
            int pos = -1;
            for (int i = 0; i < allChannels.size(); i++) {
                if (allChannels.get(i).id == channel.id) {
                    pos = i;
                    break;
                }
            }
            int channelNumber = (pos != -1) ? (pos + 1) : 1;
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("channel_id", channel.id);
            intent.putExtra("channel_number", channelNumber);
            intent.putExtra("channel_name", channel.name);
            intent.putExtra("stream_url", com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(MainActivity.this, channel.streamUrl));
            intent.putExtra("logo_url", channel.logoUrl);
            intent.putExtra("is_premium", channel.isPremium);
            intent.putExtra("stream_type", channel.streamType);
            String edgeCookie = com.ottking.devcode.network.GlobalCookieManager.getInstance(MainActivity.this).getValidatedCookie(channel.id, channel.streamUrl);
            intent.putExtra("edge_cookie", edgeCookie);
            startActivity(intent);
        });
        channelAdapter.setSpanCount(spanCount);
        channelAdapter.setFocusListener((pos, view) -> {
            setCategoryPanelExpanded(false);
            isInitialLaunch = false;
            currentActiveSection = GROUP_CHANNELS;
            lastFocusedChannelPosition = pos;
            ChannelEntity ch = channelAdapter.getChannelAt(pos);
            int chId = (ch != null) ? ch.id : -1;
            FocusManager.getInstance().savePosition(this, SCREEN_KEY, GROUP_CHANNELS, pos);
            FocusManager.getInstance().saveFocusedItemId(this, SCREEN_KEY, GROUP_CHANNELS, chId);
            FocusManager.getInstance().saveHomeChannelFocus(chId, pos, view);
            FocusManager.getInstance().saveFocus(this, SCREEN_KEY, view);
        });
        channelAdapter.setNavigationListener(new ChannelAdapter.OnChannelNavigationListener() {
            @Override
            public void onNavigateToCategories() {
                setCategoryPanelExpanded(true);
                int lastCatId = FocusManager.getInstance().getHomeLastCategoryId(selectedCategoryId);
                focusCategoryById(lastCatId);
            }

            @Override
            public void onNavigateToHeader(int channelPosition) {
                lastFocusedChannelPosition = channelPosition;
                cameToSearchFromChannel = true;
                int col = channelPosition % 5;
                View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
                View btnNotification = findViewById(R.id.btnNotification);
                View btnSettings = findViewById(R.id.btnSettings);
                if (col <= 1) {
                    if (btnVoiceSearch != null && btnVoiceSearch.getVisibility() == View.VISIBLE) {
                        btnVoiceSearch.requestFocus();
                    } else if (btnNotification != null && btnNotification.getVisibility() == View.VISIBLE) {
                        btnNotification.requestFocus();
                    } else {
                        edtSearch.requestFocus();
                    }
                } else if (col <= 2) {
                    if (btnNotification != null && btnNotification.getVisibility() == View.VISIBLE) {
                        btnNotification.requestFocus();
                    } else if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                        btnSettings.requestFocus();
                    } else {
                        edtSearch.requestFocus();
                    }
                } else if (col <= 3) {
                    if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                        btnSettings.requestFocus();
                    } else {
                        edtSearch.requestFocus();
                    }
                } else {
                    edtSearch.requestFocus();
                }
            }
        });
        allChannels.clear();
        channelAdapter.setAllChannelsList(allChannels);
        recyclerChannels.setAdapter(channelAdapter);
        filterChannels();
    }

    public void setCategoryPanelExpanded(boolean expand) {
        if (!expand && categoryContainer != null && categoryContainer.isInTouchMode()) {
            expand = true;
        }
        if (isCategoryExpanded == expand) return;
        isCategoryExpanded = expand;

        if (categoryContainer == null) return;

        int currentWidth = categoryContainer.getWidth();
        int targetWidth = expand ? UIUtils.dpToPx(this, 220) : UIUtils.dpToPx(this, 70);

        if (currentWidth <= 0) {
            currentWidth = expand ? UIUtils.dpToPx(this, 70) : UIUtils.dpToPx(this, 220);
        }

        ValueAnimator anim = ValueAnimator.ofInt(currentWidth, targetWidth);
        anim.addUpdateListener(animation -> {
            int val = (Integer) animation.getAnimatedValue();
            ViewGroup.LayoutParams lp = categoryContainer.getLayoutParams();
            if (lp != null) {
                lp.width = val;
                categoryContainer.setLayoutParams(lp);
            }
        });
        anim.setDuration(200);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        anim.start();

        if (txtCategoryHeader != null) {
            if (expand) {
                txtCategoryHeader.setVisibility(View.VISIBLE);
                txtCategoryHeader.setAlpha(0f);
                txtCategoryHeader.animate().alpha(1f).setDuration(180).start();
            } else {
                txtCategoryHeader.animate().alpha(0f).setDuration(140).withEndAction(() -> {
                    txtCategoryHeader.setVisibility(View.GONE);
                }).start();
            }
        }

        if (categoryAdapter != null) {
            categoryAdapter.setCollapsed(!expand, recyclerCategories);
        }
    }

    private void setupHeaderNavigation() {
        View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        View btnNotification = findViewById(R.id.btnNotification);
        View btnSettings = findViewById(R.id.btnSettings);

        View.OnFocusChangeListener headerFocusListener = (v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.12f, 10f);
            if (hasFocus) {
                currentActiveSection = GROUP_HEADER;
                setCategoryPanelExpanded(true);
                FocusManager.getInstance().saveFocus(this, SCREEN_KEY, v);
            }
        };

        if (btnVoiceSearch != null) {
            btnVoiceSearch.setOnFocusChangeListener(headerFocusListener);
            btnVoiceSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                            cameToSearchFromChannel = false;
                            focusChannelAtPosition(lastFocusedChannelPosition);
                        } else {
                            focusSelectedCategory();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        focusSelectedCategory();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (btnNotification != null && btnNotification.getVisibility() == View.VISIBLE) {
                            btnNotification.requestFocus();
                            return true;
                        } else if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                            btnSettings.requestFocus();
                            return true;
                        } else if (edtSearch != null) {
                            edtSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        if (btnNotification != null) {
            btnNotification.setOnFocusChangeListener(headerFocusListener);
            btnNotification.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                            cameToSearchFromChannel = false;
                            focusChannelAtPosition(lastFocusedChannelPosition);
                        } else {
                            focusSelectedCategory();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (btnVoiceSearch != null && btnVoiceSearch.getVisibility() == View.VISIBLE) {
                            btnVoiceSearch.requestFocus();
                            return true;
                        } else {
                            focusSelectedCategory();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                            btnSettings.requestFocus();
                            return true;
                        } else if (edtSearch != null) {
                            edtSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }

        if (btnSettings != null) {
            btnSettings.setOnFocusChangeListener(headerFocusListener);
            btnSettings.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                            cameToSearchFromChannel = false;
                            focusChannelAtPosition(lastFocusedChannelPosition);
                        } else {
                            focusSelectedCategory();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (btnNotification != null && btnNotification.getVisibility() == View.VISIBLE) {
                            btnNotification.requestFocus();
                            return true;
                        } else if (btnVoiceSearch != null && btnVoiceSearch.getVisibility() == View.VISIBLE) {
                            btnVoiceSearch.requestFocus();
                            return true;
                        } else {
                            focusSelectedCategory();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (edtSearch != null) {
                            edtSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }
    }

    public void focusFirstCategoryItem() {
        setCategoryPanelExpanded(true);
        currentActiveSection = GROUP_CATEGORIES;
        if (categoryAdapter != null) {
            categoryAdapter.setSelectedPosition(0);
            if (categoryAdapter.getItemCount() > 0) {
                CategoryEntity firstCat = categoryAdapter.getCategoryAt(0);
                if (firstCat != null) {
                    selectedCategoryId = firstCat.id;
                    if (txtSelectedCategoryTitle != null) {
                        txtSelectedCategoryTitle.setText(firstCat.name);
                    }
                    filterChannels();
                }
            }
        }
        if (recyclerCategories != null) {
            recyclerCategories.scrollToPosition(0);
            Runnable tryFocus = () -> {
                if (isFinishing() || isDestroyed()) return;
                RecyclerView.ViewHolder vh = recyclerCategories.findViewHolderForAdapterPosition(0);
                if (vh != null && vh.itemView != null) {
                    vh.itemView.requestFocus();
                    isInitialLaunch = false;
                } else if (recyclerCategories.getChildCount() > 0) {
                    recyclerCategories.getChildAt(0).requestFocus();
                    isInitialLaunch = false;
                }
            };
            recyclerCategories.post(tryFocus);
            recyclerCategories.postDelayed(tryFocus, 30);
            recyclerCategories.postDelayed(tryFocus, 80);
            recyclerCategories.postDelayed(tryFocus, 180);
        }
    }

    public void focusSelectedCategory() {
        int pos = categoryAdapter != null ? categoryAdapter.getSelectedPosition() : 0;
        if (pos < 0) pos = 0;
        focusCategoryAtPosition(pos);
    }

    public void focusCategoryById(int categoryId) {
        setCategoryPanelExpanded(true);
        currentActiveSection = GROUP_CATEGORIES;
        int pos = categoryAdapter != null ? categoryAdapter.getPositionByCategoryId(categoryId) : -1;
        if (pos != -1) {
            if (categoryAdapter != null) {
                categoryAdapter.setSelectedPosition(pos);
            }
            focusCategoryAtPosition(pos);
        } else {
            focusSelectedCategory();
        }
    }

    public void focusCategoryAtPosition(int targetPos) {
        setCategoryPanelExpanded(true);
        currentActiveSection = GROUP_CATEGORIES;
        if (targetPos < 0) targetPos = 0;
        final int finalPos = targetPos;
        recyclerCategories.scrollToPosition(finalPos);
        recyclerCategories.post(() -> {
            RecyclerView.ViewHolder vh = recyclerCategories.findViewHolderForAdapterPosition(finalPos);
            if (vh != null && vh.itemView != null) {
                vh.itemView.requestFocus();
            } else {
                recyclerCategories.postDelayed(() -> {
                    RecyclerView.ViewHolder vh2 = recyclerCategories.findViewHolderForAdapterPosition(finalPos);
                    if (vh2 != null && vh2.itemView != null) {
                        vh2.itemView.requestFocus();
                    } else if (recyclerCategories.getChildCount() > 0) {
                        recyclerCategories.getChildAt(0).requestFocus();
                    }
                }, 30);
            }
        });
    }

    public void focusChannelById(int channelId) {
        setCategoryPanelExpanded(false);
        currentActiveSection = GROUP_CHANNELS;
        int pos = channelAdapter != null ? channelAdapter.getChannelPositionById(channelId) : -1;
        if (pos != -1) {
            lastFocusedChannelPosition = pos;
            focusChannelAtPosition(pos);
        } else {
            focusChannelAtPosition(lastFocusedChannelPosition);
        }
    }

    public void focusChannelAtPosition(int targetPos) {
        setCategoryPanelExpanded(false);
        currentActiveSection = GROUP_CHANNELS;
        if (channelAdapter == null || channelAdapter.getItemCount() == 0) {
            focusSelectedCategory();
            return;
        }
        if (targetPos < 0) targetPos = 0;
        if (targetPos >= channelAdapter.getItemCount()) {
            targetPos = channelAdapter.getItemCount() - 1;
        }
        final int finalPos = targetPos;
        recyclerChannels.scrollToPosition(finalPos);
        recyclerChannels.post(() -> {
            RecyclerView.ViewHolder vh = recyclerChannels.findViewHolderForAdapterPosition(finalPos);
            if (vh != null && vh.itemView != null) {
                vh.itemView.requestFocus();
            } else {
                recyclerChannels.postDelayed(() -> {
                    RecyclerView.ViewHolder vh2 = recyclerChannels.findViewHolderForAdapterPosition(finalPos);
                    if (vh2 != null && vh2.itemView != null) {
                        vh2.itemView.requestFocus();
                    } else if (recyclerChannels.getChildCount() > 0) {
                        recyclerChannels.getChildAt(0).requestFocus();
                    }
                }, 50);
            }
        });
    }

    public void focusFirstChannel() {
        focusChannelAtPosition(0);
    }

    private void observeRoomDatabase() {
        AppDatabase db = AppDatabase.getInstance(this);

        db.categoryDao().getAllCategories().observe(this, categories -> {
            rawCategoriesFromDb.clear();
            if (categories != null) {
                rawCategoriesFromDb.addAll(categories);
            }
            updateCategoriesAndSelection();
        });

        db.channelDao().getAllChannels().observe(this, channels -> {
            channelsLoadedFromDb = true;
            allChannels.clear();
            if (channels != null && !channels.isEmpty()) {
                allChannels.addAll(channels);
            }
            channelAdapter.setAllChannelsList(allChannels);
            filterChannels();
            updateCategoriesAndSelection();
        });
    }

    private void updateCategoriesAndSelection() {
        List<CategoryEntity> processed = prepareCategoriesList(rawCategoriesFromDb);
        categoryAdapter.setCategories(processed);

        if (isInitialLaunch) {
            if (!processed.isEmpty()) {
                CategoryEntity firstCat = processed.get(0);
                selectedCategoryId = firstCat.id;
                allCategoryId = firstCat.id;
                categoryAdapter.setSelectedPosition(0);
                if (txtSelectedCategoryTitle != null) {
                    txtSelectedCategoryTitle.setText(firstCat.name);
                }
            }
            filterChannels();
            recyclerCategories.post(this::focusFirstCategoryItem);
        } else {
            // Real-time server update while user is on Home screen
            int foundPos = -1;
            CategoryEntity foundCat = null;
            for (int i = 0; i < processed.size(); i++) {
                if (processed.get(i).id == selectedCategoryId) {
                    foundPos = i;
                    foundCat = processed.get(i);
                    break;
                }
            }

            if (foundPos != -1 && foundCat != null) {
                categoryAdapter.setSelectedPosition(foundPos);
                if (txtSelectedCategoryTitle != null) {
                    txtSelectedCategoryTitle.setText(foundCat.name);
                }
                filterChannels();
            } else if (!processed.isEmpty()) {
                // Previously selected category was removed or is blank; fallback gracefully to All (index 0)
                CategoryEntity fallback = processed.get(0);
                selectedCategoryId = fallback.id;
                allCategoryId = fallback.id;
                categoryAdapter.setSelectedPosition(0);
                if (txtSelectedCategoryTitle != null) {
                    txtSelectedCategoryTitle.setText(fallback.name);
                }
                filterChannels();
            }
        }
    }

    private void setupSearch() {
        edtSearch.setFocusable(true);
        edtSearch.setFocusableInTouchMode(false);
        edtSearch.setCursorVisible(false);

        edtSearch.setOnFocusChangeListener((v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.04f, 8f);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!hasFocus) {
                edtSearch.setCursorVisible(false);
                edtSearch.setFocusableInTouchMode(false);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
            } else {
                currentActiveSection = GROUP_HEADER;
                setCategoryPanelExpanded(true);
                FocusManager.getInstance().saveFocus(this, SCREEN_KEY, v);
                // When focused via DPAD navigation, do NOT auto-open keyboard or activate text input
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
            }
        });

        Runnable activateSearchInput = () -> {
            edtSearch.setFocusableInTouchMode(true);
            edtSearch.setCursorVisible(true);
            edtSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(edtSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        };

        edtSearch.setOnClickListener(v -> activateSearchInput.run());

        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
                edtSearch.setCursorVisible(false);
                edtSearch.setFocusableInTouchMode(false);
                focusSelectedCategory();
                return true;
            }
            return false;
        });

        edtSearch.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    if (!edtSearch.isCursorVisible()) {
                        activateSearchInput.run();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                    }
                    edtSearch.setCursorVisible(false);
                    edtSearch.setFocusableInTouchMode(false);
                    if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                        cameToSearchFromChannel = false;
                        focusChannelAtPosition(lastFocusedChannelPosition);
                    } else {
                        focusSelectedCategory();
                    }
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    // If typing and cursor can move left within text, let normal cursor navigation handle it
                    if (edtSearch.isCursorVisible() && edtSearch.getSelectionStart() > 0) {
                        return false;
                    }
                    // Otherwise, exit editing mode and move focus directly to the left item (Settings)
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                    }
                    edtSearch.setCursorVisible(false);
                    edtSearch.setFocusableInTouchMode(false);
                    View btnSettings = findViewById(R.id.btnSettings);
                    View btnNotification = findViewById(R.id.btnNotification);
                    View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
                    if (btnSettings != null && btnSettings.getVisibility() == View.VISIBLE) {
                        btnSettings.requestFocus();
                        return true;
                    } else if (btnNotification != null && btnNotification.getVisibility() == View.VISIBLE) {
                        btnNotification.requestFocus();
                        return true;
                    } else if (btnVoiceSearch != null && btnVoiceSearch.getVisibility() == View.VISIBLE) {
                        btnVoiceSearch.requestFocus();
                        return true;
                    } else {
                        focusSelectedCategory();
                        return true;
                    }
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    // If typing and cursor can move right within text, allow it
                    if (edtSearch.isCursorVisible() && edtSearch.getSelectionStart() < edtSearch.getText().length()) {
                        return false;
                    }
                    if (btnClearSearch != null && btnClearSearch.getVisibility() == View.VISIBLE) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                        }
                        edtSearch.setCursorVisible(false);
                        edtSearch.setFocusableInTouchMode(false);
                        btnClearSearch.requestFocus();
                        return true;
                    }
                    // At the rightmost boundary of the header: consume event so focus never jumps to the left!
                    return true;
                }
            }
            return false;
        });

        if (btnClearSearch != null) {
            UIUtils.applyFocusAnimation(btnClearSearch, 1.15f, 6f);
            btnClearSearch.setOnClickListener(v -> {
                edtSearch.setText("");
                filterChannels();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
                edtSearch.setCursorVisible(false);
                edtSearch.setFocusableInTouchMode(false);
                edtSearch.requestFocus();
            });

            btnClearSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        btnClearSearch.performClick();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        edtSearch.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_BACK) {
                        if (cameToSearchFromChannel && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                            cameToSearchFromChannel = false;
                            focusChannelAtPosition(lastFocusedChannelPosition);
                        } else {
                            focusSelectedCategory();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        return true; // edge boundary
                    }
                }
                return false;
            });
        }

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (btnClearSearch != null) {
                    btnClearSearch.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                filterChannels();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private List<CategoryEntity> prepareCategoriesList(List<CategoryEntity> input) {
        List<CategoryEntity> result = new ArrayList<>();
        if (input == null || input.isEmpty()) {
            allCategoryId = DEFAULT_ALL_CATEGORY_ID;
            result.add(new CategoryEntity(DEFAULT_ALL_CATEGORY_ID, "All", "ic_tv"));
            return result;
        }

        boolean isSubActive = NetworkUtils.isSubscriptionActive(this);

        CategoryEntity foundAll = null;
        List<CategoryEntity> validCategories = new ArrayList<>();

        for (CategoryEntity c : input) {
            if (c == null) continue;
            String name = c.name != null ? c.name.trim() : "";
            // If category name is blank or null, do NOT show it
            if (name.isEmpty() || "null".equalsIgnoreCase(name)) {
                continue;
            }

            if ("all".equalsIgnoreCase(name) || "all channels".equalsIgnoreCase(name)
                    || "সকল চ্যানেল".equalsIgnoreCase(name) || "সকল".equalsIgnoreCase(name)) {
                if (foundAll == null) {
                    foundAll = new CategoryEntity(c.id, "All", (c.icon != null && !c.icon.isEmpty()) ? c.icon : "ic_tv");
                    allCategoryId = c.id;
                }
            } else {
                // If channels have been loaded or channels list is present, verify category has channels
                if (channelsLoadedFromDb || !allChannels.isEmpty()) {
                    boolean hasChannel = false;
                    for (ChannelEntity chan : allChannels) {
                        if (!isSubActive && chan.isPremium) {
                            continue;
                        }
                        if (chan.name == null || chan.name.trim().isEmpty()) {
                            continue;
                        }
                        if (chan.categoryId == c.id) {
                            hasChannel = true;
                            break;
                        }
                    }
                    if (!hasChannel) {
                        // Category is blank (has 0 channels) -> do NOT show it!
                        continue;
                    }
                }
                validCategories.add(c);
            }
        }

        if (foundAll != null) {
            result.add(foundAll);
            result.addAll(validCategories);
        } else {
            // Pick an ID not used by existing categories for the All category
            int specialId = DEFAULT_ALL_CATEGORY_ID;
            boolean id1Used = false;
            for (CategoryEntity c : validCategories) {
                if (c.id == DEFAULT_ALL_CATEGORY_ID) {
                    id1Used = true;
                    break;
                }
            }
            if (id1Used) {
                specialId = 0;
            }
            allCategoryId = specialId;
            result.add(new CategoryEntity(specialId, "All", "ic_tv"));
            result.addAll(validCategories);
        }
        return result;
    }

    private boolean isAllCategory(int categoryId) {
        if (categoryId == allCategoryId) return true;
        if (categoryId == DEFAULT_ALL_CATEGORY_ID && allCategoryId == DEFAULT_ALL_CATEGORY_ID) return true;
        if (categoryAdapter != null) {
            if (categoryAdapter.getItemCount() > 0) {
                CategoryEntity firstCat = categoryAdapter.getCategoryAt(0);
                if (firstCat != null && firstCat.id == categoryId) {
                    return true;
                }
            }
            for (CategoryEntity cat : categoryAdapter.getCategoryList()) {
                if (cat.id == categoryId) {
                    String name = cat.name != null ? cat.name.trim() : "";
                    if ("all".equalsIgnoreCase(name) || "all channels".equalsIgnoreCase(name) || "সকল চ্যানেল".equalsIgnoreCase(name) || "সকল".equalsIgnoreCase(name)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void filterChannels() {
        filteredChannels.clear();
        String query = edtSearch.getText().toString().trim().toLowerCase();
        boolean isSubActive = NetworkUtils.isSubscriptionActive(this);

        for (ChannelEntity chan : allChannels) {
            // Real-time subscription sync: if subscription has expired and channel is premium, hide it
            if (!isSubActive && chan.isPremium) {
                continue;
            }

            boolean matchesCategory = isAllCategory(selectedCategoryId) || (chan.categoryId == selectedCategoryId);
            boolean matchesSearch = query.isEmpty() || chan.name.toLowerCase().contains(query);

            if (matchesCategory && matchesSearch) {
                filteredChannels.add(chan);
            }
        }

        channelAdapter.setChannels(filteredChannels);
        if (allChannels.isEmpty()) {
            txtChannelCount.setText("0 Channels (No server data)");
            if (layoutNoChannels != null) {
                layoutNoChannels.setVisibility(View.VISIBLE);
            }
            if (recyclerChannels != null) {
                recyclerChannels.setVisibility(View.GONE);
            }
        } else if (filteredChannels.isEmpty()) {
            txtChannelCount.setText("0 Channels");
            if (layoutNoChannels != null) {
                layoutNoChannels.setVisibility(View.VISIBLE);
            }
            if (recyclerChannels != null) {
                recyclerChannels.setVisibility(View.GONE);
            }
        } else {
            hasShownEmptyModal = false;
            txtChannelCount.setText(filteredChannels.size() + " Channels");
            if (layoutNoChannels != null) {
                layoutNoChannels.setVisibility(View.GONE);
            }
            if (recyclerChannels != null) {
                recyclerChannels.setVisibility(View.VISIBLE);
            }
            dismissNoChannelsModal();
        }
    }

    public void showNoChannelsModal() {
        if (isFinishing() || isDestroyed()) return;
        if (noChannelsModalDialog != null && noChannelsModalDialog.isShowing()) return;

        noChannelsModalDialog = new CustomDialog.Builder(this)
                .setTitle(getString(R.string.title_no_channels_found))
                .setIcon(R.drawable.ic_tv)
                .setMessage(getString(R.string.msg_no_channels_found))
                .setPositiveButton(getString(R.string.btn_retry), dialog -> {
                    dialog.dismiss();
                    hasShownEmptyModal = false;
                    Toast.makeText(this, "Refreshing channels from server...", Toast.LENGTH_SHORT).show();
                    DataPollingManager.getInstance(this).triggerSyncNow();
                })
                .setNegativeButton(getString(R.string.btn_close), dialog -> dialog.dismiss())
                .setCancelable(true)
                .create();

        noChannelsModalDialog.show();
    }

    private void dismissNoChannelsModal() {
        if (noChannelsModalDialog != null && noChannelsModalDialog.isShowing()) {
            try {
                noChannelsModalDialog.dismiss();
            } catch (Exception ignored) {}
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
            return FocusManager.getInstance().handleBackPress(this, SCREEN_KEY, this::handleMainBackPressInternal, null);
        }
        return super.dispatchKeyEvent(event);
    }

    private void showExitDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (currentExitDialog != null && currentExitDialog.isShowing()) return;

        View customExitView = getLayoutInflater().inflate(R.layout.layout_exit_dialog_main, null);
        currentExitDialog = new CustomDialog.Builder(this)
                .setTitle(getString(R.string.exit_app_title))
                .setTitleTextColor(0xFFD0BCFF)
                .setIcon(R.drawable.ic_tv)
                .setMessage(getString(R.string.exit_app_message))
                .setView(customExitView)
                .setWidthPercent(0.50f)
                .setBackgroundDrawable(R.drawable.bg_exit_dialog_main)
                .setPositiveButtonDrawable(R.drawable.btn_exit_positive)
                .setPositiveButton(getString(R.string.btn_exit_app), dialog -> {
                    if (dialog != null) {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {}
                    }
                    currentExitDialog = null;
                    finishAffinity();
                })
                .setNegativeButton(getString(R.string.btn_stay_app), dialog -> {
                    if (dialog != null) {
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {}
                    }
                    currentExitDialog = null;
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        showExitDialog();
    }

    private void updateNotificationBadge() {
        if (notificationManager != null) {
            notificationManager.loadNotifications((notifications, unreadCount) -> {
                runOnUiThread(() -> updateBadgeUI(unreadCount));
            });
        }
    }

    private void updateBadgeUI(int unreadCount) {
        if (txtNotificationBadge != null) {
            if (unreadCount > 0) {
                txtNotificationBadge.setText(String.valueOf(unreadCount));
                txtNotificationBadge.setVisibility(View.VISIBLE);
            } else {
                txtNotificationBadge.setVisibility(View.GONE);
            }
        }
    }

    public void restoreActiveFocus() {
        if (isFinishing() || isDestroyed()) return;
        if (GROUP_CHANNELS.equals(currentActiveSection) && channelAdapter != null && channelAdapter.getItemCount() > 0) {
            setCategoryPanelExpanded(false);
            focusChannelAtPosition(lastFocusedChannelPosition);
        } else if (GROUP_HEADER.equals(currentActiveSection) && !isInitialLaunch) {
            setCategoryPanelExpanded(true);
            if (edtSearch != null) {
                edtSearch.requestFocus();
            } else {
                focusSelectedCategory();
            }
        } else {
            setCategoryPanelExpanded(true);
            focusSelectedCategory();
        }
    }

    private void restoreFocus() {
        if (isInitialLaunch) {
            isInitialLaunch = false;
            focusFirstCategoryItem();
            return;
        }

        View current = getCurrentFocus();
        if (current != null && current != findViewById(android.R.id.content)) {
            if (recyclerCategories != null && recyclerCategories.findContainingItemView(current) != null) {
                currentActiveSection = GROUP_CATEGORIES;
                setCategoryPanelExpanded(true);
                return;
            }
            if (recyclerChannels != null && recyclerChannels.findContainingItemView(current) != null) {
                currentActiveSection = GROUP_CHANNELS;
                setCategoryPanelExpanded(false);
                return;
            }
            if (isHeaderViewFocused(current)) {
                currentActiveSection = GROUP_HEADER;
                setCategoryPanelExpanded(true);
                return;
            }
        }

        FocusViewModel vm = FocusManager.getInstance().getViewModel(this);
        if (vm != null && vm.hasSavedFocus(SCREEN_KEY)) {
            String lastGroup = vm.getLastFocusedGroupKey(SCREEN_KEY);
            if (GROUP_CHANNELS.equals(lastGroup)) {
                setCategoryPanelExpanded(false);
                int savedChannelId = vm.getLastFocusedItemId(SCREEN_KEY, GROUP_CHANNELS, -1);
                if (savedChannelId != -1) {
                    focusChannelById(savedChannelId);
                } else {
                    focusChannelAtPosition(lastFocusedChannelPosition);
                }
                return;
            } else if (GROUP_CATEGORIES.equals(lastGroup)) {
                setCategoryPanelExpanded(true);
                int savedCategoryId = vm.getLastFocusedItemId(SCREEN_KEY, GROUP_CATEGORIES, -1);
                if (savedCategoryId != -1) {
                    focusCategoryById(savedCategoryId);
                } else {
                    focusSelectedCategory();
                }
                return;
            }
        }

        String homeSection = FocusManager.getInstance().getHomeActiveSection();
        if (GROUP_CHANNELS.equals(homeSection)) {
            setCategoryPanelExpanded(false);
            int homeChannelId = FocusManager.getInstance().getHomeLastChannelId(-1);
            if (homeChannelId != -1) {
                focusChannelById(homeChannelId);
            } else {
                focusChannelAtPosition(lastFocusedChannelPosition);
            }
            return;
        } else if (GROUP_CATEGORIES.equals(homeSection)) {
            setCategoryPanelExpanded(true);
            int homeCatId = FocusManager.getInstance().getHomeLastCategoryId(selectedCategoryId);
            focusCategoryById(homeCatId);
            return;
        }

        restoreActiveFocus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.hideSystemUI(this);

        // VPN & Proxy Security Guard: Block access if VPN is active
        if (com.ottking.devcode.security.VpnDetectionManager.isVpnOrProxyActive(this)) {
            com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(this, () -> {
                updateNetworkStatusUI(checkIsConnected());
                DataPollingManager.getInstance(MainActivity.this).triggerSyncNow();
            });
            return;
        }

        com.ottking.devcode.security.VpnDetectionManager.getInstance().startMonitoring(this, new com.ottking.devcode.security.VpnDetectionManager.VpnStateListener() {
            @Override
            public void onVpnDetected() {
                runOnUiThread(() -> {
                    com.ottking.devcode.security.VpnDetectionManager.getInstance().showVpnBlockingDialog(MainActivity.this, () -> {
                        updateNetworkStatusUI(checkIsConnected());
                        DataPollingManager.getInstance(MainActivity.this).triggerSyncNow();
                    });
                });
            }

            @Override
            public void onVpnDisconnected() {
                runOnUiThread(() -> {
                    com.ottking.devcode.security.VpnDetectionManager.getInstance().dismissDialog();
                    updateNetworkStatusUI(checkIsConnected());
                    DataPollingManager.getInstance(MainActivity.this).triggerSyncNow();
                });
            }
        });

        updateNotificationBadge();
        filterChannels();
        updateCategoriesAndSelection();
        
        // Register sync listener and start polling
        DataPollingManager.getInstance(this).addSyncListener(syncListener);
        DataPollingManager.getInstance(this).startPolling();
        DataPollingManager.getInstance(this).triggerSyncNow();

        updateNetworkStatusUI(checkIsConnected());
        restoreFocus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DataPollingManager.getInstance(this).removeSyncListener(syncListener);
        DataPollingManager.getInstance(this).stopPolling();
        com.ottking.devcode.security.VpnDetectionManager.getInstance().stopMonitoring(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        dismissNoChannelsModal();
        if (currentExitDialog != null) {
            try {
                if (currentExitDialog.isShowing()) {
                    currentExitDialog.dismiss();
                }
            } catch (Exception ignored) {}
            currentExitDialog = null;
        }
        if (notificationPanelDialog != null) {
            try {
                notificationPanelDialog.dismiss();
            } catch (Exception ignored) {}
            notificationPanelDialog = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            UIUtils.hideSystemUI(this);
            restoreFocus();
        }
    }

    private void setupNetworkMonitoring() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        updateNetworkStatusUI(checkIsConnected());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> {
                        updateNetworkStatusUI(true);
                        DataPollingManager.getInstance(MainActivity.this).triggerSyncNow();
                    });
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> updateNetworkStatusUI(false));
                }

                @Override
                public void onUnavailable() {
                    runOnUiThread(() -> updateNetworkStatusUI(false));
                }
            };

            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
        }
    }

    private boolean checkIsConnected() {
        return NetworkUtils.isNetworkConnected(this);
    }

    private void updateNetworkStatusUI(boolean isConnected) {
        if (isConnected) {
            if (viewNetworkDot != null) viewNetworkDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            if (txtNetworkStatus != null) {
                txtNetworkStatus.setText("ONLINE");
                txtNetworkStatus.setTextColor(Color.parseColor("#4CAF50"));
            }
            if (bannerNoInternet != null) {
                bannerNoInternet.setVisibility(View.GONE);
            }
        } else {
            if (viewNetworkDot != null) viewNetworkDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            if (txtNetworkStatus != null) {
                txtNetworkStatus.setText("OFFLINE");
                txtNetworkStatus.setTextColor(Color.parseColor("#FF5252"));
            }
            if (bannerNoInternet != null) {
                bannerNoInternet.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        com.ottking.devcode.security.VpnDetectionManager.getInstance().stopMonitoring(this);
        com.ottking.devcode.security.VpnDetectionManager.getInstance().dismissDialog();
        if (currentExitDialog != null) {
            try {
                if (currentExitDialog.isShowing()) {
                    currentExitDialog.dismiss();
                }
            } catch (Exception ignored) {}
            currentExitDialog = null;
        }
        if (notificationPanelDialog != null) {
            try {
                notificationPanelDialog.dismiss();
            } catch (Exception ignored) {}
            notificationPanelDialog = null;
        }
        dismissNoChannelsModal();
        DataPollingManager.getInstance(this).removeSyncListener(syncListener);
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }
}
