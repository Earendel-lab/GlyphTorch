package com.glyph.torch

import android.os.Bundle
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

class MainActivity : AppCompatActivity(), GlyphHelper.Listener {

    private lateinit var track: FrameLayout
    private lateinit var thumb: FrameLayout
    private lateinit var customToast: TextView

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
        
        GlyphHelper.initIfNeeded(this)

        track.setOnClickListener {
            GlyphHelper.toggle(this)
        }
    }

    override fun onResume() {
        super.onResume()
        GlyphHelper.addListener(this)
        updateToggleVisuals(animate = false)
    }

    override fun onPause() {
        super.onPause()
        GlyphHelper.removeListener(this)
    }

    override fun onTorchStateChanged(isOn: Boolean) {
        runOnUiThread {
            updateToggleVisuals(animate = true)
            showCustomToast(if (isOn) "Glyph ON" else "Glyph OFF")
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            showCustomToast(message)
        }
    }

    private fun updateToggleVisuals(animate: Boolean) {
        val density = resources.displayMetrics.density
        val trackWidthPx = 256f * density
        val thumbWidthPx = 104f * density
        val marginPx = 12f * density
        
        val isTorchOn = GlyphHelper.isGlyphOn
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

        if (isTorchOn) {
            track.setBackgroundResource(R.drawable.toggle_track_on)
            thumb.setBackgroundResource(R.drawable.toggle_thumb_on)
        } else {
            track.setBackgroundResource(R.drawable.toggle_track_off)
            thumb.setBackgroundResource(R.drawable.toggle_thumb_off)
        }
    }

    private fun showCustomToast(message: String) {
        customToast.text = message
        customToast.animate().cancel()
        customToast.alpha = 1f
        customToast.animate()
            .alpha(0f)
            .setStartDelay(1000)
            .setDuration(150)
            .setListener(null)
            .start()
    }
}
