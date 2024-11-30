package com.mohammadkk.mymusicplayer.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreAlbums
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreArtists
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreGenres
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs
import com.mohammadkk.mymusicplayer.services.PlaybackStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application), OnRefreshListener {
    private val context: Application get() = getApplication()
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
        val songs = MediaStoreSongs.all(context)
        val albums = MediaStoreAlbums.all(context)
        val artist = MediaStoreArtists.all(context)
        val genres = MediaStoreGenres.all(context)
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
        val songs = MediaStoreSongs.all(context)
        withContext(Dispatchers.Main) {
            _songsList.value = songs
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchAlbums() {
        val albums = MediaStoreAlbums.all(context)
        withContext(Dispatchers.Main) {
            _albumsList.value = albums
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchArtists() {
        val artists = MediaStoreArtists.all(context)
        withContext(Dispatchers.Main) {
            _artistsList.value = artists
            isReloadLibrariesCallback = false
        }
    }
    private suspend fun fetchGenres() {
        val genres = MediaStoreGenres.all(context)
        withContext(Dispatchers.Main) {
            _genresList.value = genres
            isReloadLibrariesCallback = false
        }
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