package com.mohammadkk.mymusicplayer.ui.fastscroll;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.mohammadkk.mymusicplayer.R;
import com.mohammadkk.mymusicplayer.extensions.ContextKt;
import com.mohammadkk.mymusicplayer.extensions.ViewKt;

public class FastScrollerBuilder {

    @NonNull
    private final ViewGroup mView;

    @Nullable
    private FastScroller.ViewHelper mViewHelper;

    @Nullable
    private FastScroller.PopupTextProvider mPopupTextProvider;

    @Nullable
    private FastScroller.Listener mFastScrollListener;

    @Nullable
    private Rect mPadding;

    @NonNull
    private Drawable mTrackDrawable;

    @NonNull
    private Drawable mThumbDrawable;

    @Nullable
    private FastScroller.AnimationHelper mAnimationHelper;

    public FastScrollerBuilder(@NonNull ViewGroup view) {
        mView = view;
        Context context = mView.getContext();
        mTrackDrawable = ViewKt.createTintedDrawable(
                ContextKt.getDrawableCompat(context, R.drawable.ui_scroll_track),
                ContextKt.getAttrColorCompat(context, android.R.attr.colorControlNormal)
        );
        mThumbDrawable = ContextKt.getDrawableCompat(context, R.drawable.ui_scroll_thumb);
    }

    @NonNull
    public FastScrollerBuilder setViewHelper(@Nullable FastScroller.ViewHelper viewHelper) {
        mViewHelper = viewHelper;
        return this;
    }

    @NonNull
    public FastScrollerBuilder setPopupTextProvider(@Nullable FastScroller.PopupTextProvider popupTextProvider) {
        mPopupTextProvider = popupTextProvider;
        return this;
    }

    @NonNull
    public FastScrollerBuilder setFastScrollListener(@Nullable FastScroller.Listener mFastScrollListener) {
        this.mFastScrollListener = mFastScrollListener;
        return this;
    }

    @NonNull
    public FastScrollerBuilder setPadding(int left, int top, int right, int bottom) {
        if (mPadding == null) mPadding = new Rect();
        mPadding.set(left, top, right, bottom);
        return this;
    }

    @NonNull
    public FastScrollerBuilder setPadding(@Nullable Rect padding) {
        if (padding != null) {
            if (mPadding == null) {
                mPadding = new Rect();
            }
            mPadding.set(padding);
        } else {
            mPadding = null;
        }
        return this;
    }

    @NonNull
    public FastScrollerBuilder setTrackDrawable(@NonNull Drawable trackDrawable) {
        mTrackDrawable = trackDrawable;
        return this;
    }

    @NonNull
    public FastScrollerBuilder setThumbDrawable(@NonNull Drawable thumbDrawable) {
        mThumbDrawable = thumbDrawable;
        return this;
    }


    public void setAnimationHelper(@Nullable FastScroller.AnimationHelper animationHelper) {
        mAnimationHelper = animationHelper;
    }

    public void disableScrollbarAutoHide() {
        DefaultAnimationHelper animationHelper = new DefaultAnimationHelper(mView);
        animationHelper.setScrollbarAutoHideEnabled(false);
        mAnimationHelper = animationHelper;
    }

    @NonNull
    public FastScroller build() {
        final FastScroller scroller = new FastScroller(mView, getOrCreateViewHelper(), mPadding, mTrackDrawable,
                mThumbDrawable, getOrCreateAnimationHelper());
        scroller.setListener(mFastScrollListener);
        return scroller;
    }

    @NonNull
    private FastScroller.ViewHelper getOrCreateViewHelper() {
        if (mViewHelper != null) return mViewHelper;
        if (mView instanceof FastScroller.ViewHelper) {
            return ((FastScroller.ViewHelper) mView);
        } else if (mView instanceof RecyclerView) {
            return new RecyclerViewHelper((RecyclerView) mView, mPopupTextProvider);
        } else {
            throw new UnsupportedOperationException(mView.getClass().getSimpleName()
                    + " is not supported for fast scroll");
        }
    }

    @NonNull
    private FastScroller.AnimationHelper getOrCreateAnimationHelper() {
        if (mAnimationHelper != null) return mAnimationHelper;
        return new DefaultAnimationHelper(mView);
    }
}
