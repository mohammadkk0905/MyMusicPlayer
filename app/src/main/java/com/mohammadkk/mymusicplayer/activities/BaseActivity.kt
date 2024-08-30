package com.mohammadkk.mymusicplayer.activities

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.ActionMode
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.interfaces.IMusicServiceEventListener
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.services.MusicService
import com.mohammadkk.mymusicplayer.utils.FileUtils
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference

abstract class BaseActivity : AppCompatActivity(), IMusicServiceEventListener {
    protected val settings: BaseSettings get() = BaseSettings.getInstance()
    private val mMusicServiceEventListeners = ArrayList<IMusicServiceEventListener>()
    private var mLaunchActivity: ActivityResultLauncher<IntentSenderRequest>? = null
    internal var adapterActionMode: ActionMode? = null
    private var serviceToken: AudioPlayerRemote.ServiceToken? = null
    private var musicStateReceiver: MusicStateReceiver? = null
    private var receiverRegistered: Boolean = false
    internal var isFadeAnimation = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.build(this)
        mLaunchActivity = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                deleteSongsFromQueues()
                mAfterSdk30Action?.invoke(true)
            } else {
                mAfterSdk30Action?.invoke(false)
            }
            mAfterSdk30Action = null
        }
        serviceToken = AudioPlayerRemote.bindToService(this, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                this@BaseActivity.onServiceConnected()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                this@BaseActivity.onServiceDisconnected()
            }
        })
    }
    fun addMusicServiceEventListener(listener: IMusicServiceEventListener?) {
        if (listener != null) mMusicServiceEventListeners.add(listener)
    }
    fun removeMusicServiceEventListener(listener: IMusicServiceEventListener?) {
        if (listener != null) mMusicServiceEventListeners.remove(listener)
    }
    private fun deleteSDK30Uris(uris: List<Uri>, callback: (success: Boolean) -> Unit) {
        if (Constant.isRPlus()) {
            mAfterSdk30Action = callback
            try {
                val deleteRequest = MediaStore.createDeleteRequest(contentResolver, uris)
                mLaunchActivity?.launch(IntentSenderRequest.Builder(deleteRequest.intentSender).build())
            } catch (e: Exception) {
                errorToast(e)
            }
        } else {
            callback(false)
        }
    }
    fun deleteSongs(songs: List<Song>, callback: () -> Unit) {
        if (songs.isNotEmpty()) {
            if (songs.first().isOTGMode()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val files = songs.map { File(it.data) }
                    if (Constant.isRPlus()) {
                        val resolved = FileUtils.matchFilesWithMediaStore(this@BaseActivity, files)
                        val uris = resolved.map { it.toContentUri() }
                        deleteSDK30Uris(uris) { s -> if (s) callback() }
                    } else {
                        var result = false
                        for (file in files) {
                            result = try {
                                file.delete()
                            } catch (e: Exception) {
                                false
                            }
                        }
                        if (result) withContext(Dispatchers.Main) {
                            deleteSongsFromQueues()
                            callback()
                        }
                    }
                }
                return
            }
            if (Constant.isRPlus()) {
                val uris = songs.map { it.toContentUri() }
                deleteSDK30Uris(uris) { success ->
                    if (success) {
                        callback()
                    } else {
                        DeleteSongsDialog.destroyDataset()
                        toast(R.string.unknown_error_occurred)
                    }
                }
            } else {
                lifecycleScope.launch(Dispatchers.IO) {
                    val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    for (song in songs) {
                        try {
                            val where = "${MediaStore.Audio.Media._ID} = ?"
                            val args = arrayOf(song.id.toString())
                            contentResolver.delete(contentUri, where, args)
                            File(song.data).delete()
                        } catch (ignored: Exception) {
                        }
                    }
                    withContext(Dispatchers.Main) {
                        deleteSongsFromQueues()
                        callback()
                    }
                }
            }
        }
    }
    private fun deleteSongsFromQueues() {
        val dataset = DeleteSongsDialog.getDataset()
        if (dataset.size == 1) {
            val current = dataset[0]
            if (AudioPlayerRemote.isPlaying(current)) AudioPlayerRemote.playNextSong()
            AudioPlayerRemote.removeFromQueue(current)
        } else {
            AudioPlayerRemote.removeFromQueue(dataset)
        }
    }
    open fun onReloadLibrary(tabIndex: Int?) {
    }
    open fun onShowOpenMiniPlayer(isShow: Boolean) {
    }
    final override fun onServiceConnected() {
        if (!receiverRegistered) {
            musicStateReceiver = MusicStateReceiver(this)
            val filter = IntentFilter().apply {
                addAction(MusicService.PLAY_STATE_CHANGED)
                addAction(MusicService.SHUFFLE_MODE_CHANGED)
                addAction(MusicService.REPEAT_MODE_CHANGED)
                addAction(MusicService.META_CHANGED)
                addAction(MusicService.QUEUE_CHANGED)
                addAction(MusicService.MEDIA_STORE_CHANGED)
            }
            ContextCompat.registerReceiver(this, musicStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        for (listener in mMusicServiceEventListeners) {
            listener.onServiceConnected()
        }
    }
    final override fun onServiceDisconnected() {
        if (receiverRegistered) {
            unregisterReceiver(musicStateReceiver)
            receiverRegistered = false
        }
        for (listener in mMusicServiceEventListeners) {
            listener.onServiceDisconnected()
        }
    }
    final override fun onQueueChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onQueueChanged()
        }
    }
    final override fun onPlayingMetaChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onPlayingMetaChanged()
        }
    }
    final override fun onPlayStateChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onPlayStateChanged()
        }
    }
    final override fun onRepeatModeChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onRepeatModeChanged()
        }
    }
    final override fun onShuffleModeChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onShuffleModeChanged()
        }
    }
    final override fun onMediaStoreChanged() {
        for (listener in mMusicServiceEventListeners) {
            listener.onMediaStoreChanged()
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        AudioPlayerRemote.unbindFromService(serviceToken)
        if (receiverRegistered) {
            unregisterReceiver(musicStateReceiver)
            receiverRegistered = false
        }
    }
    private class MusicStateReceiver(activity: BaseActivity) : BroadcastReceiver() {
        private val reference: WeakReference<BaseActivity> = WeakReference(activity)

        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            val activity = reference.get()
            if (activity != null && action != null) {
                when (action) {
                    MusicService.META_CHANGED -> activity.onPlayingMetaChanged()
                    MusicService.QUEUE_CHANGED -> activity.onQueueChanged()
                    MusicService.PLAY_STATE_CHANGED -> activity.onPlayStateChanged()
                    MusicService.REPEAT_MODE_CHANGED -> activity.onRepeatModeChanged()
                    MusicService.SHUFFLE_MODE_CHANGED -> activity.onShuffleModeChanged()
                    MusicService.MEDIA_STORE_CHANGED -> activity.onMediaStoreChanged()
                }
            }
        }
    }
    companion object {
        private var mAfterSdk30Action: ((success: Boolean) -> Unit)? = null
    }
}