package com.mohammadkk.mymusicplayer.extensions

import android.app.Activity
import android.app.Dialog
import android.graphics.PorterDuff
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.image.GlideExtensions.getCoverOptions
import com.mohammadkk.mymusicplayer.models.Song

val Drawable.isRtl: Boolean
    get() = DrawableCompat.getLayoutDirection(this) == View.LAYOUT_DIRECTION_RTL

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
fun CircularProgressIndicator.accentColor() {
    val color = context.getAttrColorCompat(com.google.android.material.R.attr.colorPrimary)
    setIndicatorColor(color)
    trackColor = Constant.withAlpha(color, 0.2f)
}
fun ImageView.setAnimatedVectorDrawable(@DrawableRes resId: Int, animate: Boolean) {
    val drawableIcon = AppCompatResources.getDrawable(context, resId)
    val imageTag = tag as? Int
    if (imageTag != null && imageTag == resId) return
    tag = resId
    setImageDrawable(drawableIcon)
    if (animate && imageTag != null && drawableIcon is Animatable) drawableIcon.start()
}
fun ImageView.bind(song: Song, @DrawableRes placeholder: Int) {
    val songModel = GlideExtensions.getSongModel(song)
    val coverPlaceholder = GlideExtensions.getCoverArt(context, song.id, placeholder)
    if (songModel == null) {
        setImageDrawable(coverPlaceholder)
    } else {
        Glide.with(context)
            .asBitmap()
            .getCoverOptions(song, coverPlaceholder)
            .load(songModel)
            .into(this)
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
fun Drawable.createTintedDrawable(@ColorInt color: Int): Drawable {
    var drawable = this
    drawable = DrawableCompat.wrap(drawable.mutate())
    drawable.setTintMode(PorterDuff.Mode.SRC_IN)
    drawable.setTint(color)
    return drawable
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