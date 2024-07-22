package com.mohammadkk.mymusicplayer.image

class AudioFileCover(val filePath: String, val albumId: Long) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioFileCover

        if (filePath != other.filePath) return false
        if (albumId != other.albumId) return false

        return true
    }
    override fun hashCode(): Int {
        var result = filePath.hashCode()
        result = 31 * result + albumId.hashCode()
        return result
    }
    override fun toString(): String {
        return "AudioFileCover(filePath='$filePath', albumId=$albumId)"
    }
}