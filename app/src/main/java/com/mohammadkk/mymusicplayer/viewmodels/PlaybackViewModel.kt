package com.mohammadkk.mymusicplayer.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.PlaybackStateManager

class PlaybackViewModel : ViewModel(), PlaybackStateManager.Callback {
    private val mSong = MutableLiveData<Song?>()
    private val mIsPlaying = MutableLiveData(false)
    private val mIsPermission = MutableLiveData(true)
    private val mPosition = MutableLiveData(0)

    val song: LiveData<Song?> get() = mSong
    val isPlaying: LiveData<Boolean> get() = mIsPlaying
    val isPermission: LiveData<Boolean> get() = mIsPermission
    val position: LiveData<Int> get() = mPosition

    private val playbackManager = PlaybackStateManager.getInstance()

    init {
        playbackManager.addCallback(this)
    }

    override fun onNoStoragePermission() {
        mIsPermission.postValue(false)
    }
    override fun onProgressUpdated(progress: Int) {
        mPosition.postValue(progress)
    }
    override fun onSongChanged(song: Song?) {
        mSong.postValue(song)
    }
    override fun onSongStateChanged(isPlaying: Boolean) {
        mIsPlaying.postValue(isPlaying)
    }
    override fun onCleared() {
        playbackManager.removeCallback(this)
        super.onCleared()
    }
}