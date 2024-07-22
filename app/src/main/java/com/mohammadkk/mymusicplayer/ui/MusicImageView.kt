package com.mohammadkk.mymusicplayer.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.shape.MaterialShapeDrawable
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.applyColor
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.toCoverArt
import com.mohammadkk.mymusicplayer.models.Album
import com.mohammadkk.mymusicplayer.models.Artist
import com.mohammadkk.mymusicplayer.models.Song

class MusicImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    private var tintStaticIcon: Int

    init {
        // Load view attributes
        val styledAttrs = context.obtainStyledAttributes(attrs, R.styleable.MusicImageView, defStyleAttr, 0)
        val staticIcon = styledAttrs.getResourceId(R.styleable.MusicImageView_staticIcon, ResourcesCompat.ID_NULL)
        val cornerRadius = styledAttrs.getDimension(R.styleable.MusicImageView_cornerRadius, 0f)
        tintStaticIcon = styledAttrs.getColor(R.styleable.MusicImageView_tintStaticIcon, Color.TRANSPARENT)
        val backgroundColor = styledAttrs.getColor(R.styleable.MusicImageView_backgroundColor, Color.TRANSPARENT)
        styledAttrs.recycle()

        if (staticIcon != ResourcesCompat.ID_NULL) {
            setImageDrawable(IconDrawable(getIconDrawableOrTint(context.getDrawableCompat(staticIcon))))
        }
        clipToOutline = true
        if (backgroundColor != Color.TRANSPARENT) {
            background = MaterialShapeDrawable().apply {
                fillColor = ColorStateList.valueOf(backgroundColor)
                setCornerSize(cornerRadius)
            }
        }
    }
    fun setTintStaticIcon(@ColorInt tintColor: Int) {
        tintStaticIcon = tintColor
    }
    fun setStaticIcon(@DrawableRes resId: Int) {
        setImageDrawable(IconDrawable(getIconDrawableOrTint(context.getDrawableCompat(resId))))
    }
    fun bind(song: Song, @DrawableRes errorRes: Int) {
        bindImpl(song, errorRes, R.string.desc_album_cover)
    }
    fun bind(song: Song) {
        bindImpl(song, R.drawable.ic_audiotrack, R.string.desc_album_cover)
    }
    fun bind(album: Album) {
        bindImpl(album.getSafeSong(), R.drawable.ic_album, R.string.desc_album_cover)
    }
    fun bind(artist: Artist) {
        bindImpl(artist.getSafeSong(), R.drawable.ic_artist, R.string.desc_artist_image)
    }
    private fun bindImpl(song: Song, @DrawableRes errorRes: Int, @StringRes descRes: Int) {
        val resource = song.toCoverArt(BaseSettings.getInstance().coverMode)
        if (resource == null) {
            setImageDrawable(IconDrawable(getIconDrawableOrTint(context.getDrawableCompat(errorRes))))
        } else {
            val iconDrawable = IconDrawable(getIconDrawableOrTint(context.getDrawableCompat(errorRes)))
            Glide.with(context)
                .load(resource)
                .apply(
                    RequestOptions()
                        .error(iconDrawable)
                        .placeholder(iconDrawable)
                )
                .into(this)
        }
        contentDescription = context.getString(descRes, song.title)
    }
    private fun getIconDrawableOrTint(inner: Drawable): Drawable {
        return if (tintStaticIcon != Color.TRANSPARENT) inner.applyColor(tintStaticIcon) else inner
    }
    private class IconDrawable(private var inner: Drawable) : Drawable() {
        override fun draw(canvas: Canvas) {
            val adjWidth = bounds.width() / 4
            val adjHeight = bounds.height() / 4
            inner.bounds.set(adjWidth, adjHeight, bounds.width() - adjWidth, bounds.height() - adjHeight)
            inner.draw(canvas)
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