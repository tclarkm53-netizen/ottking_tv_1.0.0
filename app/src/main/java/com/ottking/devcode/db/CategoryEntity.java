package com.ottking.devcode.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey
    public int id;
    public String name;
    public String icon;

    public CategoryEntity(int id, String name, String icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }
}
