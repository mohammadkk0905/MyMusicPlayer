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
import com.mohammadkk.mymusicplayer.databinding.ItemGridBinding
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.launchPlayerList
import com.mohammadkk.mymusicplayer.extensions.makeSectionName
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.extensions.toLocaleYear
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScrollerBuilder
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

class AlbumsFragment : Fragment(R.layout.fragment_libraries) {
    private lateinit var binding: FragmentLibrariesBinding
    private val musicViewModel: MusicViewModel by activityViewModels()
    private var albumsAdapter: AlbumsAdapter? = null
    private var unchangedList = listOf<Album>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLibrariesBinding.bind(view)
        binding.listRv.apply {
            albumsAdapter = AlbumsAdapter(requireActivity())
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = albumsAdapter
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
    private fun getSpanCountLayout(): Int {
        return resources.getInteger(
            if (requireContext().isLandscape) R.integer.def_grid_columns_land else R.integer.def_grid_columns
        )
    }
    private fun observeData() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.albumsList.collect(::updateList)
            }
        }
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                musicViewModel.searchHandle.collect(::handleSearchAdapter)
            }
        }
    }
    private fun updateList(albums: List<Album>) {
        unchangedList = albums
        albumsAdapter?.swapDataSet(unchangedList)
        handleEmptyList(false)
    }
    private fun handleSearchAdapter(search: Triple<Int, Boolean, String>) {
        if (search.first == 1) {
            if (!search.second) {
                albumsAdapter?.swapDataSet(if (search.third.isNotEmpty()) {
                    val q = search.third.lowercase()
                    unchangedList.filter { it.title.lowercase().contains(q) }
                } else {
                    unchangedList
                })
                handleEmptyList(true)
            } else {
                musicViewModel.setSearch(-1, true, null)
                albumsAdapter?.swapDataSet(unchangedList)
                handleEmptyList(false)
            }
        }
    }
    private fun handleEmptyList(isSearch: Boolean) {
        if (albumsAdapter?.itemCount == 0) {
            if (binding.empty.tag != "animated_alpha") {
                binding.emptyText.setText(if (isSearch) R.string.no_results_found else R.string.no_albums)
                binding.empty.visibility = View.VISIBLE
                binding.empty.alpha = 0f
                binding.empty.animate().alpha(1f).setDuration(200).withEndAction {
                    binding.empty.alpha = 1f
                }
                binding.empty.tag = "animated_alpha"
            }
        } else {
            binding.empty.visibility = View.GONE
            binding.emptyText.setText(R.string.no_albums)
            binding.empty.tag = null
        }
    }
    private class AlbumsAdapter(
        context: FragmentActivity
    ) : AbsMultiAdapter<AlbumsAdapter.ViewHolder, Album>(context), FastScroller.PopupTextProvider {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(ItemGridBinding.inflate(inflater, parent, false))
        }
        override fun getIdentifier(position: Int): Album? {
            return dataset.getOrNull(position)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItems(dataset[holder.absoluteAdapterPosition])
        }
        override fun getPopupText(view: View, position: Int): CharSequence {
            val album = dataset.getOrNull(position)
            val result = when (abs(baseSettings.albumsSorting)) {
                Constant.SORT_BY_TITLE -> album?.title
                Constant.SORT_BY_ARTIST -> album?.artist
                Constant.SORT_BY_YEAR -> return album?.year?.toLocaleYear() ?: "-"
                Constant.SORT_BY_DURATION -> return album?.duration?.toFormattedDuration(true) ?: "-"
                else -> album?.title
            }
            return result.makeSectionName()
        }
        fun swapDataSet(dataSet: List<Album>) {
            this.dataset = dataSet.toMutableList()
        }
        inner class ViewHolder(private val binding: ItemGridBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bindItems(album: Album) = with(binding) {
                menu.visibility = View.GONE
                if (selectionController.isFirstSelected) {
                    checkbox.visibility = View.VISIBLE
                } else {
                    checkbox.visibility = View.GONE
                }
                if (selectionController.isSelected(album)) {
                    root.isActivated = true
                    checkbox.isChecked = true
                } else {
                    root.isActivated = false
                    checkbox.isChecked = false
                }
                tvTitle.text = album.title
                text.text = context.resources.getQuantityString(
                    R.plurals.songs_plural, album.trackCount, album.trackCount
                )
                image.bind(album.getSafeSong(), R.drawable.ic_album)
                root.setOnClickListener {
                    if (selectionController.isInQuickSelectMode) {
                        selectionController.toggle(absoluteAdapterPosition)
                    } else {
                        if (!Constant.isBlockingClick()) {
                            context.launchPlayerList(Constant.ALBUM_TAB, album.id)
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