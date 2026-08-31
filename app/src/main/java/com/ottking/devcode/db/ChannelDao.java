package com.ottking.devcode.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY id ASC")
    LiveData<List<ChannelEntity>> getAllChannels();

    @Query("SELECT * FROM channels ORDER BY id ASC")
    List<ChannelEntity> getAllChannelsSync();

    @Query("SELECT * FROM channels WHERE categoryId = :catId ORDER BY id ASC")
    LiveData<List<ChannelEntity>> getChannelsByCategory(int catId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ChannelEntity> channels);

    @Query("DELETE FROM channels")
    void deleteAll();
}
