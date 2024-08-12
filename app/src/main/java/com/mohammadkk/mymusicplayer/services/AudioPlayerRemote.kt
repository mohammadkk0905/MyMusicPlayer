package com.mohammadkk.mymusicplayer.services

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import java.util.WeakHashMap

object AudioPlayerRemote {
    private val mConnectionMap = WeakHashMap<Context, ServiceBinder>()
    var musicService: MusicService? = null

    fun bindToService(context: Context, callback: ServiceConnection): ServiceToken? {
        val realActivity = (context as Activity).parent ?: context
        val contextWrapper = ContextWrapper(realActivity)
        val intent = Intent(contextWrapper, MusicService::class.java)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            ContextCompat.startForegroundService(context, intent)
        }
        val binder = ServiceBinder(callback)
        if (contextWrapper.bindService(
                Intent().setClass(contextWrapper, MusicService::class.java),
                binder, Context.BIND_AUTO_CREATE
            )
        ) {
            mConnectionMap[contextWrapper] = binder
            return ServiceToken(contextWrapper)
        }
        return null
    }
    fun broadcastStatusRestore() {
        musicService?.broadcastStatusRestore()
    }
    fun playPreviousSong() {
        musicService?.onHandlePrevious()
    }
    fun playPauseSong() {
        musicService?.onHandlePlayPause()
    }
    fun playNextSong() {
        musicService?.onHandleNext()
    }
    fun skip(forward: Boolean) {
        musicService?.onSkip(forward)
    }
    fun seekTo(millis: Int): Int {
        return musicService?.updateProgress(millis) ?: -1
    }
    fun updateAudioNotification() {
        musicService?.updateMediaSession {  }
    }
    fun quit() {
        musicService?.handleFinish(false)
    }
    fun unbindFromService(token: ServiceToken?) {
        if (token == null) return
        val mContextWrapper = token.mWrappedContext
        val mBinder = mConnectionMap.remove(mContextWrapper) ?: return
        mContextWrapper.unbindService(mBinder)
        if (mConnectionMap.isEmpty()) musicService = null
    }
    class ServiceBinder internal constructor(private val mCallback: ServiceConnection?) : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.service
            mCallback?.onServiceConnected(name, service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            mCallback?.onServiceDisconnected(name)
            musicService = null
        }
    }
    class ServiceToken internal constructor(internal var mWrappedContext: ContextWrapper)
}