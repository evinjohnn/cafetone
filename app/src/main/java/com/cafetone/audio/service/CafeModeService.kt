package com.cafetone.audio.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.AudioEffect.Descriptor
import android.media.audiofx.AudioEffect.OnControlStatusChangeListener
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
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.dsp.CafeModeDSP
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
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

    // Service state
    private val _serviceState = MutableLiveData<ServiceState>()
    val serviceState: LiveData<ServiceState> = _serviceState

    // App status
    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    companion object {
        private const val TAG = "CafeModeService"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("00000000-0000-0000-0000-000000000000")
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
            userEngagementManager.recordSessionStart()
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio processing", e)
            _serviceState.postValue(ServiceState.ERROR)
            analyticsManager.logError("start_processing_failed", e)
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
            userEngagementManager.recordSessionEnd()
            updateStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing", e)
            analyticsManager.logError("stop_processing_failed", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID?): AudioEffect? {
        return try {
            // Try the most complete constructor first
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )

            constructor.newInstance(
                type,
                uuid ?: type,
                0,  // priority
                0,  // audio session (0 = GLOBAL)
                0,  // flags
                applicationContext.packageName
            ) as AudioEffect
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioEffect with full constructor", e)
            try {
                // Fallback to simpler constructor
                val simpleConstructor = AudioEffect::class.java.getDeclaredConstructor(
                    UUID::class.java,
                    UUID::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )

                simpleConstructor.newInstance(
                    type,
                    uuid ?: type,
                    0,  // priority
                    0   // audio session (0 = GLOBAL)
                ) as AudioEffect
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback AudioEffect creation failed", e2)
                null
            }
        }
    }

    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "Effect doesn't have control")
                    return
                }

                // Convert paramId and value to byte arrays
                val paramBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
                    .putInt(paramId).array()
                val valueBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder())
                    .putFloat(value).array()

                // Use reflection to call setParameter
                val method = AudioEffect::class.java.getMethod(
                    "setParameter",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    ByteArray::class.java,
                    ByteArray::class.java
                )

                // Try both method signatures
                try {
                    // Try with int parameters first
                    method.invoke(effect, paramId, value.toInt())
                } catch (e: Exception) {
                    // Fall back to byte array parameters
                    method.invoke(effect, paramBuffer, valueBuffer)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameter on AudioEffect", e)
            }
        }

        // Update the DSP parameters
        cafeModeDSP?.let { dsp ->
            when (paramId) {
                0 -> dsp.setIntensity(value)
                1 -> dsp.setSpatialWidth(value)
                2 -> dsp.setDistance(value)
            }
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
                // Fallback to equalizer
                createAudioEffect(
                    AudioEffect.EFFECT_TYPE_EQUALIZER,
                    AudioEffect.EFFECT_TYPE_EQUALIZER
                )
            }

            audioEffect?.let {
                it.enabled = isEnabled
                setAllParams()
                Log.i(TAG, "Fallback AudioEffect created and configured")
            } ?: Log.e(TAG, "Failed to create fallback AudioEffect")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fallback audio effect", e)
            analyticsManager.logError("fallback_effect_setup_failed", e)
        }
    }

    private fun setAllParams() {
        // Set all DSP parameters
        cafeModeDSP?.let { dsp ->
            setEffectParam(0, dsp.getIntensity())
            setEffectParam(1, dsp.getSpatialWidth())
            setEffectParam(2, dsp.getDistance())
        }
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

    fun getUpdateManager(): UpdateManager = UpdateManager(this)

    fun toggleCafeMode() {
        if (isEnabled) {
            stopProcessing()
        } else {
            startProcessing()
        }
    }

    fun setIntensity(value: Float) {
        cafeModeDSP.setIntensity(value)
        updateStatus()
    }

    fun setSpatialWidth(value: Float) {
        cafeModeDSP.setSpatialWidth(value)
        updateStatus()
    }

    fun setDistance(value: Float) {
        cafeModeDSP.setDistance(value)
        updateStatus()
    }

    fun forceShizukuCheck() {
        // Implementation for Shizuku permission check
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
        // Implementation for checking Shizuku permission
        // Return true if permission is granted, false otherwise
        return false
    }

    sealed class ServiceState {
        object STOPPED : ServiceState()
        object RUNNING : ServiceState()
        object ERROR : ServiceState()
    }
}