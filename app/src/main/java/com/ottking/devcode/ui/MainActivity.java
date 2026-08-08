package com.ottking.devcode.ui;

import android.Manifest;
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

import com.ottking.devcode.R;
import com.ottking.devcode.db.AppDatabase;
import com.ottking.devcode.db.CategoryEntity;
import com.ottking.devcode.db.ChannelEntity;

import java.util.ArrayList;
import java.util.List;

import com.ottking.devcode.utils.FocusManager;
import com.ottking.devcode.utils.UIUtils;
import com.ottking.devcode.viewmodel.FocusViewModel;

public class MainActivity extends AppCompatActivity {

    public static final String SCREEN_KEY = "MainActivity";
    public static final String GROUP_CATEGORIES = "categories";
    public static final String GROUP_CHANNELS = "channels";

    private RecyclerView recyclerCategories, recyclerChannels;
    private View categoryContainer;
    private TextView txtCategoryHeader;
    private CategoryAdapter categoryAdapter;
    private ChannelAdapter channelAdapter;
    private TextView txtSelectedCategoryTitle, txtChannelCount, txtNotificationBadge;
    private EditText edtSearch;
    private com.ottking.devcode.utils.AppNotificationManager notificationManager;
    private boolean isCategoryExpanded = true;

    private ActivityResultLauncher<Intent> voiceSearchLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private final List<ChannelEntity> allChannels = new ArrayList<>();
    private final List<ChannelEntity> filteredChannels = new ArrayList<>();
    private int selectedCategoryId = 1; // 1 = All Channels

