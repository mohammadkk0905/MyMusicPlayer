package com.mohammadkk.mymusicplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import java.util.Random

class MusicMiniVisualizer @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val random: Random
    private val paint: Paint
    private val dp: Float
    private var isPlayState = false
    private var animateView: Runnable?

    init {
        val colorPrimary = ThemeManager.colorPrimary
        random = Random()
        paint = Paint().apply {
            color = colorPrimary
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        dp = context.resources.displayMetrics.density * 1f
        animateView = object : Runnable {
            override fun run() {
                postDelayed(this, 150)
                invalidate()
            }
        }
    }

    var isPlaying: Boolean
        get() = isPlayState
        set(value) {
            isPlayState = value
            if (value) {
                removeCallbacks(animateView)
                post(animateView)
            } else {
                invalidate()
                removeCallbacks(animateView)
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        try {
            val oneNumber = dp * 0
            val twoNumber = dp * 5
            val threeNumber = dp * 8
            val fourNumber = dp * 13
            val fiveNumber = dp * 16
            val sixNumber = dp * 21
            val heightFloat = height.toFloat()
            if (!isPlayState) {
                canvas.drawRect(oneNumber, height - 14.0f, twoNumber, heightFloat, paint)
                canvas.drawRect(threeNumber, height - 14.0f, fourNumber, heightFloat, paint)
                canvas.drawRect(fiveNumber, height - 14.0f, sixNumber, heightFloat, paint)
                return
            }
            val heightMiddle = (height / 1.5f).toInt() - 17
            val nextInt = if (heightMiddle > 0) random.nextInt(heightMiddle) else 0
            val nextInt2 = if (heightMiddle > 0) random.nextInt(heightMiddle) else 0
            val nextInt3 = if (heightMiddle > 0) random.nextInt(heightMiddle) else 0
            canvas.drawRect(oneNumber, heightFloat - (nextInt + 18), twoNumber, heightFloat, paint)
            canvas.drawRect(threeNumber, heightFloat - (nextInt2 + 18), fourNumber, heightFloat, paint)
            canvas.drawRect(fiveNumber, heightFloat - (nextInt3 + 18), sixNumber, heightFloat, paint)
        } catch (ex: IllegalArgumentException) {
            ex.printStackTrace()
        }
    }
}