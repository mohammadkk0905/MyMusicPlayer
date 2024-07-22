package com.mohammadkk.mymusicplayer.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.extensions.sendIntent

class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.sendIntent(Constant.DISMISS)
    }
}