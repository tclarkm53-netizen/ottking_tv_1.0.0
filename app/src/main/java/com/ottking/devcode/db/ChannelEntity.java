package com.ottking.devcode.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "channels")
public class ChannelEntity {
    @PrimaryKey
    public int id;
    public String name;
    public String logoUrl;
    public String streamUrl;
    public int categoryId;
    public boolean isPremium;
    public String streamType;

    public ChannelEntity(int id, String name, String logoUrl, String streamUrl, int categoryId, boolean isPremium, String streamType) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.streamUrl = streamUrl;
        this.categoryId = categoryId;
        this.isPremium = isPremium;
        this.streamType = streamType;
    }
}
