package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.text.Spanned
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.databinding.DialogSongDetailsBinding
import com.mohammadkk.mymusicplayer.extensions.toFormattedDate
import com.mohammadkk.mymusicplayer.extensions.toFormattedDuration
import com.mohammadkk.mymusicplayer.models.Song
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

class SongDetailDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogSongDetailsBinding.inflate(layoutInflater)
        val song = findSongFromArguments()
        if (song != null) {
            val songFile = File(song.data)
            if (songFile.exists()) {
                binding.fileName.text = makeTextWithTitle(R.string.label_file_name, songFile.name)
                binding.filePath.text = makeTextWithTitle(R.string.label_file_path, songFile.absolutePath)
                binding.dateModified.text = makeTextWithTitle(
                    R.string.date_modified, songFile.lastModified().toFormattedDate(false)
                )
                binding.fileSize.text = makeTextWithTitle(
                    R.string.label_file_size, getFileSizeString(songFile.length())
                )
                binding.trackLength.text = makeTextWithTitle(
                    R.string.duration, getDuration(song).toFormattedDuration(true)
                )
                getFormat(song).let {
                    if (it != null) {
                        binding.fileFormat.text = makeTextWithTitle(R.string.label_file_format, it)
                        binding.fileFormat.visibility = View.VISIBLE
                    } else {
                        binding.fileFormat.visibility = View.GONE
                    }
                }
                val bitrate = getBitrate(song)
                if (bitrate != 0) {
                    binding.bitrate.text = makeTextWithTitle(R.string.label_bitrate, "$bitrate kb/s")
                    binding.bitrate.visibility = View.VISIBLE
                } else {
                    binding.bitrate.visibility = View.GONE
                }
                val simpleRate = getSampleRate(song)
                if (simpleRate != 0) {
                    binding.samplingRate.text = makeTextWithTitle(R.string.label_sampling_rate, "$simpleRate Hz")
                    binding.samplingRate.visibility = View.VISIBLE
                } else {
                    binding.samplingRate.visibility = View.GONE
                }
            } else {
                binding.fileName.text = makeTextWithTitle(R.string.label_file_name, song.title)
                binding.trackLength.text = makeTextWithTitle(
                    R.string.duration, song.duration.toFormattedDuration(true)
                )
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_details)
            .setPositiveButton(android.R.string.ok, null)
            .setView(binding.root)
            .create()
    }
    private fun findSongFromArguments(): Song? {
        val bundle = arguments ?: return null
        return BundleCompat.getParcelable(bundle, "song_extra", Song::class.java)
    }
    private fun getFormat(song: Song): String? {
        val audioHeader = runCatching { AudioFileIO.read(File(song.data)).audioHeader }.getOrNull()
        return if (audioHeader != null) {
            audioHeader.format
        } else {
            val extension = MimeTypeMap.getFileExtensionFromUrl(song.data)
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            when (mimeType.orEmpty()) {
                "audio/mpeg",
                "audio/mp3" -> "MPEG-1 audio"
                "audio/mp4",
                "audio/mp4a-latm",
                "audio/mpeg4-generic" -> "MPEG-4 audio"
                "audio/aac",
                "audio/aacp",
                "audio/3gpp",
                "audio/3gpp2" -> "Advanced Audio Coding (AAC)"
                "audio/ogg",
                "application/ogg",
                "application/x-ogg" -> "Ogg audio"
                "audio/flac" -> "Free Lossless Audio Codec (FLAC)"
                "audio/wav",
                "audio/x-wav",
                "audio/wave",
                "audio/vnd.wave" -> "Microsoft WAVE"
                "audio/x-matroska" -> "Matroska audio"
                else -> null
            }
        }
    }
    private fun getDuration(song: Song): Long {
        if (song.duration <= 0) {
            val mmr = MediaMetadataRetriever()
            return try {
                mmr.setDataSource(song.data)
                val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                mmr.release()
                duration?.toLongOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        }
        return song.duration
    }
    private fun getBitrate(song: Song): Int {
        val mediaExtractor = MediaExtractor()
        var bitrate = 0
        try {
            mediaExtractor.setDataSource(song.data)
            mediaExtractor.getTrackFormat(0)
            val mediaFormat = mediaExtractor.getTrackFormat(0)
            bitrate = mediaFormat.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
            mediaExtractor.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (bitrate == 0) {
            val mmr = MediaMetadataRetriever()
            bitrate =  try {
                mmr.setDataSource(song.data)
                val bits = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
                mmr.release()
                if (bits != null) bits / 1000 else 0
            } catch (e: Exception) {
                0
            }
        }
        return bitrate
    }
    private fun getSampleRate(song: Song): Int {
        val mediaExtractor = MediaExtractor()
        var sampleRate = 0
        try {
            mediaExtractor.setDataSource(song.data)
            mediaExtractor.getTrackFormat(0)
            val mediaFormat = mediaExtractor.getTrackFormat(0)
            sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            mediaExtractor.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (sampleRate == 0 && Constant.isSPlus()) {
            val mmr = MediaMetadataRetriever()
            sampleRate =  try {
                mmr.setDataSource(song.data)
                val bits = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                mmr.release()
                bits?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
        }
        return sampleRate
    }
    private fun makeTextWithTitle(titleResId: Int, text: String?): Spanned {
        return ("<b>" + getString(titleResId) + ": " + "</b>" + text).parseAsHtml()
    }
    private fun getFileSizeString(size: Long): String {
        if (size <= 0) return "$size B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        val df = DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ENGLISH))
        return df.format(size / 1024.0.pow(digitGroups.toDouble())) + " " + units[digitGroups]
    }
    companion object {
        fun show(song: Song, manager: FragmentManager) = SongDetailDialog().run {
            arguments = bundleOf("song_extra" to song)
            show(manager, "SONG_DETAILS")
        }
    }
}