package com.mohammadkk.mymusicplayer.adapters

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.BaseActivity

abstract class AbsMultiAdapter<V : RecyclerView.ViewHolder, I>(
    val context: FragmentActivity
) : RecyclerView.Adapter<V>() {
    protected val baseSettings = BaseSettings.getInstance()
    protected open var menuRes = R.menu.menu_action_songs
    private var actionMode: ActionMode? = null
    protected val checked: MutableList<I> = mutableListOf()
    private val callbackActionMode: ActionMode.Callback
    protected val isInQuickSelectMode: Boolean
        get() = actionMode != null

    abstract fun onMultiplePrepareActionMode(menu: Menu): Boolean
    abstract fun onMultipleItemAction(menuItem: MenuItem, selection: List<I>)
    abstract fun getIdentifier(position: Int): I?

    init {
        callbackActionMode = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                val inflater = mode?.menuInflater
                inflater?.inflate(menuRes, menu)
                return true
            }
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                if (menu != null) return onMultiplePrepareActionMode(menu)
                return false
            }
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                if (item != null) onMultipleItemAction(item, ArrayList(checked))
                actionMode?.finish()
                return true
            }
            override fun onDestroyActionMode(mode: ActionMode?) {
                checked.clear()
                notifyDataSetChanged()
                actionMode = null
                (context as? BaseActivity)?.adapterActionMode = null
            }
        }
    }
    protected fun toggleChecked(position: Int): Boolean {
        val identifier = getIdentifier(position) ?: return false
        if (!checked.remove(identifier)) checked.add(identifier)
        notifyItemChanged(position, PAYLOAD_SELECTION_INDICATOR_CHANGED)
        updateCab()
        return true
    }
    private fun updateCab() {
        if (actionMode == null) {
            actionMode = context.startActionMode(callbackActionMode)
            (context as? BaseActivity)?.adapterActionMode = actionMode
        }
        val size = checked.size
        if (size <= 0) {
            actionMode?.finish()
        } else  {
            actionMode?.title = "$size/$itemCount"
        }
    }
    private companion object {
        val PAYLOAD_SELECTION_INDICATOR_CHANGED = Any()
    }
}