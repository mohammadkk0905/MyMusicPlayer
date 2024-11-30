package com.mohammadkk.mymusicplayer.ui.cab

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewStub
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.IntDef
import androidx.annotation.StyleRes
import androidx.appcompat.widget.Toolbar
import androidx.core.view.forEach
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.applyColor
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat

class ToolbarCab internal constructor(val toolbar: Toolbar) {
    @CabStatus
    var status: Int = STATUS_INACTIVE // default
        private set

    @ColorInt
    var backgroundColor: Int = Color.GRAY
        set(value) {
            field = value
            toolbar.setBackgroundColor(value)
        }

    var titleText: CharSequence = ""
        set(value) {
            field = value
            toolbar.title = value
        }

    @ColorInt
    var titleTextColor: Int = Color.WHITE
        set(value) {
            field = value
            toolbar.setTitleTextColor(value)
        }

    @StyleRes
    var popThemeRes: Int = androidx.appcompat.R.style.ThemeOverlay_AppCompat_DayNight_ActionBar
        set(value) {
            field = value
            toolbar.popupTheme = value
        }

    var navigationIcon: Drawable =
        toolbar.context.getDrawableCompat(R.drawable.ic_close).applyColor(titleTextColor)
        set(value) {
            field = value
            toolbar.navigationIcon = value
        }

    var menuHandler: (Toolbar.() -> Boolean)? = null
        set(value) {
            field = value
            setUpMenu()
        }

    init {
        toolbar.run {
            translationY = 0f
            alpha = 1f

            setBackgroundColor(backgroundColor)

            navigationIcon = this@ToolbarCab.navigationIcon

            title = titleText
            setTitleTextColor(titleTextColor)

            popupTheme = popThemeRes
            setUpMenu()
        }
    }

    private fun setUpMenu() {
        toolbar.menu?.clear()
        if (menuHandler != null) {
            menuHandler!!.invoke(toolbar)
            val iconRes = androidx.appcompat.R.drawable.abc_ic_menu_overflow_material
            toolbar.overflowIcon = toolbar.context.getDrawableCompat(iconRes).applyColor(titleTextColor)
            toolbar.menu?.forEach { it.icon = it.icon?.applyColor(titleTextColor) }
        }
    }

    @Synchronized
    fun show() = toolbar.run {
        visibility = View.VISIBLE
        bringToFront()
        status = STATUS_ACTIVE
    }

    @Synchronized
    fun hide() = toolbar.run {
        visibility = View.INVISIBLE
        status = STATUS_INACTIVE
    }

    companion object {
        const val STATUS_INACTIVE = 0
        const val STATUS_ACTIVE = 1

        @IntDef(STATUS_INACTIVE, STATUS_ACTIVE)
        @Retention(AnnotationRetention.SOURCE)
        annotation class CabStatus

        @Throws(IllegalStateException::class)
        fun initToolbarCab(activity: Activity, @IdRes stubId: Int, @IdRes inflatedId: Int): ToolbarCab {
            val inflated: View? = activity.findViewById(inflatedId)
            val stub: View? = activity.findViewById(stubId)

            val toolbar: Toolbar = if (inflated != null) {
                inflated as Toolbar
            } else if (stub != null && stub is ViewStub) {
                stub.inflatedId = inflatedId
                stub.layoutResource = R.layout.stub_toolbar
                stub.inflate() as Toolbar
            } else {
                throw IllegalStateException(
                    "Failed to create cab: ${activity.resources.getResourceName(stubId)} is not exist or not a ViewStub"
                )
            }
            return ToolbarCab(toolbar)
        }
    }
}