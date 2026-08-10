package com.example.model;

import java.io.Serializable;

public class NotificationModel implements Serializable {
    private String id;
    private String title;
    private String message;
    private String timestamp;
    private int iconRes;
    private boolean isRead;

    public NotificationModel(String id, String title, String message, String timestamp, int iconRes, boolean isRead) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.iconRes = iconRes;
        this.isRead = isRead;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }
    public int getIconRes() { return iconRes; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}
