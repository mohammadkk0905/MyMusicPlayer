package com.mohammadkk.mymusicplayer.models

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "queue_items", primaryKeys = ["song_id"])
data class QueueItem(
    @ColumnInfo(name = "song_id") var songId: Long,
    @ColumnInfo(name = "song_order") var songOrder: Int,
    @ColumnInfo(name = "is_current") var isCurrent: Boolean,
    @ColumnInfo(name = "last_position") var lastPosition: Int
) {
    constructor(id: Long, position: Int = 0) : this(
        songId = id, songOrder = 0, isCurrent = true, lastPosition = position
    )
}