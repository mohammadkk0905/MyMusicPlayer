package com.mohammadkk.mymusicplayer.image.palette;

import androidx.annotation.ColorInt;

import com.mohammadkk.mymusicplayer.utils.ThemeManager;

public class PaletteColors {
    private final int backgroundColor;
    private final int primaryTextColor;
    private final int secondaryTextColor;
    private final boolean isFallback;

    public PaletteColors() {
        backgroundColor = -15724528;
        primaryTextColor = -6974059;
        secondaryTextColor = -8684677;
        isFallback = true;
    }
    public PaletteColors(@ColorInt final int color) {
        backgroundColor = color;
        primaryTextColor = ThemeManager.primaryTextColor(!ThemeManager.isColorLight(color));
        secondaryTextColor = ThemeManager.secondaryTextColor(!ThemeManager.isColorLight(color));
        isFallback = false;
    }
    public int getBackgroundColor() {
        return backgroundColor;
    }
    public int getPrimaryTextColor() {
        return primaryTextColor;
    }
    public int getSecondaryTextColor() {
        return secondaryTextColor;
    }
    public boolean isFallback() {
        return isFallback;
    }
}