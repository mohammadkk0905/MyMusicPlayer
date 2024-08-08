package com.mohammadkk.mymusicplayer.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.PlayerActivity
import com.mohammadkk.mymusicplayer.databinding.FragmentNowPlayingBinding
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.getAttrColorCompat
import com.mohammadkk.mymusicplayer.extensions.sendIntent
import com.mohammadkk.mymusicplayer.extensions.updatePlayingState
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.services.MusicService.Companion.isGlobalPlayAnim
import com.mohammadkk.mymusicplayer.viewmodels.PlaybackViewModel
import kotlin.math.abs

class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {
    private lateinit var binding: FragmentNowPlayingBinding
    private val playbackViewModel: PlaybackViewModel by activityViewModels()
    private var isStopHandleClick = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentNowPlayingBinding.bind(view)
        view.setOnTouchListener(FlingPlayBackController())
        initializeViewModel()
        binding.root.setOnClickListener {
            if (!isStopHandleClick) {
                with(requireActivity()) {
                    val mIntent = Intent(this, PlayerActivity::class.java)
                    mIntent.putExtra("fade_anim", true)
                    val options = ActivityOptionsCompat.makeCustomAnimation(
                        this, android.R.anim.fade_in, android.R.anim.fade_out
                    ).toBundle()
                    startActivity(mIntent, options)
                }
            }
        }
        binding.btnPlayPause.setOnClickListener {
            isGlobalPlayAnim = true
            AudioPlayerRemote.playPauseSong()
        }
    }
    override fun onStop() {
        super.onStop()
        isGlobalPlayAnim = false
    }
    private fun initializeViewModel() {
        val primaryDark = requireContext().getAttrColorCompat(com.google.android.material.R.attr.colorPrimaryVariant)
        binding.songProgress.trackColor = ColorUtils.setAlphaComponent(primaryDark, 80)
        playbackViewModel.song.observe(requireActivity()) {
            onSongChanged(it)
        }
        playbackViewModel.isPlaying.observe(requireActivity()) {
            setPlayPause(it)
        }
        playbackViewModel.isPermission.observe(requireActivity()) {
            if (!it) onNoStoragePermission()
        }
        playbackViewModel.position.observe(requireActivity()) {
            onProgressUpdated(it)
        }
    }
    private fun initSongInfo(song: Song) {
        binding.trackImage.bind(song, R.drawable.ic_audiotrack)
        binding.tvTrackTitle.text = song.title
        binding.tvTrackSubtitle.text = getString(R.string.album_artist_symbol, song.album, song.artist)
        binding.songProgress.max = song.duration / 1000
    }
    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (MusicService.isMusicPlayer()) {
            isGlobalPlayAnim = false
            context?.sendIntent(Constant.BROADCAST_STATUS)
        }
    }
    private fun onNoStoragePermission() {
        Log.d(javaClass.simpleName, "Not permission storage")
    }
    private fun onProgressUpdated(progress: Int) {
        binding.songProgress.progress = progress / 1000
    }
    private fun onSongChanged(song: Song?) {
        if (song == null) {
            val cover = GlideExtensions.getCoverArt(requireContext(), -1L, R.drawable.ic_audiotrack)
            binding.trackImage.setImageDrawable(cover)
            binding.tvTrackTitle.text = MediaStore.UNKNOWN_STRING
            binding.tvTrackSubtitle.text = MediaStore.UNKNOWN_STRING
        } else {
            initSongInfo(song)
        }
    }
    private fun setPlayPause(playing: Boolean) {
        binding.btnPlayPause.updatePlayingState(playing, isGlobalPlayAnim)
        isGlobalPlayAnim = false
    }
    private inner class FlingPlayBackController : View.OnTouchListener {
        private val gestureDetector = GestureDetector(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                try {
                    val diffY = e2.y - e1!!.y
                    val diffX = e2.x - e1.x
                    if (abs(diffX) > abs(diffY)) {
                        if (abs(diffX) > 100 && abs(velocityX) > 100) {
                            if (diffX > 0) {
                                isGlobalPlayAnim = !MusicService.isPlaying()
                                AudioPlayerRemote.playNextSong()
                                return true
                            }
                            else {
                                isGlobalPlayAnim = !MusicService.isPlaying()
                                AudioPlayerRemote.playPreviousSong()
                                return true
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View?, event: MotionEvent?): Boolean {
            if (v != null && event != null) {
                isStopHandleClick = gestureDetector.onTouchEvent(event)
            }
            return false
        }
    }
}