package com.mohammadkk.mymusicplayer.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.fragment.app.FragmentActivity
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import kotlin.math.max
import kotlin.math.min

object ThemeManager {
    var colorPrimary: Int = 0
        private set

    var colorPrimaryAlpha: Int = 0
        private set

    fun build(context: FragmentActivity) {
        colorPrimary = context.getColorCompat(R.color.light_blue_primary)
        colorPrimaryAlpha = withAlpha(colorPrimary, 0.2f)
    }
    @ColorInt
    fun withAlpha(@ColorInt baseColor: Int, @FloatRange(from = 0.0, to = 1.0) alpha: Float): Int {
        val a = min(255, max(0, (alpha * 255).toInt())) shl 24
        val rgb = 0x00ffffff and baseColor
        return a + rgb
    }
    fun isColorLight(@ColorInt color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.4
    }
}