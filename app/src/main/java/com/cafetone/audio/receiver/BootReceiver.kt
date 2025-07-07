package com.cafetone.audio.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cafetone.audio.service.CafeModeService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, starting Café Mode service.")
            startCafeModeService(context)
        }
    }

    private fun startCafeModeService(context: Context) {
        try {
            val serviceIntent = Intent(context, CafeModeService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.i(TAG, "Café Mode service started on boot.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Café Mode service on boot: ${e.message}")
        }
    }
}