package com.mohammadkk.mymusicplayer.image

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.mohammadkk.mymusicplayer.models.Song
import java.io.InputStream

@GlideModule
class AudioGlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.prepend(
            Song::class.java,
            InputStream::class.java,
            AudioFileCoverLoader.Factory()
        )
    }
    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}