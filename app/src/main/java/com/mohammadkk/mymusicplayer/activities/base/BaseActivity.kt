package com.mohammadkk.mymusicplayer.activities.base

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.utils.ThemeManager

abstract class BaseActivity : AppCompatActivity() {
    protected val settings: BaseSettings get() = BaseSettings.getInstance()
    private var isStatusBarChanged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.build(this)
    }
    override fun onStart() {
        super.onStart()
        if (!isStatusBarChanged) {
            isStatusBarChanged = true
            window.statusBarColor = ThemeManager.darkenColor(ThemeManager.colorPrimary, 0.08f)
        }
    }
}