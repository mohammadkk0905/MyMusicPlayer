package com.mohammadkk.mymusicplayer.providers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.provider.MediaStore.Audio.AudioColumns
import androidx.core.database.sqlite.transaction
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.utils.Libraries

class MusicPlaybackQueue(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        createTable(db, PLAYING_QUEUE_TABLE_NAME)
        createTable(db, ORIGINAL_PLAYING_QUEUE_TABLE_NAME)
    }
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $PLAYING_QUEUE_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $ORIGINAL_PLAYING_QUEUE_TABLE_NAME")
        onCreate(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $PLAYING_QUEUE_TABLE_NAME")
        db.execSQL("DROP TABLE IF EXISTS $ORIGINAL_PLAYING_QUEUE_TABLE_NAME")
        onCreate(db)
    }
    @Synchronized
    fun saveQueues(playingQueue: List<Song>, originalPlayingQueue: List<Song>) {
        saveQueue(PLAYING_QUEUE_TABLE_NAME, playingQueue)
        saveQueue(ORIGINAL_PLAYING_QUEUE_TABLE_NAME, originalPlayingQueue)
    }
    private fun createTable(db: SQLiteDatabase, tableName: String) {
        val builder = "CREATE TABLE IF NOT EXISTS $tableName(" +
                BaseColumns._ID + " LONG NOT NULL," +
                AudioColumns.TITLE + " TEXT NOT NULL," +
                AudioColumns.TRACK + " INT NOT NULL," +
                AudioColumns.YEAR + " INT NOT NULL," +
                AudioColumns.DURATION + " LONG NOT NULL," +
                AudioColumns.DATA + " TEXT NOT NULL," +
                AudioColumns.DATE_MODIFIED + " LONG NOT NULL," +
                AudioColumns.ALBUM_ID + " LONG NOT NULL," +
                AudioColumns.ALBUM + " TEXT NOT NULL," +
                AudioColumns.ARTIST_ID + " LONG NOT NULL," +
                AudioColumns.ARTIST + " TEXT NOT NULL," +
                AudioColumns.COMPOSER + " TEXT,album_artist TEXT);"

        db.execSQL(builder)
    }

    val savedOriginalPlayingQueue: List<Song>
        get() = getQueue(ORIGINAL_PLAYING_QUEUE_TABLE_NAME)

    val savedPlayingQueue: List<Song>
        get() = getQueue(PLAYING_QUEUE_TABLE_NAME)

    private fun getQueue(tableName: String): List<Song> {
        val cursor = readableDatabase.query(
            tableName, Libraries.BASE_PROJECTION, null, null, null, null, null
        )
        return Libraries.fetchAllSongs(cursor)
    }
    @Synchronized
    private fun saveQueue(tableName: String, queue: List<Song>) {
        writableDatabase?.writeList(queue, tableName) { _, song ->
            ContentValues(13).apply {
                put(BaseColumns._ID, song.id)
                put(AudioColumns.TITLE, song.title)
                put(AudioColumns.TRACK, song.track)
                put(AudioColumns.YEAR, song.year)
                put(AudioColumns.DURATION, song.duration)
                put(AudioColumns.DATA, song.data)
                put(AudioColumns.DATE_MODIFIED, song.dateModified)
                put(AudioColumns.ALBUM_ID, song.albumId)
                put(AudioColumns.ALBUM, song.album)
                put(AudioColumns.ARTIST_ID, song.albumId)
                put(AudioColumns.ARTIST, song.artist)
                put(AudioColumns.COMPOSER, song.composer)
                put("album_artist", song.albumArtist)
            }
        }
    }
    companion object {
        const val DATABASE_NAME: String = "music_playback_state.db"

        const val PLAYING_QUEUE_TABLE_NAME: String = "playing_queue"

        const val ORIGINAL_PLAYING_QUEUE_TABLE_NAME: String = "original_playing_queue"

        private const val VERSION = 12

        private var sInstance: MusicPlaybackQueue? = null

        @Synchronized
        fun getInstance(context: Context): MusicPlaybackQueue {
            if (sInstance == null) {
                sInstance = MusicPlaybackQueue(context.applicationContext)
            }
            return sInstance!!
        }
        inline fun <reified T> SQLiteDatabase.writeList(
            list: List<T>,
            tableName: String,
            transform: (Int, T) -> ContentValues
        ) {
            transaction { delete(tableName, null, null) }
            var transactionPosition = 0
            while (transactionPosition < list.size) {
                var i = transactionPosition
                transaction {
                    while (i < list.size) {
                        val values = transform(i, list[i])
                        i++
                        insert(tableName, null, values)
                    }
                }
                transactionPosition = i
            }
        }
    }
}