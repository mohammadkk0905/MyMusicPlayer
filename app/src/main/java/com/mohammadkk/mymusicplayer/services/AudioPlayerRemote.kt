package com.mohammadkk.mymusicplayer.services

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import java.util.Random
import java.util.WeakHashMap

object AudioPlayerRemote {
    private val mConnectionMap = WeakHashMap<Context, ServiceBinder>()
    private var musicService: MusicService? = null

    @JvmStatic
    val isPlaying: Boolean
        get() = musicService?.isPlaying == true

    val currentSong: Song
        get() = musicService?.currentSong ?: Song.emptySong

    var position: Int
        get() = musicService?.position ?: -1
        set(value) { musicService?.position = value }

    @JvmStatic
    val playingQueue: List<Song>
        get() = if (musicService != null) {
            musicService?.playingQueue as List<Song>
        } else listOf()

    val songProgressMillis: Int
        get() = musicService?.songProgressMillis ?: -1

    val songDurationMillis: Int
        get() = musicService?.songDurationMillis ?: -1

    val repeatMode: PlaybackRepeat
        get() = musicService?.repeatMode ?: PlaybackRepeat.REPEAT_OFF

    val isShuffleMode: Boolean
        get() = musicService?.isShuffleMode ?: false

    val audioSessionId: Int
        get() = musicService?.audioSessionId ?: -1

    fun isPlaying(song: Song): Boolean {
        return if (!isPlaying) {
            false
        } else song.id == currentSong.id
    }
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
    fun unbindFromService(token: ServiceToken?) {
        if (token == null) return
        val mContextWrapper = token.mWrappedContext
        val mBinder = mConnectionMap.remove(mContextWrapper) ?: return
        mContextWrapper.unbindService(mBinder)
        if (mConnectionMap.isEmpty()) musicService = null
    }
    private fun playSongAt(position: Int) {
        musicService?.playSongAt(position)
    }
    @JvmStatic
    fun openQueue(queue: List<Song>, startPosition: Int, startPlaying: Boolean) {
        if (!tryToHandleOpenPlayingQueue(queue, startPosition, startPlaying)) {
            musicService?.openQueue(queue, startPosition, startPlaying)
        }
    }
    @JvmStatic
    fun openAndShuffleQueue(queue: List<Song>, startPlaying: Boolean) {
        var startPosition = 0
        if (queue.isNotEmpty()) {
            startPosition = Random().nextInt(queue.size)
        }
        if (!tryToHandleOpenPlayingQueue(queue, startPosition, startPlaying)) {
            openQueue(queue, startPosition, startPlaying)
            musicService?.setShuffleMode(true)
        }
    }
    private fun tryToHandleOpenPlayingQueue(
        queue: List<Song>,
        startPosition: Int,
        startPlaying: Boolean
    ): Boolean {
        if (playingQueue === queue) {
            if (startPlaying) {
                playSongAt(startPosition)
            } else {
                position = startPosition
            }
            return true
        }
        return false
    }
    fun playPreviousSong() {
        musicService?.playPreviousSong(true)
    }
    fun pauseSong() {
        musicService?.pause()
    }
    fun resumePlaying() {
        musicService?.play()
    }
    fun playNextSong() {
        musicService?.playNextSong(true)
    }
    fun seekTo(millis: Int): Int {
        return musicService?.seek(millis) ?: -1
    }
    fun cycleRepeatMode(): Boolean {
        val service = musicService ?: return false
        service.cycleRepeatMode()
        return true
    }
    fun toggleShuffleMode(): Boolean {
        val service = musicService ?: return false
        service.toggleShuffle()
        return true
    }
    fun removeFromQueue(song: Song): Boolean {
        val service = musicService ?: return false
        service.removeSong(song)
        return true
    }
    fun removeFromQueue(songs: List<Song>): Boolean {
        val service = musicService ?: return false
        service.removeSongs(songs)
        return true
    }
    fun quit() {
        musicService?.quit()
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