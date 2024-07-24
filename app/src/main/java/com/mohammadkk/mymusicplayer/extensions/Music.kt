package com.mohammadkk.mymusicplayer.extensions

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.image.AudioFileCover
import com.mohammadkk.mymusicplayer.models.Song
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Song.toContentUri(): Uri {
    if (!path.startsWith("content://")) {
        return ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
        )
    }
    return path.toUri()
}
fun Long.toAlbumArtURI(): Uri {
    return ContentUris.withAppendedId(
        "content://media/external/audio/albumart".toUri(), this
    )
}
fun Int.toFormattedDuration(isSeekBar: Boolean): String {
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
fun Int.toFormattedDate(): String {
    return try {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val mDate = Date(this * 1000L)
        sdf.format(mDate)
    } catch (e: Exception) {
        ""
    }
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
    return if (isOTGMode() || path.startsWith("content://")) {
        Uri.parse(path)
    } else {
        try {
            val file = File(path)
            if (Constant.isNougatPlus()) {
                val applicationId = context.packageName ?: context.applicationContext.packageName
                FileProvider.getUriForFile(context, "$applicationId.provider", file)
            } else file.toUri()
        } catch (e: IllegalArgumentException) {
            toContentUri()
        }
    }
}
fun Song.toCoverArt(mode: Int): Any? {
    return when (mode) {
        Constant.COVER_OFF -> return null
        Constant.COVER_MEDIA_STORE -> if (!isOTGMode()) {
            albumId.toAlbumArtURI()
        } else {
            AudioFileCover(path, albumId)
        }
        else -> AudioFileCover(if (!isOTGMode()) path else toContentUri().toString(), albumId)
    }
}
fun Song.getTrackArt(context: Context): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(context, toContentUri())
        val art = mmr.embeddedPicture
        mmr.release()
        if (art == null) return null
        BitmapFactory.decodeByteArray(art, 0, art.size, BitmapFactory.Options())
    } catch (e: Exception) {
        null
    }
}
fun Song.getAlbumArt(context: Context): Bitmap? {
    return try {
        val fd = context.contentResolver.openFileDescriptor(albumId.toAlbumArtURI(), "r") ?: return null
        val bitmap = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
        fd.close()
        bitmap
    } catch (e: Exception) {
        null
    }
}