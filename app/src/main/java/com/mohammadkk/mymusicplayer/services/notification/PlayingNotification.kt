package com.mohammadkk.mymusicplayer.services.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.models.Song

/**
 * @see NOTIFICATION_CONTROLS_SIZE_MULTIPLIER
 */
abstract class PlayingNotification(context: Context) : NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID) {
    abstract fun updateMetadata(song: Song, onUpdate: () -> Unit)

    abstract fun setPlaying(isPlaying: Boolean)

    companion object {
        const val NOTIFICATION_CONTROLS_SIZE_MULTIPLIER = 1.0f
        internal const val NOTIFICATION_CHANNEL_ID = "playing_notification"
        const val NOTIFICATION_ID = 1


        @RequiresApi(26)
        fun createNotificationChannel(
            context: Context,
            notificationManager: NotificationManager
        ) {
            var nChannel: NotificationChannel? = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            if (nChannel == null) {
                nChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.playing_notification_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                nChannel.description = context.getString(R.string.playing_notification_description)
                nChannel.enableLights(false)
                nChannel.enableVibration(false)
                nChannel.setShowBadge(false)
                notificationManager.createNotificationChannel(nChannel)
            }
        }
    }
}