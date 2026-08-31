package com.ottking.devcode.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;
import com.ottking.devcode.R;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.network.DataPollingManager;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.utils.NetworkUtils;
import com.ottking.devcode.utils.UIUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public class SplashActivity extends AppCompatActivity {

    private LinearLayout layoutSplashLoading;
    private LinearLayout layoutSplashError;
    private TextView txtSplashStatus;
    private TextView txtErrorTitle;
    private TextView txtErrorMessage;
    private Button btnSplashRetry;
    private Button btnSplashExit;
    private LottieAnimationView lottieSplashLoader;
    private AppPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean hasNavigated = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_splash);

        layoutSplashLoading = findViewById(R.id.layoutSplashLoading);
        layoutSplashError = findViewById(R.id.layoutSplashError);
        txtSplashStatus = findViewById(R.id.txtSplashStatus);
        txtErrorTitle = findViewById(R.id.txtErrorTitle);
        txtErrorMessage = findViewById(R.id.txtErrorMessage);
        btnSplashRetry = findViewById(R.id.btnSplashRetry);
        btnSplashExit = findViewById(R.id.btnSplashExit);
        lottieSplashLoader = findViewById(R.id.lottieSplashLoader);

        prefs = AppPreferences.getInstance(this);

        setupErrorButtons();

        // Start checking network & server sync
        performNetworkAndServerSync();
    }

    private void setupErrorButtons() {
        if (btnSplashRetry != null) {
            btnSplashRetry.setOnFocusChangeListener((v, hasFocus) -> UIUtils.animateFocus(v, hasFocus, 1.06f, 10f));
            btnSplashRetry.setOnClickListener(v -> {
                showLoadingState("পুনরায় সার্ভারের সাথে সংযোগ স্থাপন করা হচ্ছে...");
                handler.postDelayed(this::performNetworkAndServerSync, 400);
            });
        }

        if (btnSplashExit != null) {
            btnSplashExit.setOnFocusChangeListener((v, hasFocus) -> UIUtils.animateFocus(v, hasFocus, 1.05f, 6f));
            btnSplashExit.setOnClickListener(v -> {
                finishAffinity();
                System.exit(0);
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UIUtils.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            UIUtils.hideSystemUI(this);
        }
    }

    private void showLoadingState(String message) {
        if (layoutSplashError != null) layoutSplashError.setVisibility(View.GONE);
        if (layoutSplashLoading != null) layoutSplashLoading.setVisibility(View.VISIBLE);
        if (lottieSplashLoader != null) {
            lottieSplashLoader.playAnimation();
        }
        if (txtSplashStatus != null) {
            txtSplashStatus.setText(message);
        }
    }

    private void showErrorState(String title, String message) {
        if (layoutSplashLoading != null) layoutSplashLoading.setVisibility(View.GONE);
        if (layoutSplashError != null) {
            layoutSplashError.setVisibility(View.VISIBLE);
            if (txtErrorTitle != null) txtErrorTitle.setText(title);
            if (txtErrorMessage != null) txtErrorMessage.setText(message);
            if (btnSplashRetry != null) {
                btnSplashRetry.post(() -> btnSplashRetry.requestFocus());
            }
        }
    }

    private void performNetworkAndServerSync() {
        showLoadingState("ইন্টারনেট ও সার্ভার সংযোগ যাচাই করা হচ্ছে...");

        // 1. Check if device has an active network connection
        if (!NetworkUtils.isNetworkConnected(this)) {
            showErrorState(
                    "ইন্টারনেট সংযোগ পাওয়া যায়নি (No Network)",
                    "আপনার ডিভাইসে কোনো ইন্টারনেট সংযোগ নেই। দয়া করে আপনার Wi-Fi, Ethernet বা মোবাইল ডেটা অন করে পুনরায় চেষ্টা করুন।"
            );
            return;
        }

        showLoadingState("Live TV channels and subscriptions are syncing...");

        // 2. Perform live server sync
        ApiClient.getInstance(this).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                if (txtSplashStatus != null) {
                    txtSplashStatus.setText("Sync successful! Launching the app...");
                }
                
                // Start background real-time data polling
                try {
                    DataPollingManager.getInstance(SplashActivity.this).startPolling();
                } catch (Exception ignored) {}

                handler.postDelayed(SplashActivity.this::navigateNextScreenSafe, 600);
            }

            @Override
            public void onError(String errorMessage) {
                showErrorState(
                        "Server connection issue (Server Error)",
                        errorMessage != null ? errorMessage : "Could not connect to the server. Please try again after a while."
                );
            }
        });
    }

    private void navigateNextScreenSafe() {
        if (hasNavigated.compareAndSet(false, true)) {
            if (isFinishing() || isDestroyed()) return;
            boolean isBootPlayer = prefs != null && prefs.isBootPlayerEnabled();
            if (isBootPlayer) {
                Intent intent = new Intent(SplashActivity.this, PlayerActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
            }
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (layoutSplashError != null && layoutSplashError.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                finishAffinity();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}

