package com.ottking.devcode.db;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey
    public int id;
    public String name;
    public String icon;

    @ColumnInfo(name = "item_order")
    public int order;

    public CategoryEntity(int id, String name, String icon, int order) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.order = order;
    }

    @Ignore
    public CategoryEntity(int id, String name, String icon) {
        this(id, name, icon, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CategoryEntity that = (CategoryEntity) o;
        if (id != that.id) return false;
        if (order != that.order) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        return icon != null ? icon.equals(that.icon) : that.icon == null;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (icon != null ? icon.hashCode() : 0);
        result = 31 * result + order;
        return result;
    }
}

