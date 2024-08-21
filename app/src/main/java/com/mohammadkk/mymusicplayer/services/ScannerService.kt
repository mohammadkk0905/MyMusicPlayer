package com.mohammadkk.mymusicplayer.services

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore.Audio.AudioColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.MainActivity
import com.mohammadkk.mymusicplayer.extensions.hasPermission
import com.mohammadkk.mymusicplayer.extensions.toImmutableFlag
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.utils.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ScannerService : Service() {
    private var notificationManager: NotificationManager? = null
    private val serviceScope = CoroutineScope(Job() + Main)
    private var currentPath = ""
    private var isForegroundService = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(this, notificationManager!!)
        startForegroundWithNotify()
    }
    override fun onDestroy() {
        super.onDestroy()
        stopForegroundOrNotification()
        serviceScope.cancel()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Constant.isQPlus() && !hasPermission(Constant.STORAGE_PERMISSION)) {
            return START_NOT_STICKY
        }
        if (intent?.action == Constant.SCANNER) {
            startForegroundWithNotify()
            listPaths(intent) { paths -> scanPaths(paths) }
        }
        return START_NOT_STICKY
    }
    @Synchronized
    private fun listPaths(intent: Intent, onDone: (paths: Array<String?>) -> Unit) {
        currentPath = intent.getStringExtra(AudioColumns.DATA) ?: Environment.getExternalStorageDirectory().absolutePath
        val file = File(currentPath)
        serviceScope.launch(Dispatchers.IO) {
            val mPaths = try {
                val paths: Array<String?>
                if (file.isDirectory) {
                    val files = FileUtils.listFilesDeep(file, FileUtils.AUDIO_FILE_FILTER)
                    paths = arrayOfNulls(files.size)
                    for (i in files.indices) {
                        paths[i] = FileUtils.safeGetCanonicalPath(files[i])
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
    private fun createMediaScanNotification(text: String): Notification {
        val cIntent = Intent(this, MainActivity::class.java)
        cIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val piIntent = PendingIntent.getActivity(this, 0, cIntent, (0).toImmutableFlag())

        val nDismissedIntent = Intent(this, MusicService::class.java).setAction(
            Constant.NOTIFICATION_DISMISSED
        )
        val piDelete = PendingIntent.getService(
            this, 0, nDismissedIntent, PendingIntent.FLAG_CANCEL_CURRENT.toImmutableFlag()
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
            .setContentTitle(getString(R.string.scanning))
            .setSmallIcon(R.drawable.ic_audiotrack)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(piIntent)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setDeleteIntent(piDelete)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .apply {
                if (text.isNotEmpty()) {
                    setContentText(text)
                }
            }.build()
    }
    private fun notifyNotification(notification: Notification) {
        notificationManager?.notify(SCANNER_NOTIFICATION_ID, notification)
    }
    private fun cancelNotification() {
        notificationManager?.cancel(SCANNER_NOTIFICATION_ID)
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
        createMediaScanNotification(currentPath).let {
            if (!isForegroundService) {
                if (Constant.isSPlus()) {
                    isForegroundService = try {
                        startForeground(SCANNER_NOTIFICATION_ID, it)
                        true
                    } catch (ex: ForegroundServiceStartNotAllowedException) {
                        false
                    }
                } else {
                    startForeground(SCANNER_NOTIFICATION_ID, it)
                    isForegroundService = true
                }
            } else {
                notifyNotification(it)
            }
        }
    }
    private fun stopForegroundOrNotification() {
        try {
            if (isForegroundService) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForegroundService = false
            } else {
                cancelNotification()
            }
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        }
    }
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    companion object {
        private const val NOTIFICATION_CHANNEL = "audio_player_channel"
        const val SCANNER_NOTIFICATION_ID = 43

        private fun createNotificationChannel(context: Context, manager: NotificationManager) {
            if (Constant.isOreoPlus()) {
                var nChannel: NotificationChannel? = manager.getNotificationChannel(NOTIFICATION_CHANNEL)
                if (nChannel == null) {
                    nChannel = NotificationChannel(
                        NOTIFICATION_CHANNEL,
                        context.getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW
                    )
                    nChannel.enableLights(false)
                    nChannel.enableVibration(false)
                    nChannel.setShowBadge(false)

                    manager.createNotificationChannel(nChannel)
                }
            }
        }
    }
}