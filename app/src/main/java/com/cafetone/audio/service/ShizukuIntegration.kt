package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.server.IShizukuService // CORRECTED: This import was missing.
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShizukuIntegration(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
        private const val TARGET_EFFECT_CONF_PATH = "/vendor/etc/audio_effects.xml"
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
            Log.d(TAG, "Shizuku permission result: ${if (isPermissionGranted) "GRANTED" else "DENIED"}")
            if (isPermissionGranted) {
                deployAudioEffectConfiguration()
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
            Log.d(TAG, "Checking Shizuku availability...")
            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Shizuku permission is ALREADY GRANTED.")
                    isPermissionGranted = true
                    deployAudioEffectConfiguration()
                    grantAudioPermissions()
                } else {
                    isPermissionGranted = false
                    Log.w(TAG, "Shizuku permission NOT granted. Requesting permission...")
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isPermissionGranted = false
                Log.e(TAG, "Shizuku binder is NOT active.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while checking Shizuku.", e)
            isShizukuAvailable = false
            isPermissionGranted = false
        }
        onStatusChangedCallback?.invoke()
    }

    private fun extractAssetToFile(assetName: String): File? {
        return try {
            val inputStream: InputStream = context.assets.open(assetName)
            val tempFile = File(context.cacheDir, assetName)
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            Log.i(TAG, "Extracted '$assetName' to '${tempFile.absolutePath}'")
            tempFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract asset: $assetName.", e)
            null
        }
    }

    private fun deployAudioEffectConfiguration() {
        if (!isPermissionGranted) {
            Log.w(TAG, "Cannot deploy audio config, Shizuku permission not granted.")
            return
        }
        Log.i(TAG, "Deploying audio_effects.xml to system...")

        CoroutineScope(Dispatchers.IO).launch {
            val effectFile = extractAssetToFile("audio_effects.xml") ?: return@launch

            val commands = listOf(
                "mount -o remount,rw /",
                "mount -o remount,rw /vendor",
                "cp ${effectFile.absolutePath} $TARGET_EFFECT_CONF_PATH",
                "chmod 644 $TARGET_EFFECT_CONF_PATH",
                "chown root:root $TARGET_EFFECT_CONF_PATH",
                "mount -o remount,ro /vendor",
                "mount -o remount,ro /",
                "setprop ctl.restart audioserver"
            )
            commands.forEach { executeShizukuCommand(it) }
            Log.i(TAG, "Audio effects configuration deployed successfully")
        }
    }

    fun grantAudioPermissions() {
        if (!isPermissionGranted) {
            Log.w(TAG, "Cannot grant audio permissions, Shizuku permission not granted.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val packageName = context.packageName
            val permissions = listOf(
                "android.permission.MODIFY_AUDIO_SETTINGS",
                "android.permission.DUMP",
                "android.permission.CAPTURE_AUDIO_OUTPUT",
                "android.permission.MODIFY_AUDIO_ROUTING",
                "android.permission.BIND_AUDIO_SERVICE"
            )
            permissions.forEach { permission ->
                executeShizukuCommand("pm grant $packageName $permission")
            }
            Log.i(TAG, "Audio permissions granted successfully")
        }
    }

    private fun executeShizukuCommand(command: String) {
        try {
            val binder: IBinder = Shizuku.getBinder() ?: run {
                Log.e(TAG, "Shizuku binder is null, cannot execute command.")
                return
            }

            // CORRECTED: This logic was correct, just needed the import.
            val service = IShizukuService.Stub.asInterface(ShizukuBinderWrapper(binder))
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            Log.i(TAG, "Exec: '$command' -> Exit Code: $exitCode")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Shizuku command: $command", e)
        }
    }

    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeadRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
    }

    fun getStatusMessage(): String = when {
        !isShizukuAvailable -> "Shizuku service not running."
        !isPermissionGranted -> "Shizuku permission required."
        else -> "Shizuku is ready."
    }
}