    private View layoutNetworkStatus, viewNetworkDot;
    private TextView txtNetworkStatus;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;

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
        layoutNetworkStatus = findViewById(R.id.layoutNetworkStatus);
        viewNetworkDot = findViewById(R.id.viewNetworkDot);
        txtNetworkStatus = findViewById(R.id.txtNetworkStatus);

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
            new NotificationPanelDialog(this, unreadCount -> {
                updateBadgeUI(unreadCount);
            }).show();
        });

        updateNotificationBadge();

        setupAdapters();
        observeRoomDatabase();
        setupSearch();
        setupHeaderNavigation();

        FocusManager.getInstance().setupBackPressHandler(this, SCREEN_KEY, this::handleMainBackPressInternal);
    }

    private boolean handleMainBackPressInternal() {
        View currentFocus = getCurrentFocus();
        if (edtSearch != null && (edtSearch.hasFocus() || edtSearch.getText().length() > 0)) {
            edtSearch.setText("");
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
            }
            edtSearch.setCursorVisible(false);
            focusSelectedCategory();
            return true;
        }

        if (currentFocus != null && recyclerChannels != null && recyclerChannels.findContainingItemView(currentFocus) != null) {
            focusSelectedCategory();
            return true;
        }

        showExitDialog();
        return true;
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
            txtSelectedCategoryTitle.setText(category.name);
            filterChannels();
        });
        categoryAdapter.setNavigationListener(new CategoryAdapter.OnCategoryNavigationListener() {
            @Override
            public void onNavigateToChannels() {
                setCategoryPanelExpanded(false);
                focusFirstChannel();
            }

            @Override
            public void onNavigateToHeader() {
                edtSearch.requestFocus();
            }
        });
        categoryAdapter.setFocusListener((pos, view) -> {
            setCategoryPanelExpanded(true);
            FocusManager.getInstance().savePosition(this, SCREEN_KEY, GROUP_CATEGORIES, pos);
            FocusManager.getInstance().saveFocus(this, SCREEN_KEY, view);
        });
        recyclerCategories.setAdapter(categoryAdapter);

        // Channels Grid Layout (exactly 5 columns)
        int spanCount = 5;
        recyclerChannels.setLayoutManager(new GridLayoutManager(this, spanCount));
        channelAdapter = new ChannelAdapter(channel -> {
            int pos = allChannels.indexOf(channel);
            int channelNumber = (pos != -1) ? (pos + 1) : 1;
            Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
            intent.putExtra("channel_id", channel.id);
            intent.putExtra("channel_number", channelNumber);
            intent.putExtra("channel_name", channel.name);
            intent.putExtra("stream_url", channel.streamUrl);
            intent.putExtra("logo_url", channel.logoUrl);
            intent.putExtra("is_premium", channel.isPremium);
            intent.putExtra("stream_type", channel.streamType);
            startActivity(intent);
        });
        channelAdapter.setSpanCount(spanCount);
        channelAdapter.setFocusListener((pos, view) -> {
            setCategoryPanelExpanded(false);
            FocusManager.getInstance().savePosition(this, SCREEN_KEY, GROUP_CHANNELS, pos);
            FocusManager.getInstance().saveFocus(this, SCREEN_KEY, view);
        });
        channelAdapter.setNavigationListener(new ChannelAdapter.OnChannelNavigationListener() {
            @Override
            public void onNavigateToCategories() {
                setCategoryPanelExpanded(true);
                focusSelectedCategory();
            }

            @Override
            public void onNavigateToHeader(int position) {
                View btnSettings = findViewById(R.id.btnSettings);
                View btnNotification = findViewById(R.id.btnNotification);
                View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);

                int col = position % 5;
                if (col >= 3 && btnSettings != null) {
                    btnSettings.requestFocus();
                } else if (col == 2 && btnNotification != null) {
                    btnNotification.requestFocus();
                } else if (col == 1 && btnVoiceSearch != null) {
                    btnVoiceSearch.requestFocus();
                } else {
                    if (edtSearch != null) {
                        edtSearch.setFocusable(true);
                        edtSearch.setFocusableInTouchMode(true);
                        edtSearch.requestFocus();
                    }
                }
            }
        });
        recyclerChannels.setAdapter(channelAdapter);
    }

    public void setCategoryPanelExpanded(boolean expand) {
        if (categoryContainer == null) return;
        ViewGroup.LayoutParams lp = categoryContainer.getLayoutParams();
        int targetWidth = UIUtils.dpToPx(this, 220);
        if (lp != null && lp.width != targetWidth) {
            lp.width = targetWidth;
            categoryContainer.setLayoutParams(lp);
        }
        if (txtCategoryHeader != null && txtCategoryHeader.getVisibility() != View.VISIBLE) {
            txtCategoryHeader.setVisibility(View.VISIBLE);
        }
    }

    private void setupHeaderNavigation() {
        View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
        View btnNotification = findViewById(R.id.btnNotification);
        View btnSettings = findViewById(R.id.btnSettings);

        View.OnFocusChangeListener headerFocusListener = (v, hasFocus) -> {
            UIUtils.animateFocus(v, hasFocus, 1.12f, 10f);
            if (hasFocus) {
                setCategoryPanelExpanded(true);
                FocusManager.getInstance().saveFocus(this, SCREEN_KEY, v);
            }
        };

        if (btnVoiceSearch != null) {
            btnVoiceSearch.setOnFocusChangeListener(headerFocusListener);
            btnVoiceSearch.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        focusSelectedCategory();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        edtSearch.requestFocus();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (btnNotification != null) {
                            btnNotification.requestFocus();
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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        focusSelectedCategory();
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (btnVoiceSearch != null) {
                            btnVoiceSearch.requestFocus();
                            return true;
                        } else {
                            edtSearch.requestFocus();
                            return true;
                        }
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        if (btnSettings != null) {
                            btnSettings.requestFocus();
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
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if (recyclerChannels != null && channelAdapter != null && channelAdapter.getItemCount() > 0) {
                            focusFirstChannel();
                        } else {
                            focusSelectedCategory();
                        }
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (btnNotification != null) {
                            btnNotification.requestFocus();
                            return true;
                        } else if (btnVoiceSearch != null) {
                            btnVoiceSearch.requestFocus();
                            return true;
                        } else {
                            edtSearch.requestFocus();
                            return true;
                        }
                    }
                }
                return false;
            });
        }
    }

    public void focusSelectedCategory() {
        setCategoryPanelExpanded(true);
        if (recyclerCategories == null || categoryAdapter == null) return;
        int pos = categoryAdapter.getSelectedPosition();
        if (pos < 0 || pos >= categoryAdapter.getItemCount()) pos = 0;
        recyclerCategories.scrollToPosition(pos);
        int targetPos = pos;
        RecyclerView.ViewHolder vh = recyclerCategories.findViewHolderForAdapterPosition(targetPos);
        if (vh != null && vh.itemView != null) {
            vh.itemView.setFocusable(true);
            vh.itemView.setFocusableInTouchMode(true);
            vh.itemView.requestFocus();
        } else {
            recyclerCategories.post(() -> {
                RecyclerView.ViewHolder vh2 = recyclerCategories.findViewHolderForAdapterPosition(targetPos);
                if (vh2 != null && vh2.itemView != null) {
                    vh2.itemView.setFocusable(true);
                    vh2.itemView.setFocusableInTouchMode(true);
                    vh2.itemView.requestFocus();
                }
            });
        }
    }

    public void focusFirstChannel() {
        setCategoryPanelExpanded(false);
        if (recyclerChannels != null && channelAdapter != null && channelAdapter.getItemCount() > 0) {
            recyclerChannels.scrollToPosition(0);
            RecyclerView.ViewHolder vh = recyclerChannels.findViewHolderForAdapterPosition(0);
            if (vh != null && vh.itemView != null) {
                vh.itemView.setFocusable(true);
                vh.itemView.setFocusableInTouchMode(true);
                vh.itemView.requestFocus();
            } else {
                recyclerChannels.post(() -> {
                    RecyclerView.ViewHolder vh2 = recyclerChannels.findViewHolderForAdapterPosition(0);
                    if (vh2 != null && vh2.itemView != null) {
                        vh2.itemView.setFocusable(true);
                        vh2.itemView.setFocusableInTouchMode(true);
                        vh2.itemView.requestFocus();
                    }
                });
            }
        }
    }

    private void observeRoomDatabase() {
        AppDatabase db = AppDatabase.getInstance(this);

        db.categoryDao().getAllCategories().observe(this, categories -> {
            if (categories != null && !categories.isEmpty()) {
                categoryAdapter.setCategories(categories);
                recyclerCategories.post(this::focusSelectedCategory);
            }
        });

        db.channelDao().getAllChannels().observe(this, channels -> {
            if (channels != null) {
                allChannels.clear();
                allChannels.addAll(channels);
                filterChannels();
                updateNotificationBadge();
            }
        });
    }

    private void setupSearch() {
        edtSearch.setFocusable(true);
        edtSearch.setFocusableInTouchMode(true);
        edtSearch.setCursorVisible(false);

        edtSearch.setOnFocusChangeListener((v, hasFocus) -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!hasFocus) {
                edtSearch.setCursorVisible(false);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                }
            } else {
                edtSearch.setCursorVisible(true);
                setCategoryPanelExpanded(true);
                FocusManager.getInstance().saveFocus(this, SCREEN_KEY, v);
            }
        });

        Runnable activateSearchInput = () -> {
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
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                    }
                    edtSearch.setCursorVisible(false);
                    focusSelectedCategory();
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(edtSearch.getWindowToken(), 0);
                    }
                    edtSearch.setCursorVisible(false);
                    View btnVoiceSearch = findViewById(R.id.btnVoiceSearch);
                    if (btnVoiceSearch != null) {
                        btnVoiceSearch.requestFocus();
                        return true;
                    } else {
                        View btnNotification = findViewById(R.id.btnNotification);
                        if (btnNotification != null) {
                            btnNotification.requestFocus();
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterChannels();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterChannels() {
        filteredChannels.clear();
        String query = edtSearch.getText().toString().trim().toLowerCase();

        for (ChannelEntity chan : allChannels) {
            boolean matchesCategory = (selectedCategoryId == 1) || (chan.categoryId == selectedCategoryId);
            boolean matchesSearch = query.isEmpty() || chan.name.toLowerCase().contains(query);

            if (matchesCategory && matchesSearch) {
                filteredChannels.add(chan);
            }
        }

        channelAdapter.setAllChannelsList(allChannels);
        channelAdapter.setChannels(filteredChannels);
        if (allChannels.isEmpty()) {
            txtChannelCount.setText("0 Channels (No server data)");
        } else {
            txtChannelCount.setText(filteredChannels.size() + " Channels");
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
        new CustomDialog.Builder(this)
                .setTitle(getString(R.string.exit_app_title))
                .setTitleTextColor(0xFFD0BCFF)
                .setIcon(R.drawable.ic_tv)
                .setMessage(getString(R.string.exit_app_message))
                .setWidthPercent(0.50f)
                .setBackgroundDrawable(R.drawable.bg_exit_dialog_main)
                .setPositiveButtonDrawable(R.drawable.btn_exit_positive)
                .setPositiveButton(getString(R.string.btn_exit_app), dialog -> finishAffinity())
                .setNegativeButton(getString(R.string.btn_stay_app), dialog -> dialog.dismiss())
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

    private void restoreFocus() {
        View current = getCurrentFocus();
        if (current != null && current.getId() != View.NO_ID && current != findViewById(android.R.id.content)) {
            return;
        }

        FocusViewModel vm = FocusManager.getInstance().getViewModel(this);
        if (vm != null && vm.hasSavedFocus(SCREEN_KEY)) {
            String lastGroup = vm.getLastFocusedGroupKey(SCREEN_KEY);
            if (GROUP_CHANNELS.equals(lastGroup)) {
                if (FocusManager.getInstance().restoreRecyclerViewFocus(this, SCREEN_KEY, GROUP_CHANNELS, recyclerChannels, 0)) {
                    return;
                }
            } else if (GROUP_CATEGORIES.equals(lastGroup)) {
                if (FocusManager.getInstance().restoreRecyclerViewFocus(this, SCREEN_KEY, GROUP_CATEGORIES, recyclerCategories, 0)) {
                    return;
                }
            }

            if (FocusManager.getInstance().restoreFocus(this, SCREEN_KEY, null)) {
                return;
            }
        }

        focusSelectedCategory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.hideSystemUI(this);
        updateNotificationBadge();
        restoreFocus();
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
                    runOnUiThread(() -> updateNetworkStatusUI(true));
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
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        if (connectivityManager == null) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork == null) return false;
            NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(activeNetwork);
            return caps != null && (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
        } else {
            NetworkInfo info = connectivityManager.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    private void updateNetworkStatusUI(boolean isConnected) {
        if (viewNetworkDot == null || txtNetworkStatus == null) return;
        if (isConnected) {
            viewNetworkDot.setBackgroundResource(R.drawable.bg_status_dot_online);
            txtNetworkStatus.setText("ONLINE");
            txtNetworkStatus.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            viewNetworkDot.setBackgroundResource(R.drawable.bg_status_dot_offline);
            txtNetworkStatus.setText("OFFLINE");
            txtNetworkStatus.setTextColor(Color.parseColor("#FF5252"));
            Toast.makeText(this, "Network lost: Stream buffering or downtime expected", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (connectivityManager != null && networkCallback != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
    }
}
