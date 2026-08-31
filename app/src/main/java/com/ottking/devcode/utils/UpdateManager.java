package com.ottking.devcode.utils;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.ottking.devcode.R;
import com.ottking.devcode.model.UpdateInfo;
import com.ottking.devcode.network.ApiClient;
import com.ottking.devcode.ui.CustomDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.Executors;

public class UpdateManager {

    private final Activity activity;

    public UpdateManager(Activity activity) {
        this.activity = activity;
    }

    public void checkAndUpdate(boolean showUpToDateToast) {
        ApiClient.getInstance(activity).checkAppUpdate(new ApiClient.ApiCallback<UpdateInfo>() {
            @Override
            public void onSuccess(UpdateInfo info) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (info != null && info.isHasUpdate()) {
                    showUpdateAvailableDialog(info);
                } else if (showUpToDateToast) {
                    new CustomDialog.Builder(activity)
                            .setTitle(activity.getString(R.string.title_up_to_date))
                            .setMessage(activity.getString(R.string.msg_up_to_date))
                            .setPositiveButton(activity.getString(R.string.btn_ok), dialog -> dialog.dismiss())
                            .show();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (showUpToDateToast) {
                    Toast.makeText(activity, "Update check failed: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void showUpdateAvailableDialog(UpdateInfo info) {
        new CustomDialog.Builder(activity)
                .setTitle(activity.getString(R.string.title_new_update) + " (v" + info.getVersionName() + ")")
                .setMessage("Changelog:\n\n" + info.getChangelog() + "\n\nWould you like to download and install this update now?")
                .setWidthPercent(0.95f)
                .setPositiveButton(activity.getString(R.string.btn_update_now), dialog -> {
                    dialog.dismiss();
                    downloadAndInstallApk(info.getUpdateUrl(), info.getVersionName());
                })
                .setNegativeButton(activity.getString(R.string.btn_later), dialog -> dialog.dismiss())
                .show();
    }

    public void downloadAndInstallApk(String apkUrl, String versionName) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(36, 24, 36, 24);

        TextView txtStatus = new TextView(activity);
        txtStatus.setText("Connecting to server...");
        txtStatus.setTextColor(activity.getColor(R.color.white));
        txtStatus.setTextSize(15);
        txtStatus.setPadding(0, 0, 0, 16);
        layout.addView(txtStatus);

        ProgressBar progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setIndeterminate(false);
        layout.addView(progressBar);

        TextView txtProgress = new TextView(activity);
        txtProgress.setText("Downloaded: 0.0 MB / 0.0 MB (0%)");
        txtProgress.setTextColor(activity.getColor(R.color.text_secondary));
        txtProgress.setTextSize(13);
        txtProgress.setPadding(0, 16, 0, 0);
        layout.addView(txtProgress);

        TextView txtSpeed = new TextView(activity);
        txtSpeed.setText("Speed: -- KB/s");
        txtSpeed.setTextColor(activity.getColor(R.color.gold_primary));
        txtSpeed.setTextSize(12);
        txtSpeed.setPadding(0, 6, 0, 0);
        layout.addView(txtSpeed);

        final Dialog downloadDialog = new CustomDialog.Builder(activity)
                .setTitle("Downloading OTT KING v" + versionName)
                .setView(layout)
                .setWidthPercent(0.95f)
                .setCancelable(false)
                .setNegativeButton("Cancel", dialog -> dialog.dismiss())
                .show();

        final boolean[] isCancelled = {false};
        downloadDialog.setOnDismissListener(d -> isCancelled[0] = true);

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;

            try {
                String currentUrl = apkUrl;
                int redirects = 0;

                while (redirects < 5) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(20000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "OTT-KING-Android-Updater");

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == 307 || responseCode == 308) {
                        currentUrl = connection.getHeaderField("Location");
                        connection.disconnect();
                        redirects++;
                    } else {
                        break;
                    }
                }

                if (connection == null || connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP response error: " + (connection != null ? connection.getResponseCode() : "No Connection"));
                }

                int fileLength = connection.getContentLength();
                input = connection.getInputStream();

                File cacheDir = activity.getExternalCacheDir();
                if (cacheDir == null) cacheDir = activity.getCacheDir();
                File apkFile = new File(cacheDir, "ottking_update.apk");
                if (apkFile.exists()) apkFile.delete();

                output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                long startTime = System.currentTimeMillis();
                long lastTime = startTime;
                long bytesSinceLastTime = 0;

                while ((count = input.read(data)) != -1) {
                    if (isCancelled[0]) {
                        if (apkFile.exists()) apkFile.delete();
                        return;
                    }

                    output.write(data, 0, count);
                    total += count;
                    bytesSinceLastTime += count;

                    long now = System.currentTimeMillis();
                    if (now - lastTime >= 300) {
                        long timeDiff = now - lastTime;
                        double speedKbps = (bytesSinceLastTime / 1024.0) / (timeDiff / 1000.0);
                        String speedText;
                        if (speedKbps >= 1024) {
                            speedText = String.format(Locale.US, "Speed: %.2f MB/s", speedKbps / 1024.0);
                        } else {
                            speedText = String.format(Locale.US, "Speed: %.1f KB/s", speedKbps);
                        }

                        lastTime = now;
                        bytesSinceLastTime = 0;

                        final long finalTotal = total;
                        final int totalLength = fileLength;

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (downloadDialog.isShowing()) {
                                txtStatus.setText("Downloading update file...");
                                double currentMb = finalTotal / (1024.0 * 1024.0);

                                if (totalLength > 0) {
                                    int progress = (int) (finalTotal * 100 / totalLength);
                                    double totalMb = totalLength / (1024.0 * 1024.0);
                                    progressBar.setIndeterminate(false);
                                    progressBar.setProgress(Math.min(progress, 100));
                                    txtProgress.setText(String.format(Locale.US, "Downloaded: %.1f MB / %.1f MB (%d%%)", currentMb, totalMb, Math.min(progress, 100)));
                                } else {
                                    progressBar.setIndeterminate(true);
                                    txtProgress.setText(String.format(Locale.US, "Downloaded: %.1f MB", currentMb));
                                }
                                txtSpeed.setText(speedText);
                            }
                        });
                    }
                }

                output.flush();

                File finalApk = apkFile;
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    Toast.makeText(activity, "Download complete! Opening installer...", Toast.LENGTH_SHORT).show();
                    promptInstallApk(finalApk);
                });

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (downloadDialog.isShowing()) {
                        downloadDialog.dismiss();
                    }
                    new CustomDialog.Builder(activity)
                            .setTitle("Download Error")
                            .setMessage("Failed to download update: " + e.getLocalizedMessage() + "\n\nWould you like to retry?")
                            .setWidthPercent(0.95f)
                            .setPositiveButton("Retry Download", dialog -> {
                                dialog.dismiss();
                                downloadAndInstallApk(apkUrl, versionName);
                            })
                            .setNegativeButton("Cancel", dialog -> dialog.dismiss())
                            .show();
                });
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
            }
        });
    }

    public void promptInstallApk(File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            Toast.makeText(activity, "APK update file not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                new CustomDialog.Builder(activity)
                        .setTitle("Permission Required")
                        .setMessage("OTT KING requires permission to install app updates.\n\nPlease enable 'Install Unknown Apps' for OTT KING on the next screen.")
                        .setWidthPercent(0.95f)
                        .setPositiveButton("Grant Permission", dialog -> {
                            dialog.dismiss();
                            activity.startActivity(new Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:" + activity.getPackageName())
                            ));
                        })
                        .setNegativeButton("Cancel", dialog -> dialog.dismiss())
                        .show();
                return;
            }
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".provider", apkFile);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(activity, "Error starting Package Installer: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
