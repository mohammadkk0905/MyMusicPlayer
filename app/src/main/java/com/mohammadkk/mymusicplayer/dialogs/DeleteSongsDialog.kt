package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.models.Song

class DeleteSongsDialog : DialogFragment() {
    private var baseActivity: MusicServiceActivity? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            baseActivity = activity as MusicServiceActivity
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val alert = MaterialAlertDialogBuilder(requireContext())
        if (dataset.isEmpty()) {
            dismiss()
            return alert.create()
        }
        val message = if (dataset.size == 1) {
            alert.setTitle(R.string.delete_song)
            getString(R.string.delete_song_x, dataset.first().title).parseAsHtml()
        } else {
            alert.setTitle(R.string.delete_songs)
            getString(R.string.delete_x_songs, dataset.size).parseAsHtml()
        }
        alert.setMessage(message).setPositiveButton(android.R.string.ok) { _, _ ->
            baseActivity?.deleteSongs(dataset)
            dismiss()
        }.setNegativeButton(android.R.string.cancel) { _, _ ->
            destroyDataset()
            dismiss()
        }
        val alertDialog = alert.create()
        alertDialog.setCancelable(false)
        alertDialog.setCanceledOnTouchOutside(false)
        return alertDialog
    }
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        destroyDataset()
    }
    companion object {
        private val dataset = mutableListOf<Song>()

        fun create(song: Song, manager: FragmentManager) {
            create(listOf(song), manager)
        }
        fun create(songs: Collection<Song>, manager: FragmentManager) {
            dataset.clear()
            if (songs.isNotEmpty()) dataset.addAll(songs)
            DeleteSongsDialog().show(manager, "DELETE_SONGS_DIALOG")
        }
        fun getDataset(): MutableList<Song> {
            return dataset
        }
        fun destroyDataset() {
            dataset.clear()
        }
    }
}