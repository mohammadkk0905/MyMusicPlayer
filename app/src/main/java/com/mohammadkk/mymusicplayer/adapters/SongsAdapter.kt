package com.mohammadkk.mymusicplayer.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.PlayerActivity
import com.mohammadkk.mymusicplayer.databinding.ItemListBinding
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.dialogs.SongDetailDialog
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.makeSectionName
import com.mohammadkk.mymusicplayer.extensions.setIconColor
import com.mohammadkk.mymusicplayer.extensions.setTitleColor
import com.mohammadkk.mymusicplayer.extensions.shareSongIntent
import com.mohammadkk.mymusicplayer.extensions.toFormattedDate
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.utils.RingtoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongsAdapter(
    context: FragmentActivity, private var mode: String
) : AbsMultiAdapter<SongsAdapter.SongHolder, Song>(context) {
    private var currentItem = Song.emptySong
    private var isPlaying = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
        val inflater = LayoutInflater.from(parent.context)
        return SongHolder(ItemListBinding.inflate(inflater, parent, false))
    }
    override fun getIdentifier(position: Int): Song? {
        return dataset.getOrNull(position)
    }
    override fun onBindViewHolder(holder: SongHolder, position: Int) {
        holder.bindItems(dataset[holder.absoluteAdapterPosition])
    }
    fun getPopupText(position: Int): String {
        val song = dataset.getOrNull(position) ?: return ""
        val result = when (baseSettings.songsSorting) {
            Constant.SORT_BY_TITLE -> song.title
            Constant.SORT_BY_ALBUM -> song.album
            Constant.SORT_BY_ARTIST -> song.artist
            Constant.SORT_BY_DURATION -> return song.duration.toFormattedDuration(true)
            else -> song.title
        }
        return result.makeSectionName()
    }
    fun setPlaying(item: Song, isPlaying: Boolean) {
        var updatedItem = false
        if (currentItem.id != item.id) {
            val oldItem = currentItem
            currentItem = item
            if (oldItem.id != -1L) {
                val pos = dataset.indexOfFirst { it.id == oldItem.id }
                if (pos > -1) notifyItemChanged(pos, PAYLOAD_PLAYING_INDICATOR_CHANGED)
            }
            if (item.id != -1L) {
                val pos = dataset.indexOfFirst { it.id == item.id }
                if (pos > -1) notifyItemChanged(pos, PAYLOAD_PLAYING_INDICATOR_CHANGED)
            }
            updatedItem = true
        }
        if (this.isPlaying != isPlaying) {
            this.isPlaying = isPlaying
            if (!updatedItem && item.id != -1L) {
                val pos = dataset.indexOfFirst { it.id == item.id }
                if (pos > -1) notifyItemChanged(pos, PAYLOAD_PLAYING_INDICATOR_CHANGED)
            }
        }
    }
    fun swapDataSet(dataSet: List<Song>) {
        this.dataset = dataSet.toMutableList()
    }
    fun swapDeleted() {
        if (mode != "MAIN") {
            DeleteSongsDialog.getDataset().forEach { s ->
                val index = dataset.indexOf(s)
                if (index != -1) {
                    dataset.removeAt(index)
                    notifyItemRemoved(index)
                }
            }
            DeleteSongsDialog.destroyDataset()
        }
    }
    fun startFirstPlayer(isShuffle: Boolean) {
        if (dataset.isNotEmpty()) startPlayer(if (isShuffle) -1 else 0)
    }
    fun startPlayer(position: Int) {
        if (context.hasNotificationApi()) {
            context.lifecycleScope.launch(Dispatchers.IO) {
                if (position >= 0) {
                    AudioPlayerRemote.openQueue(dataset, position, true)
                } else {
                    AudioPlayerRemote.openAndShuffleQueue(dataset, true)
                }
                launchPlayer(context)
            }
        }
    }
    private fun clickHandlePopupMenu(item: MenuItem, song: Song): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                context.shareSongIntent(song)
                return true
            }
            R.id.action_ringtone -> {
                if (RingtoneManager.requiresDialog(context)) {
                    RingtoneManager.showDialog(context)
                } else {
                    RingtoneManager.setRingtone(context, song)
                }
                return true
            }
            R.id.action_details -> {
                SongDetailDialog.show(song, context.supportFragmentManager)
                return true
            }
            R.id.action_remove_file -> {
                DeleteSongsDialog.create(song, context.supportFragmentManager)
                return true
            }
        }
        return false
    }
    inner class SongHolder(private val binding: ItemListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindItems(song: Song) = with(binding) {
            if (selectionController.isFirstSelected) {
                menu.visibility = View.GONE
                checkbox.visibility = View.VISIBLE
            } else {
                menu.visibility = View.VISIBLE
                checkbox.visibility = View.GONE
            }
            if (selectionController.isSelected(song)) {
                root.isActivated = true
                checkbox.isChecked = true
            } else {
                root.isActivated = false
                checkbox.isChecked = false
            }
            tvTitle.text = song.title
            tvText.text = if (mode != "OTG") {
                context.getString(
                    R.string.duration_date_symbol,
                    song.duration.toFormattedDuration(false),
                    (song.dateModified * 1000).toFormattedDate(true)
                )
            } else {
                song.dateModified.toFormattedDate(true)
            }
            if (song.id == currentItem.id) {
                tvTitle.isSelected = true
                flVisualizer.visibility = View.VISIBLE
                visualizer.isPlaying = isPlaying
            } else {
                tvTitle.isSelected = false
                flVisualizer.visibility = View.GONE
                visualizer.isPlaying = false
            }
            image.bind(song, R.drawable.ic_audiotrack)
            root.setOnClickListener {
                if (selectionController.isInQuickSelectMode) {
                    selectionController.toggle(absoluteAdapterPosition)
                } else {
                    if (!Constant.isBlockingClick()) {
                        startPlayer(absoluteAdapterPosition)
                    }
                }
            }
            root.setOnLongClickListener {
                selectionController.toggle(absoluteAdapterPosition)
            }
            menu.setOnClickListener { v ->
                if (!Constant.isBlockingClick()) {
                    val popupMenu = PopupMenu(context, v)
                    popupMenu.menuInflater.inflate(R.menu.menu_action_song, popupMenu.menu)
                    popupMenu.menu.forEach { m ->
                        if (m.itemId == R.id.action_remove_file) {
                            val red = context.getColorCompat(R.color.red_500)
                            m.setTitleColor(red)
                            m.setIconColor(red)
                        } else if (m.itemId == R.id.action_ringtone) {
                            m.isVisible = mode != "OTG"
                        }
                    }
                    popupMenu.setForceShowIcon(true)
                    popupMenu.setOnMenuItemClickListener { clickHandlePopupMenu(it, song) }
                    popupMenu.show()
                }
            }
        }
    }
    companion object {
        private val PAYLOAD_PLAYING_INDICATOR_CHANGED = Any()

        fun launchPlayer(context: Context) {
            val options = ActivityOptionsCompat.makeCustomAnimation(
                context, R.anim.activity_bottom_in, R.anim.activity_half_fade
            ).toBundle()
            context.startActivity(Intent(context, PlayerActivity::class.java), options)
        }
    }
}