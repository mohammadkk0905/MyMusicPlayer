package com.mohammadkk.mymusicplayer.models

data class Artist(
    val id: Long,
    var duration: Long,
    var albumCount: Int,
    val songs: MutableList<Song>
) {
    val albumId: Long get() = getSafeSong().albumId
    val album: String get() = getSafeSong().album
    val title: String get() = getSafeSong().artist
    val year: Int get() = getSafeSong().year
    val trackCount: Int get() = songs.size

    fun getSafeSong(): Song {
        return songs.firstOrNull() ?: Song.emptySong
    }
}