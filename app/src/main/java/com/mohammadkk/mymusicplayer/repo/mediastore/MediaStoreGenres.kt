package com.mohammadkk.mymusicplayer.repo.mediastore

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Genres
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.loader.IGenres
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.BASE_AUDIO_SELECTION
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.BASE_SONG_PROJECTION
import java.text.Collator
import kotlin.math.abs

object MediaStoreGenres : IGenres {
    override suspend fun all(context: Context): List<Genre> {
        val songs = intoGenres(context, queryGenre(context, null, null))
        return sortAll(songs)
    }
    override suspend fun id(context: Context, id: Long): Genre {
        val genres = intoGenres(context, queryGenre(context, id))
        return genres.firstOrNull() ?: Genre(-1L, "", emptyList())
    }
    override suspend fun songs(context: Context, genreId: Long): List<Song> {
        return genreSongs(context, genreId, true)
    }
    private fun queryGenre(context: Context, genreId: Long): Cursor? {
        return queryGenre(context, "${Genres._ID} = ?", arrayOf(genreId.toString()))
    }
    private fun queryGenre(context: Context, selection: String?, selectionArgs: Array<String>?): Cursor? {
        return try {
            context.contentResolver.query(
                if (Constant.isQPlus()) {
                    Genres.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    Genres.EXTERNAL_CONTENT_URI
                }, arrayOf(Genres._ID, Genres.NAME),
                selection, selectionArgs, null
            )
        } catch (e: SecurityException) {
            null
        }
    }
    private fun intoGenres(context: Context, cursor: Cursor?): MutableList<Genre> {
        val genres = ArrayList<Genre>()
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val genre = extractGenre(context, cursor)
                    if (genre.songCount > 0) genres.add(genre)
                } while (cursor.moveToNext())
            }
        }
        return genres
    }
    private fun extractGenre(context: Context, cursor: Cursor): Genre {
        val id = cursor.getLong(0)
        return Genre(
            id = id, name = cursor.getString(1) ?: MediaStore.UNKNOWN_STRING,
            songs = genreSongs(context, id, false)
        )
    }
    private fun querySongs(context: Context, genreId: Long, isSort: Boolean): Cursor? {
        return try {
            context.contentResolver.query(
                Genres.Members.getContentUri("external", genreId),
                BASE_SONG_PROJECTION, BASE_AUDIO_SELECTION, null,
                if (isSort) MediaStoreSongs.defaultSortOrder() else null
            )
        } catch (e: SecurityException) {
            null
        }
    }
    private fun genreSongs(context: Context, genreId: Long, isSort: Boolean): List<Song> {
        return MediaStoreSongs.intoSongs(querySongs(context, genreId, isSort))
    }
    private fun sortAll(genres: MutableList<Genre>): List<Genre> {
        if (genres.size > 1) {
            val sortOrder = BaseSettings.getInstance().genresSorting
            val collator = Collator.getInstance()
            when (abs(sortOrder)) {
                Constant.SORT_BY_TITLE -> {
                    genres.sortWith { o1, o2 -> collator.compare(o1.name, o2.name) }
                    if (sortOrder < 0) genres.reverse()
                }
                Constant.SORT_BY_SONGS -> {
                    genres.sortWith { o1, o2 -> o2.songCount.compareTo(o1.songCount) }
                    if (sortOrder < 0) genres.reverse()
                }
            }
        }
        return genres
    }
}