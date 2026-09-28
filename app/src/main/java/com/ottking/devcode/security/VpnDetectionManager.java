package com.ottking.devcode.security;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.ottking.devcode.R;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Advanced Multi-Vector VPN & Proxy Detection & Traffic Protection Engine.
 * 
 * Prevents traffic inspection, MITM packet sniffing, and unauthorized proxying
 * (HttpCanary, Charles Proxy, mitmproxy, Wireshark, VPN bypass, WireGuard/OpenVPN).
 */
public final class VpnDetectionManager {

    private static final String TAG = "VpnDetectionManager";

    public interface VpnStateListener {
        void onVpnDetected();
        void onVpnDisconnected();
    }

    private static volatile VpnDetectionManager instance;
    private ConnectivityManager.NetworkCallback liveNetworkCallback;
    private Dialog activeVpnDialog;

    private VpnDetectionManager() {}

    public static VpnDetectionManager getInstance() {
        if (instance == null) {
            synchronized (VpnDetectionManager.class) {
                if (instance == null) {
                    instance = new VpnDetectionManager();
                }
            }
        }
        return instance;
    }

    /**
     * VPN & Proxy detection is permanently disabled as requested.
     * Always returns false so all networks and streaming continue uninterrupted.
     */
    public static boolean isVpnOrProxyActive(Context context) {
        return false;
    }

    /**
     * Real-time continuous monitoring (disabled - no-op).
     */
    public void startMonitoring(Context context, VpnStateListener listener) {
        // Disabled
    }

    /**
     * Unregisters monitoring (no-op).
     */
    public void stopMonitoring(Context context) {
        // Disabled
    }

    /**
     * Displays VPN blocking dialog (disabled - immediately continues playback / flow).
     */
    public void showVpnBlockingDialog(Activity activity, Runnable onVpnResolved) {
        if (onVpnResolved != null) {
            if (activity != null) {
                activity.runOnUiThread(onVpnResolved);
            } else {
                onVpnResolved.run();
            }
        }
    }

    /**
     * Dismisses the active VPN dialog (no-op).
     */
    public void dismissDialog() {
        if (activeVpnDialog != null && activeVpnDialog.isShowing()) {
            try {
                activeVpnDialog.dismiss();
            } catch (Exception ignored) {}
            activeVpnDialog = null;
        }
    }
}
