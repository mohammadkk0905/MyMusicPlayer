package com.mohammadkk.mymusicplayer.adapters

import android.content.Intent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.forEach
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.BaseActivity
import com.mohammadkk.mymusicplayer.activities.PlayerActivity
import com.mohammadkk.mymusicplayer.databinding.ItemListBinding
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.hasNotificationApi
import com.mohammadkk.mymusicplayer.extensions.resetQueueItems
import com.mohammadkk.mymusicplayer.extensions.setIconColor
import com.mohammadkk.mymusicplayer.extensions.setTitleColor
import com.mohammadkk.mymusicplayer.extensions.shareSongIntent
import com.mohammadkk.mymusicplayer.extensions.shareSongsIntent
import com.mohammadkk.mymusicplayer.extensions.toFormattedDate
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.utils.Libraries
import com.mohammadkk.mymusicplayer.utils.RingtoneManager
import me.zhanghai.android.fastscroll.PopupTextProvider
import kotlin.math.abs

class SongsAdapter(
    context: FragmentActivity,
    var dataSet: MutableList<Song>,
    private var mode: String
) : AbsMultiAdapter<SongsAdapter.SongHolder, Song>(context), PopupTextProvider {

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
    override fun getPopupText(view: View, position: Int): CharSequence {
        val song = dataSet.getOrNull(position)
        val sortName = abs(baseSettings.songsSorting)
        if (mode == "OTG") {
            return if (sortName == Constant.SORT_BY_DATE_ADDED) {
                song?.duration?.toFormattedDuration(true) ?: "-"
            } else {
                Libraries.getSectionName(song?.title, true)
            }
        }
        val result = when (sortName) {
            Constant.SORT_BY_TITLE -> song?.title
            Constant.SORT_BY_ALBUM -> song?.album
            Constant.SORT_BY_ARTIST -> song?.artist
            Constant.SORT_BY_DURATION -> return song?.duration?.toFormattedDuration(true) ?: "-"
            else -> song?.title
        }
        return Libraries.getSectionName(result, true)
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
    fun startPlayer(position: Int) {
        val song = dataSet[position]
        context.resetQueueItems(dataSet) {
            val intent = Intent(context, PlayerActivity::class.java).putExtra(
                Constant.RESTART_PLAYER, true
            )
            MusicService.mCurrSong = song
            if (mode != "MAIN") {
                if (context is BaseActivity) {
                    context.isFadeAnimation = false
                }
            }
            context.startActivity(intent)
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
        fun bindItems(song: Song) {
            with(binding) {
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
                tvText.text = if (mode != "OTG") {
                    context.getString(
                        R.string.duration_date_symbol,
                        song.duration.toFormattedDuration(false),
                        song.dateAdded.toFormattedDate()
                    )
                } else {
                    song.dateAdded.toFormattedDate()
                }
                image.bind(song)
                root.setOnClickListener {
                    if (isInQuickSelectMode) {
                        toggleChecked(absoluteAdapterPosition)
                    } else {
                        if (context.hasNotificationApi()) {
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
                            popupMenu.setOnMenuItemClickListener {
                                clickHandlePopupMenu(it, dataSet[absoluteAdapterPosition])
                            }
                            popupMenu.show()
                        }
                    }
                }
            }
        }
    }
}