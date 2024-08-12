package com.mohammadkk.mymusicplayer

import android.Manifest
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.bumptech.glide.util.Util.isOnMainThread
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

object Constant {
    private var lastClickTime: Long = 0
    val MY_EXECUTOR: ExecutorService = Executors.newSingleThreadExecutor()
    const val PERMISSION_REQUEST_STORAGE = 1001
    const val PERMISSION_REQUEST_NOTIFICATION = 1005
    //cover mode load
    const val COVER_OFF = 0
    const val COVER_MEDIA_STORE = 1

    const val SORT_BY_TITLE = 1
    const val SORT_BY_ALBUM = 2
    const val SORT_BY_ARTIST = 4
    const val SORT_BY_DURATION = 8
    const val SORT_BY_DATE_ADDED = 16
    const val SORT_BY_DATE_MODIFIED = 32
    const val SORT_BY_YEAR = 64
    const val SORT_BY_SONGS = 128
    const val SORT_BY_ALBUMS = 256

    const val LIST_CHILD = "list_child"
    const val ALBUM_TAB = "ALBUM"
    const val ARTIST_TAB = "ARTIST"
    const val GENRE_TAB = "GENRE"

    const val SONG_ID = "song_id"
    const val RESTART_PLAYER = "restart_player"

    // Notification
    private const val PATH = "com.mohammadkk.mymusicplayer.action."
    const val SCANNER = PATH + "SCANNER"
    const val INIT = PATH + "INIT"
    const val PREVIOUS = PATH + "PREVIOUS"
    const val PAUSE = PATH + "PAUSE"
    const val PLAY_PAUSE = PATH + "PLAY_PAUSE"
    const val NEXT = PATH + "NEXT"
    const val FINISH = PATH + "FINISH"
    const val DISMISS = PATH + "DISMISS"
    const val SKIP_BACKWARD = PATH + "SKIP_BACKWARD"
    const val SKIP_FORWARD = PATH + "SKIP_FORWARD"
    const val REFRESH_LIST = PATH + "REFRESH_LIST"
    const val UPDATE_QUEUE_SIZE = PATH + "UPDATE_QUEUE_SIZE"
    const val BROADCAST_STATUS = PATH + "BROADCAST_STATUS"
    const val NOTIFICATION_DISMISSED = PATH + "NOTIFICATION_DISMISSED"

    val STORAGE_PERMISSION get() = when {
        isTiramisuPlus() -> Manifest.permission.READ_MEDIA_AUDIO
        isRPlus() -> Manifest.permission.READ_EXTERNAL_STORAGE
        else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    fun isBlockingClick(): Boolean {
        val isBlocking: Boolean
        val currentTime = System.currentTimeMillis()
        isBlocking = abs(currentTime - lastClickTime) < 500L
        if (!isBlocking) lastClickTime = currentTime
        return isBlocking
    }
    fun ensureBackgroundThread(callback: () -> Unit) {
        if (isOnMainThread()) {
            Thread {
                callback()
            }.start()
        } else {
            callback()
        }
    }
    fun pairStateToJson(pair: Pair<String, Long>?): String? {
        if (pair == null) return null
        val jsObject = JSONObject()
        jsObject.put("first", pair.first)
        jsObject.put("second", pair.second)
        return jsObject.toString()
    }
    fun jsonToPairState(json: String?): Pair<String, Long>? {
        if (json == null) return null
        return try {
            val jsObject = JSONObject(json)
            Pair( jsObject.getString("first"), jsObject.getLong("second"))
        } catch (e: JSONException) {
            null
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun isUpsideDownCakePlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    fun isTiramisuPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    fun isSPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun isRPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun isQPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.O)
    fun isOreoPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.N)
    fun isNougatPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.M)
    fun isMarshmallowPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}