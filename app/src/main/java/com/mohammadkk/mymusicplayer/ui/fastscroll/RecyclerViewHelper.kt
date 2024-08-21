package com.mohammadkk.mymusicplayer.ui.fastscroll

import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.SimpleOnItemTouchListener
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller.PopupTextProvider
import com.mohammadkk.mymusicplayer.ui.fastscroll.FastScroller.ViewHelper
import kotlin.math.max

internal class RecyclerViewHelper(
    private val mView: RecyclerView,
    private val mPopupTextProvider: PopupTextProvider?
) : ViewHelper {
    private val mTempRect = Rect()

    override fun addOnPreDrawListener(onPreDraw: Runnable) {
        mView.addItemDecoration(object : ItemDecoration() {
            override fun onDraw(
                canvas: Canvas, parent: RecyclerView,
                state: RecyclerView.State
            ) {
                onPreDraw.run()
            }
        })
    }

    override fun addOnScrollChangedListener(onScrollChanged: Runnable) {
        mView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onScrollChanged.run()
            }
        })
    }

    override fun addOnTouchEventListener(onTouchEvent: (MotionEvent) -> Boolean) {
        mView.addOnItemTouchListener(object : SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(
                recyclerView: RecyclerView,
                event: MotionEvent
            ): Boolean {
                return onTouchEvent.invoke(event)
            }

            override fun onTouchEvent(
                recyclerView: RecyclerView,
                event: MotionEvent
            ) {
                onTouchEvent.invoke(event)
            }
        })
    }

    override val scrollRange: Int
        get() {
            val itemCount = itemCount
            if (itemCount == 0) {
                return 0
            }
            val itemHeight = itemHeight
            if (itemHeight == 0) {
                return 0
            }
            return mView.paddingTop + itemCount * itemHeight + mView.paddingBottom
        }

    override val scrollOffset: Int
        get() {
            val firstItemPosition = firstItemPosition
            if (firstItemPosition == RecyclerView.NO_POSITION) {
                return 0
            }
            val itemHeight = itemHeight
            val firstItemTop = firstItemOffset
            return mView.paddingTop + firstItemPosition * itemHeight - firstItemTop
        }

    override fun scrollTo(offset: Int) {
        // Stop any scroll in progress for RecyclerView.
        var mOffset = offset
        mView.stopScroll()
        mOffset -= mView.paddingTop
        val itemHeight = itemHeight
        // firstItemPosition should be non-negative even if paddingTop is greater than item height.
        val firstItemPosition = max(0.0, (mOffset / itemHeight).toDouble()).toInt()
        val firstItemTop = firstItemPosition * itemHeight - mOffset
        scrollToPositionWithOffset(firstItemPosition, firstItemTop)
    }

    override val popupText: CharSequence?
        get() {
            var popupTextProvider = mPopupTextProvider
            if (popupTextProvider == null) {
                val adapter = mView.adapter
                if (adapter is PopupTextProvider) {
                    popupTextProvider = adapter
                }
            }
            if (popupTextProvider == null) {
                return null
            }
            val position = firstItemAdapterPosition
            if (position == RecyclerView.NO_POSITION) {
                return null
            }
            return popupTextProvider.getPopupText(mView, position)
        }

    private val itemCount: Int
        get() {
            val linearLayoutManager = verticalLinearLayoutManager ?: return 0

            var itemCount = linearLayoutManager.itemCount
            if (itemCount == 0) return 0

            if (linearLayoutManager is GridLayoutManager) {
                itemCount = (itemCount - 1) / linearLayoutManager.spanCount + 1
            }
            return itemCount
        }

    private val itemHeight: Int
        get() {
            if (mView.childCount == 0) return 0
            val itemView = mView.getChildAt(0)
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect)
            return mTempRect.height()
        }

    private val firstItemPosition: Int
        get() {
            var position = firstItemAdapterPosition
            val linearLayoutManager = verticalLinearLayoutManager ?: return RecyclerView.NO_POSITION
            if (linearLayoutManager is GridLayoutManager) {
                position /= linearLayoutManager.spanCount
            }
            return position
        }

    private val firstItemAdapterPosition: Int
        get() {
            if (mView.childCount == 0) {
                return RecyclerView.NO_POSITION
            }
            val itemView = mView.getChildAt(0)
            val linearLayoutManager = verticalLinearLayoutManager ?: return RecyclerView.NO_POSITION
            return linearLayoutManager.getPosition(itemView)
        }

    private val firstItemOffset: Int
        get() {
            if (mView.childCount == 0) {
                return RecyclerView.NO_POSITION
            }
            val itemView = mView.getChildAt(0)
            mView.getDecoratedBoundsWithMargins(itemView, mTempRect)
            return mTempRect.top
        }

    private fun scrollToPositionWithOffset(position: Int, offset: Int) {
        var mPosition = position
        var mOffset = offset
        val linearLayoutManager = verticalLinearLayoutManager ?: return
        if (linearLayoutManager is GridLayoutManager) {
            mPosition *= linearLayoutManager.spanCount
        }
        // LinearLayoutManager actually takes offset from paddingTop instead of top of RecyclerView.
        mOffset -= mView.paddingTop
        linearLayoutManager.scrollToPositionWithOffset(mPosition, mOffset)
    }

    private val verticalLinearLayoutManager: LinearLayoutManager?
        get() {
            val layoutManager = mView.layoutManager as? LinearLayoutManager ?: return null
            if (layoutManager.orientation != RecyclerView.VERTICAL) {
                return null
            }
            return layoutManager
        }
}
