package com.mohammadkk.mymusicplayer.models

data class Song(
    val id: Long,
    val albumId: Long,
    val artistId: Long,
    val title: String,
    val album: String,
    val artist: String,
    val path: String,
    val year: Int,
    val duration: Int,
    val trackNumber: Int,
    val dateModified: Long
) {
    fun isOTGMode(): Boolean {
        return albumId == 0L || artistId == 0L
    }
    companion object {
        val emptySong = Song(
            id = -1,
            albumId = -1,
            artistId = -1,
            title = "",
            album = "",
            artist = "",
            path = "",
            year = -1,
            duration = -1,
            trackNumber = -1,
            dateModified = -1
        )
    }
}