package com.mohammadkk.mymusicplayer

import android.app.Application
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mohammadkk.mymusicplayer.utils.PlaybackRepeat

class BaseSettings(val app: Application) {
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

    var genresSorting: Int
        get() = prefs.getInt("genres_sorting", Constant.SORT_BY_TITLE)
        set(value) = prefs.edit { putInt("genres_sorting", value) }

    fun registerOnSharedPreferenceChangedListener(
        listener: OnSharedPreferenceChangeListener,
    ) = prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterOnSharedPreferenceChangedListener(
        changeListener: OnSharedPreferenceChangeListener,
    ) = prefs.unregisterOnSharedPreferenceChangeListener(changeListener)

    var themeUI: Int
        get() = prefs.getInt("theme_ui_mode", 2)
        set(value) = prefs.edit { putInt("theme_ui_mode", value) }

    var swapPrevNext: Boolean
        get() = prefs.getBoolean("swap_prev_next", false)
        set(value) = prefs.edit { putBoolean("swap_prev_next", value) }

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean("gapless_playback", false)
        set(value) = prefs.edit { putBoolean("gapless_playback", value) }

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1F)
        set(value) = prefs.edit { putFloat("playback_speed", value) }

    var playbackPitch: Float
        get() = prefs.getFloat("playback_pitch", 1F)
        set(value) = prefs.edit { putFloat("playback_pitch", value) }

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
        @JvmStatic
        fun getInstance(): BaseSettings {
            return INSTANCE ?: error("Not initialize settings!")
        }
    }
}