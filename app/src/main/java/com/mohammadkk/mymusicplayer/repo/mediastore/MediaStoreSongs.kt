package com.mohammadkk.mymusicplayer.repo.mediastore

import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.provider.MediaStore.Audio.AudioColumns
import androidx.core.database.getStringOrNull
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.loader.ISongs
import com.mohammadkk.mymusicplayer.utils.FileUtils
import java.io.File
import java.text.Collator
import kotlin.math.abs

object MediaStoreSongs : ISongs {
    @JvmStatic
    val BASE_SONG_PROJECTION = arrayOf(
        BaseColumns._ID, // 0
        AudioColumns.TITLE, // 1
        AudioColumns.TRACK, // 2
        AudioColumns.YEAR, // 3
        AudioColumns.DURATION, // 4
        AudioColumns.DATA, // 5
        AudioColumns.DATE_MODIFIED, // 6
        AudioColumns.ALBUM_ID, // 7
        AudioColumns.ALBUM, // 8
        AudioColumns.ARTIST_ID, // 9
        AudioColumns.ARTIST, // 10
        AudioColumns.COMPOSER, // 11
        "album_artist" // 12
    )
    const val BASE_AUDIO_SELECTION = "${AudioColumns.IS_MUSIC} = 1 AND ${AudioColumns.DURATION} >= 5000"

    override suspend fun all(context: Context): List<Song> {
        return intoSongs(querySongs(context))
    }
    override suspend fun otg(): List<Song> {
        val songs = ArrayList<Song>()
        val settings = BaseSettings.getInstance()
        val otgPath = settings.otgPartition
        if (otgPath.isEmpty()) return emptyList()
        val files = FileUtils.listFilesDeep(File(settings.otgPartition), FileUtils.AUDIO_FILE_FILTER)
        for (index in files.indices) {
            val file = files[index]
            songs.add(
                Song(
                    id = index.toLong(),
                    title = file.nameWithoutExtension,
                    track = 0, year = 0, duration = 0,
                    data = file.path,
                    dateModified = file.lastModified(),
                    albumId = 0L, album = "Unknown Album",
                    artistId = 0L, artist = "Unknown Artist",
                    composer = "", albumArtist = ""
                )
            )
        }
        if (songs.size > 1) {
            val collator = Collator.getInstance()
            val sort = settings.songsSorting
            when (abs(sort)) {
                Constant.SORT_BY_TITLE -> {
                    songs.sortWith { o1, o2 -> collator.compare(o1.title, o2.title) }
                    if (sort < 0) songs.reverse()
                }
                Constant.SORT_BY_DATE_MODIFIED -> {
                    songs.sortWith { o1, o2 -> o2.dateModified.compareTo(o1.dateModified) }
                    if (sort < 0) songs.reverse()
                }
            }
        }
        return songs
    }
    override suspend fun id(context: Context, id: Long): Song {
        return intoFirstSong(querySongs(context, "${AudioColumns._ID} = ?", arrayOf(id.toString())))
    }
    override suspend fun path(context: Context, path: String): Song {
        return intoFirstSong(querySongs(context, "${AudioColumns.DATA} = ?", arrayOf(path)))
    }
    override suspend fun album(context: Context, albumId: Long): List<Song> {
        return intoSongs(querySongs(context, "${AudioColumns.ALBUM_ID} = ?", arrayOf(albumId.toString())))
    }
    override suspend fun artist(context: Context, artistId: Long): List<Song> {
        return intoSongs(querySongs(context, "${AudioColumns.ARTIST_ID} = ?", arrayOf(artistId.toString())))
    }
    override suspend fun searchByTitle(context: Context, title: String): List<Song> {
        val cursor = querySongs(context, "${AudioColumns.TITLE} LIKE ?", arrayOf("%$title%"))
        return intoSongs(cursor)
    }
    @JvmStatic
    fun intoSongs(cursor: Cursor?): List<Song> {
        val songs = ArrayList<Song>()
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    songs.add(readSong(cursor))
                } while (cursor.moveToNext())
            }
        }
        return songs
    }
    private fun intoFirstSong(cursor: Cursor?): Song {
        return cursor?.use {
            if (cursor.moveToFirst()) {
                readSong(cursor)
            } else {
                Song.emptySong
            }
        } ?: Song.emptySong
    }
    @JvmStatic
    fun querySongs(
        context: Context,
        selection: String? = null,
        selectionValues: Array<String>? = null,
        sortOrder: String? = defaultSortOrder()
    ): Cursor? {
        val uri = if (Constant.isQPlus()) {
            Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Audio.Media.EXTERNAL_CONTENT_URI
        }
        val selectionFinal = if (selection.isNullOrBlank()) {
            BASE_AUDIO_SELECTION
        } else {
            "$BASE_AUDIO_SELECTION AND $selection"
        }
        return try {
            context.contentResolver.query(
                uri, BASE_SONG_PROJECTION, selectionFinal,
                selectionValues, sortOrder
            )
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    private fun readSong(cursor: Cursor): Song {
        return Song(
            id = cursor.getLong(0),
            title = cursor.getString(1) ?: MediaStore.UNKNOWN_STRING,
            track = cursor.getInt(2),
            year = cursor.getInt(3),
            duration = cursor.getLong(4),
            data = cursor.getString(5) ?: "",
            dateModified = cursor.getLong(6),
            albumId = cursor.getLong(7),
            album = cursor.getString(8) ?: MediaStore.UNKNOWN_STRING,
            artistId = cursor.getLong(9),
            artist = cursor.getString(10) ?: MediaStore.UNKNOWN_STRING,
            composer = cursor.getStringOrNull(11) ?: "",
            albumArtist = cursor.getStringOrNull(12) ?: ""
        )
    }
    @JvmStatic
    fun defaultSortOrder(): String {
        val currSort = BaseSettings.getInstance().songsSorting
        return when (abs(currSort)) {
            Constant.SORT_BY_TITLE -> {
                val name = "${Audio.Media.TITLE} COLLATE NO"
                name + if (currSort > 0) "CASE" else "CASE DESC"
            }
            Constant.SORT_BY_ALBUM -> {
                val name = "${Audio.Media.ALBUM} COLLATE NO"
                name + if (currSort > 0) "CASE" else "CASE DESC"
            }
            Constant.SORT_BY_ARTIST -> {
                val name = "${Audio.Media.ARTIST} COLLATE NO"
                name + if (currSort > 0) "CASE" else "CASE DESC"
            }
            Constant.SORT_BY_DURATION -> {
                Audio.Media.DURATION + if (currSort < 0) "" else " DESC"
            }
            Constant.SORT_BY_DATE_ADDED -> {
                Audio.Media.DATE_ADDED + if (currSort < 0) "" else " DESC"
            }
            Constant.SORT_BY_DATE_MODIFIED -> {
                Audio.Media.DATE_MODIFIED + if (currSort < 0) "" else " DESC"
            }
            else -> Audio.Media.DEFAULT_SORT_ORDER
        }
    }
}