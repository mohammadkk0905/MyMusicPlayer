package com.mohammadkk.mymusicplayer.services.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.MainActivity
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.toImmutableFlag
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.image.GlideExtensions.getCoverOptions
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.services.MusicService

class PlayingNotificationMaterial(
    val context: MusicService,
    mediaSessionToken: MediaSessionCompat.Token,
) : PlayingNotification(context) {

    init {
        val action = Intent(context, MainActivity::class.java)
        action.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val clickIntent = PendingIntent.getActivity(
            context, 0, action, PendingIntent.FLAG_UPDATE_CURRENT.toImmutableFlag()
        )

        val serviceName = ComponentName(context, MusicService::class.java)
        val intent = Intent(MusicService.ACTION_QUIT)
        intent.component = serviceName
        val deleteIntent = PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT.toImmutableFlag()
        )
        val playPauseAction = buildPlayAction(true)
        val previousAction = NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            context.getString(R.string.previous),
            retrievePlaybackAction(MusicService.ACTION_REWIND)
        )
        val nextAction = NotificationCompat.Action(
            R.drawable.ic_skip_next,
            context.getString(R.string.next),
            retrievePlaybackAction(MusicService.ACTION_SKIP)
        )
        val dismissAction = NotificationCompat.Action(
            R.drawable.ic_close,
            context.getString(R.string.dismiss),
            retrievePlaybackAction(MusicService.ACTION_QUIT)
        )
        setSmallIcon(R.drawable.ic_audiotrack)
        setContentIntent(clickIntent)
        setDeleteIntent(deleteIntent)
        setShowWhen(false)
        addAction(previousAction)
        addAction(playPauseAction)
        addAction(nextAction)
        addAction(dismissAction)

        setStyle(
            MediaStyle()
                .setMediaSession(mediaSessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }
    override fun updateMetadata(song: Song, onUpdate: () -> Unit) {
        if (song == Song.emptySong) return
        setContentTitle(song.title)
        setContentText(song.artist)
        setSubText(song.album)
        val bigImageSize = context.resources.getDimensionPixelSize(R.dimen.notification_big_image_size)
        try {
            Glide.with(context)
                .asBitmap()
                .getCoverOptions(song, context.getDrawableCompat(R.drawable.ic_audiotrack))
                .load(GlideExtensions.getSongModel(song))
                .centerCrop()
                .into(object : CustomTarget<Bitmap>(bigImageSize, bigImageSize) {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        setLargeIcon(resource)
                        onUpdate()
                    }
                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        setLargeIcon(
                            BitmapFactory.decodeResource(context.resources, R.drawable.ic_start_music)
                        )
                        onUpdate()
                    }
                    override fun onLoadCleared(placeholder: Drawable?) {
                        setLargeIcon(
                            BitmapFactory.decodeResource(context.resources, R.drawable.ic_start_music)
                        )
                        onUpdate()
                    }
                })
        } catch (e: IllegalArgumentException) {
            setLargeIcon(
                BitmapFactory.decodeResource(context.resources, R.drawable.ic_start_music)
            )
            onUpdate()
        }
    }
    private fun buildPlayAction(isPlaying: Boolean): NotificationCompat.Action {
        val playButtonResId = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        return NotificationCompat.Action.Builder(
            playButtonResId,
            context.getString(R.string.play_pause),
            retrievePlaybackAction(MusicService.ACTION_TOGGLE_PAUSE)
        ).build()
    }
    @SuppressLint("RestrictedApi")
    override fun setPlaying(isPlaying: Boolean) {
        mActions[1] = buildPlayAction(isPlaying)
    }
    private fun retrievePlaybackAction(action: String): PendingIntent {
        val serviceName = ComponentName(context, MusicService::class.java)
        val intent = Intent(action)
        intent.component = serviceName
        return PendingIntent.getService(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT.toImmutableFlag()
        )
    }
    companion object {
        fun from(
            context: MusicService,
            notificationManager: NotificationManager,
            mediaSession: MediaSessionCompat,
        ): PlayingNotification {
            if (Constant.isOreoPlus()) createNotificationChannel(context, notificationManager)
            return PlayingNotificationMaterial(context, mediaSession.sessionToken)
        }
    }
}