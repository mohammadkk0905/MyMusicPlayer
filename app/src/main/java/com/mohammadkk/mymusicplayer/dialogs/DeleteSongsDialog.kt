package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.BaseActivity
import com.mohammadkk.mymusicplayer.models.Song
import java.util.Locale

class DeleteSongsDialog : DialogFragment() {
    private var baseActivity: BaseActivity? = null
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            baseActivity = activity as BaseActivity
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
        if (dataset.size == 1) {
            alert.setTitle(R.string.delete_song)
        } else {
            alert.setTitle(R.string.delete_songs)
        }
        val typedArray = arrayListOf<String>()
        var isBreak = false
        for (i in dataset.indices) {
            if (i >= 9) {
                isBreak = true
                break
            }
            typedArray.add(
                String.format(Locale.getDefault(), "%d. %s", i + 1, dataset[i].title)
            )
        }
        if (isBreak) typedArray.add(
            String.format(Locale.getDefault(), "%d. %s", dataset.size, dataset.last().title)
        )
        val dialogAdapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item_dialog, typedArray) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val inflater = LayoutInflater.from(context)
                var mView = convertView
                if (mView == null) {
                    mView = inflater.inflate(R.layout.list_item_dialog, parent, false)
                }
                val title: TextView = mView!!.findViewById(R.id.title)
                title.textDirection = TextView.TEXT_DIRECTION_LTR
                title.text = typedArray[position]
                return mView
            }
        }
        alert.setAdapter(dialogAdapter, null)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                baseActivity?.deleteSongs(dataset) {
                    baseActivity?.onReloadLibrary(null)
                }
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel) {  _, _ ->
                destroyDataset()
                dismiss()
            }
        return alert.create()
    }
    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        destroyDataset()
    }
    companion object {
        private val dataset = mutableListOf<Song>()

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