package com.ottking.mobile.devcode.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ChannelDao {

    @Query("SELECT * FROM channels ORDER BY id ASC")
    List<ChannelEntity> getAllChannels();

    @Query("SELECT * FROM channels WHERE category = :category ORDER BY id ASC")
    List<ChannelEntity> getChannelsByCategory(String category);

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY id ASC")
    List<ChannelEntity> getFavoriteChannels();

    @Query("SELECT * FROM channels WHERE title LIKE '%' || :query || '%' OR subCategory LIKE '%' || :query || '%'")
    List<ChannelEntity> searchChannels(String query);

    @Query("SELECT COUNT(*) FROM channels")
    int getChannelCount();

    @Query("DELETE FROM channels WHERE subCategory = :subCategory")
    void deleteBySubCategory(String subCategory);

    @Query("DELETE FROM channels WHERE LOWER(category) = LOWER(:category)")
    void deleteByCategory(String category);

    @Query("DELETE FROM channels")
    void deleteAll();

    @Query("UPDATE channels SET isFavorite = 0")
    void clearAllFavorites();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChannelEntity> channels);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ChannelEntity channel);

    @Update
    void update(ChannelEntity channel);
}
