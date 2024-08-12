package com.mohammadkk.mymusicplayer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.PlaybackStateManager
import com.mohammadkk.mymusicplayer.utils.Libraries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.abs

class MusicViewModel(application: Application) : AndroidViewModel(application), OnRefreshListener {
    private val context: Application get() = getApplication()
    private val settings = BaseSettings.getInstance()
    private var isReloadLibrariesCallback = false

    private val _songsList = MutableStateFlow(listOf<Song>())
    val songsList: StateFlow<List<Song>> get() = _songsList

    private val _albumsList = MutableStateFlow(listOf<Album>())
    val albumsList: StateFlow<List<Album>> get() = _albumsList

    private val _artistsList = MutableStateFlow(listOf<Artist>())
    val artistsList: StateFlow<List<Artist>> get() = _artistsList

    private val _genresList = MutableStateFlow(listOf<Genre>())
    val genresList: StateFlow<List<Genre>> get() = _genresList

    private val _searchHandle = MutableStateFlow(Triple(-1, true, ""))
    val searchHandle: StateFlow<Triple<Int, Boolean, String>> get() = _searchHandle

    init {
        PlaybackStateManager.getInstance().addReload(this)
    }
    fun updateLibraries() = viewModelScope.launch(IO) {
        val songs = getAllSongs(true)
        val albums = getAllAlbums(songs)
        val artist = getAllArtists(songs)
        val genres = getAllGenres()
        withContext(Dispatchers.Main) {
            _songsList.value = songs
            _albumsList.value = albums
            _artistsList.value = artist
            _genresList.value = genres
            isReloadLibrariesCallback = false
        }
    }
    override fun onRefresh() {
        if (!isReloadLibrariesCallback) {
            isReloadLibrariesCallback = true
            updateLibraries()
        }
    }
    private suspend fun fetchSongs() {
        val songs = getAllSongs(true)
        withContext(Dispatchers.Main) {
            _songsList.value = songs
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchAlbums() {
        val albums = getAllAlbums(getAllSongs(false))
        withContext(Dispatchers.Main) {
            _albumsList.value = albums
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchArtists() {
        val artists = getAllArtists(getAllSongs(false))
        withContext(Dispatchers.Main) {
            _artistsList.value = artists
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchGenres() {
        val genres = getAllGenres()
        withContext(Dispatchers.Main) {
            _genresList.value = genres
            isReloadLibrariesCallback = false
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
    private fun getAllGenres(): List<Genre> {
        val mItems = Libraries.genres(context)
        if (mItems.isEmpty()) return mItems
        val sortOrder = settings.genresSorting
        val collator = Collator.getInstance()
        val comparator = Comparator<Genre> { o1, o2 ->
            var result = when (abs(sortOrder)) {
                Constant.SORT_BY_TITLE -> collator.compare(o1.name, o2.name)
                Constant.SORT_BY_SONGS -> o2.songCount.compareTo(o1.songCount)
                else -> return@Comparator 0
            }
            if (sortOrder < 0) result *= -1
            return@Comparator result
        }
        return mItems.sortedWith(comparator)
    }
    fun setSearch(tabIndex: Int, isClose: Boolean, query: String?) {
        _searchHandle.value = Triple(tabIndex, isClose, query.orEmpty())
    }
    fun setSearch(isClose: Boolean, query: String?) {
        val tabIndex = _searchHandle.value.first
        _searchHandle.value = Triple(tabIndex, isClose, query.orEmpty())
    }
    fun forceReload(mode: Int) = viewModelScope.launch(IO) {
        when (mode) {
            0 -> fetchSongs()
            1 -> fetchAlbums()
            2 -> fetchArtists()
            3 -> fetchGenres()
        }
    }
    override fun onCleared() {
        PlaybackStateManager.getInstance().removeReload(this)
        super.onCleared()
    }
}