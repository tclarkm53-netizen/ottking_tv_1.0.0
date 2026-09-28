package com.ottking.devcode.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY item_order ASC, id ASC")
    fun getAllChannels(): LiveData<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY item_order ASC, id ASC")
    fun getAllChannelsSync(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE categoryId = :catId ORDER BY item_order ASC, id ASC")
    fun getChannelsByCategory(catId: Int): LiveData<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    fun getChannelByIdSync(id: Int): ChannelEntity?

    @Query("UPDATE channels SET item_order = :order WHERE id = :id")
    fun updateChannelOrder(id: Int, order: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    fun deleteAll()
}
