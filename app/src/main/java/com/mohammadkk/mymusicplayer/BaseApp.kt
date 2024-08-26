package com.mohammadkk.mymusicplayer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mohammadkk.mymusicplayer.extensions.getDefaultNightMode

class BaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = BaseSettings.initialize(this)
        AppCompatDelegate.setDefaultNightMode(prefs.themeUI.getDefaultNightMode())
    }
}