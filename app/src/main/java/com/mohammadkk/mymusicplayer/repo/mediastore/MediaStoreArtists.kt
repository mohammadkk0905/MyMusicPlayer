package com.mohammadkk.mymusicplayer.repo.mediastore

import android.content.Context
import android.provider.MediaStore.Audio.AudioColumns
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.loader.IArtists
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.intoSongs
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs.querySongs
import java.text.Collator
import kotlin.math.abs

object MediaStoreArtists : IArtists {
    override suspend fun all(context: Context): List<Artist> {
        val songs = intoSongs(querySongs(context, sortOrder = null))
        return generateArtists(songs)
    }
    override suspend fun id(context: Context, id: Long): Artist {
        val songs = intoSongs(querySongs(context, "${AudioColumns.ARTIST_ID} = ?", arrayOf(id.toString()), null))
        return generateArtists(songs).firstOrNull() ?: Artist(-1L, -1L, 0, mutableListOf())
    }
    override suspend fun searchByName(context: Context, query: String): List<Artist> {
        val songs = intoSongs(querySongs(context, "${AudioColumns.ARTIST} LIKE ?", arrayOf("%$query%"), null))
        return generateArtists(songs)
    }
    private fun generateArtists(songs: List<Song>): List<Artist> {
        val artists = groupBy(songs)
        if (artists.size > 1) {
            val sortOrder = BaseSettings.getInstance().artistsSorting
            val collator = Collator.getInstance()
            val comparator = Comparator<Artist> { o1, o2 ->
                when (abs(sortOrder)) {
                    Constant.SORT_BY_TITLE -> collator.compare(o1.title, o2.title)
                    Constant.SORT_BY_DURATION -> o2.duration.compareTo(o1.duration)
                    Constant.SORT_BY_SONGS -> o2.trackCount.compareTo(o1.trackCount)
                    else -> o2.albumCount.compareTo(o1.albumCount)
                }
            }
            artists.sortWith(comparator)
            if (sortOrder < 0) artists.reverse()
        }
        return artists
    }
    private fun groupBy(songs: List<Song>): MutableList<Artist> {
        val artists = ArrayList<Artist>()
        val itemsMap = LinkedHashMap<Long, Artist>()
        for (song in songs) {
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
        for (artist in itemsMap.values) {
            artist.albumCount = artist.songs.distinctBy { it.albumId }.size
            artists.add(artist)
        }
        return artists
    }
}