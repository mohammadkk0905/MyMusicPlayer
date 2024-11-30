package com.mohammadkk.mymusicplayer.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isInvisible
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.databinding.DialogSortSheetBinding
import com.mohammadkk.mymusicplayer.databinding.ItemSortModeBinding
import com.mohammadkk.mymusicplayer.extensions.applyFullHeightDialog
import kotlin.math.abs

class ChangeSortingDialog : BottomSheetDialogFragment() {
    private var _binding: DialogSortSheetBinding? = null
    private val settings = BaseSettings.getInstance()
    private var modeDialog = 0
    private var baseActivity: MusicServiceActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        modeDialog = arguments?.getInt("change_sort_mode") ?: 0
        try {
            baseActivity = activity as MusicServiceActivity
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = DialogSortSheetBinding.inflate(inflater, container, false)
        return _binding?.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog.applyFullHeightDialog(requireActivity())
        val binding = _binding ?: return
        binding.dragSheet.alpha = 0.5f
        val adapter = SortAdapter()
        binding.rvSortMode.adapter = adapter
        binding.rvSortMode.setHasFixedSize(true)
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private inner class SortAdapter : RecyclerView.Adapter<SortAdapter.SortHolder>() {
        private val sortItems: IntArray
        private var mSortOrder = 1
        private var selectedPosition = 0
        private var isDescendingSort = false

        init {
            mSortOrder = settings.songsSorting
            sortItems = when (modeDialog) {
                0 -> {
                    intArrayOf(
                        Constant.SORT_BY_TITLE, Constant.SORT_BY_ALBUM,
                        Constant.SORT_BY_ARTIST, Constant.SORT_BY_DURATION,
                        Constant.SORT_BY_DATE_ADDED, Constant.SORT_BY_DATE_MODIFIED
                    )
                }
                1 -> {
                    mSortOrder = settings.albumsSorting
                    intArrayOf(
                        Constant.SORT_BY_TITLE, Constant.SORT_BY_ARTIST,
                        Constant.SORT_BY_YEAR, Constant.SORT_BY_DURATION,
                        Constant.SORT_BY_SONGS
                    )
                }
                2 -> {
                    mSortOrder = settings.artistsSorting
                    intArrayOf(
                        Constant.SORT_BY_TITLE, Constant.SORT_BY_DURATION,
                        Constant.SORT_BY_SONGS, Constant.SORT_BY_ALBUMS
                    )
                }
                else -> {
                    mSortOrder = settings.genresSorting
                    intArrayOf(Constant.SORT_BY_TITLE, Constant.SORT_BY_SONGS)
                }
            }
            selectedPosition = sortItems.indexOfFirst { it == abs(mSortOrder) }
            isDescendingSort = mSortOrder < 0
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SortHolder {
            val inflater = LayoutInflater.from(parent.context)
            return SortHolder(ItemSortModeBinding.inflate(inflater, parent, false))
        }
        override fun getItemCount(): Int {
            return sortItems.size
        }
        override fun onBindViewHolder(holder: SortHolder, position: Int) {
            holder.bindItems(sortItems[holder.absoluteAdapterPosition])
        }
        private fun setSortItem(itemSort: Int) {
            var sortName = itemSort
            if (mSortOrder < 0) sortName *= -1
            if (!isDescendingSort) if (sortName < 0) sortName = abs(sortName)
            if (isDescendingSort) if (sortName > 0) sortName *= -1
            if (sortName != mSortOrder) {
                when (modeDialog) {
                    0 -> settings.songsSorting = sortName
                    1 -> settings.albumsSorting = sortName
                    2 -> settings.artistsSorting = sortName
                    3 -> settings.genresSorting = sortName
                }
                baseActivity?.onReloadLibrary(modeDialog)
                dismiss()
            }
        }
        inner class SortHolder(private val binding: ItemSortModeBinding): RecyclerView.ViewHolder(binding.root) {
            fun bindItems(sort: Int) = with(binding) {
                tvSortLabel.setText(getStringBySort(sort))
                ivArrow.isInvisible = selectedPosition != absoluteAdapterPosition
                ivArrow.rotation = if (!isDescendingSort) 0f else 180f
                if (selectedPosition == absoluteAdapterPosition) {
                    tvSortLabel.isSelected = true
                    selectedThumbnailOverlay.visibility = View.VISIBLE
                } else {
                    tvSortLabel.isSelected = false
                    selectedThumbnailOverlay.visibility = View.GONE
                }
                root.setOnClickListener {
                    if (absoluteAdapterPosition != selectedPosition) {
                        isDescendingSort = false
                        notifyItemChanged(selectedPosition)
                        selectedPosition = absoluteAdapterPosition
                        notifyItemChanged(absoluteAdapterPosition)
                    } else {
                        isDescendingSort = !isDescendingSort
                        notifyItemChanged(selectedPosition)
                    }
                    setSortItem(sort)
                }
            }
            fun getStringBySort(sort: Int) = when (sort) {
                 Constant.SORT_BY_TITLE -> R.string.title
                 Constant.SORT_BY_ALBUM -> R.string.album
                 Constant.SORT_BY_ARTIST -> R.string.artist
                 Constant.SORT_BY_DURATION -> R.string.duration
                 Constant.SORT_BY_DATE_ADDED -> R.string.date_added
                 Constant.SORT_BY_DATE_MODIFIED -> R.string.date_modified
                 Constant.SORT_BY_YEAR -> R.string.year
                 Constant.SORT_BY_SONGS -> R.string.song_count
                 else -> R.string.album_count
            }
        }
    }
    companion object {
        fun showDialog(manager: FragmentManager, tabIndex: Int) {
            ChangeSortingDialog().apply {
                arguments = bundleOf("change_sort_mode" to tabIndex)
                show(manager, "CHANGE_SORT_DIALOG")
            }
        }
    }
}