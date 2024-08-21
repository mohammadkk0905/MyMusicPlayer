package com.mohammadkk.mymusicplayer.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val title: String,
    val track: Int,
    val year: Int,
    val duration: Long,
    val data: String,
    val dateModified: Long,
    val albumId: Long,
    val album: String,
    val artistId: Long,
    val artist: String,
    val composer: String?,
    val albumArtist: String?
) : Parcelable {
    fun isOTGMode(): Boolean {
        return albumId == 0L || artistId == 0L
    }
    companion object {
        @JvmStatic
        val emptySong = Song(
            id = -1,
            title = "",
            track = -1,
            year = -1,
            duration = -1,
            data = "",
            dateModified = -1,
            albumId = -1,
            album = "",
            artistId = -1,
            artist = "",
            composer = "",
            albumArtist = ""
        )
    }
}