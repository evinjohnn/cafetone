package com.cafetone.audio.service

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

class ShizukuIntegration(private val context: Context, private val onPermissionResult: (Boolean) -> Unit) {

    companion object {
        private const val TAG = "ShizukuIntegration"
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1000
    }

    var isPermissionGranted = false
        private set

    private var isShizukuAvailable = false

    private val binderDeadRecipient = Shizuku.OnBinderDeadListener {
        Log.w(TAG, "Shizuku binder died.")
        isShizukuAvailable = false
        isPermissionGranted = false
        onPermissionResult(false)
    }

    private val permissionRequestListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
            isPermissionGranted = (grantResult == PackageManager.PERMISSION_GRANTED)
            Log.i(TAG, "Shizuku permission result: ${if (isPermissionGranted) "GRANTED" else "DENIED"}")
            onPermissionResult(isPermissionGranted)
        }
    }

    init {
        // FIX: The constructor should ONLY set up listeners.
        // It should NOT perform any actions that trigger callbacks, which caused the crash.
        Shizuku.addBinderDeadListener(binderDeadRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        Log.d(TAG, "ShizukuIntegration initialized and listeners are attached.")
        // The call to checkShizukuAvailability() is REMOVED from the constructor.
    }

    fun checkShizukuAvailability() {
        Log.d(TAG, "Checking Shizuku availability...")
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                Log.w(TAG, "Shizuku version is too old or not supported.")
                isShizukuAvailable = false
                isPermissionGranted = false
                onPermissionResult(false)
                return
            }

            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                Log.d(TAG, "Shizuku binder is alive.")
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (!isPermissionGranted) {
                        Log.i(TAG, "Shizuku permission already granted.")
                        isPermissionGranted = true
                        onPermissionResult(true)
                    }
                } else {
                    Log.i(TAG, "Shizuku permission not granted. Requesting...")
                    isPermissionGranted = false
                    // This will trigger the listener we added in the init block.
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                Log.w(TAG, "Shizuku is not available (ping failed).")
                isPermissionGranted = false
                onPermissionResult(false)
            }
        } catch (e: IllegalStateException) {
            // This is common if the Shizuku app is not running.
            Log.e(TAG, "Shizuku binder not ready: ${e.message}")
            isShizukuAvailable = false
            isPermissionGranted = false
            onPermissionResult(false)
        } catch (e: Exception) {
            Log.e(TAG, "An unexpected error occurred while checking Shizuku availability", e)
            isShizukuAvailable = false
            isPermissionGranted = false
            onPermissionResult(false)
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