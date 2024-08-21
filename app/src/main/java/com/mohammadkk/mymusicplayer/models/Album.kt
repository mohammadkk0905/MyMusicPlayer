package com.mohammadkk.mymusicplayer.models

data class Album(
    val id: Long,
    var duration: Long,
    val songs: MutableList<Song>
) {
    val title: String get() = getSafeSong().album
    val artist: String get() = getSafeSong().artist
    val year: Int get() = getSafeSong().year
    val trackCount: Int get() = songs.size

    fun getSafeSong(): Song {
        return songs.firstOrNull() ?: Song.emptySong
    }
}