package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.IOException

class ShizukuIntegration(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
    }

    var isPermissionGranted = false
        private set

    private var isShizukuAvailable = false
    private var onStatusChangedCallback: (() -> Unit)? = null

    private val binderDeathRecipient = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died.")
        isShizukuAvailable = false
        isPermissionGranted = false
        onStatusChangedCallback?.invoke()
    }

    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Shizuku permission result: GRANTED by user.")
                isPermissionGranted = true
            } else {
                Log.e(TAG, "Shizuku permission result: DENIED by user.")
                isPermissionGranted = false
            }
            onStatusChangedCallback?.invoke()
        }
    }

    fun initialize(onStatusChanged: () -> Unit) {
        this.onStatusChangedCallback = onStatusChanged
        Shizuku.addBinderDeadListener(binderDeathRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        checkShizukuAvailability()
    }

    fun checkShizukuAvailability() {
        try {
            Log.d(TAG, "Checking Shizuku availability...")
            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                Log.d(TAG, "Shizuku binder is active.")
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Shizuku permission is ALREADY GRANTED.")
                    isPermissionGranted = true
                } else {
                    Log.d(TAG, "Shizuku permission NOT granted. Requesting it now.")
                    isPermissionGranted = false
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                Log.e(TAG, "Shizuku binder is NOT active.")
                isPermissionGranted = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while checking Shizuku.", e)
            isShizukuAvailable = false
            isPermissionGranted = false
        }
        onStatusChangedCallback?.invoke()
    }

    fun grantAudioPermissions() {
        if (!isPermissionGranted) return
        CoroutineScope(Dispatchers.IO).launch {
            val packageName = context.packageName
            
            // Basic permissions first
            val basicCommands = listOf(
                arrayOf("pm", "grant", packageName, "android.permission.MODIFY_AUDIO_SETTINGS"),
                arrayOf("pm", "grant", packageName, "android.permission.DUMP")
            )
            
            // Advanced permissions for global audio processing
            val advancedCommands = listOf(
                arrayOf("pm", "grant", packageName, "android.permission.CAPTURE_AUDIO_OUTPUT"),
                arrayOf("pm", "grant", packageName, "android.permission.MODIFY_AUDIO_ROUTING"),
                arrayOf("pm", "grant", packageName, "android.permission.BIND_AUDIO_SERVICE")
            )
            
            // Execute basic commands
            basicCommands.forEach { command ->
                val result = executeShizukuCommand(command)
                Log.i(TAG, "${command.joinToString(" ")} result: $result")
            }
            
            // Execute advanced commands for global processing
            advancedCommands.forEach { command ->
                val result = executeShizukuCommand(command)
                Log.i(TAG, "${command.joinToString(" ")} result: $result")
            }
            
            // Enable global audio processing
            enableGlobalAudioProcessing()
        }
    }
    
    fun enableGlobalAudioProcessing() {
        if (!isPermissionGranted) return
        
        val packageName = context.packageName
        val effectUuid = "87654321-4321-8765-4321-fedcba098765"
        
        // Audio policy modifications for global processing
        val audioPolicyCommands = listOf(
            // Critical permissions for global audio
            arrayOf("cmd", "audio", "set-global-effect-enabled", "true"),
            arrayOf("setprop", "persist.vendor.audio.global_effects.enable", "1"),
            arrayOf("setprop", "ro.audio.global_effects_enable", "true"),
            arrayOf("setprop", "persist.audio.effect.signature", "cafetone"),
            
            // Audio routing control
            arrayOf("cmd", "audio", "set-default-effect", effectUuid),
            arrayOf("cmd", "audio", "force-effect-enabled", effectUuid, "true")
        )
        
        CoroutineScope(Dispatchers.IO).launch {
            audioPolicyCommands.forEach { command ->
                val result = executeShizukuCommand(command)
                Log.i(TAG, "Audio policy command: ${command.joinToString(" ")} result: $result")
            }
        }
    }

    private fun executeShizukuCommand(command: Array<String>): String {
        if (!isPermissionGranted) return "Error: Shizuku permission not granted."
        return try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, command, null, null) as Process
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeathRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
    }

    fun getStatusMessage(): String = when {
        !isShizukuAvailable -> "Shizuku service not running."
        !isPermissionGranted -> "Shizuku permission required."
        else -> "Shizuku is ready."
    }
}