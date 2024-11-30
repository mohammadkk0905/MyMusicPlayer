package com.mohammadkk.mymusicplayer.ui.cab

import android.graphics.Color
import android.view.MenuItem
import androidx.annotation.ColorInt
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.adapters.SongsAdapter
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.extensions.applyColor
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.shareSongsIntent
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Genre
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.ui.cab.ToolbarCab.Companion.initToolbarCab
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MultiSelectionController<I>(
    private val linkedAdapter: IMultiSelectableAdapter<I>,
    private val activity: FragmentActivity,
) {
    private val selected = ArrayList<I>()
    var isFirstSelected = false

    fun toggle(datasetPosition: Int): Boolean {
        val item = linkedAdapter.getItem(datasetPosition) ?: return false
        if (!selected.remove(item)) selected.add(item)
        if (!isFirstSelected) {
            isFirstSelected = true
            cab?.toolbar?.setNavigationOnClickListener { clearSelection(true) }
            cab?.setupMenu()
            notifyDataSetChanged()
        } else {
            if (selected.size > 0) linkedAdapter.notifyItemChanged(datasetPosition)
        }
        updateCab()
        return true
    }
    private fun setOnBackPressed(runnable: Runnable?) {
        val baseActivity = activity as? MusicServiceActivity
        baseActivity?.actionModeBackPressed = runnable
    }

    val isInQuickSelectMode: Boolean
        get() = cab != null && cab?.status == ToolbarCab.STATUS_ACTIVE

    private fun selectAll() {
        selected.clear()
        for (i in 0 until linkedAdapter.getItemCount()) {
            val item = linkedAdapter.getItem(i)
            if (item != null) selected.add(item)
        }
        notifyDataSetChanged()
        updateCab()
    }
    private fun unselectedAll() {
        selected.clear()
        notifyDataSetChanged()
        updateCab()
    }
    private fun clearSelection(isNotify: Boolean) {
        cab?.hide()
        isFirstSelected = false
        if (isNotify) unselectedAll()
        setOnBackPressed(null)
        onBackPressedDispatcherRegistered = false
    }
    private fun invertSelected(): Boolean {
        val previousSelected = selected.toMutableList()
        selected.clear()
        var isNotEmpty = false
        for (i in 0 until linkedAdapter.getItemCount()) {
            val item = linkedAdapter.getItem(i)
            if (item != null && item !in previousSelected) {
                isNotEmpty = selected.add(item)
            }
        }
        notifyDataSetChanged()
        updateCab()
        return isNotEmpty
    }
    fun isSelected(item: I): Boolean {
        return selected.contains(item)
    }
    private var onBackPressedDispatcherRegistered = false

    private fun updateCab() {
        val size = selected.size
        val currentCab = cab
        if (currentCab != null) {
            if (size > 0) {
                currentCab.titleText = "$size / ${linkedAdapter.getItemCount()}"
                currentCab.setupMenu()
                currentCab.show()
            } else {
                clearSelection(false)
                notifyDataSetChanged()
            }
            if (!onBackPressedDispatcherRegistered) {
                onBackPressedDispatcherRegistered = true
                setOnBackPressed { clearSelection(true) }
            }
        }
    }

    @get:ColorInt
    val cabColor: Int get() = ThemeManager.colorPrimaryContainer

    @get:ColorInt
    val textColor: Int
        get() = if (ThemeManager.isColorLight(cabColor)) Color.BLACK else Color.WHITE

    private var _cab: ToolbarCab? = null
    private val cab: ToolbarCab?
        get() {
            val inflatedId = R.id.multi_selection_cab
            return if (_cab != null) {
                _cab
            } else {
                _cab = try {
                    initToolbarCab(activity, R.id.cab_stub, inflatedId).apply { prepare() }
                } catch (e: IllegalStateException) {
                    null
                }
                _cab
            }
        }

    private fun ToolbarCab.prepare() {
        titleText = "0"
        titleTextColor = textColor
        backgroundColor = cabColor
        navigationIcon = activity.getDrawableCompat(R.drawable.ic_close).applyColor(titleTextColor)
        setupMenu()
        toolbar.setNavigationOnClickListener {
            hide()
            unselectedAll()
            isFirstSelected = false
        }
        hide()
    }
    private fun notifyDataSetChanged() {
        for (i in 0 until linkedAdapter.getItemCount())
            linkedAdapter.notifyItemChanged(i)
    }
    private fun ToolbarCab.setupMenu() {
        menuHandler = {
            inflateMenu(R.menu.menu_media_selection)
            if (linkedAdapter.getItemCount() == 1) {
                menu?.forEach {
                    val id = it.itemId
                    it.isVisible = !(id == R.id.action_select_all_adapter || id == R.id.action_invert_select_adapter)
                }
            }
            setOnMenuItemClickListener { m -> handleClickMenu(m, convertToSongs(selected)) }
            true
        }
    }
    private fun handleClickMenu(menuItem: MenuItem, selections: List<Song>): Boolean {
        when (menuItem.itemId) {
            R.id.action_play -> {
                if (activity.hasNotificationApi()) {
                    activity.lifecycleScope.launch(Dispatchers.IO) {
                        AudioPlayerRemote.openQueue(selections, 0, true)
                        SongsAdapter.launchPlayer(activity)
                    }
                }
                clearSelection(true)
                return true
            }
            R.id.action_share -> {
                activity.shareSongsIntent(selections)
                clearSelection(true)
                return true
            }
            R.id.action_delete_from_device -> {
                DeleteSongsDialog.create(selections, activity.supportFragmentManager)
                clearSelection(true)
                return true
            }
            R.id.action_select_all_adapter -> {
                if (selected.size < linkedAdapter.getItemCount()) {
                    selectAll()
                } else {
                    clearSelection(true)
                }
                return true
            }
            R.id.action_invert_select_adapter -> {
                if (!invertSelected()) {
                    onBackPressedDispatcherRegistered = false
                    setOnBackPressed(null)
                }
                return true
            }
        }
        return false
    }
    private fun convertToSongs(selections: Iterable<*>): List<Song> {
        val songs = ArrayList<Song>()
        for (selection in selections) {
            when (selection) {
                is Song -> songs.add(selection)
                is Album -> songs.addAll(selection.songs)
                is Artist -> songs.addAll(selection.songs)
                is Genre -> songs.addAll(selection.songs)
            }
        }
        return songs.toList()
    }
    interface IMultiSelectableAdapter<I> {
        fun getItemCount(): Int
        fun getItem(datasetPosition: Int): I?
        fun notifyItemChanged(datasetPosition: Int)
    }
}