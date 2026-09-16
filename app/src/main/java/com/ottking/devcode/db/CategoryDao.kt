package com.ottking.devcode.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): LiveData<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategoriesSync(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(categories: List<CategoryEntity>)

    @Query("DELETE FROM categories")
    fun deleteAll()
}
