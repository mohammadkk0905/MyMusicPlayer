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
import com.mohammadkk.mymusicplayer.activities.BaseActivity
import com.mohammadkk.mymusicplayer.databinding.DialogSortSheetBinding
import com.mohammadkk.mymusicplayer.databinding.ItemSortModeBinding
import com.mohammadkk.mymusicplayer.extensions.applyFullHeightDialog
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import kotlin.math.abs

class ChangeSortingDialog : BottomSheetDialogFragment() {
    private var _binding: DialogSortSheetBinding? = null
    private val sortItems = mutableListOf<Pair<Int, Int>>()
    private val settings = BaseSettings.getInstance()
    private var modeDialog = Constant.SONG_ID
    private var baseActivity: BaseActivity? = null
    private var mSortOrder = 1
    private var selectedPosition = 0
    private var isDescendingSort = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        modeDialog = arguments?.getString("change_sort_mode") ?: Constant.SONG_ID
        try {
            baseActivity = activity as BaseActivity
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
        initializeList()
    }
    private fun initializeList() {
        sortItems.clear()
        mSortOrder = settings.songsSorting
        var sortName = abs(mSortOrder)
        when (modeDialog) {
            Constant.SONG_ID -> {
                sortItems.add(Pair(R.string.title, Constant.SORT_BY_TITLE))
                sortItems.add(Pair(R.string.album, Constant.SORT_BY_ALBUM))
                sortItems.add(Pair(R.string.artist, Constant.SORT_BY_ARTIST))
                sortItems.add(Pair(R.string.duration, Constant.SORT_BY_DURATION))
                sortItems.add(Pair(R.string.date_added, Constant.SORT_BY_DATE_ADDED))
            }
            Constant.ALBUM_ID -> {
                mSortOrder = settings.albumsSorting
                sortName = abs(mSortOrder)
                sortItems.add(Pair(R.string.title, Constant.SORT_BY_TITLE))
                sortItems.add(Pair(R.string.artist, Constant.SORT_BY_ARTIST))
                sortItems.add(Pair(R.string.year, Constant.SORT_BY_YEAR))
                sortItems.add(Pair(R.string.duration, Constant.SORT_BY_DURATION))
                sortItems.add(Pair(R.string.song_count, Constant.SORT_BY_SONGS))
            }
            Constant.ARTIST_ID -> {
                mSortOrder = settings.artistsSorting
                sortName = abs(mSortOrder)
                sortItems.add(Pair(R.string.title, Constant.SORT_BY_TITLE))
                sortItems.add(Pair(R.string.duration, Constant.SORT_BY_DURATION))
                sortItems.add(Pair(R.string.song_count, Constant.SORT_BY_SONGS))
                sortItems.add(Pair(R.string.album_count, Constant.SORT_BY_ALBUMS))
            }
        }
        selectedPosition = sortItems.indexOfFirst { it.second == sortName }
        isDescendingSort = mSortOrder < 0
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
        private val colors = Pair(
            requireContext().getColorCompat(R.color.toolbar_bg),
            requireContext().getColorCompat(R.color.blue_primary_container)
        )

        inner class SortHolder(private val binding: ItemSortModeBinding): RecyclerView.ViewHolder(binding.root) {
            fun bindItems(item: Pair<Int, Int>) {
                with(binding) {
                    tvSortItem.setText(item.first)
                    ivSortItem.isInvisible = selectedPosition != absoluteAdapterPosition
                    ivSortItem.rotation = if (!isDescendingSort) 0f else 180f
                    root.setCardBackgroundColor(
                        if (selectedPosition == absoluteAdapterPosition) {
                            colors.second
                        } else colors.first
                    )
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
                        setSortItem(item)
                    }
                }
            }
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
        private fun setSortItem(itemSort: Pair<Int, Int>) {
            var sortName = itemSort.second
            if (mSortOrder < 0) sortName *= -1
            if (!isDescendingSort) {
                if (sortName < 0) sortName = abs(sortName)
            }
            if (isDescendingSort) {
                if (sortName > 0) sortName *= -1
            }
            if (sortName != mSortOrder) {
                when (modeDialog) {
                    Constant.SONG_ID -> settings.songsSorting = sortName
                    Constant.ALBUM_ID -> settings.albumsSorting = sortName
                    Constant.ARTIST_ID -> settings.artistsSorting = sortName
                }
                baseActivity?.onReloadLibrary(modeDialog)
                dismiss()
            }
        }
    }
    companion object {
        fun showDialog(manager: FragmentManager, modeId: String) {
            ChangeSortingDialog().apply {
                arguments = bundleOf("change_sort_mode" to modeId)
                show(manager, "CHANGE_SORT_DIALOG")
            }
        }
    }
}