package com.mohammadkk.mymusicplayer

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat
import org.json.JSONException
import org.json.JSONObject

class BaseSettings(app: Application) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(app.applicationContext)

    var actionModeIndex: Int = -1

    var songsSorting: Int
        get() = prefs.getInt("songs_sorting", Constant.SORT_BY_TITLE)
        set(value) = prefs.edit { putInt("songs_sorting", value) }

    var albumsSorting: Int
        get() = prefs.getInt("albums_sorting", Constant.SORT_BY_TITLE)
        set(value) = prefs.edit { putInt("albums_sorting", value) }

    var artistsSorting: Int
        get() = prefs.getInt("artists_sorting", Constant.SORT_BY_TITLE)
        set(value) = prefs.edit { putInt("artists_sorting", value) }

    var swapPrevNext: Boolean
        get() = prefs.getBoolean("swap_prev_next", false)
        set(value) = prefs.edit { putBoolean("swap_prev_next", value) }

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless_playback", false)
        set(value) = prefs.edit { putBoolean("gapless_playback", value) }

    var autoplay: Boolean
        get() = prefs.getBoolean("autoplay", true)
        set(value) = prefs.edit { putBoolean("autoplay", value) }

    var playbackRepeat: PlaybackRepeat
        get() {
            val index = prefs.getInt("playback_repeat", PlaybackRepeat.REPEAT_OFF.ordinal)
            return PlaybackRepeat.entries.getOrNull(index) ?: PlaybackRepeat.REPEAT_OFF
        }
        set(value) = prefs.edit { putInt("playback_repeat", value.ordinal) }

    var isShuffleEnabled: Boolean
        get() = prefs.getBoolean("shuffle", false)
        set(value) = prefs.edit {putBoolean("shuffle", value) }

    var otgTreeUri: String
        get() = prefs.getString("otg_tree_uri", null).orEmpty()
        set(value) = prefs.edit { putString("otg_tree_uri", value) }

    var otgPartition: String
        get() = prefs.getString("otg_partition", null).orEmpty()
        set(value) = prefs.edit { putString("otg_partition", value) }

    var coverMode: Int
        get() = prefs.getInt("cover_mode", Constant.COVER_MEDIA_STORE)
        set(value) = prefs.edit { putInt("cover_mode", value) }

    var lastStateMode: Pair<String, Long>?
        get() = jsonToPairStrLong(prefs.getString("last_state_mode", null))
        set(value) = prefs.edit { putString("last_state_mode", pairStrLongToJson(value)) }

    companion object {
        @Volatile
        private var INSTANCE: BaseSettings? = null

        fun initialize(application: Application): BaseSettings {
            return INSTANCE ?: synchronized(this) {
                val instance = BaseSettings(application)
                INSTANCE = instance
                instance
            }
        }
        fun getInstance(): BaseSettings {
            return INSTANCE ?: error("Not initialize settings!")
        }
        fun jsonToPairStrLong(json: String?): Pair<String, Long>? {
            if (json == null) return null
            return try {
                val obj = JSONObject(json)
                Pair(obj.getString("first"), obj.getLong("second"))
            } catch (e: JSONException) {
                null
            }
        }
        fun pairStrLongToJson(pair: Pair<String, Long>?): String? {
            if (pair == null) return null
            val obj = JSONObject()
            obj.put("first", pair.first)
            obj.put("second", pair.second)
            return obj.toString()
        }
    }
}