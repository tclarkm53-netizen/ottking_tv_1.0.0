package com.ottking.devcode.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    LiveData<List<CategoryEntity>> getAllCategories();

    @Query("SELECT * FROM categories ORDER BY id ASC")
    List<CategoryEntity> getAllCategoriesSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryEntity> categories);

    @Query("DELETE FROM categories")
    void deleteAll();
}
