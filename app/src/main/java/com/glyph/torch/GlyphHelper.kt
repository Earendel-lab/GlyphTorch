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
    
    private var cameraManager: android.hardware.camera2.CameraManager? = null
    private var cameraId: String? = null

    var isGlyphOn: Boolean = false
        private set

    var isSOSMode: Boolean = false
        private set

    private val listeners = mutableListOf<Listener>()
    private var sosHandler: android.os.Handler? = null
    private var sosRunnable: Runnable? = null

    interface Listener {
        fun onTorchStateChanged(isOn: Boolean, isSOS: Boolean)
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
                    if (isSOSMode) {
                        startSOSHardware()
                    } else {
                        applyHardwareState()
                    }
                }
            } catch (e: GlyphException) {
                Log.e(TAG, "Glyph session error: ${e.message}")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            isServiceReady = false
            stopSOS()
        }
    }

    fun initIfNeeded(context: Context) {
        if (glyphManager == null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            isGlyphOn = prefs.getBoolean(KEY_TORCH_STATE, false)
            // SOS mode should probably not persist across app launches for safety
            isSOSMode = false 
            
            glyphManager = GlyphManager.getInstance(context.applicationContext)
            glyphManager?.init(callback)

            initFlashlight(context)
        } else if (!isServiceReady) {
            // If already initialized but not ready (e.g. session closed), try to re-init
            glyphManager?.init(callback)
        }
    }

    private fun initFlashlight(context: Context) {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            cameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                val chars = cameraManager?.getCameraCharacteristics(id)
                chars?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraMetadata.LENS_FACING_BACK
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight init error: ${e.message}")
        }
    }

    private fun setFlashlight(isOn: Boolean) {
        val id = cameraId ?: return
        try {
            cameraManager?.setTorchMode(id, isOn)
        } catch (e: Exception) {
            Log.e(TAG, "setFlashlight error: ${e.message}")
        }
    }

    private fun persistState(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TORCH_STATE, isGlyphOn)
            .apply()
    }

    fun toggle(context: Context) {
        if (isSOSMode) {
            turnOff(context)
            return
        }
        isGlyphOn = !isGlyphOn
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()
        
        if (isGlyphOn) {
            GlyphService.start(context)
        } else {
            GlyphService.stop(context)
        }
        
        // Notify System to refresh the tile if it's listening
        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    fun startSOS(context: Context) {
        if (isSOSMode) return
        isSOSMode = true
        isGlyphOn = true
        initIfNeeded(context)
        persistState(context)
        startSOSHardware()
        notifyStateChanged()
        GlyphService.start(context)

        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    private fun startSOSHardware() {
        stopSOSInternal()

        sosHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // SOS pattern in ms: Dot=150, Dash=450, gap=150, letterGap=450, wordGap=900
        val pattern = listOf(
            150, 150, 150, 150, 150, 450, // S (Dot, gap, Dot, gap, Dot, letterGap)
            450, 150, 450, 150, 450, 450, // O (Dash, gap, Dash, gap, Dash, letterGap)
            150, 150, 150, 150, 150, 900  // S (Dot, gap, Dot, gap, Dot, wordGap)
        )

        var step = 0
        sosRunnable = object : Runnable {
            override fun run() {
                if (!isSOSMode) return
                
                val isOnStep = step % 2 == 0
                
                // Synchronized control of both hardware sources
                setFlashlight(isOnStep)
                applyGlyphSOSState(isOnStep)

                val delay = pattern[step % pattern.size].toLong()
                step++
                sosHandler?.postDelayed(this, delay)
            }
        }
        sosHandler?.post(sosRunnable!!)
    }

    private fun applyGlyphSOSState(isOn: Boolean) {
        if (!isServiceReady) return
        val gm = glyphManager ?: return
        try {
            if (isOn) {
                val frame = gm.glyphFrameBuilder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .build()
                gm.toggle(frame)
            } else {
                gm.turnOff()
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyGlyphSOSState error: ${e.message}")
        }
    }

    private fun stopSOS() {
        isSOSMode = false
        stopSOSInternal()
        setFlashlight(false)
    }

    private fun stopSOSInternal() {
        sosRunnable?.let { sosHandler?.removeCallbacks(it) }
        sosRunnable = null
        sosHandler = null
    }

    fun turnOn(context: Context) {
        if (isGlyphOn && !isSOSMode) return
        stopSOS()
        isGlyphOn = true
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()
        GlyphService.start(context)

        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    fun turnOff(context: Context) {
        if (!isGlyphOn && !isSOSMode) return
        stopSOS()
        isGlyphOn = false
        initIfNeeded(context)
        persistState(context)
        applyHardwareState()
        notifyStateChanged()
        GlyphService.stop(context)

        android.service.quicksettings.TileService.requestListeningState(
            context.applicationContext,
            ComponentName(context, GlyphTileService::class.java)
        )
    }

    private fun applyHardwareState() {
        if (!isServiceReady) return
        val gm = glyphManager ?: return

        try {
            if (isGlyphOn && !isSOSMode) {
                val frame = gm.glyphFrameBuilder
                    .buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .build()
                gm.toggle(frame)
            } else {
                gm.turnOff()
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyHardwareState error: ${e.message}")
        }
    }

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onTorchStateChanged(isGlyphOn, isSOSMode)
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyStateChanged() {
        listeners.forEach { it.onTorchStateChanged(isGlyphOn, isSOSMode) }
    }
}
