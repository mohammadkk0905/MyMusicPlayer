package com.mohammadkk.mymusicplayer.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.PlayerListActivity
import com.mohammadkk.mymusicplayer.databinding.FragmentLibrariesBinding
import com.mohammadkk.mymusicplayer.databinding.ItemGenreBinding
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.collectImmediately
import com.mohammadkk.mymusicplayer.extensions.isLandscape
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.utils.Libraries
import com.mohammadkk.mymusicplayer.viewmodels.MusicViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GenresFragment : Fragment(R.layout.fragment_libraries) {
    private val musicViewModel: MusicViewModel by activityViewModels()
    private var _binding: FragmentLibrariesBinding? = null
    private val binding get() = _binding!!
    private var unchangedList = listOf<Genre>()
    private var genreAdapter: GenreAdapter? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLibrariesBinding.bind(view)
        binding.listRv.apply {
            genreAdapter = GenreAdapter(requireActivity(), listOf())
            setHasFixedSize(true)
            layoutManager = createLayoutManager()
            adapter = genreAdapter
        }
        collectImmediately(musicViewModel.genresList, ::updateList)
        collectImmediately(musicViewModel.searchHandle, ::handleSearchAdapter)
        binding.fragRefresher.setOnRefreshListener {
            binding.fragRefresher.postDelayed({
                musicViewModel.updateLibraries()
                binding.fragRefresher.isRefreshing = false
            }, 200)
        }
    }
    private fun createLayoutManager(): LinearLayoutManager {
        val contextActivity = requireActivity()
        return if (contextActivity.isLandscape) {
            GridLayoutManager(contextActivity, 4)
        } else {
            GridLayoutManager(contextActivity, 2)
        }
    }
    private fun updateList(genres: List<Genre>) {
        unchangedList = genres
        genreAdapter?.swapDataSet(genres)
        handleEmptyList(false)
    }
    private fun handleSearchAdapter(search: Triple<Int, Boolean, String>) {
        if (search.first == 3) {
            if (!search.second) {
                genreAdapter?.swapDataSet(if (search.third.isNotEmpty()) {
                    val q = search.third.lowercase()
                    unchangedList.filter { it.name.lowercase().contains(q) }
                } else {
                    unchangedList
                })
                handleEmptyList(true)
            } else {
                musicViewModel.setSearch(-1, true, null)
                genreAdapter?.swapDataSet(unchangedList)
                handleEmptyList(false)
            }
        }
    }
    private fun handleEmptyList(isSearch: Boolean) = _binding?.run {
        if (genreAdapter?.itemCount == 0) {
            if (empty.tag != "animated_alpha") {
                emptyText.setText(if (isSearch) R.string.no_results_found else R.string.no_albums)
                empty.visibility = View.VISIBLE
                empty.alpha = 0f
                empty.animate().alpha(1f).setDuration(200).withEndAction {
                    empty.alpha = 1f
                }
                empty.tag = "animated_alpha"
            }
        } else {
            empty.visibility = View.GONE
            emptyText.setText(R.string.no_albums)
            empty.tag = null
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private class GenreAdapter(
        private val context: FragmentActivity,
        var dataSet: List<Genre>
    ) : RecyclerView.Adapter<GenreAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(context)
            return ViewHolder(ItemGenreBinding.inflate(inflater, parent, false))
        }
        override fun getItemCount(): Int {
            return dataSet.size
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItem(dataSet[position])
        }
        fun swapDataSet(list: List<Genre>) {
            dataSet = list
            notifyDataSetChanged()
        }
        private fun startTracks(genre: Genre) {
            Intent(context, PlayerListActivity::class.java).apply {
                val json = Constant.pairStateToJson(Pair(Constant.GENRE_TAB, genre.id))
                putExtra(Constant.LIST_CHILD, json)
                val options = ActivityOptionsCompat.makeCustomAnimation(
                    context, android.R.anim.fade_in, android.R.anim.fade_out
                ).toBundle()
                context.startActivity(this, options)
            }
        }
        private fun loadGenreImage(genre: Genre, image: AppCompatImageView) {
            CoroutineScope(Dispatchers.IO).launch {
                val genreSong = Libraries.songByGenre(context.contentResolver, genre.id)
                withContext(Dispatchers.Main) { image.bind(genreSong, R.drawable.ic_audiotrack) }
            }
        }
        inner class ViewHolder(val binding: ItemGenreBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bindItem(genre: Genre) {
                binding.tvTitle.text = genre.name
                binding.tvText.text = context.resources.getQuantityString(
                    R.plurals.songs_plural, genre.songCount, genre.songCount
                )
                loadGenreImage(genre, binding.image)
                binding.root.setOnClickListener {
                    if (!Constant.isBlockingClick()) {
                        startTracks(genre)
                    }
                }
            }
        }
    }
}