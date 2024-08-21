package com.mohammadkk.mymusicplayer.providers

import android.content.Context
import androidx.core.content.edit
import com.mohammadkk.mymusicplayer.extensions.toAlbumArtURI
import com.mohammadkk.mymusicplayer.models.Song

class PersistentStorage(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveSong(song: Song) {
        prefs.edit {
            putLong("song_id", song.id)
            putString("song_title", song.title)
            putString("song_artist", song.artist)
            putString("song_cover", song.albumId.toAlbumArtURI().toString())
        }
    }
    fun savePosition(position: Int) {
        prefs.edit {
            putInt("position_track", position)
        }
    }
    fun savePositionInTrack(progressMillis: Int) {
        prefs.edit {
            putInt("position_in_track", progressMillis)
        }
    }
    fun getPosition(): Int {
        val position = prefs.getInt("position_track", -1)
        return position
    }
    fun getPositionInTrack(): Int {
        val position = prefs.getInt("position_in_track", -1)
        return position
    }
    companion object {
        const val PREFERENCES_NAME = "music_recent"

        @Volatile
        private var instance: PersistentStorage? = null

        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: PersistentStorage(context).also { instance = it }
        }
    }
}