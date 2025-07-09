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
import com.cafetone.audio.update.UpdateManager // <-- FIX: Added missing import
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

    // Service state
    private val _serviceState = MutableLiveData<ServiceState>()
    val serviceState: LiveData<ServiceState> = _serviceState

    // App status
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
        updateManager = UpdateManager(applicationContext) // <-- FIX: Initialize UpdateManager
        cafeModeDSP = CafeModeDSP()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "START" -> startProcessing()
                "STOP" -> stopProcessing()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun startProcessing() {
        if (isEnabled) return

        try {
            setupGlobalAudioEffect()
            isEnabled = true
            _serviceState.postValue(ServiceState.RUNNING)
            analyticsManager.logEvent("audio_processing_started")
            userEngagementManager.recordSessionStart() // <-- FIX: This method is now added to its class
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio processing", e)
            _serviceState.postValue(ServiceState.ERROR)
            analyticsManager.logError("start_processing_failed", e) // <-- FIX: This method is now fixed in its class
        }
    }

    private fun stopProcessing() {
        if (!isEnabled) return

        try {
            audioEffect?.release()
            audioEffect = null
            isEnabled = false
            _serviceState.postValue(ServiceState.STOPPED)
            analyticsManager.logEvent("audio_processing_stopped")
            userEngagementManager.recordSessionEnd() // <-- FIX: This method is now added to its class
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing", e)
            analyticsManager.logError("stop_processing_failed", e) // <-- FIX: This method is now fixed in its class
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID?): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            constructor.newInstance(
                type,
                uuid ?: type,
                0,  // priority
                0   // audio session (0 = GLOBAL)
            ) as AudioEffect
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioEffect", e)
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

        when (paramId) {
            CafeModeDSP.PARAM_INTENSITY -> cafeModeDSP.setIntensity(value)
            CafeModeDSP.PARAM_SPATIAL_WIDTH -> cafeModeDSP.setSpatialWidth(value)
            CafeModeDSP.PARAM_DISTANCE -> cafeModeDSP.setDistance(value)
        }
    }

    private fun setupGlobalAudioEffect() {
        try {
            audioEffect = createAudioEffect(EFFECT_UUID_CAFETONE, EFFECT_UUID_CAFETONE)
                ?: throw Exception("Failed to create custom audio effect")

            audioEffect?.let {
                it.enabled = isEnabled
                setAllParams()
                Log.i(TAG, "Global AudioEffect enabled - processing ALL apps")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create global AudioEffect", e)
            setupFallbackAudioEffect()
        }
    }

    private fun setupFallbackAudioEffect() {
        try {
            val descriptors = AudioEffect.queryEffects()
            val cafeToneDescriptor = descriptors.find { it.uuid == EFFECT_UUID_CAFETONE }

            audioEffect = if (cafeToneDescriptor != null) {
                createAudioEffect(cafeToneDescriptor.type, cafeToneDescriptor.uuid)
            } else {
                createAudioEffect(AudioEffect.EFFECT_TYPE_EQUALIZER, AudioEffect.EFFECT_TYPE_EQUALIZER)
            }

            audioEffect?.let {
                it.enabled = isEnabled
                setAllParams()
                Log.i(TAG, "Fallback AudioEffect created and configured")
            } ?: Log.e(TAG, "Failed to create fallback AudioEffect")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fallback audio effect", e)
            analyticsManager.logError("fallback_effect_setup_failed", e) // <-- FIX: Fixed call
        }
    }

    private fun setAllParams() {
        setEffectParam(CafeModeDSP.PARAM_INTENSITY, cafeModeDSP.getIntensity())
        setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, cafeModeDSP.getSpatialWidth())
        setEffectParam(CafeModeDSP.PARAM_DISTANCE, cafeModeDSP.getDistance())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Cafe Mode Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for Cafe Mode audio processing"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cafe Mode")
            .setContentText("Processing audio...")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        stopProcessing()
        super.onDestroy()
    }

    fun getAnalyticsManager(): AnalyticsManager = analyticsManager
    fun getEngagementManager(): UserEngagementManager = userEngagementManager
    fun getPlayStoreIntegration(): PlayStoreIntegration = playStoreIntegration
    fun getUpdateManager(): UpdateManager = updateManager

    // FIX: ADDED GETTER METHODS REQUIRED BY MainActivity
    fun getIntensity(): Float = cafeModeDSP.getIntensity()
    fun getSpatialWidth(): Float = cafeModeDSP.getSpatialWidth()
    fun getDistance(): Float = cafeModeDSP.getDistance()
    fun getCafeModeDSP(): CafeModeDSP = cafeModeDSP


    fun toggleCafeMode() {
        if (isEnabled) {
            stopProcessing()
        } else {
            startProcessing()
        }
    }

    fun setIntensity(value: Float) {
        setEffectParam(CafeModeDSP.PARAM_INTENSITY, value)
        updateStatus()
    }

    fun setSpatialWidth(value: Float) {
        setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, value)
        updateStatus()
    }

    fun setDistance(value: Float) {
        setEffectParam(CafeModeDSP.PARAM_DISTANCE, value)
        updateStatus()
    }

    fun forceShizukuCheck() {
        val hasPermission = checkShizukuPermission()
        updateStatus(hasPermission)
    }

    private fun updateStatus(hasShizukuPermission: Boolean = _status.value?.isShizukuReady ?: false) {
        val currentStatus = _status.value ?: AppStatus()
        _status.postValue(
            currentStatus.copy(
                isEnabled = isEnabled,
                isShizukuReady = hasShizukuPermission,
                shizukuMessage = if (hasShizukuPermission) "" else "Shizuku permission required",
                intensity = cafeModeDSP.getIntensity(),
                spatialWidth = cafeModeDSP.getSpatialWidth(),
                distance = cafeModeDSP.getDistance()
            )
        )
    }

    private fun checkShizukuPermission(): Boolean {
        return false
    }

    sealed class ServiceState {
        object STOPPED : ServiceState()
        object RUNNING : ServiceState()
        object ERROR : ServiceState()
    }
}