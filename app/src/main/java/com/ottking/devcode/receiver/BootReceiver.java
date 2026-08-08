package com.ottking.devcode.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.ottking.devcode.preferences.AppPreferences;
import com.ottking.devcode.ui.SplashActivity;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) {
            AppPreferences prefs = AppPreferences.getInstance(context);
            if (prefs.isBootPlayerEnabled()) {
                Intent splashIntent = new Intent(context, SplashActivity.class);
                splashIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(splashIntent);
            }
        }
    }
}
