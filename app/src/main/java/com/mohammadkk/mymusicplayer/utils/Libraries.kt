package com.mohammadkk.mymusicplayer.utils

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import androidx.core.net.toUri
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.extensions.fromTreeUri
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Song
import java.text.Collator
import kotlin.math.abs

object Libraries {
    private const val IS_MUSIC = "${Audio.AudioColumns.IS_MUSIC} = 1 AND ${Audio.AudioColumns.DURATION} >= 5000"

    fun fetchAllSongs(context: Context, selection: String?, selectionArgs: Array<String>?): List<Song> {
        val songs = arrayListOf<Song>()
        val uri = if (Constant.isQPlus()) {
            Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            Audio.AudioColumns._ID,// 0
            Audio.AudioColumns.ALBUM_ID,// 1
            Audio.AudioColumns.ARTIST_ID,// 2
            Audio.AudioColumns.TITLE,// 3
            Audio.AudioColumns.ALBUM,// 4
            Audio.AudioColumns.ARTIST,// 5
            Audio.AudioColumns.DATA,// 6
            Audio.AudioColumns.YEAR,// 7
            Audio.AudioColumns.DURATION,// 8
            Audio.AudioColumns.DATE_ADDED,// 9
            Audio.AudioColumns.DATE_MODIFIED// 10
        )
        val ms = if (selection != null && selection.trim { it <= ' ' } != "") {
            "$IS_MUSIC AND $selection"
        } else {
            IS_MUSIC
        }
        val sortOrder = Audio.Media.DEFAULT_SORT_ORDER
        try {
            val cursor = context.contentResolver.query(uri, projection, ms, selectionArgs, sortOrder)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    do {
                        songs.add(getSongFromCursorImpl(cursor))
                    } while (cursor.moveToNext())
                }
            }
        } catch (ignored: Exception) {
        }
        return songs
    }
    private fun getAlbumCount(list: List<Song>): Int {
        val set = HashSet<Long>()
        var count = 0
        for (song in list) {
            if (set.add(song.albumId)) count++
        }
        return count
    }
    private fun getSongFromCursorImpl(cursor: Cursor): Song {
        return Song(
            id = cursor.getLong(0),
            albumId = cursor.getLong(1),
            artistId = cursor.getLong(2),
            title = cursor.getString(3) ?: MediaStore.UNKNOWN_STRING,
            album = cursor.getString(4) ?: MediaStore.UNKNOWN_STRING,
            artist = cursor.getString(5) ?: MediaStore.UNKNOWN_STRING,
            path = cursor.getString(6) ?: "",
            year = cursor.getInt(7),
            duration = cursor.getInt(8),
            dateAdded = cursor.getInt(9),
            dateModified = cursor.getLong(10)
        )
    }
    fun splitsIntoAlbums(items: List<Song>): List<Album> {
        val itemsMap = linkedMapOf<Long, Album>()
        val albums = arrayListOf<Album>()
        for (song in items) {
            val album: Album
            if (itemsMap.containsKey(song.albumId)) {
                album = itemsMap[song.albumId]!!
                album.duration += song.duration
                album.songs.add(song)
            } else {
                val list = mutableListOf<Song>()
                list.add(song)
                album = Album(song.albumId, song.duration, list)
                albums.add(album)
            }
            itemsMap[song.albumId] = album
        }
        return albums
    }
    fun splitsIntoArtists(items: List<Song>): List<Artist> {
        val itemsMap = linkedMapOf<Long, Artist>()
        for (song in items) {
            val artist: Artist
            if (itemsMap.containsKey(song.artistId)) {
                artist = itemsMap[song.artistId]!!
                artist.duration += song.duration
                artist.songs.add(song)
            } else {
                val list = mutableListOf<Song>()
                list.add(song)
                artist = Artist(song.artistId, song.duration, 0, list)
            }
            itemsMap[song.artistId] = artist
        }
        return itemsMap.values.map { artist ->
            artist.albumCount = getAlbumCount(artist.songs)
            artist
        }
    }
    fun getSortedSongs(songs: List<Song>?): List<Song> {
        if (songs.isNullOrEmpty()) return emptyList()
        val sortOrder = BaseSettings.getInstance().songsSorting
        val collator = Collator.getInstance()
        val comparator = Comparator<Song> { o1, o2 ->
            var result = when (abs(sortOrder)) {
                Constant.SORT_BY_TITLE -> collator.compare(o1.title, o2.title)
                Constant.SORT_BY_ALBUM -> collator.compare(o1.album, o2.album)
                Constant.SORT_BY_ARTIST-> collator.compare(o1.artist, o2.artist)
                Constant.SORT_BY_DURATION -> o2.duration.compareTo(o1.duration)
                Constant.SORT_BY_DATE_ADDED -> o2.dateAdded.compareTo(o1.dateAdded)
                else -> return@Comparator 0
            }
            if (sortOrder < 0) result *= -1
            return@Comparator result
        }
        return songs.sortedWith(comparator)
    }
    fun fetchSongsByAlbumId(context: Context, id: Long): List<Song> {
        val selection = "${Audio.AudioColumns.ALBUM_ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        return fetchAllSongs(context, selection, selectionArgs)
    }
    fun fetchSongsByArtistId(context: Context, id: Long): List<Song> {
        val selection = "${Audio.AudioColumns.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(id.toString())
        return fetchAllSongs(context, selection, selectionArgs)
    }
    fun fetchSongsByOtg(context: Context): List<Song> {
        val documentFile = context.fromTreeUri(BaseSettings.getInstance().otgTreeUri.toUri())
        if (documentFile == null || !documentFile.exists()) return emptyList()
        var index = 0
        val items = FileUtils.listFilesDeep(context.applicationContext, documentFile.uri)
        return items.mapNotNull { it.toSongItem(index++) }
    }
    fun getSectionName(mediaTitle: String?, stripPrefix: Boolean = false): String {
        var mMediaTitle = mediaTitle
        return try {
            if (mMediaTitle.isNullOrEmpty()) return "-"
            mMediaTitle = mMediaTitle.trim { it <= ' ' }
            if (stripPrefix) mMediaTitle = sliceArticle(mMediaTitle)
            mMediaTitle.firstOrNull()?.uppercase() ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    private fun sliceArticle(title: String): String {
        if (title.length > 5 && title.startsWith("the ", true)) {
            return title.slice(4..title.lastIndex)
        }
        if (title.length > 4 && title.startsWith("an ", true)) {
            return title.slice(3..title.lastIndex)
        }
        if (title.length > 3 && title.startsWith("a ", true)) {
            return title.slice(2..title.lastIndex)
        }
        return title
    }
}