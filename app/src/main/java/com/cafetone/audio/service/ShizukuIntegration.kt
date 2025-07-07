package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import java.io.IOException

class ShizukuIntegration(private val context: Context) {
    
    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
    }
    
    private var isShizukuAvailable = false
    private var isPermissionGranted = false
    private var onPermissionGrantedCallback: (() -> Unit)? = null
    
    // Shizuku binder death listener
    private val binderDeathRecipient = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died, attempting to reconnect...")
        checkShizukuAvailability()
    }
    
    // Permission request result listener
    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "Shizuku permission granted")
                isPermissionGranted = true
                onPermissionGrantedCallback?.invoke()
            } else {
                Log.e(TAG, "Shizuku permission denied")
                isPermissionGranted = false
            }
        }
    }
    
    /**
     * Initialize Shizuku integration
     */
    fun initialize(onPermissionGranted: () -> Unit) {
        this.onPermissionGrantedCallback = onPermissionGranted
        
        // Add listeners
        Shizuku.addBinderDeadListener(binderDeathRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        
        checkShizukuAvailability()
    }
    
    /**
     * Check if Shizuku is available and working
     */
    private fun checkShizukuAvailability() {
        try {
            if (Shizuku.pingBinder()) {
                isShizukuAvailable = true
                Log.i(TAG, "Shizuku is available and running")
                
                // Check if we have permission
                if (Shizuku.isPreV11()) {
                    Log.w(TAG, "Shizuku version is too old, please update")
                    return
                }
                
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isPermissionGranted = true
                    Log.i(TAG, "Shizuku permission already granted")
                    onPermissionGrantedCallback?.invoke()
                } else {
                    Log.i(TAG, "Requesting Shizuku permission")
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isShizukuAvailable = false
                Log.e(TAG, "Shizuku is not available - please start Shizuku service")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
            isShizukuAvailable = false
        }
    }
    
    /**
     * Grant audio processing permissions via Shizuku
     */
    fun grantAudioPermissions() {
        if (!isShizukuAvailable || !isPermissionGranted) {
            Log.e(TAG, "Shizuku not available or permission not granted")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageName = context.packageName
                
                // Grant MODIFY_AUDIO_SETTINGS permission
                val modifyAudioCmd = arrayOf("pm", "grant", packageName, "android.permission.MODIFY_AUDIO_SETTINGS")
                val modifyAudioResult = executeShizukuCommand(modifyAudioCmd)
                Log.i(TAG, "MODIFY_AUDIO_SETTINGS grant result: $modifyAudioResult")
                
                // Grant DUMP permission
                val dumpCmd = arrayOf("pm", "grant", packageName, "android.permission.DUMP")
                val dumpResult = executeShizukuCommand(dumpCmd)
                Log.i(TAG, "DUMP grant result: $dumpResult")
                
                // Set appops for system audio processing
                val appopsCmd = arrayOf("appops", "set", packageName, "PROJECT_MEDIA", "allow")
                val appopsResult = executeShizukuCommand(appopsCmd)
                Log.i(TAG, "PROJECT_MEDIA appops result: $appopsResult")
                
                // Set appops for system alert window
                val systemAlertCmd = arrayOf("appops", "set", packageName, "SYSTEM_ALERT_WINDOW", "allow")
                val systemAlertResult = executeShizukuCommand(systemAlertCmd)
                Log.i(TAG, "SYSTEM_ALERT_WINDOW appops result: $systemAlertResult")
                
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "All audio permissions granted via Shizuku")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error granting audio permissions", e)
            }
        }
    }
    
    /**
     * Execute command via Shizuku
     */
    private fun executeShizukuCommand(command: Array<String>): String {
        return try {
            val process = Shizuku.newProcess(command, null, null)
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result
        } catch (e: IOException) {
            Log.e(TAG, "Error executing Shizuku command: ${command.joinToString(" ")}", e)
            "Error: ${e.message}"
        }
    }
    
    /**
     * Check if device requires manufacturer-specific workarounds
     */
    private fun applyManufacturerWorkarounds() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val packageName = context.packageName
        
        when (manufacturer) {
            "xiaomi" -> {
                // Xiaomi requires disabling permission monitoring
                Log.i(TAG, "Applying Xiaomi workarounds")
                CoroutineScope(Dispatchers.IO).launch {
                    executeShizukuCommand(arrayOf("appops", "set", packageName, "RUN_IN_BACKGROUND", "allow"))
                }
            }
            "samsung" -> {
                // Samsung requires additional location permission
                Log.i(TAG, "Applying Samsung workarounds")
                CoroutineScope(Dispatchers.IO).launch {
                    executeShizukuCommand(arrayOf("appops", "set", packageName, "COARSE_LOCATION", "allow"))
                }
            }
            "oppo", "oneplus" -> {
                // OPPO/OnePlus requires battery optimization bypass
                Log.i(TAG, "Applying OPPO/OnePlus workarounds")
                CoroutineScope(Dispatchers.IO).launch {
                    executeShizukuCommand(arrayOf("appops", "set", packageName, "RUN_ANY_IN_BACKGROUND", "allow"))
                }
            }
        }
    }
    
    /**
     * Cleanup Shizuku integration
     */
    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeathRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
        onPermissionGrantedCallback = null
    }
    
    /**
     * Check if Shizuku is available
     */
    fun isShizukuAvailable(): Boolean = isShizukuAvailable
    
    /**
     * Check if we have Shizuku permission
     */
    fun isPermissionGranted(): Boolean = isPermissionGranted
    
    /**
     * Get Shizuku status message for UI
     */
    fun getStatusMessage(): String {
        return when {
            !isShizukuAvailable -> "Shizuku service not available. Please install and start Shizuku app."
            !isPermissionGranted -> "Shizuku permission not granted. Please grant permission in settings."
            else -> "Shizuku is ready for system audio processing"
        }
    }
}