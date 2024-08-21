package com.mohammadkk.mymusicplayer.services.playback

import android.content.Context
import android.media.MediaPlayer
import android.os.PowerManager
import android.util.Log
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.setPlaybackSpeedPitch
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.models.Song

class MultiPlayer(context: Context) : LocalPlayback(context) {
    private var mCurrentMediaPlayer = MediaPlayer()
    private var mNextMediaPlayer: MediaPlayer? = null
    override var callbacks: Playback.PlaybackCallbacks? = null

    override var isInitialized = false
        private set

    init {
        mCurrentMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
    }

    override fun setDataSource(song: Song, force: Boolean, completion: (success: Boolean) -> Unit) {
        isInitialized = false
        setDataSourceImpl(mCurrentMediaPlayer, song.toContentUri().toString()) { success ->
            isInitialized = success
            if (isInitialized) {
                setNextDataSource(null)
            }
            completion(isInitialized)
        }
    }
    override fun setNextDataSource(path: String?) {
        try {
            mCurrentMediaPlayer.setNextMediaPlayer(null)
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "Next media player is current one, continuing")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Media player not initialized!")
            return
        }
        if (mNextMediaPlayer != null) {
            mNextMediaPlayer?.release()
            mNextMediaPlayer = null
        }
        if (path == null) return

        if (settings.gaplessPlayback) {
            mNextMediaPlayer = MediaPlayer()
            mNextMediaPlayer?.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            mNextMediaPlayer?.audioSessionId = audioSessionId
            setDataSourceImpl(mNextMediaPlayer!!, path) { success ->
                if (success) {
                    try {
                        mCurrentMediaPlayer.setNextMediaPlayer(mNextMediaPlayer)
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "setNextDataSource: setNextMediaPlayer()", e)
                        if (mNextMediaPlayer != null) {
                            mNextMediaPlayer?.release()
                            mNextMediaPlayer = null
                        }
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "setNextDataSource: setNextMediaPlayer()", e)
                        if (mNextMediaPlayer != null) {
                            mNextMediaPlayer?.release()
                            mNextMediaPlayer = null
                        }
                    }
                } else {
                    if (mNextMediaPlayer != null) {
                        mNextMediaPlayer?.release()
                        mNextMediaPlayer = null
                    }
                }
            }
        }
    }
    override fun start(): Boolean {
        super.start()
        return try {
            mCurrentMediaPlayer.start()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
    override fun stop() {
        super.stop()
        mCurrentMediaPlayer.reset()
        isInitialized = false
    }
    override fun release() {
        stop()
        mCurrentMediaPlayer.release()
        mNextMediaPlayer?.release()
    }
    override fun pause(): Boolean {
        super.pause()
        return try {
            mCurrentMediaPlayer.pause()
            true
        } catch (e: IllegalStateException) {
            false
        }
    }

    override val isPlaying: Boolean
        get() = isInitialized && mCurrentMediaPlayer.isPlaying

    override fun duration(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            mCurrentMediaPlayer.duration
        } catch (e: IllegalStateException) {
            -1
        }
    }
    override fun position(): Int {
        return if (!this.isInitialized) {
            -1
        } else try {
            mCurrentMediaPlayer.currentPosition
        } catch (e: IllegalStateException) {
            -1
        }
    }
    override fun seek(whereto: Int, force: Boolean): Int {
        return try {
            mCurrentMediaPlayer.seekTo(whereto)
            whereto
        } catch (e: IllegalStateException) {
            -1
        }
    }
    override fun setVolume(vol: Float): Boolean {
        return try {
            mCurrentMediaPlayer.setVolume(vol, vol)
            true
        } catch (e: IllegalStateException) {
            false
        }
    }
    override fun setAudioSessionId(sessionId: Int): Boolean {
        return try {
            mCurrentMediaPlayer.audioSessionId = sessionId
            true
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    override val audioSessionId: Int
        get() = mCurrentMediaPlayer.audioSessionId

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        isInitialized = false
        mCurrentMediaPlayer.release()
        mCurrentMediaPlayer = MediaPlayer()
        mCurrentMediaPlayer.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        context.toast(R.string.unplayable_file)
        Log.e(TAG, what.toString() + extra)
        return false
    }
    override fun onCompletion(mp: MediaPlayer) {
        if (mp == mCurrentMediaPlayer && mNextMediaPlayer != null) {
            isInitialized = false
            mCurrentMediaPlayer.release()
            mCurrentMediaPlayer = mNextMediaPlayer!!
            isInitialized = true
            mNextMediaPlayer = null
            callbacks?.onTrackWentToNext()
        } else {
            callbacks?.onTrackEnded()
        }
    }
    override fun setPlaybackSpeedPitch(speed: Float, pitch: Float) {
        mCurrentMediaPlayer.setPlaybackSpeedPitch(speed, pitch)
        mNextMediaPlayer?.setPlaybackSpeedPitch(speed, pitch)
    }
    companion object {
        val TAG: String = MultiPlayer::class.java.simpleName
    }
}