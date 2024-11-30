package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.os.bundleOf
import androidx.core.text.parseAsHtml
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.Constant
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.databinding.DialogRecyclerBinding
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
        val binding = DialogRecyclerBinding.inflate(layoutInflater)
        binding.btnNegative.visibility = View.GONE
        binding.btnPositive.setText(android.R.string.ok)
        binding.btnPositive.setOnClickListener { dismiss() }
        val dataset = ArrayList<Spanned>()
        val song = findSongFromArguments()
        if (song != null) {
            val songFile = File(song.data)
            if (songFile.exists()) {
                dataset.add(makeTextWithTitle(R.string.label_file_name, songFile.name))
                dataset.add(makeTextWithTitle(R.string.label_file_path, songFile.absolutePath))
                dataset.add(
                    makeTextWithTitle(
                        R.string.date_modified, songFile.lastModified().toFormattedDate(false)
                    )
                )
                dataset.add(
                    makeTextWithTitle(
                        R.string.label_file_size, getFileSizeString(songFile.length())
                    )
                )
                dataset.add(
                    makeTextWithTitle(
                        R.string.duration, getDuration(song).toFormattedDuration(true)
                    )
                )
                dataset.add(makeTextWithTitle(R.string.label_file_format, getFormat(songFile)))
                val bitrate = getBitrate(song)
                if (bitrate != 0) {
                    dataset.add(makeTextWithTitle(R.string.label_bitrate, "$bitrate kb/s"))
                }
                val simpleRate = getSampleRate(song)
                if (simpleRate != 0) {
                    dataset.add(makeTextWithTitle(R.string.label_sampling_rate, "$simpleRate Hz"))
                }
            } else {
                dataset.add(makeTextWithTitle(R.string.label_file_name, song.title))
                dataset.add(
                    makeTextWithTitle(
                        R.string.duration, song.duration.toFormattedDuration(true)
                    )
                )
            }
        }
        binding.dialogRecycler.adapter = DetailsAdapter(dataset)
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.action_details)
            .setView(binding.root)
            .create()
    }
    private fun findSongFromArguments(): Song? {
        val bundle = arguments ?: return null
        return BundleCompat.getParcelable(bundle, "song_extra", Song::class.java)
    }
    private fun getFormat(sonFile: File): String {
        val audioHeader = runCatching { AudioFileIO.read(sonFile).audioHeader }.getOrNull()
        return if (audioHeader != null) audioHeader.format else sonFile.extension.uppercase()
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
    private class DetailsAdapter(private val dataset: List<Spanned>) : RecyclerView.Adapter<DetailsAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val mContent = parent.context
            val spacing = mContent.resources.getDimensionPixelSize(R.dimen.spacing_medium)
            return ViewHolder(TextView(mContent).apply {
                id = android.R.id.title
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(spacing, spacing, spacing, 0)
                TextViewCompat.setTextAppearance(this, R.style.TextColorPrimaryAppearance)
            })
        }
        override fun getItemCount(): Int {
            return dataset.size
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindItems(dataset[position])
        }
        class ViewHolder(itemView: TextView) : RecyclerView.ViewHolder(itemView) {
            val textView = itemView

            fun bindItems(text: Spanned) {
                textView.text = text
            }
        }
    }
    companion object {
        fun show(song: Song, manager: FragmentManager) = SongDetailDialog().run {
            arguments = bundleOf("song_extra" to song)
            show(manager, "SONG_DETAILS")
        }
    }
}