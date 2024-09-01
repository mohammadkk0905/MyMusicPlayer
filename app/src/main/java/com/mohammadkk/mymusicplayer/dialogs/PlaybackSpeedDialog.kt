package com.mohammadkk.mymusicplayer.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mohammadkk.mymusicplayer.BaseSettings
import com.mohammadkk.mymusicplayer.R
import com.mohammadkk.mymusicplayer.databinding.DialogPlaybackSpeedBinding
import java.util.Locale

class PlaybackSpeedDialog : DialogFragment() {
    private val settings = BaseSettings.getInstance()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogPlaybackSpeedBinding.inflate(layoutInflater)
        binding.playbackSpeedSlider.addOnChangeListener { _, value, _ ->
            binding.playbackSpeedValue.text = String.format(Locale.getDefault(), "%.2f", value)
        }
        binding.playbackPitchSlider.addOnChangeListener{ _, value, _ ->
            binding.playbackPitchValue.text = String.format(Locale.getDefault(), "%.2f", value)
        }
        binding.playbackSpeedSlider.value = settings.playbackSpeed
        binding.playbackPitchSlider.value = settings.playbackPitch
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.playback_speed)
            .setView(binding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                updatePlaybackAndPitch(
                    binding.playbackSpeedSlider.value,
                    binding.playbackPitchSlider.value
                )
            }
            .setNeutralButton(R.string.reset_action) {_, _ ->
                updatePlaybackAndPitch(
                    1F, 1F
                )
            }
            .create()
    }
    private fun updatePlaybackAndPitch(speed: Float, pitch: Float) {
        settings.playbackSpeed = speed
        settings.playbackPitch = pitch
    }
    companion object {
        fun show(manager: FragmentManager) = PlaybackSpeedDialog().run {
            show(manager, "PLAYBACK_SETTINGS")
        }
    }
}