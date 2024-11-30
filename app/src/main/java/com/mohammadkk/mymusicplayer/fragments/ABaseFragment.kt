package com.mohammadkk.mymusicplayer.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.CallSuper
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import com.mohammadkk.mymusicplayer.activities.base.MusicServiceActivity
import com.mohammadkk.mymusicplayer.interfaces.IMusicServiceEventListener

open class ABaseFragment(@LayoutRes layout: Int) : Fragment(layout), IMusicServiceEventListener {
    var serviceActivity: MusicServiceActivity? = null
        private set

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            serviceActivity = context as MusicServiceActivity?
        } catch (e: ClassCastException) {
            throw RuntimeException(context.javaClass.simpleName + " must be an instance of " + MusicServiceActivity::class.java.simpleName)
        }
    }
    override fun onDetach() {
        super.onDetach()
        serviceActivity = null
    }
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        serviceActivity?.addMusicServiceEventListener(this)
    }
    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        serviceActivity?.removeMusicServiceEventListener(this)
    }
    override fun onServiceConnected() {
    }
    override fun onServiceDisconnected() {
    }
    override fun onQueueChanged() {
    }
    override fun onPlayingMetaChanged() {
    }
    override fun onPlayStateChanged() {
    }
    override fun onRepeatModeChanged() {
    }
    override fun onShuffleModeChanged() {
    }
    override fun onMediaStoreChanged() {
    }
}