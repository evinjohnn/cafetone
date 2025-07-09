package com.cafetone.audio.dsp

import android.util.Log

/**
 * Sony Café Mode DSP - Native Audio Processing Interface
 * 
 * This class provides access to the Sony Café Mode audio processing engine
 * implementing the complete effect chain:
 * 1. Distance EQ (Multi-band with Sony frequency response)
 * 2. Rear Positioning (Phase inversion, asymmetric delays, HRTF)
 * 3. Spatial Effects (Width expansion, decorrelation, soundstage)
 * 4. Reverb Engine (Café acoustics, 2.1s decay, 42ms pre-delay)
 * 5. Dynamic Processing (Multi-band compression, soft limiting)
 */
class CafeModeDSP {
    
    companion object {
        private const val TAG = "SonyCafeModeDSP"
        
        // Load native Sony Café Mode DSP library
        init {
            try {
                System.loadLibrary("cafetone-dsp")
                Log.i(TAG, "Sony Café Mode DSP library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load Sony Café Mode DSP library: ${e.message}")
            }
        }
        
        // Sony Café Mode parameter constants
        const val PARAM_INTENSITY = 0      // Master intensity (0.0-1.0)
        const val PARAM_SPATIAL_WIDTH = 1  // Spatial width - up to 170% expansion
        const val PARAM_DISTANCE = 2       // Distance simulation (0.0-1.0)
        
        // Sony Café Mode Effect UUID (matches native implementation)
        const val EFFECT_UUID = "87654321-4321-8765-4321-fedcba098765"
    }
    
    private var isInitialized = false
    private var effectHandle: Long = 0
    
    // Initialize DSP with default parameters
    init {
        val result = init()
        if (result == 0) {
            // Set default Sony Café Mode parameters
            setIntensity(0.7f)
            setSpatialWidth(0.6f)
            setDistance(0.8f)
        }
    }
    
    /**
     * Initialize Sony Café Mode DSP engine
     * @return 0 on success, negative error code on failure
     */
    fun init(): Int {
        return try {
            val result = nativeInit()
            if (result == 0) {
                isInitialized = true
                Log.i(TAG, "Sony Café Mode DSP engine initialized successfully")
            } else {
                Log.e(TAG, "Failed to initialize Sony Café Mode DSP engine: $result")
                // Try fallback initialization
                initializeFallback()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Sony Café Mode DSP initialization", e)
            // Initialize fallback mode
            initializeFallback()
            -1
        }
    }
    
    /**
     * Fallback initialization when native library is not available
     */
    private fun initializeFallback() {
        isInitialized = true
        Log.w(TAG, "Sony Café Mode DSP running in fallback mode (native library not available)")
    }
    
    /**
     * Release Sony Café Mode DSP engine resources
     */
    fun release() {
        if (isInitialized) {
            try {
                nativeRelease()
                isInitialized = false
                effectHandle = 0
                Log.i(TAG, "Sony Café Mode DSP engine released")
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Sony Café Mode DSP release", e)
            }
        }
    }
    
    /**
     * Get Sony Café Mode effect UUID
     */
    fun getEffectUUID(): String = EFFECT_UUID
    
    /**
     * Check if Sony Café Mode DSP is initialized
     */
    fun isInitialized(): Boolean = isInitialized
    
    /**
     * Set master intensity (dry/wet mix)
     * @param intensity 0.0 (bypass) to 1.0 (full effect)
     */
    fun setIntensity(intensity: Float) {
        if (isInitialized) {
            val clampedIntensity = intensity.coerceIn(0.0f, 1.0f)
            nativeSetParameter(PARAM_INTENSITY, clampedIntensity)
            Log.v(TAG, "Sony Café Mode intensity set to: $clampedIntensity")
        }
    }
    
    /**
     * Set spatial width (Sony 170% expansion at maximum)
     * @param width 0.0 (mono) to 1.0 (maximum 170% width)
     */
    fun setSpatialWidth(width: Float) {
        if (isInitialized) {
            val clampedWidth = width.coerceIn(0.0f, 1.0f)
            nativeSetParameter(PARAM_SPATIAL_WIDTH, clampedWidth)
            Log.v(TAG, "Sony Café Mode spatial width set to: $clampedWidth")
        }
    }
    
    /**
     * Set distance simulation (Sony psychoacoustic distance modeling)
     * @param distance 0.0 (close) to 1.0 (distant café speakers)
     */
    fun setDistance(distance: Float) {
        if (isInitialized) {
            val clampedDistance = distance.coerceIn(0.0f, 1.0f)
            nativeSetParameter(PARAM_DISTANCE, clampedDistance)
            Log.v(TAG, "Sony Café Mode distance set to: $clampedDistance")
        }
    }
    
    /**
     * Enable/disable Sony Café Mode processing
     * @param enabled true to enable, false to bypass
     */
    fun setEnabled(enabled: Boolean) {
        if (isInitialized) {
            nativeSetEnabled(enabled)
            Log.i(TAG, "Sony Café Mode DSP ${if (enabled) "enabled" else "disabled"}")
        }
    }
    
    /**
     * Get current intensity value
     */
    fun getIntensity(): Float {
        return if (isInitialized) {
            nativeGetParameter(PARAM_INTENSITY)
        } else 0.0f
    }
    
    /**
     * Get current spatial width value
     */
    fun getSpatialWidth(): Float {
        return if (isInitialized) {
            nativeGetParameter(PARAM_SPATIAL_WIDTH)
        } else 0.0f
    }
    
    /**
     * Get current distance value
     */
    fun getDistance(): Float {
        return if (isInitialized) {
            nativeGetParameter(PARAM_DISTANCE)
        } else 0.0f
    }
    
    /**
     * Get Sony Café Mode DSP status information
     */
    fun getStatusInfo(): String {
        return if (isInitialized) {
            "Sony Café Mode DSP Active\n" +
            "Intensity: ${String.format("%.1f", getIntensity() * 100)}%\n" +
            "Spatial Width: ${String.format("%.1f", getSpatialWidth() * 100)}%\n" +
            "Distance: ${String.format("%.1f", getDistance() * 100)}%\n" +
            "Effect Chain: EQ → Positioning → Spatial → Reverb → Dynamics"
        } else {
            "Sony Café Mode DSP Not Initialized"
        }
    }
    
    // Native method declarations
    private external fun nativeInit(): Int
    private external fun nativeRelease()
    private external fun nativeSetParameter(paramId: Int, value: Float)
    private external fun nativeGetParameter(paramId: Int): Float
    private external fun nativeSetEnabled(enabled: Boolean)
}