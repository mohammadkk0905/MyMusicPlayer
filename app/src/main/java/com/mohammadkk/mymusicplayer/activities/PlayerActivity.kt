package com.mohammadkk.mymusicplayer.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.ImageViewCompat
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.databinding.ActivityPlayerBinding
import com.mohammadkk.mymusicplayer.dialogs.PlaybackSpeedDialog
import com.mohammadkk.mymusicplayer.dialogs.SongDetailDialog
import com.mohammadkk.mymusicplayer.dialogs.SongShareDialog
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.getAttrColorCompat
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.overridePendingTransitionCompat
import com.mohammadkk.mymusicplayer.extensions.setAnimatedVectorDrawable
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.interfaces.IMusicServiceEventListener
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.services.MusicProgressViewUpdate
import com.mohammadkk.mymusicplayer.ui.MusicSeekBar
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class PlayerActivity : BaseActivity(), MusicProgressViewUpdate.Callback {
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var musicProgressViewUpdate: MusicProgressViewUpdate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(mBackPressedCallback)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        musicProgressViewUpdate = MusicProgressViewUpdate(this)
        addMusicServiceEventListener(musicServiceCallback)
        binding.playbackToolbar.setOnMenuItemClickListener { onSelectedItemMenu(it) }
        binding.playbackToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        initializeSlider()
        initializeButtons()
    }
    private fun updateSongInfo() {
        val song = AudioPlayerRemote.currentSong
        if (song.id == -1L || song.duration < 0) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }
        initializeSongInfo(song)
    }
    private fun initializeSongInfo(song: Song) = with(binding) {
        playbackCover.bind(song, R.drawable.ic_audiotrack)
        playbackSong.text = song.title
        playbackAlbum.text = song.album
        playbackArtist.text = song.artist
        val indexPosition = if (AudioPlayerRemote.isShuffleMode) {
            AudioPlayerRemote.playingQueue.indexOf(song)
        } else {
            AudioPlayerRemote.position
        }
        playbackCount.text = String.format(
            Locale.getDefault(), "%d / %d",
            indexPosition.plus(1), AudioPlayerRemote.playingQueue.size
        )
        playbackSeekBar.durationMills = song.duration
    }
    private val mBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }
    private fun initializeSlider() {
        binding.playbackSeekBar.setOnCallback(object : MusicSeekBar.Callback {
            override fun onSeekTo(position: Int) {
                AudioPlayerRemote.seekTo(position)
                if (!AudioPlayerRemote.isPlaying) AudioPlayerRemote.resumePlaying()
            }
        })
        binding.playbackSeekBar.setOnSkipBackward { handleSkip(false) }
        binding.playbackSeekBar.setOnSkipForward { handleSkip(true) }
    }
    private fun handleSkip(isForward: Boolean) {
        var duration = AudioPlayerRemote.songProgressMillis
        duration = if (isForward) {
            min(duration.plus(10000), AudioPlayerRemote.songDurationMillis)
        } else {
            max(duration.minus(10000), 0)
        }
        if (duration >= 0) {
            AudioPlayerRemote.seekTo(duration)
            if (!AudioPlayerRemote.isPlaying) AudioPlayerRemote.resumePlaying()
        }
    }
    private fun initializeButtons() {
        binding.playbackShuffle.setOnClickListener {
            if (AudioPlayerRemote.toggleShuffleMode()) {
                toast(if (AudioPlayerRemote.isShuffleMode) {
                    R.string.shuffle_enabled
                } else R.string.shuffle_disabled)
            }
        }
        binding.playbackSkipPrev.setOnClickListener {
            if (binding.playbackSeekBar.positionMills >= 5) {
                binding.playbackSeekBar.positionMills = 0
            }
            AudioPlayerRemote.playPreviousSong()
        }
        binding.playbackPlayPause.setOnClickListener {
            if (AudioPlayerRemote.isPlaying) {
                AudioPlayerRemote.pauseSong()
            } else {
                AudioPlayerRemote.resumePlaying()
            }
        }
        binding.playbackSkipNext.setOnClickListener {
            if (binding.playbackSeekBar.positionMills >= 5) {
                binding.playbackSeekBar.positionMills = 0
            }
            AudioPlayerRemote.playNextSong()
        }
        binding.playbackRepeat.setOnClickListener {
            if (AudioPlayerRemote.cycleRepeatMode()) {
                toast(AudioPlayerRemote.repeatMode.descriptionRes)
            }
        }
    }
    private fun onSelectedItemMenu(item: MenuItem): Boolean {
        val song = AudioPlayerRemote.currentSong
        when (item.itemId) {
            R.id.action_playback_speed -> {
                PlaybackSpeedDialog.show(supportFragmentManager)
                return true
            }
            R.id.action_share -> {
                SongShareDialog.show(song, supportFragmentManager)
                return true
            }
            R.id.action_details -> {
                SongDetailDialog.show(song, supportFragmentManager)
                return true
            }
            R.id.action_equalizer -> {
                openEqualizer(this)
                return true
            }
        }
        return false
    }
    private fun openEqualizer(activity: Activity) {
        val sessionId = AudioPlayerRemote.audioSessionId
        if (sessionId == AudioEffect.ERROR_BAD_VALUE) {
            toast(R.string.no_audio_ID, Toast.LENGTH_LONG)
        } else {
            try {
                val effects = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                effects.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                effects.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                activity.startActivityForResult(effects, 0)
            } catch (e: ActivityNotFoundException) {
                errorToast(getString(R.string.no_equalizer), Toast.LENGTH_LONG)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        musicProgressViewUpdate.start()
    }
    private fun updateShuffleState() {
        val isShuffleMode = AudioPlayerRemote.isShuffleMode
        val content = getString(if (isShuffleMode) R.string.shuffle_enabled else R.string.shuffle_disabled)
        binding.playbackShuffle.apply {
            contentDescription = content
            alpha = if (isShuffleMode) 1f else 0.9f
            updateImageTint(this, isShuffleMode)
        }
    }
    private fun updateRepeatState() {
        val playbackRepeat = AudioPlayerRemote.repeatMode
        binding.playbackRepeat.apply {
            contentDescription = getString(playbackRepeat.nextPlayBackRepeat.descriptionRes)
            setImageResource(playbackRepeat.iconRes)
            val isRepeatEnabled = playbackRepeat != PlaybackRepeat.REPEAT_OFF
            alpha = if (isRepeatEnabled) 1f else 0.9f
            updateImageTint(this, isRepeatEnabled)
        }
    }
    private fun updateImageTint(imageView: ImageView, isTint: Boolean) {
        val colorTinted = if (isTint) {
            getAttrColorCompat(com.google.android.material.R.attr.colorPrimary)
        } else {
            getColorCompat(R.color.widgets_color)
        }
        ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(colorTinted))
    }
    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.playbackSeekBar.durationMills = total.toLong()
        binding.playbackSeekBar.positionMills = progress.toLong()
    }
    override fun onStop() {
        super.onStop()
        binding.playbackPlayPause.tag = null
    }
    private fun updatePlayPauseDrawableState() {
        if (AudioPlayerRemote.isPlaying) {
            binding.playbackPlayPause.setAnimatedVectorDrawable(R.drawable.anim_play_to_pause, true)
        } else {
            binding.playbackPlayPause.setAnimatedVectorDrawable(R.drawable.anim_pause_to_play, true)
        }
    }
    private fun isFadeAnim(): Boolean {
        return intent?.getBooleanExtra("fade_anim", false) ?: false
    }
    override fun onPause() {
        super.onPause()
        musicProgressViewUpdate.stop()
        if (isFadeAnim()) {
            overridePendingTransitionCompat(
                true, android.R.anim.fade_in, android.R.anim.fade_out
            )
        }
    }
    override fun onDestroy() {
        removeMusicServiceEventListener(musicServiceCallback)
        super.onDestroy()
    }
    private val musicServiceCallback = object : IMusicServiceEventListener {
        override fun onServiceConnected() {
            updatePlayPauseDrawableState()
            updateRepeatState()
            updateShuffleState()
            updateSongInfo()
        }
        override fun onServiceDisconnected() {
        }
        override fun onQueueChanged() {
        }
        override fun onPlayingMetaChanged() {
            updateSongInfo()
        }
        override fun onPlayStateChanged() {
            updatePlayPauseDrawableState()
        }
        override fun onRepeatModeChanged() {
            updateRepeatState()
        }
        override fun onShuffleModeChanged() {
            updateShuffleState()
        }
        override fun onMediaStoreChanged() {
        }
    }
}