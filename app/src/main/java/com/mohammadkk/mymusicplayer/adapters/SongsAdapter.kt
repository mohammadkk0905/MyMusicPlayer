package com.mohammadkk.mymusicplayer.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.BaseActivity
import com.mohammadkk.mymusicplayer.activities.PlayerActivity
import com.mohammadkk.mymusicplayer.databinding.ItemListBinding
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.dialogs.SongDetailDialog
import com.mohammadkk.mymusicplayer.extensions.bind
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.setIconColor
import com.mohammadkk.mymusicplayer.extensions.setTitleColor
import com.mohammadkk.mymusicplayer.extensions.shareSongIntent
import com.mohammadkk.mymusicplayer.extensions.shareSongsIntent
import com.mohammadkk.mymusicplayer.extensions.toFormattedDate
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.utils.RingtoneManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SongsAdapter(
    context: FragmentActivity,
    var dataSet: MutableList<Song>,
    private var mode: String
) : AbsMultiAdapter<SongsAdapter.SongHolder, Song>(context) {
    private var selectedSong = Song.emptySong

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongHolder {
        val inflater = LayoutInflater.from(parent.context)
        return SongHolder(ItemListBinding.inflate(inflater, parent, false))
    }
    override fun getItemCount(): Int {
        return dataSet.size
    }
    override fun onMultiplePrepareActionMode(menu: Menu): Boolean {
        baseSettings.actionModeIndex = 0
        return true
    }
    override fun onMultipleItemAction(menuItem: MenuItem, selection: List<Song>) {
        when (menuItem.itemId) {
            R.id.action_share -> handleActionShare(selection)
            R.id.action_remove_files -> DeleteSongsDialog.create(
                selection, context.supportFragmentManager
            )
        }
    }
    override fun getIdentifier(position: Int): Song? {
        return dataSet.getOrNull(position)
    }
    override fun onBindViewHolder(holder: SongHolder, position: Int) {
        holder.bindItems(dataSet[holder.absoluteAdapterPosition])
    }
    fun setPlaying(item: Song) {
        if (selectedSong.id != item.id) {
            val oldItem = selectedSong
            selectedSong = item
            // Remove the playing indicator from the old item
            if (oldItem.id != -1L) {
                val pos = dataSet.indexOfFirst { it.id == oldItem.id }
                if (pos > -1) notifyItemChanged(pos, PAYLOAD_PLAYING_INDICATOR_CHANGED)
            }
            // Enable the playing indicator on the new item
            if (item.id != -1L) {
                val pos = dataSet.indexOfFirst { it.id == item.id }
                if (pos > -1) notifyItemChanged(pos, PAYLOAD_PLAYING_INDICATOR_CHANGED)
            }
        }
    }
    fun swapDataSet(dataSet: List<Song>) {
        this.dataSet = ArrayList(dataSet)
        notifyDataSetChanged()
    }
    fun swapDeleted() {
        if (mode != "MAIN") {
            DeleteSongsDialog.getDataset().forEach { s ->
                val index = dataSet.indexOf(s)
                if (index != -1) {
                    dataSet.removeAt(index)
                    notifyItemRemoved(index)
                }
            }
            DeleteSongsDialog.destroyDataset()
        }
    }
    fun startFirstPlayer() {
        if (dataSet.size > 0) startPlayer(0)
    }
    fun startShufflePlayer() {
        if (dataSet.size > 0) startPlayer(-1)
    }
    fun startPlayer(position: Int) {
        if (context.hasNotificationApi()) {
            context.lifecycleScope.launch(Dispatchers.IO) {
                if (position >= 0) {
                    AudioPlayerRemote.openQueue(dataSet, position, true)
                } else {
                    AudioPlayerRemote.openAndShuffleQueue(dataSet, true)
                }
                if (mode != "MAIN") (context as? BaseActivity)?.isFadeAnimation = false
                context.startActivity(Intent(context, PlayerActivity::class.java))
            }
        }
    }
    private fun clickHandlePopupMenu(item: MenuItem, song: Song): Boolean {
        when (item.itemId) {
            R.id.action_share -> {
                handleActionShare(listOf(song))
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
                DeleteSongsDialog.create(listOf(song), context.supportFragmentManager)
                return true
            }
        }
        return false
    }
    private fun handleActionShare(songs: List<Song>) {
        if (songs.size == 1) {
            context.shareSongIntent(songs.first())
        } else {
            context.shareSongsIntent(songs)
        }
    }
    inner class SongHolder(private val binding: ItemListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindItems(song: Song) = with(binding) {
            if (checked.contains(song)) {
                root.isActivated = true
                menu.setImageResource(R.drawable.ic_check_circle)
                menu.setBackgroundResource(android.R.color.transparent)
            } else {
                root.isActivated = false
                menu.setImageResource(R.drawable.ic_more_horiz)
                menu.setBackgroundResource(R.drawable.round_selector)
            }
            tvTitle.text = song.title
            tvTitle.isSelected = song.id == selectedSong.id
            tvText.text = if (mode != "OTG") {
                context.getString(
                    R.string.duration_date_symbol,
                    song.duration.toFormattedDuration(false),
                    (song.dateModified * 1000).toFormattedDate(true)
                )
            } else {
                song.dateModified.toFormattedDate(true)
            }
            image.bind(song, R.drawable.ic_audiotrack)
            root.setOnClickListener {
                if (isInQuickSelectMode) {
                    toggleChecked(absoluteAdapterPosition)
                } else {
                    if (!Constant.isBlockingClick()) {
                        startPlayer(absoluteAdapterPosition)
                    }
                }
            }
            root.setOnLongClickListener {
                toggleChecked(absoluteAdapterPosition)
                true
            }
            menu.setOnClickListener { v ->
                if (isInQuickSelectMode) {
                    toggleChecked(absoluteAdapterPosition)
                } else {
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
    }
    private companion object {
        val PAYLOAD_PLAYING_INDICATOR_CHANGED = Any()
    }
}