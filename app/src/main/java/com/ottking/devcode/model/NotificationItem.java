package com.ottking.devcode.model;

public class NotificationItem {
    private String id;
    private String title;
    private String message;
    private String time;
    private int iconRes;
    private String type; // UPDATE, CHANNEL, SYSTEM
    private boolean isRead;
    private String actionText;

    public NotificationItem(String id, String title, String message, String time, int iconRes, String type, boolean isRead, String actionText) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.time = time;
        this.iconRes = iconRes;
        this.type = type;
        this.isRead = isRead;
        this.actionText = actionText;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTime() {
        return time;
    }

    public int getIconRes() {
        return iconRes;
    }

    public String getType() {
        return type;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public String getActionText() {
        return actionText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NotificationItem that = (NotificationItem) o;
        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
