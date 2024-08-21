package com.mohammadkk.mymusicplayer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import com.mohammadkk.mymusicplayer.BuildConfig

class MediaButtonIntentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent != null) {
            if (BuildConfig.DEBUG) Log.v(TAG, "Received intent: $intent")
            if (handleIntent(context, intent) && isOrderedBroadcast) {
                abortBroadcast()
            }
        }
    }
    companion object {
        val TAG: String = MediaButtonIntentReceiver::class.java.simpleName
        private const val MSG_HEADSET_DOUBLE_CLICK_TIMEOUT = 2
        private const val DOUBLE_CLICK = 400

        private var wakeLock: WakeLock? = null
        private var mClickCounter = 0
        private var mLastClickTime: Long = 0

        private val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    MSG_HEADSET_DOUBLE_CLICK_TIMEOUT -> {
                        val clickCount = msg.arg1
                        if (BuildConfig.DEBUG) Log.v(TAG, "Handling headset click, count = $clickCount")
                        val command = when (clickCount) {
                            1 -> MusicService.ACTION_TOGGLE_PAUSE
                            2 -> MusicService.ACTION_SKIP
                            3 -> MusicService.ACTION_REWIND
                            else -> null
                        }
                        if (command != null) {
                            val context = msg.obj as Context
                            startService(context, command)
                        }
                    }
                }
                releaseWakeLockIfHandlerIdle()
            }
        }
        fun handleIntent(context: Context, intent: Intent): Boolean {
            val intentAction = intent.action
            if (Intent.ACTION_MEDIA_BUTTON == intentAction) {
                val event = getEventExtra(intent) ?: return false
                val keycode = event.keyCode
                val action = event.action
                val eventTime = if (event.eventTime != 0L)
                    event.eventTime
                else
                    System.currentTimeMillis()

                var command: String? = null
                when (keycode) {
                    KeyEvent.KEYCODE_MEDIA_STOP -> command = MusicService.ACTION_STOP
                    KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        command = MusicService.ACTION_TOGGLE_PAUSE
                    }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> command = MusicService.ACTION_SKIP
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> command = MusicService.ACTION_REWIND
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> command = MusicService.ACTION_PAUSE
                    KeyEvent.KEYCODE_MEDIA_PLAY -> command = MusicService.ACTION_PLAY
                }
                if (command != null) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            if (keycode == KeyEvent.KEYCODE_HEADSETHOOK || keycode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                                if (eventTime - mLastClickTime >= DOUBLE_CLICK) mClickCounter = 0
                                mClickCounter++
                                if (BuildConfig.DEBUG) Log.v(TAG, "Got headset click, count = $mClickCounter")
                                mHandler.removeMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT)

                                val msg = mHandler.obtainMessage(
                                    MSG_HEADSET_DOUBLE_CLICK_TIMEOUT, mClickCounter, 0, context
                                )

                                val delay = (if (mClickCounter < 3) DOUBLE_CLICK else 0).toLong()
                                if (mClickCounter >= 3) mClickCounter = 0
                                mLastClickTime = eventTime
                                acquireWakeLockAndSendMessage(context, msg, delay)
                            } else {
                                startService(context, command)
                            }
                            return true
                        }
                    }
                }
            }
            return false
        }
        private fun getEventExtra(intent: Intent): KeyEvent? {
            val event = intent.extras?.let {
                BundleCompat.getParcelable(it, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            }
            if (event == null) {
                return IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            }
            return event
        }
        private fun startService(context: Context, command: String?) {
            val intent = Intent(context, MusicService::class.java)
            intent.action = command
            try {
                context.startService(intent)
            } catch (e: Exception) {
                ContextCompat.startForegroundService(context, intent)
            }
        }
        private fun acquireWakeLockAndSendMessage(context: Context, msg: Message, delay: Long) {
            if (wakeLock == null) {
                val pm = context.applicationContext.getSystemService<PowerManager>()
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "MusicApp:Wakelock headset button"
                )
                wakeLock!!.setReferenceCounted(false)
            }
            if (BuildConfig.DEBUG) Log.v(TAG, "Acquiring wake lock and sending " + msg.what)
            wakeLock!!.acquire(10000)
            mHandler.sendMessageDelayed(msg, delay)
        }
        private fun releaseWakeLockIfHandlerIdle() {
            if (mHandler.hasMessages(MSG_HEADSET_DOUBLE_CLICK_TIMEOUT)) {
                if (BuildConfig.DEBUG) Log.v(TAG, "Handler still has messages pending, not releasing wake lock")
                return
            }
            if (wakeLock != null) {
                if (BuildConfig.DEBUG) Log.v(TAG, "Releasing wake lock")
                wakeLock!!.release()
                wakeLock = null
            }
        }
    }
}