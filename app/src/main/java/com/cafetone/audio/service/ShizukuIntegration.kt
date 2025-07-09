package com.cafetone.audio.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import rikka.shizuku.Shizuku.UserServiceConnection

class ShizukuIntegration(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
        private const val TARGET_EFFECT_CONF_PATH = "/vendor/etc/audio_effects.xml"

        // Define the UserService component
        private val USER_SERVICE_COMPONENT = ComponentName(
            "com.cafetone.audio", // Your app's package name
            "com.cafetone.audio.service.CommandUserService" // The class name of your user service
        )
    }

    var isPermissionGranted = false
        private set

    private var isShizukuAvailable = false
    private var onStatusChangedCallback: (() -> Unit)? = null

    // Service connection for the user service
    private val serviceConnection = UserServiceConnection()

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
                bindUserService()
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
            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    isPermissionGranted = true
                    bindUserService()
                } else {
                    isPermissionGranted = false
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isPermissionGranted = false
            }
        } catch (e: Exception) {
            isShizukuAvailable = false
            isPermissionGranted = false
        }
        onStatusChangedCallback?.invoke()
    }

    private fun bindUserService() {
        val args = UserServiceArgs(USER_SERVICE_COMPONENT)
            .daemon(false)
            .processNameSuffix(":shizuku")
            .debuggable(true) // Set to false for release
            .version(1)

        Shizuku.bindUserService(args, serviceConnection)
        Log.i(TAG, "Binding to Shizuku UserService...")
        // After binding, we can execute commands
        deployAudioEffectConfiguration()
        grantAudioPermissions()
    }

    private fun extractAssetToFile(assetName: String): File? {
        return try {
            val inputStream: InputStream = context.assets.open(assetName)
            val tempFile = File(context.cacheDir, assetName)
            FileOutputStream(tempFile).use { it.copyTo(inputStream) }
            tempFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract asset: $assetName.", e)
            null
        }
    }

    private fun deployAudioEffectConfiguration() {
        if (!isPermissionGranted) return
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
        }
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

    private fun executeShizukuCommand(command: String) {
        // This method will now be handled by the UserService
        // For now, we assume direct execution for simplicity, but the binding is key
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            Log.i(TAG, "Exec: '$command' -> Exit Code: $exitCode")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Shizuku command: $command", e)
        }
    }


    fun cleanup() {
        Shizuku.removeBinderDeadListener(binderDeadRecipient)
        Shizuku.removeRequestPermissionResultListener(permissionRequestListener)
        try {
            Shizuku.unbindUserService(serviceConnection, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbind Shizuku UserService", e)
        }
    }

    fun getStatusMessage(): String = when {
        !isShizukuAvailable -> "Shizuku service not running."
        !isPermissionGranted -> "Shizuku permission required."
        else -> "Shizuku is ready."
    }
}