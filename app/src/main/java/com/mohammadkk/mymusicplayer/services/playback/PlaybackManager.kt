package com.mohammadkk.mymusicplayer.services.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.mohammadkk.mymusicplayer.models.Song

private const val PLAYBACK_LOCAL = 0

class PlaybackManager(val context: Context) {
    private var playback: Playback? = null
    private var playbackLocation = PLAYBACK_LOCAL

    val isLocalPlayback get() = playbackLocation == PLAYBACK_LOCAL

    private val audioSessionId: Int
        get() = playback?.audioSessionId ?: 0

    val songDurationMillis: Int
        get() = playback?.duration() ?: -1

    val songProgressMillis: Int
        get() = playback?.position() ?: -1

    val isPlaying: Boolean
        get() = playback?.isPlaying == true

    init {
        playback = MultiPlayer(context)
    }

    fun setCallbacks(callbacks: Playback.PlaybackCallbacks) {
        playback?.callbacks = callbacks
    }
    fun play(onNotInitialized: () -> Unit) {
        if (playback != null && !playback!!.isPlaying) {
            if (!playback!!.isInitialized) {
                onNotInitialized()
            } else {
                openAudioEffectSession()
                playback?.start()
            }
        }
    }
    fun pause(force: Boolean, onPause: () -> Unit) {
        if (playback?.isPlaying == true) {
            if (force) {
                playback?.pause()
                closeAudioEffectSession()
                onPause()
            } else {
                playback?.pause()
                closeAudioEffectSession()
                onPause()
            }
        }
    }
    fun seek(millis: Int, force: Boolean): Int {
        return playback!!.seek(millis, force)
    }
    fun setDataSource(
        song: Song, force: Boolean,
        completion: (success: Boolean) -> Unit
    ) {
        playback?.setDataSource(song, force, completion)
    }
    fun setNextDataSource(trackUri: String?) {
        playback?.setNextDataSource(trackUri)
    }
    fun release() {
        playback?.release()
        playback = null
        closeAudioEffectSession()
    }
    private fun openAudioEffectSession() {
        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        intent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        context.sendBroadcast(intent)
    }
    private fun closeAudioEffectSession() {
        val audioEffectsIntent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        if (playback != null) {
            audioEffectsIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playback!!.audioSessionId)
        }
        audioEffectsIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
        context.sendBroadcast(audioEffectsIntent)
    }
}