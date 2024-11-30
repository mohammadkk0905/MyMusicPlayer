package com.mohammadkk.mymusicplayer.adapters

import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.ui.cab.MultiSelectionController

abstract class AbsMultiAdapter<V : RecyclerView.ViewHolder, I>(
    val context: FragmentActivity
) : RecyclerView.Adapter<V>() {
    protected val baseSettings = BaseSettings.getInstance()
    var dataset: MutableList<I> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    protected val selectionController: MultiSelectionController<I> by lazy {
        MultiSelectionController(object : MultiSelectionController.IMultiSelectableAdapter<I> {
            override fun getItemCount(): Int {
                return itemCount
            }
            override fun getItem(datasetPosition: Int): I? {
                return getIdentifier(datasetPosition)
            }
            override fun notifyItemChanged(datasetPosition: Int) {
                notifyItemChanged(datasetPosition, PAYLOAD_SELECTION_INDICATOR_CHANGED)
            }
        }, context)
    }

    abstract fun getIdentifier(position: Int): I?

    override fun getItemCount(): Int {
        return dataset.size
    }

    private companion object {
        val PAYLOAD_SELECTION_INDICATOR_CHANGED = Any()
    }
}