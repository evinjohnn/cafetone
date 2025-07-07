package com.cafetone.audio.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

        // Updated UUID to match native implementation
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
        private val EFFECT_TYPE_CAFETONE = UUID.fromString("12345678-1234-5678-1234-567890abcdef")

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
    private lateinit var shizukuIntegration: ShizukuIntegration
    private var audioManager: AudioManager? = null

    // Effect parameters
    private var intensity = 0.7f
    private var spatialWidth = 0.6f
    private var distance = 0.8f
    private var isEnabled = false
    private var isShizukuReady = false

    inner class LocalBinder : Binder() {
        fun getService(): CafeModeService = this@CafeModeService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Café Mode Service created")
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Initialize Shizuku integration
        shizukuIntegration = ShizukuIntegration(this)
        shizukuIntegration.initialize { onShizukuPermissionGranted() }
        
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
        shizukuIntegration.cleanup()
        isServiceRunning = false
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
    }

    private fun onShizukuPermissionGranted() {
        Log.i(TAG, "Shizuku permission granted, setting up audio processing")
        isShizukuReady = true
        
        // Grant audio permissions via Shizuku
        shizukuIntegration.grantAudioPermissions()
        
        // Setup audio effect after permissions are granted
        setupAudioEffect()
    }

    private fun setupAudioEffect() {
        if (!isShizukuReady) {
            Log.w(TAG, "Shizuku not ready, cannot setup audio effect")
            return
        }
        
        // Check if we have the required permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "MODIFY_AUDIO_SETTINGS permission not granted")
            return
        }

        try {
            // Query available audio effects
            val descriptors = AudioEffect.queryEffects()
            Log.i(TAG, "Available audio effects: ${descriptors?.size ?: 0}")
            
            descriptors?.forEach { descriptor ->
                Log.d(TAG, "Effect: ${descriptor.name}, UUID: ${descriptor.uuid}, Type: ${descriptor.type}")
            }

            // Try to find our custom effect
            val cafeToneDescriptor = descriptors?.find { it.uuid == EFFECT_UUID_CAFETONE }
            
            if (cafeToneDescriptor != null) {
                Log.i(TAG, "Found CaféTone effect descriptor: ${cafeToneDescriptor.name}")
                createCustomAudioEffect(cafeToneDescriptor)
            } else {
                Log.w(TAG, "CaféTone effect not found, attempting to create with system effects")
                createSystemAudioEffect()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio effect", e)
        }
    }

    private fun createCustomAudioEffect(descriptor: AudioEffect.Descriptor) {
        try {
            // Create AudioEffect for global output session (session ID 0)
            audioEffect = AudioEffect(
                descriptor.type,
                descriptor.uuid,
                0, // Priority
                0  // Session ID (0 for global output)
            )
            
            audioEffect?.let { effect ->
                setParameters()
                effect.enabled = isEnabled
                Log.i(TAG, "Custom AudioEffect created successfully")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create custom AudioEffect", e)
            // Fallback to system effects
            createSystemAudioEffect()
        }
    }

    private fun createSystemAudioEffect() {
        try {
            // Use system equalizer as fallback
            audioEffect = AudioEffect(
                AudioEffect.EFFECT_TYPE_EQUALIZER,
                AudioEffect.EFFECT_TYPE_NULL,
                0, // Priority
                0  // Session ID (0 for global output)
            )
            
            audioEffect?.let { effect ->
                effect.enabled = isEnabled
                Log.i(TAG, "System AudioEffect created as fallback")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create system AudioEffect", e)
        }
    }

    private fun releaseAudioEffect() {
        try {
            audioEffect?.let { effect ->
                if (effect.hasControl()) {
                    effect.enabled = false
                    effect.release()
                    Log.i(TAG, "AudioEffect released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioEffect", e)
        } finally {
            audioEffect = null
        }
    }

    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "AudioEffect does not have control")
                    return
                }
                
                val paramBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val valueBytes = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()

                val result = effect.setParameter(paramBytes, valueBytes)
                
                if (result != AudioEffect.SUCCESS) {
                    Log.e(TAG, "setParameter failed for param $paramId with code $result")
                } else {
                    Log.d(TAG, "Parameter $paramId set to $value successfully")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error setting parameter $paramId", e)
            }
        }
    }

    private fun setParameters() {
        setEffectParam(PARAM_INTENSITY, intensity)
        setEffectParam(PARAM_SPATIAL_WIDTH, spatialWidth)
        setEffectParam(PARAM_DISTANCE, distance)
    }

    fun toggleCafeMode() {
        if (!isShizukuReady) {
            Log.w(TAG, "Shizuku not ready, cannot toggle café mode")
            return
        }
        
        isEnabled = !isEnabled
        
        audioEffect?.let { effect ->
            try {
                if (effect.hasControl()) {
                    effect.enabled = isEnabled
                    Log.i(TAG, "Café mode ${if (isEnabled) "enabled" else "disabled"}")
                } else {
                    Log.w(TAG, "AudioEffect does not have control")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling café mode", e)
            }
        }
        
        updateNotification()
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
    fun isShizukuReady(): Boolean = isShizukuReady
    fun getShizukuStatus(): String = shizukuIntegration.getStatusMessage()

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Café Mode", 
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Audio processing service"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val toggleIntent = Intent(this, CafeModeService::class.java).apply { 
            action = ACTION_TOGGLE 
        }
        val pToggleIntent = PendingIntent.getService(
            this, 
            0, 
            toggleIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            !isShizukuReady -> "Shizuku setup required"
            isEnabled -> "Transforming your audio experience"
            else -> "Tap to activate"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Café Mode")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentIntent(pIntent)
            .addAction(
                R.drawable.ic_toggle, 
                if (isEnabled) "Disable" else "Enable", 
                pToggleIntent
            )
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        if (isServiceRunning) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, createNotification())
        }
    }
}
