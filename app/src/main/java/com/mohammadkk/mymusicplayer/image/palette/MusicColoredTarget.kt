package com.mohammadkk.mymusicplayer.image.palette

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.palette.graphics.Palette
import androidx.palette.graphics.Target
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.mohammadkk.mymusicplayer.BaseSettings
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

private val coroutineName = CoroutineName("PaletteGenerator")
private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

abstract class MusicColoredTarget(view: ImageView) : ImageViewTarget<Bitmap>(view) {
    abstract fun onResolveColor(colors: PaletteColors)

    override fun setResource(resource: Bitmap?) {
        if (resource != null) view.setImageBitmap(resource)
    }
    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        onResolveColor(PaletteColors())
    }
    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
        super.onResourceReady(resource, transition)
        coroutineScope.launch(Dispatchers.IO) {
            val paletteColor = paletteColor(resource)
            val paletteColors = if (paletteColor != null) {
                PaletteColors(paletteColor)
            } else {
                PaletteColors()
            }
            coroutineScope.launch(Dispatchers.Main.immediate) {
                onResolveColor(paletteColors)
            }
        }
    }
    private suspend fun paletteColor(bitmap: Bitmap): Int? {
        val resizeBitmap = getResizeBitmap(bitmap)
        val builder = Palette.from(resizeBitmap)
            .setRegion(0, 0, resizeBitmap.getWidth() / 2, resizeBitmap.getHeight())
            .clearTargets()
            .addTarget(Target.VIBRANT)
            .addTarget(Target.MUTED)
            .resizeBitmapArea(22500)

        val palette = withContext(Dispatchers.Default + coroutineName) {
            builder.generate()
        }
        val swatch = with(palette) { vibrantSwatch ?: mutedSwatch }
        return swatch?.rgb
    }
    private fun getResizeBitmap(bitmap: Bitmap): Bitmap {
        val context = view.context ?: BaseSettings.getInstance().app
        val drawable = BitmapDrawable(context.resources, bitmap)
        var width = drawable.intrinsicWidth
        var height = drawable.intrinsicHeight
        val area = width * height
        if (area > 22500) {
            val factor = sqrt(22500f / area)
            width = (factor * width).toInt()
            height = (factor * height).toInt()
        }
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return newBitmap
    }
}