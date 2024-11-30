package com.mohammadkk.mymusicplayer.viewmodels

import android.app.Application
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreAlbums
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreArtists
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreGenres
import com.mohammadkk.mymusicplayer.repo.mediastore.MediaStoreSongs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application get() = getApplication()
    private val liveData = MutableLiveData<List<Song>>()
    private var currentSong = Song.emptySong
    private var durations = 0L

    fun getListData(): LiveData<List<Song>> = liveData
    fun getCurrentSong() = currentSong
    fun getDuration() = durations

    fun updateList(modePair: Pair<String, Long>) = viewModelScope.launch(Dispatchers.IO) {
        val songs = when (modePair.first) {
            Constant.ALBUM_TAB -> {
                val album = MediaStoreAlbums.id(context, modePair.second)
                currentSong = album.getSafeSong()
                durations = album.duration
                MediaStoreSongs.album(context, modePair.second)
            }
            Constant.ARTIST_TAB -> {
                val artist = MediaStoreArtists.id(context, modePair.second)
                currentSong = artist.getSafeSong()
                durations = artist.duration
                MediaStoreSongs.artist(context, modePair.second)
            }
            Constant.GENRE_TAB -> {
                val genre = MediaStoreGenres.id(context, modePair.second)
                currentSong = genre.currentSong
                durations = genre.songs.sumOf { it.duration }
                MediaStoreGenres.songs(context, modePair.second)
            }
            else -> {
                val items = MediaStoreSongs.otg()
                currentSong = items.getOrElse(0) { Song.emptySong }
                initOtgMetadata()
                durations = currentSong.duration
                items
            }
        }
        withContext(Dispatchers.Main) { liveData.value = songs }
    }
    private fun initOtgMetadata() {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(context, currentSong.toContentUri())
            currentSong = currentSong.copy(
                title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: MediaStore.UNKNOWN_STRING,
                album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: MediaStore.UNKNOWN_STRING,
                year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0,
                duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            )
            mmr.release()
        } catch (ignored: Exception) {
        }
    }
}