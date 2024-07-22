package com.mohammadkk.mymusicplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.PlayerActivity
import com.mohammadkk.mymusicplayer.databinding.FragmentNowPlayingBinding
import com.mohammadkk.mymusicplayer.extensions.sendIntent
import com.mohammadkk.mymusicplayer.extensions.updatePlayingState
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.services.MusicService.Companion.isGlobalPlayAnim
import com.mohammadkk.mymusicplayer.viewmodels.PlaybackViewModel

class NowPlayingFragment : Fragment(R.layout.fragment_now_playing) {
    private lateinit var binding: FragmentNowPlayingBinding
    private val playbackViewModel: PlaybackViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentNowPlayingBinding.bind(view)
        initializeViewModel()
        binding.root.setOnClickListener {
            with(requireActivity()) {
                val mIntent = Intent(this, PlayerActivity::class.java)
                mIntent.putExtra("fade_anim", true)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    this, android.R.anim.fade_in, android.R.anim.fade_out
                ).toBundle()
                startActivity(mIntent, options)
            }
        }
        binding.btnPlayPause.setOnClickListener {
            isGlobalPlayAnim = true
            requireContext().sendIntent(Constant.PLAY_PAUSE)
        }
    }
    override fun onStop() {
        super.onStop()
        isGlobalPlayAnim = false
    }
    private fun initializeViewModel() {
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
        binding.trackImage.bind(song)
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
            binding.trackImage.setStaticIcon(R.drawable.ic_audiotrack)
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
}