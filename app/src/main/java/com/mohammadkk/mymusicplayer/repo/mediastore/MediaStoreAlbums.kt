package com.mohammadkk.mymusicplayer.repo.mediastore

import android.content.Context
import android.provider.MediaStore.Audio.AudioColumns
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.loader.IAlbums
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.intoSongs
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.querySongs
import java.text.Collator
import kotlin.math.abs

object MediaStoreAlbums : IAlbums {
    override suspend fun all(context: Context): List<Album> {
        val songs = intoSongs(querySongs(context, sortOrder = null))
        return generateAlbums(songs)
    }
    override suspend fun id(context: Context, id: Long): Album {
        val songs = intoSongs(querySongs(context, "${AudioColumns.ALBUM_ID} = ?", arrayOf(id.toString()), null))
        val album = generateAlbums(songs).firstOrNull()
        return album ?: Album(-1L, -1L, mutableListOf())
    }
    override suspend fun searchByName(context: Context, query: String): List<Album> {
        val songs = intoSongs(querySongs(context, "${AudioColumns.ALBUM} LIKE ?", arrayOf("%$query%"), null))
        return generateAlbums(songs)
    }
    override suspend fun artist(context: Context, artistId: Long): List<Album> {
        val songs = intoSongs(querySongs(context, "${AudioColumns.ARTIST_ID} = ?", arrayOf(artistId.toString()), null))
        return groupBy(songs).sortedBy { it.year }
    }
    private fun generateAlbums(songs: List<Song>): List<Album> {
        val albums = groupBy(songs)
        if (albums.size > 1) {
            val collator = Collator.getInstance()
            val sort = BaseSettings.getInstance().albumsSorting
            val comparator = Comparator<Album> { o1, o2 ->
                when (abs(sort)) {
                    Constant.SORT_BY_TITLE -> collator.compare(o1.title, o2.title)
                    Constant.SORT_BY_ARTIST -> collator.compare(o1.artist, o2.artist)
                    Constant.SORT_BY_YEAR -> o2.year.compareTo(o1.year)
                    Constant.SORT_BY_DURATION -> o2.duration.compareTo(o1.duration)
                    else -> o2.trackCount.compareTo(o1.trackCount)
                }
            }
            albums.sortWith(comparator)
            if (sort < 0) albums.reverse()
        }
        return albums
    }
    private fun groupBy(songs: List<Song>): MutableList<Album> {
        val itemsMap = LinkedHashMap<Long, Album>()
        val albums = ArrayList<Album>()
        for (song in songs) {
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
}