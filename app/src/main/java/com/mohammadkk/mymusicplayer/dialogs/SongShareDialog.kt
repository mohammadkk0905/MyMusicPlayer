package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.activities.base.BaseActivity
import com.mohammadkk.mymusicplayer.extensions.applyColor
import com.mohammadkk.mymusicplayer.extensions.getDrawableCompat
import com.mohammadkk.mymusicplayer.extensions.shareSongIntent
import com.mohammadkk.mymusicplayer.extensions.toAlbumArtURI
import com.mohammadkk.mymusicplayer.extensions.toast
import com.mohammadkk.mymusicplayer.image.GlideExtensions
import com.mohammadkk.mymusicplayer.models.Song
import com.mohammadkk.mymusicplayer.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.io.OutputStream

class SongShareDialog : DialogFragment() {
    private lateinit var baseActivity: BaseActivity
    private var currentSong: Song? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            baseActivity = activity as BaseActivity
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }
        currentSong = arguments?.let {
            BundleCompat.getParcelable(it, "song_extra", Song::class.java)
        }
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val song = currentSong
        if (song == null) {
            dismiss()
            context?.toast(R.string.unknown_error_occurred)
            return super.onCreateDialog(savedInstanceState)
        }
        val listening = getString(R.string.currently_listening_to_x_by_x, song.title, song.artist)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.what_do_you_want_to_share)
            .setItems(
                arrayOf(
                    getString(R.string.audio_file_sharing),
                    "\u201C" + listening + "\u201D",
                    getString(R.string.audio_image_sharing)
                )
            ) { _, which ->
                withAction(which, song, listening)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }
    private fun withAction(which: Int, song: Song, currentlyListening: String) {
        when (which) {
            0 -> activity?.shareSongIntent(song)
            1 -> startActivity(
                Intent.createChooser(
                    Intent()
                        .setAction(Intent.ACTION_SEND)
                        .putExtra(Intent.EXTRA_TEXT, currentlyListening)
                        .setType("text/plain"),
                    null
                )
            )
            2 -> {
                baseActivity.lifecycleScope.launch(Dispatchers.IO) {
                    var bitmap = fetchImageBySong(song)
                    if (bitmap == null) {
                        bitmap = getBitmap(
                            baseActivity.getDrawableCompat(R.drawable.ic_audiotrack).applyColor(
                                GlideExtensions.getColor(baseActivity, song.id)
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        val pathUri = try {
                            saveImage(bitmap)
                        } catch (e: Exception) {
                            null
                        }
                        if (pathUri == null) {
                            baseActivity.toast(R.string.unknown_error_occurred)
                            return@withContext
                        }
                        val feedIntent = Intent(Intent.ACTION_SEND)
                        feedIntent.type = "image/*"
                        feedIntent.putExtra(Intent.EXTRA_STREAM, pathUri)
                        baseActivity.startActivity(feedIntent, null)
                    }
                }
            }
        }
    }
    private fun saveImage(bitmap: Bitmap): Uri? {
        if (Constant.isRPlus()) {
            val filename = "img_${System.currentTimeMillis()}.jpg"
            var fos: OutputStream?
            var imageUri: Uri?
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val result = baseActivity.application.contentResolver.also { resolver ->
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
            fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            result.update(imageUri!!, contentValues, null, null)
            return imageUri
        } else {
            @Suppress("DEPRECATION")
            return MediaStore.Images.Media.insertImage(
                baseActivity.application.contentResolver, bitmap,
                "img_${System.currentTimeMillis()}", null
            )?.toUri()
        }
    }
    private fun fetchImageBySong(song: Song): Bitmap? {
        val model = GlideExtensions.getSongModel(song)
        return if (model == null) {
            null
        } else {
            if (model is Song) {
                getCoverArt(model.data) ?: getFallback(model)
            } else {
                getCoverArt(song.albumId)
            }
        }
    }
    private fun getFallback(model: Song): Bitmap? {
        val audioIO = runCatching { AudioFileIO.read(File(model.data)).tag }.getOrNull()
        val bytes = audioIO?.firstArtwork?.binaryData?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options())
        }
        return bytes ?: getCoverArt(model.albumId)
    }
    private fun getCoverArt(path: String) = MediaMetadataRetriever().run {
        try {
            setDataSource(path)
            val cover = embeddedPicture
            release()
            cover?.let { BitmapFactory.decodeByteArray(it, 0, it.size, BitmapFactory.Options()) }
        } catch (e: Exception) {
            null
        }
    }
    private fun getCoverArt(albumId: Long): Bitmap? {
        val contentResolver = baseActivity.applicationContext.contentResolver
        return try {
            val fd = contentResolver.openFileDescriptor(albumId.toAlbumArtURI(), "r") ?:return null
            val result = BitmapFactory.decodeFileDescriptor(fd.fileDescriptor)
            fd.close()
            result
        } catch (e: Exception) {
            null
        }
    }
    private fun getBitmap(drawable: Drawable): Bitmap {
        val width = drawable.intrinsicWidth * 3
        val height = drawable.intrinsicHeight * 3
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(newBitmap)
        canvas.drawColor(ThemeManager.colorSurfaceContainer)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return newBitmap
    }
    companion object {
        fun show(song: Song, manager: FragmentManager) {
            SongShareDialog().run {
                arguments = bundleOf("song_extra" to song)
                show(manager, "SHARE_SONG")
            }
        }
    }
}