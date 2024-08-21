package com.mohammadkk.mymusicplayer.services.playback

import com.mohammadkk.mymusicplayer.models.Song

interface Playback {
    val isInitialized: Boolean
    val isPlaying: Boolean
    val audioSessionId: Int

    fun setDataSource(song: Song, force: Boolean, completion: (success: Boolean) -> Unit)
    fun setNextDataSource(path: String?)
    var callbacks: PlaybackCallbacks?
    fun start(): Boolean
    fun stop()
    fun release()
    fun pause(): Boolean
    fun duration(): Int
    fun position(): Int
    fun seek(whereto: Int, force: Boolean): Int
    fun setVolume(vol: Float): Boolean
    fun setAudioSessionId(sessionId: Int): Boolean
    fun setPlaybackSpeedPitch(speed: Float, pitch: Float)

    interface PlaybackCallbacks {
        fun onTrackWentToNext()
        fun onTrackEnded()
        fun onPlayStateChanged()
    }
}