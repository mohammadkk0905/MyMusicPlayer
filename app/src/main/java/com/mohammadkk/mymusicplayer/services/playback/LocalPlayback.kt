package com.mohammadkk.mymusicplayer.services.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.PlaybackParams
import androidx.annotation.CallSuper
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.services.MusicService

private const val VOLUME_DUCK = 0.2f
private const val VOLUME_NORMAL = 1.0f

abstract class LocalPlayback(val context: Context) : Playback, MediaPlayer.OnErrorListener, MediaPlayer.OnCompletionListener {
    private val becomingNoisyReceiverIntentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
    private val audioManager: AudioManager? = context.getSystemService()
    protected val settings get() = BaseSettings.getInstance()

    private var becomingNoisyReceiverRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && context != null) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    val serviceIntent = Intent(context, MusicService::class.java)
                    serviceIntent.action = MusicService.ACTION_PAUSE
                    context.startService(serviceIntent)
                }
            }
        }
    }

    private var isPausedByTransientLossOfFocus = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (!isPlaying && isPausedByTransientLossOfFocus) {
                    start()
                    callbacks?.onPlayStateChanged()
                    isPausedByTransientLossOfFocus = false
                }
                setVolume(VOLUME_NORMAL)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
                callbacks?.onPlayStateChanged()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                val wasPlaying = isPlaying
                pause()
                callbacks?.onPlayStateChanged()
                isPausedByTransientLossOfFocus = wasPlaying
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                setVolume(VOLUME_DUCK)
            }
        }
    }

    private val audioFocusRequest: AudioFocusRequestCompat =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .setAudioAttributes(AudioAttributesCompat.Builder().setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC).build())
            .build()

    @CallSuper
    override fun start(): Boolean {
        if (!requestFocus()) context.toast(R.string.audio_focus_denied)
        registerBecomingNoisyReceiver()
        return true
    }
    @CallSuper
    override fun stop() {
        abandonFocus()
        unregisterBecomingNoisyReceiver()
    }
    @CallSuper
    override fun pause(): Boolean {
        unregisterBecomingNoisyReceiver()
        return true
    }
    fun setDataSourceImpl(player: MediaPlayer, path: String, completion: (success: Boolean) -> Unit) {
        player.reset()
        try {
            if (path.startsWith("content://")) {
                player.setDataSource(context, path.toUri())
            } else {
                player.setDataSource(path)
            }
            player.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
            )
            if (Constant.isMarshmallowPlus()) {
                settings.gaplessPlayback
                player.playbackParams = PlaybackParams().setSpeed(settings.playbackSpeed).setPitch(settings.playbackPitch)
            }
            player.setOnPreparedListener {
                player.setOnPreparedListener(null)
                completion(true)
            }
            player.prepareAsync()
        } catch (e: Exception) {
            completion(false)
        }
        player.setOnCompletionListener(this)
        player.setOnErrorListener(this)
    }
    private fun unregisterBecomingNoisyReceiver() {
        if (becomingNoisyReceiverRegistered) {
            context.unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }
    }
    private fun registerBecomingNoisyReceiver() {
        if (!becomingNoisyReceiverRegistered) {
            context.registerReceiver(becomingNoisyReceiver, becomingNoisyReceiverIntentFilter)
            becomingNoisyReceiverRegistered = true
        }
    }
    private fun requestFocus(): Boolean {
        return AudioManagerCompat.requestAudioFocus(audioManager!!, audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun abandonFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, audioFocusRequest)
    }
}