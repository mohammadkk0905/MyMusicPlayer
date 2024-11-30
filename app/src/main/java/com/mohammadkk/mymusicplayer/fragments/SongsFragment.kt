package com.mohammadkk.mymusicplayer.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.MainActivity
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentLibrariesBinding
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScrollerBuilder
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

class SongsFragment : ABaseFragment(R.layout.fragment_libraries) {
    private var _binding: FragmentLibrariesBinding? = null
    private val binding get() = _binding!!
    private val musicViewModel: MusicViewModel by activityViewModels()
    private var songsAdapter: SongsAdapter? = null
    private var unchangedList = listOf<Song>()
    private var mainActivity: MainActivity? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibrariesBinding.bind(view)
        mainActivity = serviceActivity as? MainActivity
        binding.listRv.apply {
            songsAdapter = SongsAdapter(requireActivity(), "MAIN")
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = songsAdapter
            FastScrollerBuilder(this)
                .setPadding(0, 0, 0, 0)
                .setPopupTextProvider(object : FastScroller.PopupTextProvider {
                    override fun getPopupText(view: View, position: Int): CharSequence {
                        return songsAdapter?.getPopupText(position) ?: ""
                    }
                })
                .setFastScrollListener(object : FastScroller.Listener {
                    override fun onFastScrollingChanged(isFastScrolling: Boolean) {
                        mainActivity?.setVisibilityFloating(!isFastScrolling)
                    }
                }).build()

        }
        observeData()
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
        }
    }
    private fun getSpanCountLayout(): Int {
        return resources.getInteger(
            if (requireContext().isLandscape) R.integer.def_list_columns_land else R.integer.def_list_columns
        )
    }
    private fun observeData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.songsList.collect(::updateList)
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.searchHandle.collect(::handleSearchAdapter)
            }
        }
    }
    private fun updateList(songs: List<Song>) {
        unchangedList = songs
        updateAdapter(unchangedList)
        handleEmptyList(false)
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
                mainActivity?.setVisibilityFloating(false)
                empty.tag = "animated_alpha"
            }
        } else {
            empty.visibility = View.GONE
            emptyText.setText(R.string.no_artists)
            mainActivity?.setVisibilityFloating(true)
            empty.tag = null
        }
    }
    private fun updateAdapter(items: List<Song>) {
        songsAdapter?.swapDataSet(items)
    }
    override fun onServiceConnected() {
        songsAdapter?.setPlaying(AudioPlayerRemote.currentSong, AudioPlayerRemote.isPlaying)
    }
    override fun onPlayingMetaChanged() {
        songsAdapter?.setPlaying(AudioPlayerRemote.currentSong, AudioPlayerRemote.isPlaying)
    }
    override fun onPlayStateChanged() {
        songsAdapter?.setPlaying(AudioPlayerRemote.currentSong, AudioPlayerRemote.isPlaying)
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