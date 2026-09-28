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
     * Comprehensive synchronous VPN & Proxy inspection across system interfaces and transports.
     * Accurately identifies genuine active VPN tunnels and proxies while strictly preventing
     * false positives on Android TV (e.g. Wi-Fi Direct p2p0, remote controls, bridge interfaces, dormant drivers).
     */
    public static boolean isVpnOrProxyActive(Context context) {
        if (context == null) return false;

        try {
            // 1. Authoritative Android Platform ConnectivityManager Inspection
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network activeNetwork = cm.getActiveNetwork();
                    if (activeNetwork != null) {
                        NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                        if (caps != null) {
                            // Active network is routing through a VPN transport
                            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                                Log.w(TAG, "Active network has TRANSPORT_VPN");
                                return true;
                            }
                            // Active network explicitly lacks the NET_CAPABILITY_NOT_VPN guarantee
                            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                                Log.w(TAG, "Active network lacks NET_CAPABILITY_NOT_VPN");
                                return true;
                            }
                        }
                    }

                    // Check secondary networks: only consider if it has TRANSPORT_VPN AND active INTERNET capability
                    Network[] allNetworks = cm.getAllNetworks();
                    if (allNetworks != null) {
                        for (Network network : allNetworks) {
                            NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                            if (nc != null) {
                                boolean isVpnTransport = nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                                        || !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                                if (isVpnTransport && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                                    Log.w(TAG, "Active secondary network with Internet has TRANSPORT_VPN: " + network);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            // 2. Hardware / Kernel Network Interface Inspection
            // Specifically looks for ACTIVE virtual VPN tunnels (tun, utun, wg).
            // Strictly excludes Wi-Fi Direct (p2p0, p2p-wlan0), Ethernet (eth0), Wi-Fi (wlan0), and dormant interfaces.
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                    if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback()) {
                        continue;
                    }

                    String name = networkInterface.getName().toLowerCase(Locale.US);

                    // Strictly ignore all non-VPN interfaces common on Android TV & phones
                    if (name.startsWith("p2p") || name.startsWith("wlan") || name.startsWith("eth")
                            || name.startsWith("dummy") || name.startsWith("lo") || name.startsWith("sit")
                            || name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tap")
                            || name.startsWith("ppp") || name.startsWith("bridge") || name.startsWith("vbox")) {
                        continue;
                    }

                    // Only check recognized VPN tunnel patterns (tun0, utun0, wg0, vpn0)
                    boolean isVpnPattern = name.matches("^(tun|utun|wg|vpn)[0-9]+.*$");
                    if (isVpnPattern) {
                        // Crucial: Must have a valid, assigned, non-link-local IP address to be an active routing VPN!
                        Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                        boolean hasRoutableAddress = false;
                        if (addresses != null) {
                            while (addresses.hasMoreElements()) {
                                InetAddress addr = addresses.nextElement();
                                if (addr != null && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                                    hasRoutableAddress = true;
                                    break;
                                }
                            }
                        }

                        if (hasRoutableAddress) {
                            Log.w(TAG, "Active VPN Network Interface detected with valid IP: " + name);
                            return true;
                        }
                    }
                }
            }

            // 3. System HTTP/HTTPS Proxy Inspection (Charles, Burp, Fiddler, HttpCanary)
            // Exclude local loopback addresses (127.0.0.1, localhost) which some TVs use internally
            String proxyHost = System.getProperty("http.proxyHost");
            String proxyPort = System.getProperty("http.proxyPort");
            if (isValidExternalProxy(proxyHost, proxyPort)) {
                Log.w(TAG, "System HTTP Proxy detected: " + proxyHost + ":" + proxyPort);
                return true;
            }

            String httpsProxyHost = System.getProperty("https.proxyHost");
            String httpsProxyPort = System.getProperty("https.proxyPort");
            if (isValidExternalProxy(httpsProxyHost, httpsProxyPort)) {
                Log.w(TAG, "System HTTPS Proxy detected: " + httpsProxyHost + ":" + httpsProxyPort);
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in VPN detection check", e);
        }

        return false;
    }

    private static boolean isValidExternalProxy(String host, String port) {
        if (host == null || host.trim().isEmpty()) return false;
        String cleanHost = host.trim().toLowerCase(Locale.US);
        if ("0".equals(port) || "-1".equals(port)) return false;
        if ("localhost".equals(cleanHost) || "127.0.0.1".equals(cleanHost)
                || "0.0.0.0".equals(cleanHost) || "::1".equals(cleanHost)) {
            return false;
        }
        return true;
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
                if (btnRetry != null) {
                    btnRetry.requestFocus();
                }
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
