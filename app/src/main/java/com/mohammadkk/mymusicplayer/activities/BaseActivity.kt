package com.mohammadkk.mymusicplayer.activities

import android.app.Activity
import android.content.ComponentName
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.ActionMode
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.dialogs.DeleteSongsDialog
import com.mohammadkk.mymusicplayer.extensions.errorToast
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.AudioPlayerRemote
import com.mohammadkk.mymusicplayer.utils.FileUtils
import java.io.File

abstract class BaseActivity : AppCompatActivity() {
    protected val settings: BaseSettings get() = BaseSettings.getInstance()
    private var mLaunchActivity: ActivityResultLauncher<IntentSenderRequest>? = null
    internal var adapterActionMode: ActionMode? = null
    private var serviceToken: AudioPlayerRemote.ServiceToken? = null
    internal var isFadeAnimation = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mLaunchActivity = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            mAfterSdk30Action?.invoke(it.resultCode == Activity.RESULT_OK)
            mAfterSdk30Action = null
        }
        serviceToken = AudioPlayerRemote.bindToService(this, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {

            }
            override fun onServiceDisconnected(name: ComponentName?) {

            }
        })
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
                Constant.ensureBackgroundThread {
                    val files = songs.map { File(it.path) }
                    if (Constant.isRPlus()) {
                        val resolved = FileUtils.matchFilesWithMediaStore(this, files)
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
                        if (result) runOnUiThread { callback() }
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
                val contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                songs.forEach { song ->
                    try {
                        val where = "${MediaStore.Audio.Media._ID} = ?"
                        val args = arrayOf(song.id.toString())
                        contentResolver.delete(contentUri, where, args)
                        File(song.path).delete()
                    } catch (ignored: Exception) {
                    }
                }
                callback()
            }
        }
    }
    private fun isRootUri(uri: Uri): Boolean {
        return uri.lastPathSegment?.endsWith(":") ?: false
    }
    private fun isInternalStorage(uri: Uri): Boolean {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        return isExternalStorageDocument(uri) && documentId.contains("primary")
    }
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return Constant.EXTERNAL_STORAGE_AUTHORITY == uri.authority
    }
    open fun onReloadLibrary(mode: String?) {
    }

    override fun onDestroy() {
        super.onDestroy()
        AudioPlayerRemote.unbindFromService(serviceToken)
    }
    companion object {
        private var mAfterSdk30Action: ((success: Boolean) -> Unit)? = null
    }
}