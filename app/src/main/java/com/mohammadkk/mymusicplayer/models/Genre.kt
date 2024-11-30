package com.mohammadkk.mymusicplayer.models

data class Genre(
    val id: Long,
    val name: String,
    val songs: List<Song>
) {
    val songCount get() = songs.size
    val currentSong get() = songs.firstOrNull() ?: Song.emptySong
}