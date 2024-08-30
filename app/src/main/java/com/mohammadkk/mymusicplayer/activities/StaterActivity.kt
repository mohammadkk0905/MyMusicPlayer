package com.mohammadkk.mymusicplayer.activities

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.mohammadkk.mymusicplayer.databinding.ActivityStaterBinding
import com.mohammadkk.mymusicplayer.utils.ThemeManager

class StaterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStaterBinding
    private val callbackAnim = object : MotionLayout.TransitionListener {
        override fun onTransitionStarted(motionLayout: MotionLayout?, startId: Int, endId: Int) {
            binding.imageStarter.background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.RIGHT_LEFT
                colors = intArrayOf(Color.parseColor("#4AC9DA"), Color.parseColor("#1A88DF"))
                shape = GradientDrawable.OVAL
            }
        }
        override fun onTransitionChange(motionLayout: MotionLayout?, startId: Int, endId: Int, progress: Float) {
        }
        override fun onTransitionCompleted(motionLayout: MotionLayout?, currentId: Int) {
            migrated()
            startApp()
        }
        override fun onTransitionTrigger(motionLayout: MotionLayout?, triggerId: Int, positive: Boolean, progress: Float) {

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.build(this)
        onBackPressedDispatcher.addCallback(this, true) {
        }
        binding = ActivityStaterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.addTransitionListener(callbackAnim)
    }
    private fun startApp() {
        val intent = Intent(this, MainActivity::class.java)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this, android.R.anim.fade_in, android.R.anim.fade_out
        ).toBundle()
        startActivity(intent, options)
        finishAfterTransition()
    }
    private fun migrated() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        if (prefs.contains("last_state_mode")) {
            prefs.edit { remove("last_state_mode") }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        binding.root.removeTransitionListener(callbackAnim)
    }
}