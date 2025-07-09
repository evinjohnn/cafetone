package com.cafetone.audio.service

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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
import com.cafetone.audio.privileged.IPrivilegedAudioService
import com.cafetone.audio.update.UpdateManager
import rikka.shizuku.Shizuku
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class CafeModeService : Service() {
    private val binder = LocalBinder()
    private lateinit var cafeModeDSP: CafeModeDSP
    private var isEnabled = false

    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var userEngagementManager: UserEngagementManager
    private lateinit var playStoreIntegration: PlayStoreIntegration
    private lateinit var updateManager: UpdateManager
    private lateinit var shizukuIntegration: ShizukuIntegration

    // Privileged service approach
    private var privilegedService: IPrivilegedAudioService? = null
    private var isPrivilegedServiceBound = false

    // Direct AudioEffect fallback approach
    private var audioEffect: AudioEffect? = null
    private var usePrivilegedService = false

    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    companion object {
        private const val TAG = "CafeModeService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "CafeModeServiceChannel"
        private const val PRIVILEGED_SERVICE_TAG = "CafeTonePrivilegedService"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")

        private val EFFECT_TYPE_NULL: UUID by lazy {
            try {
                val field: Field = AudioEffect::class.java.getField("EFFECT_TYPE_NULL")
                field.get(null) as UUID
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get EFFECT_TYPE_NULL via reflection, using fallback UUID", e)
                UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
            }
        }
    }

    private val privilegedServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "PrivilegedAudioService connected.")
            isPrivilegedServiceBound = true
            privilegedService = IPrivilegedAudioService.Stub.asInterface(service)
            usePrivilegedService = true
            // If the service was meant to be on, re-apply the state
            if (isEnabled) {
                startProcessing()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "PrivilegedAudioService disconnected. Falling back to direct AudioEffect.")
            isPrivilegedServiceBound = false
            privilegedService = null
            usePrivilegedService = false
            // If we were enabled, try to restart with fallback method
            if (isEnabled) {
                startProcessing()
            }
        }
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

        shizukuIntegration = ShizukuIntegration(applicationContext) { isGranted ->
            if (isGranted) {
                bindPrivilegedService()
            } else {
                unbindPrivilegedService()
                // If we were using privileged service and it's no longer available, try fallback
                if (usePrivilegedService && isEnabled) {
                    usePrivilegedService = false
                    startProcessing()
                }
            }
            updateStatus()
        }

        createNotificationChannel()

        // Use consistent foreground service type to fix AAPT build error
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun bindPrivilegedService() {
        if (isPrivilegedServiceBound || !shizukuIntegration.isPermissionGranted) return

        try {
            val componentName = ComponentName(this, "com.cafetone.audio.privileged.PrivilegedAudioService")
            val serviceArgs = Shizuku.UserServiceArgs(componentName)
                .tag(PRIVILEGED_SERVICE_TAG)
                .daemon(false) // Don't keep alive if main app is killed
                .debuggable(true) // For development

            Shizuku.bindUserService(serviceArgs, privilegedServiceConnection)
            Log.i(TAG, "Attempting to bind to PrivilegedAudioService...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind privileged service, using fallback", e)
            usePrivilegedService = false
        }
    }

    private fun unbindPrivilegedService() {
        if (isPrivilegedServiceBound) {
            try {
                Shizuku.unbindUserService(privilegedServiceConnection, true)
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding privileged service", e)
            }
            isPrivilegedServiceBound = false
            privilegedService = null
            usePrivilegedService = false
            Log.i(TAG, "Unbound from PrivilegedAudioService.")
        }
    }

    private fun startProcessing() {
        if (isEnabled) return

        // Try privileged service first if available
        if (shizukuIntegration.isPermissionGranted && usePrivilegedService && privilegedService != null) {
            startPrivilegedProcessing()
        } else {
            // Fall back to direct AudioEffect
            startDirectProcessing()
        }
    }

    private fun startPrivilegedProcessing() {
        if (privilegedService == null || !isPrivilegedServiceBound) {
            Log.w(TAG, "Cannot start privileged processing, service not bound. Trying fallback.")
            startDirectProcessing()
            return
        }

        try {
            privilegedService?.create()
            privilegedService?.setEnabled(true)
            setAllParams()
            isEnabled = true
            Log.i(TAG, "Started processing via privileged service")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio processing via privileged service, trying fallback", e)
            analyticsManager.logError("privileged_processing_failed", e)
            startDirectProcessing()
        }
        updateStatus()
    }

    private fun startDirectProcessing() {
        try {
            setupGlobalAudioEffect()
            if (audioEffect != null) {
                isEnabled = true
                setAllParams()
                Log.i(TAG, "Started processing via direct AudioEffect")
            } else {
                Log.e(TAG, "AudioEffect is null after setup. Aborting start.")
                isEnabled = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start direct audio processing", e)
            analyticsManager.logError("direct_processing_failed", e)
            isEnabled = false
        }
        updateStatus()
    }

    private fun stopProcessing() {
        if (!isEnabled) return

        try {
            // Stop privileged service if being used
            if (usePrivilegedService && privilegedService != null) {
                privilegedService?.setEnabled(false)
                privilegedService?.release()
            }

            // Stop direct AudioEffect if being used
            audioEffect?.release()
            audioEffect = null

            isEnabled = false
            analyticsManager.logEvent("audio_processing_stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing", e)
            analyticsManager.logError("stop_processing_failed", e)
        }
        updateStatus()
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun setupGlobalAudioEffect() {
        try {
            Log.i(TAG, "Attempting to create global AudioEffect for session 0...")
            audioEffect = createAudioEffect(EFFECT_TYPE_NULL, EFFECT_UUID_CAFETONE, 0, 0)

            if (audioEffect == null) {
                throw Exception("Failed to create custom audio effect via reflection.")
            }

            audioEffect?.enabled = true
            Log.i(TAG, "Global AudioEffect enabled successfully for session 0.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup global AudioEffect", e)
            analyticsManager.logError("setup_global_audio_effect_failed", e)
            audioEffect = null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID, priority: Int, audioSession: Int): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java,
                UUID::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            constructor.isAccessible = true
            constructor.newInstance(type, uuid, priority, audioSession)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioEffect via reflection", e)
            analyticsManager.logError("create_audio_effect_failed", e)
            null
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun setEffectParam(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "Effect doesn't have control, cannot set parameter.")
                    return
                }

                val p = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val v = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                val setParameterMethod = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                val result = setParameterMethod.invoke(effect, p, v) as Int
                if (result != 0) {
                    Log.w(TAG, "setParameter returned error code: $result for param: $paramId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameter on AudioEffect via reflection", e)
                analyticsManager.logError("set_effect_param_failed", e)
            }
        }
    }

    private fun setAllParams() {
        if (usePrivilegedService && privilegedService != null) {
            try {
                privilegedService?.setParameter(CafeModeDSP.PARAM_INTENSITY, cafeModeDSP.getIntensity())
                privilegedService?.setParameter(CafeModeDSP.PARAM_SPATIAL_WIDTH, cafeModeDSP.getSpatialWidth())
                privilegedService?.setParameter(CafeModeDSP.PARAM_DISTANCE, cafeModeDSP.getDistance())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameters via privileged service", e)
            }
        } else {
            setEffectParam(CafeModeDSP.PARAM_INTENSITY, cafeModeDSP.getIntensity())
            setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, cafeModeDSP.getSpatialWidth())
            setEffectParam(CafeModeDSP.PARAM_DISTANCE, cafeModeDSP.getDistance())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopProcessing()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Cafe Mode Service", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val contentText = when {
            !shizukuIntegration.isPermissionGranted -> "Shizuku permission required"
            isEnabled -> if (usePrivilegedService) "Sony Café Mode is Active (Privileged)" else "Sony Café Mode is Active (Direct)"
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
        unbindPrivilegedService()
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
            // Try privileged service if available, otherwise use direct approach
            if (shizukuIntegration.isPermissionGranted) {
                if (!isPrivilegedServiceBound) {
                    bindPrivilegedService()
                }
                startProcessing()
                analyticsManager.logEvent("audio_processing_started")
                userEngagementManager.trackCafeModeUsage(activity)
            } else {
                // Use direct AudioEffect approach without Shizuku
                startDirectProcessing()
                if (isEnabled) {
                    analyticsManager.logEvent("audio_processing_started")
                    userEngagementManager.trackCafeModeUsage(activity)
                } else {
                    Log.e(TAG, "Failed to start processing with any method")
                }
            }
        }
    }

    fun setIntensity(value: Float) {
        cafeModeDSP.setIntensity(value)
        if (usePrivilegedService && privilegedService != null) {
            try {
                privilegedService?.setParameter(CafeModeDSP.PARAM_INTENSITY, value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set intensity via privileged service", e)
            }
        } else {
            setEffectParam(CafeModeDSP.PARAM_INTENSITY, value)
        }
        updateStatus()
    }

    fun setSpatialWidth(value: Float) {
        cafeModeDSP.setSpatialWidth(value)
        if (usePrivilegedService && privilegedService != null) {
            try {
                privilegedService?.setParameter(CafeModeDSP.PARAM_SPATIAL_WIDTH, value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set spatial width via privileged service", e)
            }
        } else {
            setEffectParam(CafeModeDSP.PARAM_SPATIAL_WIDTH, value)
        }
        updateStatus()
    }

    fun setDistance(value: Float) {
        cafeModeDSP.setDistance(value)
        if (usePrivilegedService && privilegedService != null) {
            try {
                privilegedService?.setParameter(CafeModeDSP.PARAM_DISTANCE, value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set distance via privileged service", e)
            }
        } else {
            setEffectParam(CafeModeDSP.PARAM_DISTANCE, value)
        }
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