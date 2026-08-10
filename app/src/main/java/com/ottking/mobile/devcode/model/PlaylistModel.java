package com.ottking.mobile.devcode.model;

import java.io.Serializable;

public class PlaylistModel implements Serializable {
    private String id;
    private String title;
    private String description;
    private String categoryFilter; // e.g. "Sports", "News", "movie", "series", "MPEG-TS", "favorite", "all", "custom"
    private String iconUrl;
    private int channelCount;

    public PlaylistModel(String id, String title, String description, String categoryFilter, String iconUrl, int channelCount) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.categoryFilter = categoryFilter;
        this.iconUrl = iconUrl;
        this.channelCount = channelCount;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategoryFilter() { return categoryFilter; }
    public String getIconUrl() { return iconUrl; }
    public int getChannelCount() { return channelCount; }
    public void setChannelCount(int channelCount) { this.channelCount = channelCount; }
}
