package com.mohammadkk.mymusicplayer.image

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.Key
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.MediaStoreSignature
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.applyColor
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.toAlbumArtURI
import com.mohammadkk.mymusicplayer.extensions.toContentUri
import com.mohammadkk.mymusicplayer.models.Song

object GlideExtensions {
    private val DEFAULT_DISK_CACHE_STRATEGY = DiskCacheStrategy.NONE
    private var colors: IntArray? = null

    fun getSongModel(song: Song): Any? {
        return when (BaseSettings.getInstance().coverMode) {
            Constant.COVER_OFF -> return null
            Constant.COVER_MEDIA_STORE -> if (!song.isOTGMode()) {
                song.albumId.toAlbumArtURI()
            } else {
                AudioFileCover(song.path, song.albumId)
            }
            else -> {
                val path = if (!song.isOTGMode()) song.path else song.toContentUri().toString()
                AudioFileCover(path, song.albumId)
            }
        }
    }
    fun <T> RequestBuilder<T>.getCoverOptions(
        song: Song,
        drawable: Drawable,
    ): RequestBuilder<T> {
        return diskCacheStrategy(DEFAULT_DISK_CACHE_STRATEGY)
            .error(drawable)
            .placeholder(drawable)
            .signature(createSignature(song))
    }
    private fun createSignature(song: Song): Key {
        return MediaStoreSignature("", song.dateModified, 0)
    }
    fun getCoverArt(context: Context, songId: Long, @DrawableRes resId: Int): Drawable {
        if (colors == null) {
            colors = intArrayOf(
                context.getColorCompat(R.color.blue_art),
                context.getColorCompat(R.color.green_art),
                context.getColorCompat(R.color.yellow_art),
                context.getColorCompat(R.color.pink_art),
                context.getColorCompat(R.color.purple_art),
                context.getColorCompat(R.color.red_art),
                context.getColorCompat(R.color.cyan_art)
            )
        }
        val cover = context.getDrawableCompat(resId)
        val index = (songId % 7).toInt()
        return StyledDrawable(cover.applyColor(colors!![if (index >= 0) index else 0]))
    }
    private class StyledDrawable(private val inner: Drawable) : Drawable() {
        override fun draw(canvas: Canvas) {
            val adj = (bounds.width() / 4)
            inner.bounds.set(adj, adj, bounds.width() - adj, bounds.height() - adj)
            inner.draw(canvas)
        }
        override fun getAlpha(): Int {
            return inner.alpha
        }
        override fun setAlpha(alpha: Int) {
            inner.alpha = alpha
        }
        override fun setColorFilter(colorFilter: ColorFilter?) {
            inner.colorFilter = colorFilter
        }
        @Deprecated("Deprecated in Java",
            ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
        )
        override fun getOpacity(): Int {
            return PixelFormat.TRANSLUCENT
        }
    }
}