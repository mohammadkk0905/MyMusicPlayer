package com.mohammadkk.mymusicplayer.extensions

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.ui.drawables.PopupBackground
import me.zhanghai.android.fastscroll.FastScroller
import me.zhanghai.android.fastscroll.FastScrollerBuilder
import me.zhanghai.android.fastscroll.PopupStyles

fun ViewPager2.reduceDragSensitivity() {
    try {
        val recycler = ViewPager2::class.java.getDeclaredField("mRecyclerView")
        recycler.isAccessible = true
        val recyclerView = recycler.get(this) as RecyclerView
        val touchSlopField = RecyclerView::class.java.getDeclaredField("mTouchSlop")
        touchSlopField.isAccessible = true
        val touchSlop = touchSlopField.get(recyclerView) as Int
        touchSlopField.set(recyclerView, touchSlop * 3) // 3x seems to be the best fit here
    } catch (e: Exception) {
        Log.e("MainActivity", e.stackTraceToString())
    }
}
fun RecyclerView.createFastScroll(): FastScroller {
    val ctx = context
    val textColor = ctx.getColorCompat(R.color.main_bg)
    val fastScrollerBuilder = FastScrollerBuilder(this)
    fastScrollerBuilder.useMd2Style()
    fastScrollerBuilder.setPopupStyle { popupText ->
        PopupStyles.MD2.accept(popupText)
        popupText.background = PopupBackground(context)
        popupText.setTextColor(textColor)
    }
    return fastScrollerBuilder.setPadding(0, 0, 0, 0).build()
}
fun ImageView.updateIconTint(@ColorInt color: Int) {
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
}
fun ImageView.updatePlayingState(isPlaying: Boolean, isAnimation: Boolean) {
    val defaultIcon =  if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
    val animateIcon =  if (isPlaying) R.drawable.anim_play_to_pause else R.drawable.anim_pause_to_play
    if (!isAnimation) {
        setImageResource(defaultIcon)
    } else {
        val drawableIcon = context.getDrawableCompat(animateIcon)
        setImageDrawable(drawableIcon)
        if (drawableIcon is Animatable) {
            drawableIcon.start()
        } else {
            setImageResource(defaultIcon)
        }
    }
}
fun Dialog?.applyFullHeightDialog(activity: Activity) {
    // to ensure full dialog's height
    val height = activity.resources.displayMetrics.heightPixels
    this?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)?.let { bs ->
        BottomSheetBehavior.from(bs).peekHeight = height
    }
}
fun Drawable.applyColor(@ColorInt color: Int): Drawable {
    val copy = mutate()
    copy.colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
        color, BlendModeCompat.SRC_IN
    )
    return copy
}
fun MenuItem.setTitleColor(@ColorInt color: Int) {
    if (title.isNullOrEmpty()) return
    SpannableString(title).apply {
        setSpan(ForegroundColorSpan(color), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        title = this
    }
}
fun MenuItem.setIconColor(@ColorInt color: Int) {
    val dwIcon = icon ?: return
    icon = dwIcon.applyColor(color)
}