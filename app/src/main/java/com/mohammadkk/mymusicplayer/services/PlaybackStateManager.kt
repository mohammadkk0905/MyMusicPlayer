package com.mohammadkk.mymusicplayer.services

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import com.mohammadkk.mymusicplayer.models.Song

class PlaybackStateManager private constructor() {
    private val callbacks = mutableListOf<Callback>()
    private val reloads = mutableListOf<OnRefreshListener>()

    fun addCallback(callback: Callback) {
        callbacks.add(callback)
    }
    fun removeCallback(callback: Callback) {
        callbacks.remove(callback)
    }
    fun addReload(callback: OnRefreshListener) {
        reloads.add(callback)
    }
    fun removeReload(callback: OnRefreshListener) {
        reloads.remove(callback)
    }
    fun onReloadLibraries() {
        reloads.forEach { it.onRefresh() }
    }
    fun noStoragePermission() {
        callbacks.forEach { it.onNoStoragePermission() }
    }
    fun progressUpdated(progress: Int) {
        callbacks.forEach { it.onProgressUpdated(progress) }
    }
    fun songChanged(song: Song?) {
        callbacks.forEach { it.onSongChanged(song) }
    }
    fun songStateChanged(isPlaying: Boolean) {
        callbacks.forEach { it.onSongStateChanged(isPlaying) }
    }
    interface Callback {
        fun onNoStoragePermission()
        fun onProgressUpdated(progress: Int)
        fun onSongChanged(song: Song?)
        fun onSongStateChanged(isPlaying: Boolean)
    }
    companion object {
        @Volatile
        private var INSTANCE: PlaybackStateManager? = null

        fun getInstance(): PlaybackStateManager {
            val currentInstance = INSTANCE

            if (currentInstance != null) {
                return currentInstance
            }
            synchronized(this) {
                val newInstance = PlaybackStateManager()
                INSTANCE = newInstance
                return newInstance
            }
        }
    }
}