package com.mohammadkk.mymusicplayer.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.databinding.ActivityPlayerBinding
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.getPrimaryColor
import com.mohammadkk.mymusicplayer.extensions.overridePendingTransitionCompat
import com.mohammadkk.mymusicplayer.extensions.sendIntent
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.extensions.updateIconTint
import com.mohammadkk.mymusicplayer.extensions.updatePlayingState
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.services.MusicService.Companion.isGlobalPlayAnim
import com.mohammadkk.mymusicplayer.ui.MusicSeekBar
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import com.mohammadkk.mymusicplayer.viewmodels.PlaybackViewModel
import java.util.Locale

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val baseSettings get() = BaseSettings.getInstance()
    private val playbackViewModel: PlaybackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(mBackPressedCallback)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.playbackToolbar)
        supportActionBar?.title = getString(R.string.playing)
        binding.playbackToolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        initializeSlider()
        initializeButtons()
        val song = MusicService.mCurrSong
        if (song == null || song.duration < 0) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }
        initializeViewModel()
        initializeSongInfo(song)
        if (intent.hasExtra(Constant.RESTART_PLAYER)) {
            intent.removeExtra(Constant.RESTART_PLAYER)
            Intent(this, MusicService::class.java).apply {
                putExtra(Constant.SONG_ID, song.id)
                action = Constant.INIT
                try {
                    startService(this)
                    setPlayPause(true)
                } catch (e: Exception) {
                    errorToast(e)
                }
            }
        } else {
            isGlobalPlayAnim = false
            sendIntent(Constant.BROADCAST_STATUS)
        }
    }
    private fun initializeViewModel() {
        playbackViewModel.song.observe(this) {
            if (it == null) {
                finish()
            } else {
                initializeSongInfo(it)
            }
        }
        playbackViewModel.isPlaying.observe(this) {
            setPlayPause(it)
        }
        playbackViewModel.isPermission.observe(this) {
            if (!it) {
                toast(R.string.permission_storage_denied)
                finish()
            }
        }
        playbackViewModel.position.observe(this) {
            binding.playbackSeekBar.positionMills = it
        }
    }
    private fun initializeSongInfo(song: Song) {
        binding.playbackCover.bind(song)
        binding.playbackSong.text = song.title
        binding.playbackAlbum.text = song.album
        binding.playbackArtist.text = song.artist
        binding.playbackCount.text = String.format(
            Locale.getDefault(), "%d / %d",
            MusicService.findIndex().plus(1),
            MusicService.mSongs.size
        )
        binding.playbackSeekBar.durationMills = song.duration
    }
    private val mBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            finish()
        }
    }
    private fun initializeSlider() {
        binding.playbackSeekBar.setOnCallback(object : MusicSeekBar.Callback {
            override fun onSeekTo(position: Int) {
                isGlobalPlayAnim = !MusicService.isPlaying()
                Intent(this@PlayerActivity, MusicService::class.java).apply {
                    putExtra(Constant.PROGRESS, position)
                    action = Constant.SET_PROGRESS
                    startService(this)
                }
            }
        })
        binding.playbackSeekBar.setOnSkipBackward { sendIntent(Constant.SKIP_BACKWARD) }
        binding.playbackSeekBar.setOnSkipForward { sendIntent(Constant.SKIP_FORWARD) }
    }
    private fun initializeButtons() {
        binding.playbackShuffle.setOnClickListener { toggleShuffle() }
        binding.playbackSkipPrev.setOnClickListener {
            isGlobalPlayAnim = !MusicService.isPlaying()
            if (binding.playbackSeekBar.positionMills >= 5) {
                binding.playbackSeekBar.positionMills = 0
            }
            sendIntent(Constant.PREVIOUS)
        }
        binding.playbackPlayPause.setOnClickListener {
            isGlobalPlayAnim = true
            sendIntent(Constant.PLAY_PAUSE)
        }
        binding.playbackSkipNext.setOnClickListener {
            isGlobalPlayAnim = !MusicService.isPlaying()
            if (binding.playbackSeekBar.positionMills >= 5) {
                binding.playbackSeekBar.positionMills = 0
            }
            sendIntent(Constant.NEXT)
        }
        binding.playbackRepeat.setOnClickListener { togglePlaybackRepeat() }
        initializeBtnShuffle()
        initializeBtnRepeat()
    }
    private fun toggleShuffle() {
        val isShuffleEnabled = !baseSettings.isShuffleEnabled
        baseSettings.isShuffleEnabled = isShuffleEnabled
        toast(if (isShuffleEnabled) R.string.shuffle_enabled else R.string.shuffle_disabled)
        initializeBtnShuffle()
        sendIntent(Constant.REFRESH_LIST)
    }
    private fun initializeBtnShuffle() {
        val isShuffle = baseSettings.isShuffleEnabled
        binding.playbackShuffle.apply {
            alpha = if (isShuffle) 1f else 0.9f
            updateIconTint(if (isShuffle) getPrimaryColor() else getColorCompat(R.color.widgets_color))
            contentDescription = getString(if (isShuffle) R.string.shuffle_enabled else R.string.shuffle_disabled)
        }
    }
    private fun togglePlaybackRepeat() {
        val newPlaybackRepeat = baseSettings.playbackRepeat.nextPlayBackRepeat
        baseSettings.playbackRepeat = newPlaybackRepeat
        toast(newPlaybackRepeat.descriptionRes)
        initializeBtnRepeat()
    }
    private fun initializeBtnRepeat() {
        val playbackRepeat = baseSettings.playbackRepeat
        binding.playbackRepeat.apply {
            contentDescription = getString(playbackRepeat.nextPlayBackRepeat.descriptionRes)
            setImageResource(playbackRepeat.iconRes)
            val isRepeatOff = playbackRepeat == PlaybackRepeat.REPEAT_OFF
            alpha = if (isRepeatOff) 0.9f else 1f
            updateIconTint(if (isRepeatOff) getColorCompat(R.color.widgets_color) else getPrimaryColor())
        }
    }
    private fun setPlayPause(playing: Boolean) {
        binding.playbackPlayPause.updatePlayingState(playing, isGlobalPlayAnim)
        isGlobalPlayAnim = false
    }
    private fun isFadeAnim(): Boolean {
        return intent?.getBooleanExtra("fade_anim", false) ?: false
    }
    override fun onPause() {
        super.onPause()
        if (isFadeAnim()) {
            overridePendingTransitionCompat(
                true, android.R.anim.fade_in, android.R.anim.fade_out
            )
        }
    }
}