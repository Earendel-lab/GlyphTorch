package com.glyph.torch

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick Settings Tile for Glyph Torch.
 * Provides instant toggling using GlyphHelper.
 */
class GlyphTileService : TileService(), GlyphHelper.Listener {

    override fun onStartListening() {
        super.onStartListening()
        GlyphHelper.initIfNeeded(this)
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        tile.state = if (GlyphHelper.isGlyphOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
        
        GlyphHelper.addListener(this)
    }

    override fun onStopListening() {
        super.onStopListening()
        GlyphHelper.removeListener(this)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        try {
            GlyphHelper.toggle(applicationContext)
            // State will be updated via onTorchStateChanged listener callback, 
            // but we update here too for immediate local response.
            tile.state = if (GlyphHelper.isGlyphOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        } catch (e: Exception) {
            tile.state = Tile.STATE_INACTIVE
        }

        tile.updateTile()
    }

    override fun onTorchStateChanged(isOn: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onError(message: String) {
        // Handle error if needed
    }
}
