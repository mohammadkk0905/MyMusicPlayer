package com.mohammadkk.mymusicplayer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.extensions.sendIntent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (val action = intent?.action) {
            Constant.PREVIOUS, Constant.PLAY_PAUSE,
            Constant.NEXT, Constant.FINISH, Constant.DISMISS -> {
                MusicService.isGlobalPlayAnim = true
                context?.sendIntent(action)
            }
        }
    }
}