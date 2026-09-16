package com.ottking.devcode.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.ottking.devcode.model.UserInfo;
import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.utils.NetworkUtils;

public class DataPollingManager {

    private static DataPollingManager instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPolling = false;
    private static final long INTERVAL_MS = 15000; // 15 seconds real-time subscription & channel sync

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isPolling) return;

            // Only sync if network is available
            if (NetworkUtils.isNetworkConnected(context)) {
                AppPreferences prefs = AppPreferences.getInstance(context);
                if (prefs.isAutoSyncEnabled()) {
                    // Check subscription status in real-time
                    boolean wasActive = NetworkUtils.isSubscriptionActive(context);

                    ApiClient.getInstance(context).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {}

                        @Override
                        public void onError(String errorMessage) {}
                    });

                    // Check session validity with server if logged in
                    if (prefs.isLoggedIn() && !prefs.getSessionToken().isEmpty()) {
                        ApiClient.getInstance(context).checkSession(new ApiClient.ApiCallback<UserInfo>() {
                            @Override
                            public void onSuccess(UserInfo info) {}

                            @Override
                            public void onError(String errorMessage) {}
                        });
                    }
                }
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
        handler.removeCallbacks(pollRunnable);
        handler.post(pollRunnable);
    }

    public void stopPolling() {
        isPolling = false;
        handler.removeCallbacks(pollRunnable);
    }

    public void triggerSyncNow() {
        if (NetworkUtils.isNetworkConnected(context)) {
            ApiClient.getInstance(context).syncCategoriesAndChannels(new ApiClient.ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {}
                @Override
                public void onError(String errorMessage) {}
            });
        }
    }
}
