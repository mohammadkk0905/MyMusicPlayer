package com.mohammadkk.mymusicplayer.fragments

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.MediaStore
import android.text.TextUtils
import android.view.GestureDetector
import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import androidx.core.os.BundleCompat
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentNowPlayingBinding
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.setAnimatedVectorDrawable
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.services.MusicProgressViewUpdate
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class NowPlayingFragment : ABaseFragment(R.layout.fragment_now_playing), MusicProgressViewUpdate.Callback {
    private var _binding: FragmentNowPlayingBinding? = null
    private val binding get() = _binding!!
    private lateinit var progressViewUpdate: MusicProgressViewUpdate
    private var cacheSong: Song? = null
    private var isStopHandleClick = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progressViewUpdate = MusicProgressViewUpdate(this)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNowPlayingBinding.bind(view)
        view.setOnTouchListener(FlingPlayBackController())
        with(binding) {
            songProgress.setIndicatorColor(ThemeManager.colorPrimary)
            songProgress.trackColor = ThemeManager.withAlpha(ThemeManager.colorPrimary, 0.2f)
            root.setCardBackgroundColor(ColorStateList.valueOf(darkAccentColor()))
        }
        if (savedInstanceState != null) {
            cacheSong = BundleCompat.getParcelable(savedInstanceState, "cache_song", Song::class.java)
            binding.songProgress.max = max(savedInstanceState.getInt("progress_max"), 0)
            binding.songProgress.progress = max(savedInstanceState.getInt("progress_song"), 0)
        }
        binding.root.setOnClickListener {
            if (!isStopHandleClick) {
                SongsAdapter.launchPlayer(requireActivity())
            }
        }
        binding.btnPlayPause.setOnClickListener {
            if (AudioPlayerRemote.isPlaying) {
                AudioPlayerRemote.pauseSong()
            } else {
                AudioPlayerRemote.resumePlaying()
            }
        }
    }
    private fun darkAccentColor(): Int {
        val surface = ThemeManager.colorSurface
        return ColorUtils.blendARGB(
            ThemeManager.colorPrimary, surface,
            if (ThemeManager.isColorLight(surface)) 0.9f else 0.92f
        )
    }
    override fun onStop() {
        super.onStop()
        binding.btnPlayPause.tag = null
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.run {
            outState.putParcelable("cache_song", AudioPlayerRemote.currentSong)
            outState.putInt("progress_max", songProgress.max)
            outState.putInt("progress_song", songProgress.progress)
        }
    }
    private fun updateSongInfo() = _binding?.run {
        val currentSong = AudioPlayerRemote.currentSong
        var song = cacheSong ?: currentSong
        if (song.id != currentSong.id) {
            cacheSong = currentSong
            song = currentSong
        }
        if (song.id == -1L && song.duration < 0) {
            val cover = GlideExtensions.getCoverArt(requireContext(), song.id, R.drawable.ic_audiotrack)
            trackImage.setImageDrawable(cover)
            tvTrackTitle.text = MediaStore.UNKNOWN_STRING
            tvTrackSubtitle.text = MediaStore.UNKNOWN_STRING
            serviceActivity?.onShowOpenMiniPlayer(false)
        } else {
            trackImage.bind(song, R.drawable.ic_audiotrack)
            tvTrackTitle.text = song.title
            tvTrackSubtitle.text = getString(R.string.album_artist_symbol, song.album, song.artist)
            songProgress.max = (song.duration / 1000).toInt()
            serviceActivity?.onShowOpenMiniPlayer(true)
        }
    }
    override fun onResume() {
        super.onResume()
        progressViewUpdate.start()
    }
    override fun onPause() {
        super.onPause()
        progressViewUpdate.stop()
    }
    override fun onUpdateProgressViews(progress: Int, total: Int) {
        _binding?.run {
            songProgress.max = total / 1000
            songProgress.progress = progress / 1000
        }
    }
    private fun updatePlayPauseDrawableState() {
        if (AudioPlayerRemote.isPlaying) {
            binding.btnPlayPause.setAnimatedVectorDrawable(R.drawable.anim_play_to_pause, true)
        } else {
            binding.btnPlayPause.setAnimatedVectorDrawable(R.drawable.anim_pause_to_play, true)
        }
    }
    override fun onServiceConnected() {
        updateSongInfo()
        updatePlayPauseDrawableState()
    }
    override fun onPlayingMetaChanged() {
        updateSongInfo()
    }
    override fun onPlayStateChanged() {
        updatePlayPauseDrawableState()
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private inner class FlingPlayBackController : View.OnTouchListener {
        private val viewConfig = ViewConfiguration.get(requireContext())
        private val gestureDetector = GestureDetector(requireContext(), object : OnGestureListener {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onShowPress(e: MotionEvent) = Unit

            override fun onSingleTapUp(e: MotionEvent): Boolean  = false

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean = false

            override fun onLongPress(e: MotionEvent) = Unit

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                e1 ?: return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > viewConfig.scaledTouchSlop &&
                    abs(velocityX) > viewConfig.scaledMinimumFlingVelocity) {
                    if (diffX > 0) onSwipeRight() else onSwipeLeft()
                    return true
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
        private fun onSwipeRight() {
            if (isRtl())
                AudioPlayerRemote.playNextSong()
            else
                AudioPlayerRemote.playPreviousSong()
        }
        private fun onSwipeLeft() {
            if (isRtl())
                AudioPlayerRemote.playPreviousSong()
            else
                AudioPlayerRemote.playNextSong()
        }
        private fun isRtl(): Boolean {
            val direction = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
            return direction == View.LAYOUT_DIRECTION_RTL
        }
    }
}