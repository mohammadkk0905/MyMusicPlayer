package com.mohammadkk.mymusicplayer.models

import android.net.Uri

data class FileItem(
    val filename: String?,
    val isDirectory: Boolean,
    val modified: Long,
    val contentUri: Uri
) {
    fun toSongItem(id: Int = 0): Song? {
        val title = filename?.substringBeforeLast('.', "")
        val album = "Unknown album"
        val artist = "Unknown artist"
        if (title == null || title == "") return null
        val path = contentUri.toString()
        val dateAdded = modified.toInt()
        return Song(id.toLong(), 0, 0, title, album, artist, path, 0, 0, dateAdded, modified)
    }
}