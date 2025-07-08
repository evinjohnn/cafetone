package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.IOException

class ShizukuIntegration(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
    }

    private var isShizukuAvailable = false
    private var isPermissionGranted = false
    private var onPermissionGrantedCallback: (() -> Unit)? = null

    private val binderDeathRecipient = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died, attempting to reconnect...")
        checkShizukuAvailability()
    }

    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                isPermissionGranted = true
                onPermissionGrantedCallback?.invoke()
            } else {
                isPermissionGranted = false
            }
        }
    }

    fun initialize(onPermissionGranted: () -> Unit) {
        this.onPermissionGrantedCallback = onPermissionGranted
        Shizuku.addBinderDeadListener(binderDeathRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        checkShizukuAvailability()
    }

    private fun checkShizukuAvailability() {
        try {
            if (Shizuku.pingBinder()) {
                isShizukuAvailable = true
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isPermissionGranted = true
                    onPermissionGrantedCallback?.invoke()
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isShizukuAvailable = false
            }
        } catch (e: Exception) {
            isShizukuAvailable = false
        }
    }

    fun grantAudioPermissions() {
        if (!isShizukuAvailable || !isPermissionGranted) return
        CoroutineScope(Dispatchers.IO).launch {
            val packageName = context.packageName
            val commands = listOf(
                arrayOf("pm", "grant", packageName, "android.permission.MODIFY_AUDIO_SETTINGS"),
                arrayOf("pm", "grant", packageName, "android.permission.DUMP")
            )
            commands.forEach { executeShizukuCommand(it) }
            withContext(Dispatchers.Main) {
                Log.i(TAG, "Audio permissions granted via Shizuku")
            }
        }
    }

    private fun executeShizukuCommand(command: Array<String>): String {
        if (!isPermissionGranted) return "Error: Shizuku permission not granted."
        return try {
            // Use reflection to call the private static method
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true // Make it accessible
            val process = method.invoke(null, command, null, null) as Process
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Shizuku command via reflection", e)
            "Error: ${e.message}"
        }
    }

    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeathRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
        onPermissionGrantedCallback = null
    }

    fun getStatusMessage(): String = when {
        !isShizukuAvailable -> "Shizuku service not running."
        !isPermissionGranted -> "Shizuku permission required."
        else -> "Shizuku is ready."
    }
}