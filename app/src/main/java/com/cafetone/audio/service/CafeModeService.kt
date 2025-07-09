package com.cafetone.audio.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cafetone.audio.MainActivity
import com.cafetone.audio.R
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.dsp.CafeModeDSP
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
import com.cafetone.audio.update.UpdateManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class CafeModeService : Service() {
    private val binder = LocalBinder()
    private lateinit var cafeModeDSP: CafeModeDSP
    private var audioEffect: AudioEffect? = null
    private var isEnabled = false
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var userEngagementManager: UserEngagementManager
    private lateinit var playStoreIntegration: PlayStoreIntegration
    private lateinit var updateManager: UpdateManager
    private lateinit var shizukuIntegration: ShizukuIntegration

    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    companion object {
        private const val TAG = "CafeModeService"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "CafeModeServiceChannel"
    }

    inner class LocalBinder : Binder() {
        fun getService(): CafeModeService = this@CafeModeService
    }

    override fun onCreate() {
        super.onCreate()
        analyticsManager = AnalyticsManager(applicationContext)
        userEngagementManager = UserEngagementManager(applicationContext)
        playStoreIntegration = PlayStoreIntegration(applicationContext)
        updateManager = UpdateManager(applicationContext)
        cafeModeDSP = CafeModeDSP()
        shizukuIntegration = ShizukuIntegration(applicationContext)

        shizukuIntegration.initialize {
            Log.d(TAG, "Shizuku status changed. Updating app status.")
            updateStatus()
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> stopProcessing()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun startProcessing(activity: Activity) {
        if (isEnabled) return
        if (!shizukuIntegration.isPermissionGranted) {
            Log.e(TAG, "Cannot start processing, Shizuku permission not granted.")
            updateStatus()
            return
        }

        try {
            setupGlobalAudioEffect()
            if (audioEffect != null) {
                isEnabled = true
                analyticsManager.logEvent("audio_processing_started")
                userEngagementManager.trackCafeModeUsage(activity)
            } else {
                Log.e(TAG, "AudioEffect is null after setup. Aborting start.")
                isEnabled = false
            }
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio processing", e)
            isEnabled = false
            updateStatus()
            analyticsManager.logError("start_processing_failed", e)
        }
    }

    private fun stopProcessing() {
        if (!isEnabled) return
        try {
            audioEffect?.release()
            audioEffect = null
            isEnabled = false
            analyticsManager.logEvent("audio_processing_stopped")
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing", e)
            analyticsManager.logError("stop_processing_failed", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID?): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            constructor.isAccessible = true
            constructor.newInstance(type, uuid ?: type, 0, 0) as AudioEffect
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioEffect via reflection", e)
            null
        }
    }

    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "Effect doesn't have control")
                    return
                }

                val paramBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val valueBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()

                val method = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                method.invoke(effect, paramBuffer, valueBuffer)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameter on AudioEffect", e)
            }
        }
    }

    private fun setupGlobalAudioEffect() {
        try {
            audioEffect = createAudioEffect(EFFECT_UUID_CAFETONE, EFFECT_UUID_CAFETONE)
            audioEffect?.let {
                it.enabled = true
                setAllParams()
                Log.i(TAG, "Global AudioEffect enabled successfully for session 0.")
            } ?: throw RuntimeException("AudioEffect creation returned null.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create global AudioEffect. Is audio_effects.xml deployed correctly?", e)
            audioEffect = null
        }
    }

    private fun setAllParams() {
        cafeModeDSP.setIntensity(cafeModeDSP.getIntensity())
        cafeModeDSP.setSpatialWidth(cafeModeDSP.getSpatialWidth())
        cafeModeDSP.setDistance(cafeModeDSP.getDistance())
        setEffectParam(CafeModeDSP.PARAM_INTENSITY, cafeModeDSP.getIntensity())
        setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, cafeModeDSP.getSpatialWidth())
        setEffectParam(CafeModeDSP.PARAM_DISTANCE, cafeModeDSP.getDistance())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Cafe Mode Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val contentText = when {
            !shizukuIntegration.isPermissionGranted -> "Shizuku permission required"
            isEnabled -> "Sony Café Mode is Active"
            else -> "Café Mode is Inactive"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CaféTone")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopProcessing()
        shizukuIntegration.cleanup()
        super.onDestroy()
    }

    fun getAnalyticsManager(): AnalyticsManager = analyticsManager
    fun getEngagementManager(): UserEngagementManager = userEngagementManager
    fun getPlayStoreIntegration(): PlayStoreIntegration = playStoreIntegration
    fun getUpdateManager(): UpdateManager = updateManager
    fun getIntensity(): Float = cafeModeDSP.getIntensity()
    fun getSpatialWidth(): Float = cafeModeDSP.getSpatialWidth()
    fun getDistance(): Float = cafeModeDSP.getDistance()
    fun getCafeModeDSP(): CafeModeDSP = cafeModeDSP

    fun toggleCafeMode(activity: Activity) {
        if (isEnabled) {
            stopProcessing()
        } else {
            startProcessing(activity)
        }
    }

    fun setIntensity(value: Float) {
        cafeModeDSP.setIntensity(value)
        setEffectParam(CafeModeDSP.PARAM_INTENSITY, value)
        updateStatus()
    }

    fun setSpatialWidth(value: Float) {
        cafeModeDSP.setSpatialWidth(value)
        setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, value)
        updateStatus()
    }

    fun setDistance(value: Float) {
        cafeModeDSP.setDistance(value)
        setEffectParam(CafeModeDSP.PARAM_DISTANCE, value)
        updateStatus()
    }

    fun forceShizukuCheck() {
        shizukuIntegration.checkShizukuAvailability()
    }

    private fun updateStatus() {
        val currentStatus = _status.value ?: AppStatus()
        _status.postValue(
            currentStatus.copy(
                isEnabled = isEnabled,
                isShizukuReady = shizukuIntegration.isPermissionGranted,
                shizukuMessage = shizukuIntegration.getStatusMessage(),
                intensity = cafeModeDSP.getIntensity(),
                spatialWidth = cafeModeDSP.getSpatialWidth(),
                distance = cafeModeDSP.getDistance()
            )
        )
        startForeground(NOTIFICATION_ID, createNotification())
    }
}