package com.ottking.devcode.model;

public class UpdateInfo {
    private boolean hasUpdate;
    private int versionCode;
    private String versionName;
    private String changelog;
    private String updateUrl;

    public UpdateInfo(boolean hasUpdate, int versionCode, String versionName, String changelog, String updateUrl) {
        this.hasUpdate = hasUpdate;
        this.versionCode = versionCode;
        this.versionName = versionName;
        this.changelog = changelog;
        this.updateUrl = updateUrl;
    }

    public boolean isHasUpdate() { return hasUpdate; }
    public int getVersionCode() { return versionCode; }
    public String getVersionName() { return versionName; }
    public String getChangelog() { return changelog; }
    public String getUpdateUrl() { return updateUrl; }
}
