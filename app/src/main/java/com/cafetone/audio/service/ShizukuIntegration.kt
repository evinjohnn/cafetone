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
        Shizuku.addBinderDeadListener(binderDeadRecipient)
        Shizuku.addRequestPermissionResultListener(permissionRequestListener)
        checkShizukuAvailability()
    }

    fun checkShizukuAvailability() {
        try {
            if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                isShizukuAvailable = false
                isPermissionGranted = false
                onPermissionResult(false)
                return
            }

            isShizukuAvailable = Shizuku.pingBinder()
            if (isShizukuAvailable) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (!isPermissionGranted) {
                        isPermissionGranted = true
                        onPermissionResult(true)
                    }
                } else {
                    isPermissionGranted = false
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
                }
            } else {
                isPermissionGranted = false
                onPermissionResult(false)
            }
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Shizuku binder not ready, will retry: ${e.message}")
            isShizukuAvailable = false
            isPermissionGranted = false
            onPermissionResult(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku availability", e)
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