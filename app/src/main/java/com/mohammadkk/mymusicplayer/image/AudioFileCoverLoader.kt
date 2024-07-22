package com.mohammadkk.mymusicplayer.image

import android.content.Context
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

class AudioFileCoverLoader(private val context: Context) : ModelLoader<AudioFileCover, InputStream> {
    override fun buildLoadData(
        audioFileCover: AudioFileCover,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<InputStream> {
        return LoadData(
            ObjectKey("${audioFileCover.filePath}, ${audioFileCover.albumId}"),
            AudioFileCoverFetcher(context, audioFileCover)
        )
    }
    override fun handles(audioFileCover: AudioFileCover): Boolean {
        return true
    }
    class Factory(private val context: Context) : ModelLoaderFactory<AudioFileCover, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AudioFileCover, InputStream> {
            return AudioFileCoverLoader(context)
        }
        override fun teardown() {
        }
    }
}