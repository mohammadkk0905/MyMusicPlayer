package com.mohammadkk.mymusicplayer.ui.drawables;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.mohammadkk.mymusicplayer.R;

public class PopupBackground extends Drawable {
    private final int mPaddingEnd;
    private final int mPaddingStart;
    @NonNull private final Paint mPaint;
    @NonNull private final Path mPath = new Path();
    @NonNull private final Matrix mTempMatrix = new Matrix();

    public PopupBackground(@NonNull Context context) {
        Resources resources = context.getResources();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setColor(ContextCompat.getColor(context, R.color.cyan_300));
        mPaint.setStyle(Paint.Style.FILL);
        mPaddingStart = resources.getDimensionPixelOffset(me.zhanghai.android.fastscroll.R.dimen.afs_md2_popup_padding_start);
        mPaddingEnd = resources.getDimensionPixelOffset(me.zhanghai.android.fastscroll.R.dimen.afs_md2_popup_padding_end);
    }
    private static void pathArcTo(
            @NonNull Path path,
            float centerX,
            float centerY,
            float radius,
            float startAngle,
            float sweepAngle) {
        path.arcTo(
                centerX - radius,
                centerY - radius,
                centerX + radius,
                centerY + radius,
                startAngle,
                sweepAngle,
                false);
    }
    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawPath(mPath, mPaint);
    }
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
    @Override
    public void getOutline(@NonNull Outline outline) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            outline.setPath(mPath);
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            outline.setConvexPath(mPath);
        } else {
            if (!mPath.isConvex()) {
                super.getOutline(outline);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {}

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        if (needMirroring()) {
            padding.set(mPaddingEnd, 0, mPaddingStart, 0);
        } else {
            padding.set(mPaddingStart, 0, mPaddingEnd, 0);
        }
        return true;
    }

    @Override
    public boolean isAutoMirrored() {
        return true;
    }

    @Override
    public boolean onLayoutDirectionChanged(int layoutDirection) {
        updatePath();
        return true;
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        updatePath();
    }

    private boolean needMirroring() {
        return DrawableCompat.getLayoutDirection(this) == View.LAYOUT_DIRECTION_RTL;
    }

    private void updatePath() {
        mPath.reset();

        Rect bounds = getBounds();
        float width = bounds.width();
        float height = bounds.height();
        float r = height / 2;
        float sqrt2 = (float) Math.sqrt(2);
        // Ensure we are convex.
        width = Math.max(r + sqrt2 * r, width);
        pathArcTo(mPath, r, r, r, 90, 180);
        float o1X = width - sqrt2 * r;
        pathArcTo(mPath, o1X, r, r, -90, 45f);
        float r2 = r / 5;
        float o2X = width - sqrt2 * r2;
        pathArcTo(mPath, o2X, r, r2, -45, 90);
        pathArcTo(mPath, o1X, r, r, 45f, 45f);
        mPath.close();

        if (needMirroring()) {
            mTempMatrix.setScale(-1, 1, width / 2, 0);
        } else {
            mTempMatrix.reset();
        }
        mTempMatrix.postTranslate(bounds.left, bounds.top);
        mPath.transform(mTempMatrix);
    }
}