package com.ottking.mobile.devcode.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "channels")
public class ChannelEntity {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String streamUrl;
    private String logoUrl;
    private String category; // tv, movie, series
    private String subCategory; // Sports, News, Action, Drama, etc.
    private boolean isFavorite;
    private String streamType; // hls, dash, ts
    private String resolution; // 1080p, 720p, SD

    public ChannelEntity(String title, String streamUrl, String logoUrl, String category, String subCategory, boolean isFavorite, String streamType, String resolution) {
        this.title = title;
        this.streamUrl = streamUrl;
        this.logoUrl = logoUrl;
        this.category = category;
        this.subCategory = subCategory;
        this.isFavorite = isFavorite;
        this.streamType = streamType;
        this.resolution = resolution;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSubCategory() { return subCategory; }
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getStreamType() { return streamType; }
    public void setStreamType(String streamType) { this.streamType = streamType; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
}
