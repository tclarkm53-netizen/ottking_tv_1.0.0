package com.ottking.devcode.model;

public class UserInfo {
    private String username;
    private String packageName;
    private String expiryDate;
    private String deviceId;

    public UserInfo(String username, String packageName, String expiryDate, String deviceId) {
        this.username = username;
        this.packageName = packageName;
        this.expiryDate = expiryDate;
        this.deviceId = deviceId;
    }

    public String getUsername() { return username; }
    public String getPackageName() { return packageName; }
    public String getExpiryDate() { return expiryDate; }
    public String getDeviceId() { return deviceId; }
}
