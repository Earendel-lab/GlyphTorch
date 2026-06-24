package com.glyph.torch

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * A transparent activity launched via long-press on the Quick Settings tile.
 * Overrides the default "App Info" behavior using the ACTION_QS_TILE_PREFERENCES intent filter.
 */
class SosTriggerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Activate SOS mode (Glyph + Flashlight)
        GlyphHelper.startSOS(applicationContext)
        
        // Close immediately so the user remains in the QS panel
        finish()
    }
}
