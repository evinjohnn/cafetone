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

    var isPermissionGranted = false // <-- Public property now
        private set

    private var isShizukuAvailable = false
    private var onPermissionGrantedCallback: (() -> Unit)? = null

    private val binderDeathRecipient = Shizuku.OnBinderDeadListener {
        isShizukuAvailable = false
        isPermissionGranted = false
        onPermissionGrantedCallback?.invoke()
    }

    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            isPermissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED)
            onPermissionGrantedCallback?.invoke()
        }
    }

    fun initialize(onStatusChanged: () -> Unit) {
        this.onPermissionGrantedCallback = onStatusChanged
        Shizuku.addBinderDeadListener(binderDeathRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        checkShizukuAvailability()
    }

    private fun checkShizukuAvailability() {
        try {
            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isPermissionGranted = true
                    onPermissionGrantedCallback?.invoke()
                } else {
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                onPermissionGrantedCallback?.invoke()
            }
        } catch (e: Exception) {
            isShizukuAvailable = false
            onPermissionGrantedCallback?.invoke()
        }
    }

    fun grantAudioPermissions() {
        if (!isPermissionGranted) return
        CoroutineScope(Dispatchers.IO).launch {
            val packageName = context.packageName
            val commands = listOf(
                arrayOf("pm", "grant", packageName, "android.permission.MODIFY_AUDIO_SETTINGS"),
                arrayOf("pm", "grant", packageName, "android.permission.DUMP")
            )
            commands.forEach { executeShizukuCommand(it) }
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