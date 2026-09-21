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

    public String getDecryptedStreamUrl(android.content.Context context) {
        return com.ottking.devcode.security.DatabaseKeyManager.getDecryptedUrl(context, this.streamUrl);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelEntity that = (ChannelEntity) o;
        if (id != that.id) return false;
        if (categoryId != that.categoryId) return false;
        if (isPremium != that.isPremium) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (logoUrl != null ? !logoUrl.equals(that.logoUrl) : that.logoUrl != null) return false;
        if (streamUrl != null ? !streamUrl.equals(that.streamUrl) : that.streamUrl != null) return false;
        return streamType != null ? streamType.equals(that.streamType) : that.streamType == null;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (logoUrl != null ? logoUrl.hashCode() : 0);
        result = 31 * result + (streamUrl != null ? streamUrl.hashCode() : 0);
        result = 31 * result + categoryId;
        result = 31 * result + (isPremium ? 1 : 0);
        result = 31 * result + (streamType != null ? streamType.hashCode() : 0);
        return result;
    }
}
