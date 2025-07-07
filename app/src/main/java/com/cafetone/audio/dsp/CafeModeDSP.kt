package com.cafetone.audio.dsp

import android.util.Log

/**
 * Legacy DSP wrapper - now replaced by standard AudioEffect API
 * This class is kept for compatibility but no longer used
 */
@Deprecated("Use standard AudioEffect API instead")
class CafeModeDSP {
    
    companion object {
        private const val TAG = "CafeModeDSP"
        
        // Load native library for AudioEffect
        init {
            try {
                System.loadLibrary("cafetone-dsp")
                Log.i(TAG, "Native AudioEffect library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native AudioEffect library: ${e.message}")
            }
        }
        
        // Parameter constants (kept for compatibility)
        const val PARAM_INTENSITY = 0
        const val PARAM_SPATIAL_WIDTH = 1
        const val PARAM_DISTANCE = 2
        const val PARAM_ENABLED = 3
    }
    
    /**
     * This class is deprecated - use AudioEffect API directly
     */
    init {
        Log.w(TAG, "CafeModeDSP is deprecated - use AudioEffect API instead")
    }
    
    // Deprecated methods kept for compatibility
    @Deprecated("Use AudioEffect API")
    fun init(): Int {
        Log.w(TAG, "init() is deprecated - AudioEffect handles initialization")
        return 0
    }
    
    @Deprecated("Use AudioEffect API")
    fun release() {
        Log.w(TAG, "release() is deprecated - AudioEffect handles cleanup")
    }
    
    @Deprecated("Use AudioEffect API")
    fun getEffectUUID(): String {
        return "12345678-1234-5678-1234-567890abcdef"
    }
    
    @Deprecated("Use AudioEffect API")
    fun isInitialized(): Boolean {
        return true
    }
    
    @Deprecated("Use AudioEffect.setParameter()")
    fun setIntensity(intensity: Float) {
        Log.w(TAG, "setIntensity() is deprecated - use AudioEffect.setParameter()")
    }
    
    @Deprecated("Use AudioEffect.setParameter()")
    fun setSpatialWidth(width: Float) {
        Log.w(TAG, "setSpatialWidth() is deprecated - use AudioEffect.setParameter()")
    }
    
    @Deprecated("Use AudioEffect.setParameter()")
    fun setDistance(distance: Float) {
        Log.w(TAG, "setDistance() is deprecated - use AudioEffect.setParameter()")
    }
    
    @Deprecated("Use AudioEffect.enabled property")
    fun setEnabled(enabled: Boolean) {
        Log.w(TAG, "setEnabled() is deprecated - use AudioEffect.enabled property")
    }
}