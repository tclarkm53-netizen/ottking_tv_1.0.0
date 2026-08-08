package com.ottking.devcode.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ottking.devcode.R;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.network.DataPollingManager;
import com.ottking.devcode.preferences.AppPreferences;

import com.ottking.devcode.utils.UIUtils;

public class SplashActivity extends AppCompatActivity {

    private TextView txtSplashStatus;
    private AppPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UIUtils.hideSystemUI(this);
        setContentView(R.layout.activity_splash);

        txtSplashStatus = findViewById(R.id.txtSplashStatus);
        prefs = AppPreferences.getInstance(this);

        // Start background polling service
        DataPollingManager.getInstance(this).startPolling();

        // Perform Initial Sync
        performServerSyncAndRoute();
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

    private void performServerSyncAndRoute() {
        txtSplashStatus.setText("Syncing Live TV Channels...");

        // Trigger server sync
        ApiClient.getInstance(this).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> navigateNextScreen(), 600);
            }

            @Override
            public void onError(String errorMessage) {
                showAppErrorDialog(errorMessage);
            }
        });
    }

    private void navigateNextScreen() {
        boolean isBootPlayer = prefs.isBootPlayerEnabled();
        if (isBootPlayer) {
            Intent intent = new Intent(SplashActivity.this, PlayerActivity.class);
            startActivity(intent);
        } else {
            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
        }
        finish();
    }

    private void showAppPermanentlyDownDialog() {
        new CustomDialog.Builder(this)
                .setTitle(getString(R.string.title_app_permanently_down))
                .setMessage(getString(R.string.msg_app_permanently_down))
                .setCancelable(false)
                .setWidthPercent(0.95f)
                .setPositiveButton(getString(R.string.btn_retry), dialog -> {
                    dialog.dismiss();
                    performServerSyncAndRoute();
                })
                .setNegativeButton(getString(R.string.btn_cancel), dialog -> finish())
                .show();
    }

    private void showAppErrorDialog(String error) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            new CustomDialog.Builder(this)
                    .setTitle(getString(R.string.title_server_connection_failed))
                    .setMessage(getString(R.string.msg_server_connection_failed) + "\n\n" + error)
                    .setCancelable(false)
                    .setWidthPercent(0.95f)
                    .setPositiveButton(getString(R.string.btn_retry), dialog -> {
                        dialog.dismiss();
                        performServerSyncAndRoute();
                    })
                    .setNegativeButton(getString(R.string.btn_exit_app), dialog -> finish())
                    .show();
        });
    }
}
