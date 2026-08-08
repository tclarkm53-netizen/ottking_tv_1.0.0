package com.ottking.devcode.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ottking.devcode.preferences.AppPreferences;

public class DataPollingManager {

    private static DataPollingManager instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private static final long INTERVAL_MS = 30000; // 30 seconds real-time polling

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;
            AppPreferences prefs = AppPreferences.getInstance(context);
            if (prefs.isAutoSyncEnabled()) {
                ApiClient.getInstance(context).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
                    @Override
                    public void onSuccess(Boolean result) {}
                    @Override
                    public void onError(String errorMessage) {}
                });
            }
            handler.postDelayed(pollRunnable, INTERVAL_MS);
        }
    };

    private DataPollingManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized DataPollingManager getInstance(Context context) {
        if (instance == null) {
            instance = new DataPollingManager(context);
        }
        return instance;
    }

    public void startPolling() {
        if (isPolling) return;
        isPolling = true;
        handler.post(pollRunnable);
    }

    public void stopPolling() {
        isPolling = false;
        handler.removeCallbacks(pollRunnable);
    }
}
