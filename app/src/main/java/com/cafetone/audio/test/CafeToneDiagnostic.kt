package com.cafetone.audio.test

import android.content.Context
import android.util.Log
import com.cafetone.audio.service.ShizukuIntegration
import com.cafetone.audio.dsp.CafeModeDSP
import rikka.shizuku.Shizuku
import android.content.pm.PackageManager

/**
 * Diagnostic test for CaféTone core functionality
 */
class CafeToneDiagnostic(private val context: Context) {
    
    companion object {
        private const val TAG = "CafeToneDiagnostic"
    }
    
    data class DiagnosticResult(
        val shizukuAvailable: Boolean,
        val shizukuPermissionGranted: Boolean,
        val dspInitialized: Boolean,
        val audioPermissionsGranted: Boolean,
        val overallStatus: String
    )
    
    fun runDiagnostics(): DiagnosticResult {
        Log.i(TAG, "Starting CaféTone diagnostic tests...")
        
        // Test 1: Shizuku availability
        val shizukuAvailable = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku not available: ${e.message}")
            false
        }
        
        // Test 2: Shizuku permission
        val shizukuPermissionGranted = try {
            shizukuAvailable && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku permission check failed: ${e.message}")
            false
        }
        
        // Test 3: DSP initialization
        val dspInitialized = try {
            val dsp = CafeModeDSP()
            dsp.isInitialized()
        } catch (e: Exception) {
            Log.e(TAG, "DSP initialization failed: ${e.message}")
            false
        }
        
        // Test 4: Audio permissions
        val audioPermissionsGranted = checkAudioPermissions()
        
        // Overall status
        val overallStatus = when {
            !shizukuAvailable -> "❌ Shizuku not available - Please install and start Shizuku"
            !shizukuPermissionGranted -> "⚠️ Shizuku permission required - Please grant permission to CaféTone"
            !dspInitialized -> "❌ DSP initialization failed - Check native library"
            !audioPermissionsGranted -> "⚠️ Audio permissions missing - Will be granted via Shizuku"
            else -> "✅ All systems ready - CaféTone should work correctly"
        }
        
        Log.i(TAG, "Diagnostic completed: $overallStatus")
        
        return DiagnosticResult(
            shizukuAvailable = shizukuAvailable,
            shizukuPermissionGranted = shizukuPermissionGranted,
            dspInitialized = dspInitialized,
            audioPermissionsGranted = audioPermissionsGranted,
            overallStatus = overallStatus
        )
    }
    
    private fun checkAudioPermissions(): Boolean {
        val permissions = listOf(
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.DUMP",
            "android.permission.CAPTURE_AUDIO_OUTPUT",
            "android.permission.MODIFY_AUDIO_ROUTING",
            "android.permission.BIND_AUDIO_SERVICE"
        )
        
        return permissions.all { permission ->
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
    }
}