package com.mohammadkk.mymusicplayer.fragments

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.adapters.AbsMultiAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentLibrariesBinding
import com.mohammadkk.mymusicplayer.databinding.ItemGridArtistBinding
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.launchPlayerList
import com.mohammadkk.mymusicplayer.extensions.makeSectionName
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScrollerBuilder
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

class ArtistsFragment : Fragment(R.layout.fragment_libraries) {
    private lateinit var binding: FragmentLibrariesBinding
    private val musicViewModel: MusicViewModel by activityViewModels()
    private var artistsAdapter: ArtistsAdapter? = null
    private var unchangedList = listOf<Artist>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLibrariesBinding.bind(view)
        binding.listRv.apply {
            artistsAdapter = ArtistsAdapter(requireActivity())
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = artistsAdapter
            FastScrollerBuilder(binding.listRv).setPadding(Rect()).build()
        }
        observeData()
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
        }
    }
    private fun observeData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.artistsList.collect(::updateList)
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.searchHandle.collect(::handleSearchAdapter)
            }
        }
    }
    private fun updateList(artists: List<Artist>) {
        unchangedList = artists
        artistsAdapter?.swapDataSet(unchangedList)
        handleEmptyList(false)
    }
    private fun getSpanCountLayout(): Int {
        return resources.getInteger(
            if (requireContext().isLandscape) R.integer.def_grid_columns_land else R.integer.def_grid_columns
        )
    }
    private fun handleSearchAdapter(search: Triple<Int, Boolean, String>) {
        if (search.first == 2) {
            if (!search.second) {
                artistsAdapter?.swapDataSet(if (search.third.isNotEmpty()) {
                    val q = search.third.lowercase()
                    unchangedList.filter { it.title.lowercase().contains(q) }
                } else {
                    unchangedList
                })
                handleEmptyList(true)
            } else {
                musicViewModel.setSearch(-1, true, null)
                artistsAdapter?.swapDataSet(unchangedList)
                handleEmptyList(false)
            }
        }
    }
    private fun handleEmptyList(isSearch: Boolean) {
        if (artistsAdapter?.itemCount == 0) {
            if (binding.empty.tag != "animated_alpha") {
                binding.emptyText.setText(if (isSearch) R.string.no_results_found else R.string.no_artists)
                binding.empty.visibility = View.VISIBLE
                binding.empty.alpha = 0f
                binding.empty.animate().alpha(1f).setDuration(200).withEndAction {
                    binding.empty.alpha = 1f
                }
                binding.empty.tag = "animated_alpha"
            }
        } else {
            binding.empty.visibility = View.GONE
            binding.emptyText.setText(R.string.no_artists)
            binding.empty.tag = null
        }
    }
    private class ArtistsAdapter(
        context: FragmentActivity
    ) : AbsMultiAdapter<ArtistsAdapter.ViewHolder, Artist>(context), FastScroller.PopupTextProvider {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(ItemGridArtistBinding.inflate(inflater, parent, false))
        }
        override fun getIdentifier(position: Int): Artist? {
            return dataset.getOrNull(position)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItems(dataset[holder.absoluteAdapterPosition])
        }
        override fun getPopupText(view: View, position: Int): CharSequence {
            val artist = dataset.getOrNull(position)
            val result = when (abs(baseSettings.artistsSorting)) {
                Constant.SORT_BY_TITLE -> artist?.title
                Constant.SORT_BY_DURATION -> return artist?.duration?.toFormattedDuration(true) ?: "-"
                else -> artist?.title
            }
            return result.makeSectionName()
        }
        fun swapDataSet(dataSet: List<Artist>) {
            this.dataset = dataSet.toMutableList()
        }
        inner class ViewHolder(private val binding: ItemGridArtistBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bindItems(artist: Artist) = with(binding) {
                menu.visibility = View.GONE
                if (selectionController.isFirstSelected) {
                    checkbox.visibility = View.VISIBLE
                } else {
                    checkbox.visibility = View.GONE
                }
                if (selectionController.isSelected(artist)) {
                    root.isActivated = true
                    checkbox.isChecked = true
                } else {
                    root.isActivated = false
                    checkbox.isChecked = false
                }
                tvTitle.text = artist.title
                text.text = context.getString(
                    R.string.artists_symbol, artist.albumCount, artist.trackCount
                )
                image.bind(artist.getSafeSong(), R.drawable.ic_artist)
                root.setOnClickListener {
                    if (selectionController.isInQuickSelectMode) {
                        selectionController.toggle(absoluteAdapterPosition)
                    } else {
                        if (!Constant.isBlockingClick()) {
                            context.launchPlayerList(Constant.ARTIST_TAB, artist.id)
                        }
                    }
                }
                root.setOnLongClickListener {
                    selectionController.toggle(absoluteAdapterPosition)
                }
            }
        }
    }
}