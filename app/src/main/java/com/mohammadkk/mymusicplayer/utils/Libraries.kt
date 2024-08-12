package com.mohammadkk.mymusicplayer.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.provider.MediaStore.Audio.Genres
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song
import java.io.File
import java.text.Collator
import kotlin.math.abs

object Libraries {
    private const val IS_MUSIC = "${Audio.AudioColumns.IS_MUSIC} = 1 AND ${Audio.AudioColumns.DURATION} >= 5000"
    private val BASE_PROJECTION = arrayOf(
        Audio.AudioColumns._ID,// 0
        Audio.AudioColumns.ALBUM_ID,// 1
        Audio.AudioColumns.ARTIST_ID,// 2
        Audio.AudioColumns.TITLE,// 3
        Audio.AudioColumns.ALBUM,// 4
        Audio.AudioColumns.ARTIST,// 5
        Audio.AudioColumns.DATA,// 6
        Audio.AudioColumns.YEAR,// 7
        Audio.AudioColumns.DURATION,// 8
        Audio.AudioColumns.TRACK,// 9
        Audio.AudioColumns.DATE_MODIFIED// 10
    )

    fun fetchAllSongs(context: Context, selection: String?, selectionArgs: Array<String>?): List<Song> {
        val songs = arrayListOf<Song>()
        val cursor = makeSongCursor(context, selection, selectionArgs)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    songs.add(getSongFromCursorImpl(cursor))
                } while (cursor.moveToNext())
            }
        }
        return songs
    }
    @JvmStatic
    fun fetchAllSongs(cursor: Cursor?): List<Song> {
        val songs = arrayListOf<Song>()
        if (cursor != null && cursor.moveToFirst()) {
            do {
                songs.add(getSongFromCursorImpl(cursor))
            } while (cursor.moveToNext())
        }
        cursor?.close()
        return songs
    }
    @JvmStatic
    fun makeSongCursor(
        context: Context,
        selection: String?,
        selectionValues: Array<String>?
    ): Cursor? {
        val uri = if (Constant.isQPlus()) {
            Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            Audio.Media.EXTERNAL_CONTENT_URI
        }
        val ms = if (selection != null && selection.trim { it <= ' ' } != "") {
            "$IS_MUSIC AND $selection"
        } else {
            IS_MUSIC
        }
        return try {
            context.contentResolver.query(
                uri, BASE_PROJECTION,
                ms, selectionValues,
                BaseSettings.getInstance().songsSortingAtName
            )
        } catch (ex: SecurityException) {
            return null
        }
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
            trackNumber = cursor.getInt(9),
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
        return when (abs(sortOrder)) {
            Constant.SORT_BY_TITLE -> songs.sortedWith { s1, s2 ->
                if (sortOrder > 0) {
                    collator.compare(s1.title, s2.title)
                } else {
                    collator.compare(s2.title, s1.title)
                }
            }
            Constant.SORT_BY_ALBUM -> songs.sortedWith { s1, s2 ->
                if (sortOrder > 0) {
                    collator.compare(s1.album, s2.album)
                } else {
                    collator.compare(s2.album, s1.album)
                }

            }
            Constant.SORT_BY_ARTIST -> songs.sortedWith { s1, s2 ->
                if (sortOrder > 0) {
                    collator.compare(s1.artist, s2.artist)
                } else {
                    collator.compare(s2.artist, s1.artist)
                }
            }
            else -> songs
        }
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
    fun fetchSongsByGenreId(context: Context, id: Long): List<Song> {
        return fetchAllSongs(makeGenreSongCursor(context.contentResolver, id))
    }
    fun fetchSongsByOtg(context: Context): List<Song> {
        val otgPath = BaseSettings.getInstance().otgPartition
        if (otgPath.isEmpty()) return emptyList()
        val files = FileUtils.listFilesDeep(File(otgPath), FileUtils.AUDIO_FILE_FILTER)
        return files.mapIndexed { index, file ->
            Song(
                id = index.toLong(),
                albumId = 0L,
                artistId = 0L,
                title = file.nameWithoutExtension,
                album = "Unknown Album",
                artist = "Unknown Artist",
                path = file.path,
                year = 0,
                duration = 0,
                trackNumber = 0,
                dateModified = file.lastModified()
            )
        }
    }
    fun genres(context: Context): List<Genre> {
        val resolver = context.contentResolver
        return getGenresFromCursor(resolver, makeGenreCursor(resolver))
    }
    private fun getGenresFromCursor(resolver: ContentResolver, cursor: Cursor?): List<Genre> {
        val genres = arrayListOf<Genre>()
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    val genre = getGenreFromCursor(resolver, cursor)
                    if (genre.songCount > 0) genres.add(genre)
                } while (cursor.moveToNext())
            }
        }
        return genres
    }
    private fun getGenreFromCursor(resolver: ContentResolver, cursor: Cursor): Genre {
        val id = cursor.getLong(0)
        val name = cursor.getString(1) ?: ""
        val songCount = getSongCount(resolver, id)
        return Genre(id, name, songCount)
    }
    private fun getSongCount(resolver: ContentResolver, genreId: Long): Int {
        resolver.query(
            Genres.Members.getContentUri("external", genreId),
            null, null, null, null
        ).use {
            return it?.count ?: 0
        }
    }
    fun songByGenre(resolver: ContentResolver, genreId: Long): Song {
        val cursor = makeGenreSongCursor(resolver, genreId)
        val song = if (cursor != null && cursor.moveToFirst()) {
            getSongFromCursorImpl(cursor)
        } else {
            Song.emptySong
        }
        cursor?.close()
        return song
    }
    private fun makeGenreSongCursor(resolver: ContentResolver, genreId: Long): Cursor? {
        return try {
            resolver.query(
                Genres.Members.getContentUri("external", genreId),
                BASE_PROJECTION, IS_MUSIC, null,
                Audio.Media.DEFAULT_SORT_ORDER
            )
        } catch (e: Exception) {
            return null
        }
    }
    private fun makeGenreCursor(resolver: ContentResolver): Cursor? {
        val projection = arrayOf(Genres._ID, Genres.NAME)
        return try {
            resolver.query(
                Genres.EXTERNAL_CONTENT_URI,
                projection, null, null,
                Genres.DEFAULT_SORT_ORDER
            )
        } catch (e: SecurityException) {
            null
        }
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