package com.mohammadkk.mymusicplayer.fragments

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.PlayerListActivity
import com.mohammadkk.mymusicplayer.adapters.AbsMultiAdapter
import com.mohammadkk.mymusicplayer.databinding.FragmentLibrariesBinding
import com.mohammadkk.mymusicplayer.databinding.ItemGridArtistBinding
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.collectImmediately
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.extensions.shareSongsIntent
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScrollerBuilder
import com.mohammadkk.mymusicplayer.utils.Libraries
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
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
            artistsAdapter = ArtistsAdapter(requireActivity(), mutableListOf())
            setHasFixedSize(true)
            layoutManager = GridLayoutManager(requireContext(), getSpanCountLayout())
            adapter = artistsAdapter
            FastScrollerBuilder(binding.listRv).setPadding(Rect()).build()
        }
        collectImmediately(musicViewModel.artistsList, ::updateList)
        collectImmediately(musicViewModel.searchHandle, ::handleSearchAdapter)
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
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
        context: FragmentActivity,
        var dataSet: MutableList<Artist>
    ) : AbsMultiAdapter<ArtistsAdapter.ViewHolder, Artist>(context), FastScroller.PopupTextProvider {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(ItemGridArtistBinding.inflate(inflater, parent, false))
        }
        override fun onMultiplePrepareActionMode(menu: Menu): Boolean {
            baseSettings.actionModeIndex = 2
            return true
        }
        override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Artist>) {
            val selectedItems = selection.flatMap { it.songs }
            when (menuItem.itemId) {
                R.id.action_share -> context.shareSongsIntent(selectedItems)
                R.id.action_remove_files -> DeleteSongsDialog.create(
                    selectedItems, context.supportFragmentManager
                )
            }
        }
        override fun getIdentifier(position: Int): Artist? {
            return dataSet.getOrNull(position)
        }
        override fun getItemCount(): Int {
            return dataSet.size
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItems(dataSet[holder.absoluteAdapterPosition])
        }
        override fun getPopupText(view: View, position: Int): CharSequence {
            val artist = dataSet.getOrNull(position)
            val result = when (abs(baseSettings.artistsSorting)) {
                Constant.SORT_BY_TITLE -> artist?.title
                Constant.SORT_BY_DURATION -> return artist?.duration?.toFormattedDuration(true) ?: "-"
                else -> artist?.title
            }
            return Libraries.getSectionName(result, true)
        }
        fun swapDataSet(dataSet: List<Artist>) {
            this.dataSet = ArrayList(dataSet)
            notifyDataSetChanged()
        }
        private fun startTracks(artist: Artist) {
            Intent(context, PlayerListActivity::class.java).apply {
                val json = Constant.pairStateToJson(Pair(Constant.ARTIST_TAB, artist.id))
                putExtra(Constant.LIST_CHILD, json)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    context, android.R.anim.fade_in, android.R.anim.fade_out
                ).toBundle()
                context.startActivity(this, options)
            }
        }
        inner class ViewHolder(private val binding: ItemGridArtistBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bindItems(artist: Artist) {
                with(binding) {
                    if (checked.contains(artist)) {
                        checkbox.visibility = View.VISIBLE
                        root.setCardBackgroundColor(ThemeManager.colorPrimaryAlpha)
                    } else {
                        checkbox.visibility = View.INVISIBLE
                        root.setCardBackgroundColor(Color.TRANSPARENT)
                    }
                    tvTitle.text = artist.title
                    tvText.text = context.getString(
                        R.string.artists_symbol, artist.albumCount, artist.trackCount
                    )
                    image.bind(artist.getSafeSong(), R.drawable.ic_artist)
                    root.setOnClickListener {
                        if (isInQuickSelectMode) {
                            toggleChecked(absoluteAdapterPosition)
                        } else {
                            if (!Constant.isBlockingClick()) startTracks(artist)
                        }
                    }
                    root.setOnLongClickListener {
                        toggleChecked(absoluteAdapterPosition)
                        true
                    }
                }
            }
        }
    }
}