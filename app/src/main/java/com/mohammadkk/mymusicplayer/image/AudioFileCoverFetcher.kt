package com.mohammadkk.mymusicplayer.image

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.data.DataFetcher
import com.mohammadkk.mymusicplayer.extensions.toAlbumArtURI
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

class AudioFileCoverFetcher(private val context: Context, private val model: AudioFileCover) : DataFetcher<InputStream> {
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
        if (path.startsWith("content://")) {
            mmr.setDataSource(context, path.toUri())
        } else {
            mmr.setDataSource(path)
        }
        val picture = mmr.embeddedPicture
        mmr.release()
        return picture?.let { ByteArrayInputStream(it) }
    }
    private fun getFallback(): InputStream? {
        return context.contentResolver.openInputStream(model.albumId.toAlbumArtURI())
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