package com.ottking.devcode.model;

import java.io.Serializable;

public class Channel implements Serializable {
    private int id;
    private String name;
    private String logoUrl;
    private String streamUrl;
    private int categoryId;
    private boolean isPremium;
    private String streamType;

    public Channel(int id, String name, String logoUrl, String streamUrl, int categoryId, boolean isPremium, String streamType) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.streamUrl = streamUrl;
        this.categoryId = categoryId;
        this.isPremium = isPremium;
        this.streamType = streamType;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getLogoUrl() { return logoUrl; }
    public String getStreamUrl() { return streamUrl; }
    public int getCategoryId() { return categoryId; }
    public boolean isPremium() { return isPremium; }
    public String getStreamType() { return streamType; }
}
