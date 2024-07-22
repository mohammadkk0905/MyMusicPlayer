package com.mohammadkk.mymusicplayer.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mohammadkk.mymusicplayer.models.QueueItem

@Dao
interface QueueItemsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(queueItems: List<QueueItem>)

    @Query("SELECT * FROM queue_items ORDER BY song_order")
    fun getAll(): List<QueueItem>

    @Query("UPDATE queue_items SET is_current = 0")
    fun resetCurrent()

    @Query("SELECT * FROM queue_items WHERE is_current = 1")
    fun getCurrent(): QueueItem?

    @Query("UPDATE queue_items SET is_current = 1 WHERE song_id = :songId")
    fun saveCurrentSong(songId: Long)

    @Query("UPDATE queue_items SET is_current = 1, last_position = :lastPosition WHERE song_id = :songId")
    fun saveCurrentSongProgress(songId: Long, lastPosition: Int)

    @Query("DELETE FROM queue_items")
    fun deleteAllItems()
}