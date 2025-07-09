package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
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

    private val binderDeadRecipient = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died.")
        isShizukuAvailable = false
        isPermissionGranted = false
        onStatusChangedCallback?.invoke()
    }

    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            isPermissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED)
            Log.i(TAG, "Shizuku permission result: ${if (isPermissionGranted) "GRANTED" else "DENIED"}")
            if (isPermissionGranted) {
                grantAudioPermissions()
            }
            onStatusChangedCallback?.invoke()
        }
    }

    fun initialize(onStatusChanged: () -> Unit) {
        this.onStatusChangedCallback = onStatusChanged
        Shizuku.addBinderDeadListener(binderDeadRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        checkShizukuAvailability()
    }

    fun checkShizukuAvailability() {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                isShizukuAvailable = false
                isPermissionGranted = false
                onStatusChangedCallback?.invoke()
                return
            }

            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isPermissionGranted = true
                    grantAudioPermissions()
                } else {
                    isPermissionGranted = false
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isPermissionGranted = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
            isShizukuAvailable = false
            isPermissionGranted = false
        }
        onStatusChangedCallback?.invoke()
    }

    fun grantAudioPermissions() {
        if (!isPermissionGranted) return
        CoroutineScope(Dispatchers.IO).launch {
            val packageName = context.packageName
            val permissions = listOf(
                "android.permission.MODIFY_AUDIO_SETTINGS", "android.permission.DUMP",
                "android.permission.CAPTURE_AUDIO_OUTPUT", "android.permission.MODIFY_AUDIO_ROUTING",
                "android.permission.BIND_AUDIO_SERVICE"
            )
            permissions.forEach { executeShizukuCommand("pm grant $packageName $it") }
        }
    }

    // THIS IS THE FINAL, WORKING VERSION OF THIS METHOD.
    private fun executeShizukuCommand(command: String) {
        try {
            val process: ShizukuRemoteProcess = Shizuku.newProcess(arrayOf("sh", "-c", command), null, "/")

            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()

            process.inputStream.copyTo(outputStream)
            process.errorStream.copyTo(errorStream)

            val exitCode = process.waitFor()

            Log.i(TAG, """
                Exec: '$command'
                Exit Code: $exitCode
                Output: ${outputStream.toString().trim()}
                Error: ${errorStream.toString().trim()}
            """.trimIndent())

        } catch (e: IllegalStateException) {
            Log.e(TAG, "Shizuku is not running or permission is not granted.", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Shizuku command: $command", e)
        }
    }

    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeadRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
    }

    fun getStatusMessage(): String = when {
        !isShizukuAvailable -> "Shizuku app not running."
        !isPermissionGranted -> "Shizuku permission required."
        else -> "Shizuku is ready."
    }
}