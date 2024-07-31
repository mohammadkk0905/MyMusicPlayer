package com.mohammadkk.mymusicplayer.services

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore.Audio.AudioColumns
import androidx.core.app.ServiceCompat
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.utils.FileUtils
import com.mohammadkk.mymusicplayer.utils.NotificationUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScannerService : Service() {
    private val myBinder: IBinder = LocalScanner()
    private var notificationUtils: NotificationUtils? = null
    private val serviceJob = Job()
    private val indexScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private var currentPath = ""
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        notificationUtils = NotificationUtils.createInstance(this)
        startForegroundWithNotify()
    }
    override fun onDestroy() {
        super.onDestroy()
        isForegroundService = false
        serviceJob.cancel()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Constant.isQPlus() && !hasPermission(Constant.STORAGE_PERMISSION)) {
            return START_NOT_STICKY
        }
        if (intent?.action == Constant.SCANNER) {
            startForegroundWithNotify()
            listPaths(intent) { paths ->
                scanPaths(paths)
            }
        }
        return START_NOT_STICKY
    }
    @Synchronized
    private fun listPaths(intent: Intent, onDone: (paths: Array<String?>) -> Unit) {
        currentPath = intent.getStringExtra(AudioColumns.DATA) ?: Environment.getExternalStorageDirectory().absolutePath
        val file = File(currentPath)
        indexScope.launch {
            val mPaths = try {
                val paths: Array<String?>
                if (file.isDirectory) {
                    val files = FileUtils.listFilesDeep(file, FileUtils.AUDIO_FILE_FILTER)
                    paths = arrayOfNulls(files.size)
                    for (i in files.indices) {
                        val f = files[i]
                        paths[i] = FileUtils.safeGetCanonicalPath(f)
                    }
                } else {
                    paths = arrayOfNulls(1)
                    paths[0] = file.path
                }
                paths
            } catch (e: Exception) {
                arrayOf()
            }
            withContext(Dispatchers.Main) {
                onDone(mPaths)
            }
        }
    }
    private fun scanPaths(toBeScanned: Array<String?>) {
        if (toBeScanned.isEmpty()) {
            toast(R.string.nothing_to_scan)
            stopForegroundOrNotification()
            stopSelf()
            isForegroundService = false
        } else {
            var cnt = toBeScanned.size
            MediaScannerConnection.scanFile(applicationContext, toBeScanned, null) { _, _ ->
                if (--cnt == 0) {
                    toast(getString(R.string.scann_message, toBeScanned.size))
                    PlaybackStateManager.getInstance().onReloadLibraries()
                    stopForegroundOrNotification()
                    stopSelf()
                    isForegroundService = false
                }
            }
        }
    }
    private fun startForegroundWithNotify() {
        notificationUtils?.createMediaScanNotification(currentPath, 0, 0)?.let {
            if (!isForegroundService) {
                if (Constant.isSPlus()) {
                    isForegroundService = try {
                        startForeground(NotificationUtils.SCANNER_NOTIFICATION_ID, it)
                        true
                    } catch (ex: ForegroundServiceStartNotAllowedException) {
                        false
                    }
                } else {
                    startForeground(NotificationUtils.SCANNER_NOTIFICATION_ID, it)
                    isForegroundService = true
                }
            } else {
                notificationUtils!!.notify(NotificationUtils.SCANNER_NOTIFICATION_ID, it)
            }
        }
    }
    private fun stopForegroundOrNotification() {
        try {
            if (isForegroundService) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForegroundService = false
            } else {
                notificationUtils?.cancel(NotificationUtils.SCANNER_NOTIFICATION_ID)
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
    override fun onBind(intent: Intent?): IBinder {
        return myBinder
    }
    inner class LocalScanner : Binder() {
        val instance: ScannerService get() = this@ScannerService
    }
}