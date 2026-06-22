package com.glyph.torch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager

class MainActivity : AppCompatActivity() {

    private var glyphManager: GlyphManager? = null
    private var isServiceReady = false
    private var isTorchOn = false

    private lateinit var track: FrameLayout
    private lateinit var thumb: FrameLayout
    private lateinit var customToast: TextView

    private val callback = object : GlyphManager.Callback {
        override fun onServiceConnected(componentName: ComponentName) {
            try {
                val gm = glyphManager ?: return

                val registered = when {
                    Common.is23113() -> gm.register(Glyph.DEVICE_23113)
                    Common.is23111() -> gm.register(Glyph.DEVICE_23111)
                    Common.is24111() -> gm.register(Glyph.DEVICE_24111)
                    Common.is22111() -> gm.register(Glyph.DEVICE_22111)
                    Common.is20111() -> gm.register(Glyph.DEVICE_20111)
                    else -> gm.register()
                }

                if (!registered) {
                    showCustomToast("Register failed")
                    return
                }

                gm.openSession()
                isServiceReady = true

                try { gm.turnOff() } catch (_: Exception) {}
                applyTorchState(false)
            } catch (e: GlyphException) {
                Log.e(TAG, "Glyph session error: ${e.message}")
                showCustomToast("Session error")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isServiceReady = false
            try {
                glyphManager?.closeSession()
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        track = findViewById(R.id.bigToggleTrack)
        thumb = findViewById(R.id.bigToggleThumb)
        customToast = findViewById(R.id.customToast)
        
        // Ensure UI matches initial state (OFF)
        updateToggleVisuals(animate = false)

        track.setOnClickListener {
            isTorchOn = !isTorchOn
            updateToggleVisuals(animate = true)
            applyTorchState(showToast = true)
        }

        glyphManager = GlyphManager.getInstance(applicationContext)
        glyphManager?.init(callback)
    }

    override fun onDestroy() {
        try {
            glyphManager?.turnOff()
        } catch (_: Exception) {}
        try {
            glyphManager?.closeSession()
        } catch (_: Exception) {}
        glyphManager?.unInit()
        super.onDestroy()
    }

    private fun updateToggleVisuals(animate: Boolean) {
        // Calculate dimensions manually so it scales perfectly across all screen densities
        val density = resources.displayMetrics.density
        val trackWidthPx = 256f * density
        val thumbWidthPx = 104f * density
        val marginPx = 12f * density
        
        // Translation X target: slide to right side of track if ON
        val targetTranslationX = if (isTorchOn) {
            trackWidthPx - thumbWidthPx - (marginPx * 2)
        } else {
            0f
        }

        if (animate) {
            thumb.animate()
                .translationX(targetTranslationX)
                .setDuration(250)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            thumb.translationX = targetTranslationX
        }

        // Snap colors immediately to avoid cross-fade clipping artifacts
        if (isTorchOn) {
            track.setBackgroundResource(R.drawable.toggle_track_on)
            thumb.setBackgroundResource(R.drawable.toggle_thumb_on)
        } else {
            track.setBackgroundResource(R.drawable.toggle_track_off)
            thumb.setBackgroundResource(R.drawable.toggle_thumb_off)
        }
    }

    private fun applyTorchState(showToast: Boolean) {
        if (!isServiceReady) return
        val gm = glyphManager ?: return

        try {
            gm.turnOff()
            if (isTorchOn) {
                val frame = gm.glyphFrameBuilder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .build()

                gm.toggle(frame)
                
                if (showToast) showCustomToast("Glyph ON")
            } else {
                if (showToast) showCustomToast("Glyph OFF")
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyTorchState error: ${e.message}")
        }
    }

    private fun showCustomToast(message: String) {
        runOnUiThread {
            customToast.text = message
            
            // Cancel any ongoing animations so clicking rapidly doesn't queue delays
            customToast.animate().cancel()
            customToast.alpha = 1f // Snap visible immediately
            
            // Wait 1 second (1000ms), then fade out very quickly
            customToast.animate()
                .alpha(0f)
                .setStartDelay(1000)
                .setDuration(150)
                .setListener(null)
                .start()
        }
    }

    companion object {
        private const val TAG = "GlyphTorch"
    }
}
