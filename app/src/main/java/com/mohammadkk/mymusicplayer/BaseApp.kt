package com.mohammadkk.mymusicplayer

import android.app.Application

class BaseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BaseSettings.initialize(this)
    }
}