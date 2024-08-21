package com.mohammadkk.mymusicplayer.services

import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.mohammadkk.mymusicplayer.models.Song

class MediaSessionCallback(
    private val musicService: MusicService
) : MediaSessionCompat.Callback() {
    override fun onPrepare() {
        super.onPrepare()
        if (musicService.currentSong != Song.emptySong)
            musicService.restoreState(::onPlay)
    }
    override fun onPlay() {
        super.onPlay()
        if (musicService.currentSong != Song.emptySong) musicService.play()
    }
    override fun onPause() {
        super.onPause()
        musicService.pause()
    }
    override fun onSkipToNext() {
        super.onSkipToNext()
        musicService.playNextSong(true)
    }
    override fun onSkipToPrevious() {
        super.onSkipToPrevious()
        musicService.playPreviousSong(true)
    }
    override fun onStop() {
        super.onStop()
        musicService.quit()
    }
    override fun onSeekTo(pos: Long) {
        super.onSeekTo(pos)
        musicService.seek(pos.toInt())
    }
    override fun onCustomAction(action: String?, extras: Bundle?) {
        if (action == MusicService.ACTION_STOP) {
            musicService.quit()
        } else {
            Log.e("MediaSessionCallback","Unsupported action: $action")
        }
    }
}