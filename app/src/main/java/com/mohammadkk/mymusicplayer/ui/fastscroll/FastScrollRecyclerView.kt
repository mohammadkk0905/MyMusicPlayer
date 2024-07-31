
package com.mohammadkk.mymusicplayer.ui.fastscroll

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.isUnder
import kotlin.math.abs


class FastScrollRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, @AttrRes defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {
    // Thumb
    private val thumbView = View(context).apply {
        alpha = 0f
        background = context.getDrawableCompat(R.drawable.ui_scroll_thumb)
    }

    private val thumbWidth = thumbView.background.intrinsicWidth
    private val thumbHeight = thumbView.background.intrinsicHeight
    private val thumbPadding = Rect(0, 0, 0, 0)
    private var thumbOffset = 0

    private var showingThumb = false
    private val hideThumbRunnable = Runnable {
        if (!dragging) hideScrollbar()
    }

    // Popup
    private val popupView = FastScrollPopupView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            marginEnd = context.resources.getDimensionPixelSize(R.dimen.spacing_small)
        }
    }

    private var showingPopup = false
    // Touch
    private val minTouchTargetSize = context.resources.getDimensionPixelSize(R.dimen.size_btn)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragStartY = 0f
    private var dragStartThumbOffset = 0

    private var dragging = false
        set(value) {
            if (field == value) return
            field = value

            if (value) {
                parent.requestDisallowInterceptTouchEvent(true)
            }
            thumbView.isPressed = value
            if (field) {
                removeCallbacks(hideThumbRunnable)
                showScrollbar()
                showPopup()
            } else {
                postAutoHideScrollbar()
                hidePopup()
            }
            listener?.onFastScrollingChanged(field)
        }

    private val tRect = Rect()

    var popupProvider: PopupProvider? = null
    var listener: Listener? = null

    init {
        overlay.add(thumbView)
        overlay.add(popupView)

        addItemDecoration(
            object : ItemDecoration() {
                override fun onDraw(canvas: Canvas, parent: RecyclerView, state: State) {
                    onPreDraw()
                }
            }
        )
        addOnItemTouchListener(
            object : SimpleOnItemTouchListener() {
                override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
                    onItemTouch(event)
                }

                override fun onInterceptTouchEvent(
                    recyclerView: RecyclerView,
                    event: MotionEvent
                ): Boolean {
                    return onItemTouch(event)
                }
            }
        )
    }

    private fun onPreDraw() {
        updateScrollbarState()

        thumbView.layoutDirection = layoutDirection
        popupView.layoutDirection = layoutDirection

        val thumbLeft = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            thumbPadding.left
        } else {
            width - thumbPadding.right - thumbWidth
        }

        val thumbTop = thumbPadding.top + thumbOffset
        thumbView.layout(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight)

        val child = getChildAt(0)
        val firstAdapterPos = if (child != null) {
            layoutManager?.getPosition(child) ?: NO_POSITION
        } else {
            NO_POSITION
        }


        val provider = popupProvider
        val popupText = if (firstAdapterPos != NO_POSITION && provider != null) {
            popupView.isInvisible = false
            provider.getPopup(firstAdapterPos) ?: "?"
        } else {
            popupView.isInvisible = true
            ""
        }

        val popupLayoutParams = popupView.layoutParams as FrameLayout.LayoutParams

        if (popupView.text != popupText) {
            popupView.text = popupText
            val widthMeasureSpec = ViewGroup.getChildMeasureSpec(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                thumbPadding.left +
                        thumbPadding.right +
                        thumbWidth +
                        popupLayoutParams.leftMargin +
                        popupLayoutParams.rightMargin,
                popupLayoutParams.width
            )
            val heightMeasureSpec = ViewGroup.getChildMeasureSpec(
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
                thumbPadding.top +
                        thumbPadding.bottom +
                        popupLayoutParams.topMargin +
                        popupLayoutParams.bottomMargin,
                popupLayoutParams.height
            )
            popupView.measure(widthMeasureSpec, heightMeasureSpec)
        }

        val popupWidth = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight
        val popupLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            thumbPadding.left + thumbWidth + popupLayoutParams.leftMargin
        } else {
            width - thumbPadding.right - thumbWidth - popupLayoutParams.rightMargin - popupWidth
        }

        val popupAnchorY = popupHeight / 2
        val thumbAnchorY = thumbView.paddingTop

        val popupTop = (thumbTop + thumbAnchorY - popupAnchorY)
                .coerceAtLeast(thumbPadding.top + popupLayoutParams.topMargin)
                .coerceAtMost(height - thumbPadding.bottom - popupLayoutParams.bottomMargin - popupHeight)

        popupView.layout(popupLeft, popupTop, popupLeft + popupWidth, popupTop + popupHeight)
    }
    override fun onScrolled(dx: Int, dy: Int) {
        super.onScrolled(dx, dy)
        updateScrollbarState()
        if (dx == 0 && dy == 0) return
        showScrollbar()
        postAutoHideScrollbar()
    }
    private fun updateScrollbarState() {
        if (scrollRange <= height || childCount == 0) {
            return
        }
        getDecoratedBoundsWithMargins(getChildAt(0), tRect)
        val child = getChildAt(0)
        val firstAdapterPos = when (val mgr = layoutManager) {
            is GridLayoutManager -> mgr.getPosition(child) / mgr.spanCount
            is LinearLayoutManager -> mgr.getPosition(child)
            else -> 0
        }

        val scrollOffset = paddingTop + (firstAdapterPos * itemHeight) - tRect.top
        thumbOffset = (thumbOffsetRange.toLong() * scrollOffset / scrollOffsetRange).toInt()
    }
    private fun onItemTouch(event: MotionEvent): Boolean {
        val eventX = event.x
        val eventY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = eventX
                downY = eventY
                if (eventX >= thumbView.left && eventX < thumbView.right) {
                    dragStartY = eventY

                    if (thumbView.isUnder(eventX, eventY, minTouchTargetSize)) {
                        dragStartThumbOffset = thumbOffset
                    } else {
                        dragStartThumbOffset =
                            (eventY - thumbPadding.top - thumbHeight / 2f).toInt()
                        scrollToThumbOffset(dragStartThumbOffset)
                    }

                    dragging = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging &&
                    thumbView.isUnder(downX, thumbView.top.toFloat(), minTouchTargetSize) &&
                    abs(eventY - downY) > touchSlop) {
                    if (thumbView.isUnder(downX, downY, minTouchTargetSize)) {
                        dragStartY = lastY
                        dragStartThumbOffset = thumbOffset
                    } else {
                        dragStartY = eventY
                        dragStartThumbOffset = (eventY - thumbPadding.top - thumbHeight / 2f).toInt()
                        scrollToThumbOffset(dragStartThumbOffset)
                    }
                    dragging = true
                }
                if (dragging) {
                    val thumbOffset = dragStartThumbOffset + (eventY - dragStartY).toInt()
                    scrollToThumbOffset(thumbOffset)
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> dragging = false
        }
        lastY = eventY
        return dragging
    }
    private fun scrollToThumbOffset(thumbOffset: Int) {
        val clampedThumbOffset = thumbOffset.coerceAtLeast(0).coerceAtMost(thumbOffsetRange)

        val scrollOffset =
            (scrollOffsetRange.toLong() * clampedThumbOffset / thumbOffsetRange).toInt() -
                paddingTop

        scrollTo(scrollOffset)
    }
    private fun scrollTo(offset: Int) {
        if (childCount == 0) return
        stopScroll()
        val trueOffset = offset - paddingTop
        val itemHeight = itemHeight
        val firstItemPosition = 0.coerceAtLeast(trueOffset / itemHeight)
        val firstItemTop = firstItemPosition * itemHeight - trueOffset
        scrollToPositionWithOffset(firstItemPosition, firstItemTop)
    }
    private fun scrollToPositionWithOffset(position: Int, offset: Int) {
        var targetPosition = position
        val trueOffset = offset - paddingTop
        when (val mgr = layoutManager) {
            is GridLayoutManager -> {
                targetPosition *= mgr.spanCount
                mgr.scrollToPositionWithOffset(targetPosition, trueOffset)
            }
            is LinearLayoutManager -> {
                mgr.scrollToPositionWithOffset(targetPosition, trueOffset)
            }
        }
    }
    private fun postAutoHideScrollbar() {
        removeCallbacks(hideThumbRunnable)
        postDelayed(hideThumbRunnable, AUTO_HIDE_SCROLLBAR_DELAY_MILLIS.toLong())
    }
    private fun showScrollbar() {
        if (showingThumb) return
        showingThumb = true
        animateViewIn(thumbView)
    }
    private fun hideScrollbar() {
        if (!showingThumb) return
        showingThumb = false
        animateViewOut(thumbView)
    }
    private fun showPopup() {
        if (showingPopup) return
        showingPopup = true
        animateViewIn(popupView)
    }
    private fun hidePopup() {
        if (!showingPopup) return
        showingPopup = false
        animateViewOut(popupView)
    }
    private fun animateViewIn(view: View) {
        view.animate()
            .alpha(1f)
            .setDuration(200L)
            .start()
    }
    private fun animateViewOut(view: View) {
        view.animate()
            .alpha(0f)
            .setDuration(100L)
            .start()
    }
    // --- LAYOUT STATE ---
    private val thumbOffsetRange: Int
        get() {
            return height - thumbPadding.top - thumbPadding.bottom - thumbHeight
        }

    private val scrollRange: Int
        get() {
            val itemCount = itemCount
            if (itemCount == 0) return 0
            val itemHeight = itemHeight
            return if (itemHeight != 0) {
                paddingTop + itemCount * itemHeight + paddingBottom
            } else {
                0
            }
        }

    private val scrollOffsetRange: Int
        get() = scrollRange - height

    private val itemHeight: Int
        get() {
            if (childCount == 0) return 0
            val itemView = getChildAt(0)
            getDecoratedBoundsWithMargins(itemView, tRect)
            return tRect.height()
        }

    private val itemCount: Int
        get() =
            when (val mgr = layoutManager) {
                is GridLayoutManager -> (mgr.itemCount - 1) / mgr.spanCount + 1
                is LinearLayoutManager -> mgr.itemCount
                else -> 0
            }

    interface PopupProvider {
        fun getPopup(pos: Int): String?
    }
    interface Listener {
        fun onFastScrollingChanged(isFastScrolling: Boolean)
    }
    private companion object {
        const val AUTO_HIDE_SCROLLBAR_DELAY_MILLIS = 1500
    }
}