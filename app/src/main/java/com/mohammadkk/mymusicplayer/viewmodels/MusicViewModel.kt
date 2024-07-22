package com.mohammadkk.mymusicplayer.viewmodels

import android.app.Application
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.utils.Libraries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.abs

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application get() = getApplication()
    private val settings = BaseSettings.getInstance()
    val fragmentLibraries = hashMapOf<Int, SearchView.OnQueryTextListener?>()

    private val _songsList = MutableStateFlow(listOf<Song>())
    val songsList: StateFlow<List<Song>> get() = _songsList

    private val _albumsList = MutableStateFlow(listOf<Album>())
    val albumsList: StateFlow<List<Album>> get() = _albumsList

    private val _artistsList = MutableStateFlow(listOf<Artist>())
    val artistsList: StateFlow<List<Artist>> get() = _artistsList

    fun updateLibraries() = viewModelScope.launch(IO) {
        val songs = getAllSongs(true)
        val albums = getAllAlbums(songs)
        val artist = getAllArtists(songs)
        withContext(Dispatchers.Main) {
            _songsList.value = songs
            _albumsList.value = albums
            _artistsList.value = artist
        }
    }
    fun reloadLibraries() {
        viewModelScope.launch(IO) {
            _songsList.tryEmit(songsList.value)
            _albumsList.tryEmit(albumsList.value)
            _artistsList.tryEmit(artistsList.value)
        }
    }
    private suspend fun fetchSongs() {
        val songs = getAllSongs(true)
        withContext(Dispatchers.Main) {
            _songsList.value = songs
        }
    }
    private suspend fun fetchAlbums() {
        val albums = getAllAlbums(getAllSongs(false))
        withContext(Dispatchers.Main) {
            _albumsList.value = albums
        }
    }
    private suspend fun fetchArtists() {
        val artists = getAllArtists(getAllSongs(false))
        withContext(Dispatchers.Main) {
            _artistsList.value = artists
        }
    }
    private fun getAllSongs(isSorting: Boolean): List<Song> {
        val songs = Libraries.fetchAllSongs(context, null, null)
        if (!isSorting) return songs
        return Libraries.getSortedSongs(songs)
    }
    private fun getAllAlbums(items: List<Song>): List<Album> {
        val mItems = Libraries.splitsIntoAlbums(items)
        if (mItems.isEmpty()) return mItems
        val sortOrder = settings.albumsSorting
        val collator = Collator.getInstance()
        val comparator = Comparator<Album> { o1, o2 ->
            var result = when (abs(sortOrder)) {
                Constant.SORT_BY_TITLE -> collator.compare(o1.title, o2.title)
                Constant.SORT_BY_ARTIST -> collator.compare(o1.artist, o2.artist)
                Constant.SORT_BY_YEAR -> o2.year.compareTo(o1.year)
                Constant.SORT_BY_DURATION -> o2.duration.compareTo(o1.duration)
                Constant.SORT_BY_SONGS -> o2.trackCount.compareTo(o1.trackCount)
                else -> return@Comparator 0
            }
            if (sortOrder < 0) result *= -1
            return@Comparator result
        }
        return mItems.sortedWith(comparator)
    }
    private fun getAllArtists(items: List<Song>): List<Artist> {
        val mItems = Libraries.splitsIntoArtists(items)
        if (mItems.isEmpty()) return mItems
        val sortOrder = settings.artistsSorting
        val collator = Collator.getInstance()
        val comparator = Comparator<Artist> { o1, o2 ->
            var result = when (abs(sortOrder)) {
                Constant.SORT_BY_TITLE -> collator.compare(o1.title, o2.title)
                Constant.SORT_BY_DURATION -> o2.duration.compareTo(o1.duration)
                Constant.SORT_BY_SONGS -> o2.trackCount.compareTo(o1.trackCount)
                Constant.SORT_BY_ALBUMS -> o2.albumCount.compareTo(o1.albumCount)
                else -> return@Comparator 0
            }
            if (sortOrder < 0) result *= -1
            return@Comparator result
        }
        return mItems.sortedWith(comparator)
    }
    fun forceReload(mode: Int) = viewModelScope.launch(IO) {
        when (mode) {
            0 -> fetchSongs()
            1 -> fetchAlbums()
            2 -> fetchArtists()
        }
    }
}