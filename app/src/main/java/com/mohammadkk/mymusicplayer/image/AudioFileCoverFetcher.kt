package com.mohammadkk.mymusicplayer.image

import android.media.MediaMetadataRetriever
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.extensions.toAlbumArtURI
import org.jaudiotagger.audio.AudioFileIO
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class AudioFileCoverFetcher(private val model: AudioFileCover) : DataFetcher<InputStream> {
    private var stream: InputStream? = null

    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        try {
            stream = getStream(model.filePath) ?: getFallback()
            callback.onDataReady(stream)
        } catch (e: Exception) {
            callback.onLoadFailed(e)
        }
    }
    private fun getStream(path: String): InputStream? {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(path)
        val cover = mmr.embeddedPicture
        mmr.release()
        return cover?.let { ByteArrayInputStream(it) }
    }
    private fun getFallback(): InputStream? {
        val audioIO = runCatching { AudioFileIO.read(File(model.filePath)).tag }.getOrNull()
        val bytes = audioIO?.firstArtwork?.binaryData?.let { ByteArrayInputStream(it) }
        if (bytes == null && model.albumId != 0L) {
            val contentResolver = BaseSettings.getInstance().app.contentResolver
            return contentResolver.openInputStream(model.albumId.toAlbumArtURI())
        }
        return bytes
    }
    override fun cleanup() {
        if (stream != null) {
            try {
                stream?.close()
            } catch (ignore: IOException) {
                // can't do much about it
            }
        }
    }
    override fun cancel() {
        // cannot cancel
    }
    override fun getDataClass(): Class<InputStream> {
        return InputStream::class.java
    }
    override fun getDataSource(): DataSource {
        return DataSource.LOCAL
    }
}