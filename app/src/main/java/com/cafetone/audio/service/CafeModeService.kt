package com.cafetone.audio.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cafetone.audio.MainActivity
import com.cafetone.audio.R
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

data class AppStatus(val isEnabled: Boolean, val shizukuMessage: String, val isShizukuReady: Boolean)

class CafeModeService : Service() {

    companion object {
        private const val TAG = "CafeToneService" // Corrected Logcat Tag
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "cafetone_channel"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
        const val ACTION_TOGGLE = "com.cafetone.audio.TOGGLE"
    }

    private val binder = LocalBinder()
    private var audioEffect: AudioEffect? = null
    private var isServiceRunning = false
    private lateinit var shizukuIntegration: ShizukuIntegration

    private var intensity = 0.7f
    private var spatialWidth = 0.6f
    private var distance = 0.8f

    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    inner class LocalBinder : Binder() {
        fun getService(): CafeModeService = this@CafeModeService
    }

    override fun onCreate() {
        super.onCreate()
        shizukuIntegration = ShizukuIntegration(this)
        shizukuIntegration.initialize { onShizukuStatusChanged() }
        createNotificationChannel()
        updateStatus()
    }

    private fun onShizukuStatusChanged() {
        val granted = shizukuIntegration.isPermissionGranted
        Log.d(TAG, "onShizukuStatusChanged called. isPermissionGranted = $granted")
        if (granted) {
            Log.d(TAG, "Permission is granted, now granting audio permissions and setting up effect.")
            shizukuIntegration.grantAudioPermissions()
            setupAudioEffect()
        }
        updateStatus()
    }

    fun forceShizukuCheck() {
        Log.d(TAG, "Manual refresh triggered from UI.")
        shizukuIntegration.checkShizukuAvailability()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            toggleCafeMode()
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
        super.onDestroy()
    }

    private fun setupAudioEffect() {
        if (!shizukuIntegration.isPermissionGranted) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) return

        try {
            val descriptors = AudioEffect.queryEffects()
            val cafeToneDescriptor = descriptors?.find { it.uuid == EFFECT_UUID_CAFETONE }
            audioEffect = cafeToneDescriptor?.let { createAudioEffect(it.type, it.uuid) }
                ?: createAudioEffect(AudioEffect.EFFECT_TYPE_EQUALIZER, null)

            audioEffect?.let {
                it.enabled = _status.value?.isEnabled ?: false
                setAllParams()
                Log.i(TAG, "AudioEffect created and configured.")
            } ?: Log.e(TAG, "Failed to create any AudioEffect.")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up audio effect", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID?): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getConstructor(UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            constructor.newInstance(type, uuid ?: type, 0, 0)
        } catch (e: Exception) {
            null
        }
    }

    private fun releaseAudioEffect() {
        audioEffect?.release()
        audioEffect = null
    }

    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) return
                val paramBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val valueBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                val method: Method = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                method.invoke(effect, paramBuffer, valueBuffer)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun setAllParams() {
        setEffectParam(0, intensity)
        setEffectParam(1, spatialWidth)
        setEffectParam(2, distance)
    }

    fun toggleCafeMode() {
        if (!shizukuIntegration.isPermissionGranted) return
        val newIsEnabledState = !(_status.value?.isEnabled ?: false)
        audioEffect?.enabled = newIsEnabledState
        updateStatus(isEnabled = newIsEnabledState)
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0.0f, 1.0f)
        setEffectParam(0, intensity)
    }

    fun setSpatialWidth(value: Float) {
        spatialWidth = value.coerceIn(0.0f, 1.0f)
        setEffectParam(1, spatialWidth)
    }

    fun setDistance(value: Float) {
        distance = value.coerceIn(0.0f, 1.0f)
        setEffectParam(2, distance)
    }

    fun getIntensity(): Float = intensity
    fun getSpatialWidth(): Float = spatialWidth
    fun getDistance(): Float = distance

    private fun updateStatus(isEnabled: Boolean = _status.value?.isEnabled ?: false) {
        val shizukuMessage = shizukuIntegration.getStatusMessage()
        val shizukuReady = shizukuIntegration.isPermissionGranted
        _status.postValue(AppStatus(isEnabled, shizukuMessage, shizukuReady))
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Café Mode", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val currentStatus = _status.value ?: AppStatus(false, "Initializing...", false)
        val pIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pToggleIntent = PendingIntent.getService(this, 0, Intent(this, CafeModeService::class.java).apply { action = ACTION_TOGGLE }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val statusText = when {
            !currentStatus.isShizukuReady -> currentStatus.shizukuMessage
            currentStatus.isEnabled -> "Transforming audio"
            else -> "Tap to activate"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Café Mode")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_cafe_mode)
            .setContentIntent(pIntent)
            .addAction(R.drawable.ic_toggle, if (currentStatus.isEnabled) "Disable" else "Enable", pToggleIntent)
            .setOngoing(true).setSilent(true).build()
    }

    private fun updateNotification() {
        if (isServiceRunning) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification())
        }
    }
}