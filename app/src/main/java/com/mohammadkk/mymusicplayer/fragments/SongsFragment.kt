package com.mohammadkk.mymusicplayer.fragments

import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentSongsBinding
import com.mohammadkk.mymusicplayer.extensions.collectImmediately
import com.mohammadkk.mymusicplayer.extensions.createFastScroll
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlin.random.Random.Default.nextInt

class SongsFragment : Fragment(R.layout.fragment_songs), SearchView.OnQueryTextListener {
    private lateinit var binding: FragmentSongsBinding
    private val musicViewModel: MusicViewModel by activityViewModels()
    private val settings = BaseSettings.getInstance()
    private var songsAdapter: SongsAdapter? = null
    private var unchangedList = listOf<Song>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        musicViewModel.fragmentLibraries[0] = this
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentSongsBinding.bind(view)
        binding.songsListView.apply {
            songsAdapter = SongsAdapter(requireActivity(), mutableListOf(), "MAIN")
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = songsAdapter
            createFastScroll()
        }
        collectImmediately(musicViewModel.songsList, ::updateList)
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
        }
        binding.fabShuffle.setOnClickListener {
            songsAdapter?.let { adapter ->
                if (adapter.itemCount > 0) {
                    settings.isShuffleEnabled = true
                    adapter.startPlayer(nextInt(adapter.itemCount))
                } else {
                    settings.isShuffleEnabled = false
                    binding.fabShuffle.hide()
                }
                initializeBtnShuffle()
            }
        }
        initializeBtnShuffle()
    }
    private fun getSpanCountLayout(): Int {
        return resources.getInteger(
            if (requireContext().isLandscape) R.integer.def_list_columns_land else R.integer.def_list_columns
        )
    }
    private fun updateList(songs: List<Song>) {
        unchangedList = songs
        updateAdapter(unchangedList)
        handleEmptyList(false)
    }
    private fun initializeBtnShuffle() {
        val isShuffle = settings.isShuffleEnabled
        binding.fabShuffle.contentDescription = getString(
            if (isShuffle) R.string.shuffle_enabled else R.string.shuffle_disabled
        )
    }
    override fun onQueryTextSubmit(query: String?): Boolean {
        handleSearchAdapter(query)
        return false
    }
    override fun onQueryTextChange(newText: String?): Boolean {
        handleSearchAdapter(newText)
        return false
    }
    private fun handleSearchAdapter(query: String?) {
        songsAdapter?.swapDataSet(if (!query.isNullOrEmpty()) {
            val q = query.lowercase()
            unchangedList.filter { it.title.lowercase().contains(q) }
        } else {
            unchangedList
        })
        handleEmptyList(true)
    }
    private fun handleEmptyList(isSearch: Boolean) {
        if (songsAdapter?.itemCount == 0) {
            if (binding.empty.tag != "animated_alpha") {
                binding.emptyText.setText(if (isSearch) R.string.no_results_found else R.string.no_songs)
                binding.empty.visibility = View.VISIBLE
                binding.empty.alpha = 0f
                binding.empty.animate().alpha(1f).setDuration(200).withEndAction {
                    binding.empty.alpha = 1f
                }
                binding.fabShuffle.visibility = View.INVISIBLE
                binding.empty.tag = "animated_alpha"
            }
        } else {
            binding.empty.visibility = View.GONE
            binding.emptyText.setText(R.string.no_artists)
            binding.fabShuffle.visibility = View.VISIBLE
            binding.empty.tag = null
        }
    }
    private fun updateAdapter(items: List<Song>) {
        songsAdapter?.swapDataSet(items)
        binding.fabShuffle.text = items.size.toString()
    }
}