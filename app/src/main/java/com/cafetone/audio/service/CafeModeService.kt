package com.cafetone.audio.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
        // CORRECTED: Use ServiceInfo for foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                @Suppress("DEPRECATION")
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, createNotification(), foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
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
            shizukuIntegration.checkShizukuAvailability() // Re-trigger permission request if needed
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
    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "Effect doesn't have control")
                    return
                }

                val p = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val v = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                val setParameterMethod = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                setParameterMethod.invoke(effect, p, v)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameter on AudioEffect", e)
            }
        }
    }

    private fun setupGlobalAudioEffect() {
        try {
            Log.i(TAG, "Attempting to create global AudioEffect for session 0...")
            // CORRECTED: This is the standard, public constructor for global effects.
            // It was correct in my previous answer, but confirming it here.
            audioEffect = AudioEffect(
                AudioEffect.EFFECT_TYPE_NULL,
                EFFECT_UUID_CAFETONE,
                0, // priority
                0  // session ID 0 = Global Output Mix
            )

            audioEffect?.enabled = true
            setAllParams()
            Log.i(TAG, "Global AudioEffect enabled successfully for session 0.")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup global AudioEffect", e)
            analyticsManager.logError("setup_global_audio_effect_failed", e)
            audioEffect = null
        }
    }


    private fun setAllParams() {
        setIntensity(cafeModeDSP.getIntensity())
        setSpatialWidth(cafeModeDSP.getSpatialWidth())
        setDistance(cafeModeDSP.getDistance())
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
}