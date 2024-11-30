package com.mohammadkk.mymusicplayer.ui.fastscroll

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import androidx.core.widget.TextViewCompat
import com.google.android.material.textview.MaterialTextView
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.getAttrColorCompat
import com.mohammadkk.mymusicplayer.extensions.isRtl
import com.google.android.material.R as MR


class FastScrollPopupView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleRes: Int = 0
) : MaterialTextView(context, attrs, defStyleRes) {

    init {
        minimumWidth = context.resources.getDimensionPixelSize(R.dimen.fast_scroll_popup_min_width)
        minimumHeight = context.resources.getDimensionPixelSize(R.dimen.fast_scroll_popup_min_height)

        TextViewCompat.setTextAppearance(this, MR.style.TextAppearance_Material3_HeadlineLarge)
        setTextColor(context.getAttrColorCompat(MR.attr.colorOnSecondary))
        ellipsize = TextUtils.TruncateAt.MIDDLE
        gravity = Gravity.CENTER
        includeFontPadding = false

        alpha = 0f
        elevation = context.resources.getDimensionPixelSize(R.dimen.elevation_normal).toFloat()
        background = FastScrollPopupDrawable(context)
    }
    private class FastScrollPopupDrawable(context: Context) : Drawable() {
        private val paint = Paint().apply {
            isAntiAlias = true
            color = context.getAttrColorCompat(MR.attr.colorSecondary)
            style = Paint.Style.FILL
        }
        private val path = Path()
        private val matrix = Matrix()
        private val paddingStart = context.resources.getDimensionPixelSize(R.dimen.spacing_medium)
        private val paddingEnd = context.resources.getDimensionPixelSize(R.dimen.fast_scroll_popup_padding_end)

        override fun draw(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }
        override fun onBoundsChange(bounds: Rect) {
            updatePath()
        }
        override fun onLayoutDirectionChanged(layoutDirection: Int): Boolean {
            updatePath()
            return true
        }
        @Suppress("DEPRECATION")
        override fun getOutline(outline: Outline) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> outline.setPath(path)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> outline.setConvexPath(path)
                else -> if (!path.isConvex) {
                    super.getOutline(outline)
                }
            }
        }
        override fun getPadding(padding: Rect): Boolean {
            if (isRtl) {
                padding[paddingEnd, 0, paddingStart] = 0
            } else {
                padding[paddingStart, 0, paddingEnd] = 0
            }
            return true
        }

        override fun isAutoMirrored(): Boolean = true

        override fun setAlpha(alpha: Int) {}

        override fun setColorFilter(colorFilter: ColorFilter?) {}

        @Deprecated("Deprecated in Java",
            ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
        )
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private fun updatePath() {
            val r = bounds.height().toFloat() / 2
            val w = (r + SQRT2 * r).coerceAtLeast(bounds.width().toFloat())

            path.apply {
                reset()
                // Draw the left pill shape
                val o1X = w - SQRT2 * r
                arcToSafe(r, r, r, 90f, 180f)
                arcToSafe(o1X, r, r, -90f, 45f)
                // Draw the right arrow shape
                val point = r / 5
                val o2X = w - SQRT2 * point
                arcToSafe(o2X, r, point, -45f, 90f)
                arcToSafe(o1X, r, r, 45f, 45f)

                close()
            }
            matrix.apply {
                reset()
                if (isRtl) setScale(-1f, 1f, w / 2, 0f)
                postTranslate(bounds.left.toFloat(), bounds.top.toFloat())
            }
            path.transform(matrix)
        }
        private fun Path.arcToSafe(
            centerX: Float,
            centerY: Float,
            radius: Float,
            startAngle: Float,
            sweepAngle: Float
        ) {
            arcTo(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                startAngle, sweepAngle,
                false
            )
        }
    }
    private companion object {
        // Pre-calculate sqrt(2)
        const val SQRT2 = 1.4142135f
    }
}