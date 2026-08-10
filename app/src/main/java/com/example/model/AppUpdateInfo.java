package com.example.model;

import org.json.JSONObject;
import java.io.Serializable;

public class AppUpdateInfo implements Serializable {
    private int versionCode;
    private String versionName;
    private int minVersionCode;
    private boolean forceUpdate;
    private String title;
    private String message;
    private String downloadUrl;
    private String releaseNotes;
    private String updateTime;

    public AppUpdateInfo(int versionCode, String versionName, int minVersionCode, boolean forceUpdate,
                         String title, String message, String downloadUrl, String releaseNotes, String updateTime) {
        this.versionCode = versionCode;
        this.versionName = versionName != null ? versionName : "";
        this.minVersionCode = minVersionCode;
        this.forceUpdate = forceUpdate;
        this.title = title != null && !title.isEmpty() ? title : "New Update Available!";
        this.message = message != null && !message.isEmpty() ? message : "A new version of the app is available with performance improvements and new features.";
        this.downloadUrl = downloadUrl != null ? downloadUrl : "";
        this.releaseNotes = releaseNotes != null ? releaseNotes : "";
        this.updateTime = updateTime != null ? updateTime : "";
    }

    public static AppUpdateInfo fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            JSONObject updateObj = json.optJSONObject("app_update");
            if (updateObj == null) {
                updateObj = json.optJSONObject("update");
            }
            if (updateObj == null) {
                updateObj = json;
            }

            int vCode = updateObj.optInt("version_code", updateObj.optInt("versionCode", 0));
            String vName = updateObj.optString("version_name", updateObj.optString("versionName", ""));
            int minVCode = updateObj.optInt("min_version_code", updateObj.optInt("minVersionCode", 0));
            boolean force = updateObj.optBoolean("force_update", updateObj.optBoolean("forceUpdate", false));
            String title = updateObj.optString("title", "New Update Available!");
            String message = updateObj.optString("message", updateObj.optString("msg", ""));
            String downloadUrl = updateObj.optString("download_url", updateObj.optString("downloadUrl", updateObj.optString("apk_url", "")));
            String releaseNotes = updateObj.optString("release_notes", updateObj.optString("releaseNotes", updateObj.optString("changelog", "")));
            String updateTime = updateObj.optString("update_time", updateObj.optString("updateTime", ""));

            if (vCode > 0 || !vName.isEmpty()) {
                return new AppUpdateInfo(vCode, vName, minVCode, force, title, message, downloadUrl, releaseNotes, updateTime);
            }
        } catch (Exception ignored) {}
        return null;
    }

    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public int getMinVersionCode() { return minVersionCode; }
    public boolean isForceUpdate() { return forceUpdate; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getDownloadUrl() { return downloadUrl; }
    public String getReleaseNotes() { return releaseNotes; }
    public String getUpdateTime() { return updateTime; }

    public boolean isUpdateAvailable(int currentVersionCode) {
        return versionCode > currentVersionCode;
    }

    public boolean isForceUpdateRequired(int currentVersionCode) {
        return forceUpdate || (minVersionCode > 0 && minVersionCode > currentVersionCode);
    }
}
