package com.ottking.devcode.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY id ASC")
    fun getAllChannels(): LiveData<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY id ASC")
    fun getAllChannelsSync(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE categoryId = :catId ORDER BY id ASC")
    fun getChannelsByCategory(catId: Int): LiveData<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    fun deleteAll()
}
