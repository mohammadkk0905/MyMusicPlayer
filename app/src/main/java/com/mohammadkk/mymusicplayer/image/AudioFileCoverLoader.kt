package com.mohammadkk.mymusicplayer.image

import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoader.LoadData
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import com.mohammadkk.mymusicplayer.models.Song
import java.io.InputStream

class AudioFileCoverLoader : ModelLoader<Song, InputStream> {
    override fun buildLoadData(
        model: Song,
        width: Int,
        height: Int,
        options: Options
    ): LoadData<InputStream> {
        return LoadData(
            ObjectKey("${model.data}, ${model.id}"),
            AudioFileCoverFetcher(model)
        )
    }
    override fun handles(model: Song): Boolean {
        return true
    }
    class Factory : ModelLoaderFactory<Song, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<Song, InputStream> {
            return AudioFileCoverLoader()
        }
        override fun teardown() {
        }
    }
}