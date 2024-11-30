package com.mohammadkk.mymusicplayer.utils

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.toast

class MediaStoreScanner(val context: Context, val callback: () -> Unit) : MediaScannerConnection.MediaScannerConnectionClient {
    private val scannerConnection = MediaScannerConnection(context, this)
    private val scannedFiles = context.getString(R.string.scanned_files)
    private val couldNotScanFiles = context.getString(R.string.could_not_scan_files)

    class Task(
        val target: Array<String>,
        var failed: MutableList<String> = mutableListOf(),
        var succeed: Int = 0
    )

    private val queue: ArrayDeque<Task> = ArrayDeque(0)

    fun scan(paths: Array<String>) {
        val task = Task(paths)
        synchronized(scannerConnection) {
            if (!scannerConnection.isConnected) {
                queue.addFirst(task)
                scannerConnection.connect()
            }  else {
                if (queue.isEmpty()) {
                    queue.addFirst(task)
                    executeTask(queue.first())
                } else {
                    queue.addLast(task)
                }
            }
        }
    }
    private fun executeTask(task: Task) {
        val paths: Array<String> = task.target
        for (path in paths) {
            scannerConnection.scanFile(path, null)
        }
    }
    override fun onScanCompleted(path: String?, uri: Uri?) {
        if (path != null) {
            val task = queue.first()
            if (uri == null) {
                task.failed.add(path)
            } else {
                task.succeed++
            }
            if (task.succeed >= task.target.size) {
                synchronized(queue) {
                    val completed = queue.removeFirst()
                    reportResult(completed)
                    val next = queue.firstOrNull() ?: return
                    executeTask(next)
                }
            }
        }
    }
    override fun onMediaScannerConnected() {
        val task = queue.firstOrNull() ?: return
        executeTask(task)
    }
    private fun reportResult(task: Task) {
        Handler(Looper.getMainLooper()).post {
            val failed = task.failed.size
            val succeed = task.succeed
            if (failed > 0) {
                callback()
                context.toast(String.format(couldNotScanFiles, failed))
            } else {
                callback()
                context.toast(String.format(scannedFiles, succeed, succeed))
            }
        }
    }
}