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
     * Comprehensive synchronous VPN & Proxy inspection across all system interfaces and transports.
     * Returns true if ANY VPN tunnel, virtual adapter, or HTTP proxy is active.
     */
    public static boolean isVpnOrProxyActive(Context context) {
        if (context == null) return false;

        try {
            // 1. Check ConnectivityManager Active Network Capabilities
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            Log.w(TAG, "Active network has TRANSPORT_VPN");
                            return true;
                        }
                    }

                    // Check all available system networks
                    Network[] allNetworks = cm.getAllNetworks();
                    for (Network network : allNetworks) {
                        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                            Log.w(TAG, "Secondary network has TRANSPORT_VPN");
                            return true;
                        }
                    }
                }
            }

            // 2. Deep Network Interface Inspection (detects tun0, ppp0, tap0, wg0, utun, etc.)
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                    if (networkInterface != null && networkInterface.isUp()) {
                        String name = networkInterface.getName().toLowerCase(Locale.US);
                        if (name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("tap")
                                || name.startsWith("utun") || name.startsWith("wg") || name.contains("vpn")
                                || name.contains("ipsec") || name.startsWith("p2p")) {
                            Log.w(TAG, "Suspicious VPN Network Interface detected: " + name);
                            return true;
                        }
                    }
                }
            }

            // 3. System HTTP/HTTPS Proxy Inspection (Charles, Burp, Fiddler, HttpCanary)
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            if (proxyHost != null && !proxyHost.trim().isEmpty() && !"0".equals(proxyPort)) {
                Log.w(TAG, "System HTTP Proxy detected: " + proxyHost + ":" + proxyPort);
                return true;
            }

            String httpsProxyHost = System.getProperty("https.proxyHost");
            String httpsProxyPort = System.getProperty("https.proxyPort");
            if (httpsProxyHost != null && !httpsProxyHost.trim().isEmpty() && !"0".equals(httpsProxyPort)) {
                Log.w(TAG, "System HTTPS Proxy detected: " + httpsProxyHost + ":" + httpsProxyPort);
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in VPN detection check", e);
        }

        return false;
    }

    /**
     * Registers real-time continuous monitoring for VPN connect/disconnect events.
     */
    public void startMonitoring(Context context, VpnStateListener listener) {
        if (context == null || listener == null) return;
        stopMonitoring(context);

        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            liveNetworkCallback = new ConnectivityManager.NetworkCallback() {
                private final Handler mainHandler = new Handler(Looper.getMainLooper());

                @Override
                public void onAvailable(@NonNull Network network) {
                    checkAndNotify();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    checkAndNotify();
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                    checkAndNotify();
                }

                private void checkAndNotify() {
                    mainHandler.post(() -> {
                        if (isVpnOrProxyActive(context)) {
                            listener.onVpnDetected();
                        } else {
                            listener.onVpnDisconnected();
                        }
                    });
                }
            };

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(liveNetworkCallback);
            } else {
                NetworkRequest request = new NetworkRequest.Builder().build();
                cm.registerNetworkCallback(request, liveNetworkCallback);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error registering VPN network callback", e);
        }
    }

    /**
     * Unregisters real-time monitoring.
     */
    public void stopMonitoring(Context context) {
        if (context == null || liveNetworkCallback == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                cm.unregisterNetworkCallback(liveNetworkCallback);
            }
        } catch (Exception ignored) {
        } finally {
            liveNetworkCallback = null;
        }
    }

    /**
     * Displays a strict, non-dismissible security blocking dialog when VPN is detected.
     */
    public void showVpnBlockingDialog(Activity activity, Runnable onVpnResolved) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        activity.runOnUiThread(() -> {
            if (activeVpnDialog != null && activeVpnDialog.isShowing()) {
                return;
            }

            try {
                activeVpnDialog = new Dialog(activity, R.style.Theme_OttKing_Dialog_Alert);
                activeVpnDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                activeVpnDialog.setContentView(R.layout.dialog_vpn_warning);
                activeVpnDialog.setCancelable(false);
                activeVpnDialog.setCanceledOnTouchOutside(false);

                Window window = activeVpnDialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    window.setGravity(Gravity.CENTER);
                }

                Button btnRetry = activeVpnDialog.findViewById(R.id.btnVpnRetry);
                Button btnExit = activeVpnDialog.findViewById(R.id.btnVpnExit);

                if (btnRetry != null) {
                    btnRetry.setOnClickListener(v -> {
                        if (!isVpnOrProxyActive(activity)) {
                            if (activeVpnDialog != null && activeVpnDialog.isShowing()) {
                                activeVpnDialog.dismiss();
                                activeVpnDialog = null;
                            }
                            Toast.makeText(activity, "The VPN connection has been disconnected. Welcome!", Toast.LENGTH_SHORT).show();
                            if (onVpnResolved != null) {
                                onVpnResolved.run();
                            }
                        } else {
                            Toast.makeText(activity, "The VPN is still on! Please turn off the VPN. ", Toast.LENGTH_LONG).show();
                        }
                    });
                }

                if (btnExit != null) {
                    btnExit.setOnClickListener(v -> {
                        if (activeVpnDialog != null && activeVpnDialog.isShowing()) {
                            activeVpnDialog.dismiss();
                            activeVpnDialog = null;
                        }
                        activity.finishAffinity();
                    });
                }

                activeVpnDialog.show();
            } catch (Exception e) {
                Log.e(TAG, "Error showing VPN warning dialog", e);
            }
        });
    }

    /**
     * Dismisses the active VPN dialog if VPN was turned off.
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
