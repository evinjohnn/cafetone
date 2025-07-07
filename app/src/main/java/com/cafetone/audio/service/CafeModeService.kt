package com.cafetone.audio.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cafetone.audio.MainActivity
import com.cafetone.audio.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class CafeModeService : Service() {

    companion object {
        private const val TAG = "CafeModeService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "cafetone_channel"

        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")

        const val ACTION_TOGGLE = "com.cafetone.audio.TOGGLE"
        const val ACTION_SET_INTENSITY = "com.cafetone.audio.SET_INTENSITY"
        const val ACTION_SET_SPATIAL_WIDTH = "com.cafetone.audio.SET_SPATIAL_WIDTH"
        const val ACTION_SET_DISTANCE = "com.cafetone.audio.SET_DISTANCE"

        const val EXTRA_INTENSITY = "intensity"
        const val EXTRA_SPATIAL_WIDTH = "spatial_width"
        const val EXTRA_DISTANCE = "distance"

        private const val PARAM_INTENSITY = 0
        private const val PARAM_SPATIAL_WIDTH = 1
        private const val PARAM_DISTANCE = 2
    }

    private val binder = LocalBinder()
    private var audioEffect: AudioEffect? = null
    private var isServiceRunning = false

    private var intensity = 0.7f
    private var spatialWidth = 0.6f
    private var distance = 0.8f
    private var isEnabled = false

    inner class LocalBinder : Binder() {
        fun getService(): CafeModeService = this@CafeModeService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Café Mode Service created")
        setupAudioEffect()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggleCafeMode()
            ACTION_SET_INTENSITY -> setIntensity(intent.getFloatExtra(EXTRA_INTENSITY, intensity))
            ACTION_SET_SPATIAL_WIDTH -> setSpatialWidth(intent.getFloatExtra(EXTRA_SPATIAL_WIDTH, spatialWidth))
            ACTION_SET_DISTANCE -> setDistance(intent.getFloatExtra(EXTRA_DISTANCE, distance))
        }
        if (!isServiceRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            isServiceRunning = true
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        releaseAudioEffect()
        isServiceRunning = false
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    private fun setupAudioEffect() {
        val descriptors = AudioEffect.queryEffects()
        if (descriptors.isNullOrEmpty()) {
            Log.e(TAG, "AudioEffect.queryEffects() returned null or empty.")
            return
        }

        val cafeToneDescriptor = descriptors.find { it.uuid == EFFECT_UUID_CAFETONE }
        if (cafeToneDescriptor == null) {
            Log.e(TAG, "Could not find CaféTone effect descriptor. Is the native library loaded correctly?")
            return
        }

        try {
            val constructor = AudioEffect::class.java.getConstructor(
                java.util.UUID::class.java,
                java.util.UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            audioEffect = constructor.newInstance(
                cafeToneDescriptor.type,
                cafeToneDescriptor.uuid,
                0, // Priority
                0  // Session ID (0 for global output)
            ).apply {
                setParameters()
                enabled = isEnabled
            }
            Log.i(TAG, "AudioEffect created successfully for ${cafeToneDescriptor.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AudioEffect from descriptor.", e)
        }
    }

    private fun releaseAudioEffect() {
        audioEffect?.release()
        audioEffect = null
    }

    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                val setParameterMethod = AudioEffect::class.java.getMethod(
                    "setParameter",
                    ByteArray::class.java,
                    ByteArray::class.java
                )

                val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()

                val invocationResult: Any? = setParameterMethod.invoke(effect, paramBytes, valueBytes)

                if (invocationResult is Int) {
                    // *** FIX: Add an empty else branch to satisfy the compiler ***
                    if (invocationResult != AudioEffect.SUCCESS) {
                        Log.e(TAG, "setParameter failed for param $paramId with code $invocationResult")
                    } else {
                        // This empty 'else' is required to fix the compiler error.
                    }
                } else {
                    Log.e(TAG, "setParameter via reflection did not return an Int as expected.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting parameter $paramId via reflection", e)
            }
        }
    }

    private fun setParameters() {
        setEffectParam(PARAM_INTENSITY, intensity)
        setEffectParam(PARAM_SPATIAL_WIDTH, spatialWidth)
        setEffectParam(PARAM_DISTANCE, distance)
    }

    fun toggleCafeMode() {
        isEnabled = !isEnabled
        audioEffect?.enabled = isEnabled
        updateNotification()
        Log.i(TAG, "Café mode ${if (isEnabled) "enabled" else "disabled"}")
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0.0f, 1.0f)
        setEffectParam(PARAM_INTENSITY, intensity)
    }

    fun setSpatialWidth(value: Float) {
        spatialWidth = value.coerceIn(0.0f, 1.0f)
        setEffectParam(PARAM_SPATIAL_WIDTH, spatialWidth)
    }

    fun setDistance(value: Float) {
        distance = value.coerceIn(0.0f, 1.0f)
        setEffectParam(PARAM_DISTANCE, distance)
    }

    fun getIntensity(): Float = intensity
    fun getSpatialWidth(): Float = spatialWidth
    fun getDistance(): Float = distance
    fun isEnabled(): Boolean = isEnabled

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Café Mode", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val toggleIntent = Intent(this, CafeModeService::class.java).apply { action = ACTION_TOGGLE }
        val pToggleIntent = PendingIntent.getService(this, 0, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Café Mode")
            .setContentText(if (isEnabled) "Transforming your audio experience" else "Tap to activate")
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentIntent(pIntent)
            .addAction(R.drawable.ic_toggle, if (isEnabled) "Disable" else "Enable", pToggleIntent)
            .setOngoing(true).setSilent(true).build()
    }

    private fun updateNotification() {
        if (isServiceRunning) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
        }
    }
}
