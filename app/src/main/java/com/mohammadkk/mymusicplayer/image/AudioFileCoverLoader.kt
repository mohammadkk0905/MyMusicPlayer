package com.mohammadkk.mymusicplayer.image

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.InputStream

class AudioFileCoverLoader : ModelLoader<AudioFileCover, InputStream> {
    override fun buildLoadData(
        audioFileCover: AudioFileCover,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<InputStream> {
        return LoadData(
            ObjectKey("${audioFileCover.filePath}, ${audioFileCover.albumId}"),
            AudioFileCoverFetcher(audioFileCover)
        )
    }
    override fun handles(audioFileCover: AudioFileCover): Boolean {
        return true
    }
    class Factory : ModelLoaderFactory<AudioFileCover, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<AudioFileCover, InputStream> {
            return AudioFileCoverLoader()
        }
        override fun teardown() {
        }
    }
}