package com.mohammadkk.mymusicplayer.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentSongsBinding
import com.mohammadkk.mymusicplayer.extensions.collectImmediately
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScrollerBuilder
import com.mohammadkk.mymusicplayer.utils.Libraries
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlin.math.abs

class SongsFragment : ABaseFragment(R.layout.fragment_songs) {
    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!
    private val musicViewModel: MusicViewModel by activityViewModels()
    private val settings = BaseSettings.getInstance()
    private var songsAdapter: SongsAdapter? = null
    private var unchangedList = listOf<Song>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSongsBinding.bind(view)
        binding.songsListView.apply {
            songsAdapter = SongsAdapter(requireActivity(), mutableListOf(), "MAIN")
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = songsAdapter
            FastScrollerBuilder(binding.songsListView)
                .setPadding(0, 0, 0, 0)
                .setPopupTextProvider(object : FastScroller.PopupTextProvider {
                    override fun getPopupText(view: View, position: Int): CharSequence {
                        return getPopupText(position)
                    }
                })
                .setFastScrollListener(object : FastScroller.Listener {
                    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
                        if (isFastScrolling) {
                            binding.fabShuffle.hide()
                        } else {
                            binding.fabShuffle.show()
                        }
                    }
                }).build()
        }
        collectImmediately(musicViewModel.songsList, ::updateList)
        collectImmediately(musicViewModel.searchHandle, ::handleSearchAdapter)
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
        }
        binding.fabShuffle.setOnClickListener {
            songsAdapter?.startShufflePlayer()
            initializeBtnShuffle()
        }
        initializeBtnShuffle()
    }
    private fun getSpanCountLayout(): Int {
        return resources.getInteger(
            if (requireContext().isLandscape) R.integer.def_list_columns_land else R.integer.def_list_columns
        )
    }
    private fun getPopupText(position: Int): String {
        val song = unchangedList.getOrNull(position)
        val sortName = abs(settings.songsSorting)
        val result = when (sortName) {
            Constant.SORT_BY_TITLE -> song?.title
            Constant.SORT_BY_ALBUM -> song?.album
            Constant.SORT_BY_ARTIST -> song?.artist
            Constant.SORT_BY_DURATION -> return song?.duration?.toFormattedDuration(true) ?: "-"
            else -> song?.title
        }
        return Libraries.getSectionName(result, true)
    }
    private fun updateList(songs: List<Song>) {
        unchangedList = songs
        updateAdapter(unchangedList)
        handleEmptyList(false)
    }
    private fun initializeBtnShuffle() {
        val isShuffle = AudioPlayerRemote.isShuffleMode
        binding.fabShuffle.contentDescription = getString(
            if (isShuffle) R.string.shuffle_enabled else R.string.shuffle_disabled
        )
    }
    private fun handleSearchAdapter(search: Triple<Int, Boolean, String>) {
        if (search.first == 0) {
            if (!search.second) {
                updateAdapter(if (search.third.isNotEmpty()) {
                    val q = search.third.lowercase()
                    unchangedList.filter { it.title.lowercase().contains(q) }
                } else {
                    unchangedList
                })
                handleEmptyList(true)
            } else {
                musicViewModel.setSearch(-1, true, null)
                updateAdapter(unchangedList)
                handleEmptyList(false)
            }
        }
    }
    private fun handleEmptyList(isSearch: Boolean) = _binding?.run {
        if (songsAdapter?.itemCount == 0) {
            if (empty.tag != "animated_alpha") {
                emptyText.setText(if (isSearch) R.string.no_results_found else R.string.no_songs)
                empty.visibility = View.VISIBLE
                empty.alpha = 0f
                empty.animate().alpha(1f).setDuration(200).withEndAction {
                    binding.empty.alpha = 1f
                }
                fabShuffle.visibility = View.INVISIBLE
                empty.tag = "animated_alpha"
            }
        } else {
            empty.visibility = View.GONE
            emptyText.setText(R.string.no_artists)
            fabShuffle.visibility = View.VISIBLE
            empty.tag = null
        }
    }
    private fun updateAdapter(items: List<Song>) {
        songsAdapter?.swapDataSet(items)
        binding.fabShuffle.text = items.size.toString()
    }
    override fun onServiceConnected() {
        songsAdapter?.setPlaying(AudioPlayerRemote.currentSong)
    }
    override fun onPlayingMetaChanged() {
        songsAdapter?.setPlaying(AudioPlayerRemote.currentSong)
    }
    override fun onMediaStoreChanged() {
        super.onMediaStoreChanged()
        if (requireContext().hasPermission(Constant.STORAGE_PERMISSION)) {
            musicViewModel.updateLibraries()
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}