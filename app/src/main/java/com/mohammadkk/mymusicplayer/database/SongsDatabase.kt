package com.mohammadkk.mymusicplayer.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.QueueItem

@Database(entities = [QueueItem::class], version = 1)
abstract class SongsDatabase : RoomDatabase() {
    abstract fun QueueItemsDao(): QueueItemsDao

    companion object {
        @Volatile private var INSTANCE: SongsDatabase? = null

        fun getInstance(context: Context): SongsDatabase {
            val currentInstance = INSTANCE
            if (currentInstance != null) return currentInstance

            synchronized(this) {
                val newInstance = Room.databaseBuilder(context.applicationContext, SongsDatabase::class.java, "songs.db")
                    .setQueryExecutor(Constant.MY_EXECUTOR)
                    .build()

                INSTANCE = newInstance
                return newInstance
            }
        }
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}