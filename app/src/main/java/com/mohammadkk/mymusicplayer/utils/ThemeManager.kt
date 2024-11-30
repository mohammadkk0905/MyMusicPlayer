package com.mohammadkk.mymusicplayer.utils

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.FragmentActivity
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.getColorCompat
import kotlin.math.max
import kotlin.math.min

object ThemeManager {
    private var initializer = false
    private val themeColors = intArrayOf(0, 0, 0, 0)

    val colorPrimary: Int get() = themeColors[0]
    val colorPrimaryContainer: Int get() = themeColors[1]
    val colorSurface: Int get() = themeColors[2]
    val colorSurfaceContainer: Int get() = themeColors[3]

    fun build(context: FragmentActivity) {
        if (initializer) return
        BaseSettings.initialize(context.application)
        themeColors[0] = context.getColorCompat(R.color.light_blue_primary)
        themeColors[1] = context.getColorCompat(R.color.light_blue_primary_container)
        themeColors[2] = context.getColorCompat(R.color.light_blue_surface)
        themeColors[3] = context.getColorCompat(R.color.light_blue_surface_container)
        initializer = true
    }
    @ColorInt
    fun darkenColor(@ColorInt color: Int, value: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] -= value
        hsl[2] = hsl[2].coerceIn(0f, 1f)
        return ColorUtils.HSLToColor(hsl)
    }
    @ColorInt
    fun withAlpha(@ColorInt baseColor: Int, @FloatRange(from = 0.0, to = 1.0) alpha: Float): Int {
        val a = min(255, max(0, (alpha * 255).toInt())) shl 24
        val rgb = 0x00ffffff and baseColor
        return a + rgb
    }
    @JvmStatic
    fun isColorLight(@ColorInt color: Int): Boolean {
        val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
        return darkness < 0.4
    }
    @JvmStatic
    fun isNightTheme(resources: Resources): Boolean {
        val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }
    @JvmStatic
    @ColorInt
    fun primaryTextColor(dark: Boolean): Int {
        return if (dark) Color.WHITE else withAlpha(Color.BLACK, 0.87f)
    }
    @JvmStatic
    @ColorInt
    fun secondaryTextColor(dark: Boolean): Int {
        return if (dark) withAlpha(Color.WHITE, 0.70f) else withAlpha(Color.BLACK, 0.54f)
    }
    fun clear() {
        initializer = false
    }
}