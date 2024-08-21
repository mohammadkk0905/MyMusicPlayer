package com.mohammadkk.mymusicplayer.extensions

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.provider.MediaStore
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat.QueueItem
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.models.Song
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun MediaPlayer.setPlaybackSpeedPitch(speed: Float, pitch: Float) {
    if (Constant.isMarshmallowPlus()) {
        val wasPlaying = isPlaying
        playbackParams = PlaybackParams().setSpeed(speed).setPitch(pitch)
        if (!wasPlaying) pause()
    }
}
fun Song.toContentUri(): Uri {
    if (!isOTGMode()) {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
        )
    }
    return data.toUri()
}
fun Long.toAlbumArtURI(): Uri {
    return ContentUris.withAppendedId(
        "content://media/external/audio/albumart".toUri(), this
    )
}
fun Long.toFormattedDuration(isSeekBar: Boolean): String {
    var minutes = this / 1000 / 60
    val seconds = this / 1000 % 60
    return if (minutes < 60) {
        val defFormat = if (!isSeekBar) "%02dm:%02ds" else "%02d:%02d"
        String.format(Locale.getDefault(), defFormat, minutes, seconds)
    } else {
        val hours = minutes / 60
        minutes %= 60
        if (!isSeekBar) {
            String.format(Locale.getDefault(), "%02dh:%02dm", hours, minutes)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
}
fun Long.toFormattedDate(): String = try {
    val date = Date(this * 1000)
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.getDefault()).format(date)
} catch (e: Exception) {
    ""
}
fun Int.toLocaleYear(): String {
    return if (this > 0) {
        String.format(Locale.getDefault(), "%d", this)
    } else "-"
}
fun Int.toImmutableFlag(): Int {
    var flags = this
    if (Constant.isMarshmallowPlus()) flags = flags or PendingIntent.FLAG_IMMUTABLE
    return flags
}
fun Song.toProviderUri(context: Context): Uri {
    return if (isOTGMode() || data.startsWith("content://")) {
        Uri.parse(data)
    } else {
        try {
            val file = File(data)
            if (Constant.isNougatPlus()) {
                val applicationId = context.packageName ?: context.applicationContext.packageName
                FileProvider.getUriForFile(context, "$applicationId.provider", file)
            } else file.toUri()
        } catch (e: IllegalArgumentException) {
            toContentUri()
        }
    }
}
fun ArrayList<Song>.toMediaSessionQueue(): List<QueueItem> {
    return map { song ->
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artist)
            .setIconUri(song.albumId.toAlbumArtURI())
            .build()
        QueueItem(mediaDescription, song.hashCode().toLong())
    }
}