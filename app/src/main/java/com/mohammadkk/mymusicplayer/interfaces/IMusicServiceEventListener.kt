package com.mohammadkk.mymusicplayer.interfaces

interface IMusicServiceEventListener {
    fun onServiceConnected()
    fun onServiceDisconnected()
    fun onQueueChanged()
    fun onPlayingMetaChanged()
    fun onPlayStateChanged()
    fun onRepeatModeChanged()
    fun onShuffleModeChanged()
    fun onMediaStoreChanged()
}