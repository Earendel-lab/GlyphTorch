package com.glyph.torch

import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager

/**
 * Singleton helper to handle Glyph hardware control directly.
 * Replaces GlyphTorchManager to provide instant toggling.
 */
object GlyphHelper {
    private const val TAG = "GlyphHelper"
    private const val PREFS_NAME = "glyph_prefs"
    private const val KEY_TORCH_STATE = "torch_state"

    private var glyphManager: GlyphManager? = null
    private var isServiceReady = false
    
    var isGlyphOn: Boolean = false
        private set

    private val listeners = mutableListOf<Listener>()

    interface Listener {
        fun onTorchStateChanged(isOn: Boolean)
        fun onError(message: String)
    }

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

                if (registered) {
                    gm.openSession()
                    isServiceReady = true
                    // Apply current state upon connection (in case it was toggled before bind finished)
                    applyHardwareState()
                }
            } catch (e: GlyphException) {
                Log.e(TAG, "Glyph session error: ${e.message}")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isServiceReady = false
        }
    }

    fun initIfNeeded(context: Context) {
        if (glyphManager == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isGlyphOn = prefs.getBoolean(KEY_TORCH_STATE, false)
            
            glyphManager = GlyphManager.getInstance(context.applicationContext)
            glyphManager?.init(callback)
        }
    }

    private fun persistState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TORCH_STATE, isGlyphOn)
            .apply()
    }

    fun toggle(context: Context) {
        isGlyphOn = !isGlyphOn
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()
        
        // Notify System to refresh the tile if it's listening
        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    fun turnOn(context: Context) {
        if (isGlyphOn) return
        isGlyphOn = true
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()

        // Notify System to refresh the tile if it's listening
        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    fun turnOff(context: Context) {
        if (!isGlyphOn) return
        isGlyphOn = false
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()

        // Notify System to refresh the tile if it's listening
        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    private fun applyHardwareState() {
        if (!isServiceReady) return
        val gm = glyphManager ?: return

        try {
            gm.turnOff()
            if (isGlyphOn) {
                val frame = gm.glyphFrameBuilder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .build()
                gm.toggle(frame)
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyHardwareState error: ${e.message}")
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onTorchStateChanged(isGlyphOn)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyStateChanged() {
        listeners.forEach { it.onTorchStateChanged(isGlyphOn) }
    }
}
