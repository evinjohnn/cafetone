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
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
import com.cafetone.audio.update.UpdateManager
import com.cafetone.audio.dsp.CafeModeDSP
import com.cafetone.audio.system.AudioPolicyManager
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

data class AppStatus(val isEnabled: Boolean, val shizukuMessage: String, val isShizukuReady: Boolean)

class CafeModeService : Service() {

    companion object {
        private const val TAG = "CafeToneService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "cafetone_channel"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
        const val ACTION_TOGGLE = "com.cafetone.audio.TOGGLE"
    }

    private val binder = LocalBinder()
    private var audioEffect: AudioEffect? = null
    private var isServiceRunning = false
    private lateinit var shizukuIntegration: ShizukuIntegration
    
    // Sony Café Mode DSP Engine
    private var cafeModeDSP: CafeModeDSP? = null

    // Advanced Features
    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var engagementManager: UserEngagementManager
    private lateinit var playStoreIntegration: PlayStoreIntegration
    private lateinit var updateManager: UpdateManager

    // Sony Café Mode Parameters
    private var intensity = 0.7f
    private var spatialWidth = 0.6f
    private var distance = 0.8f
    private var isEnabled = false

    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    inner class LocalBinder : Binder() {
        fun getService(): CafeModeService = this@CafeModeService
    }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize advanced features
        analyticsManager = AnalyticsManager(this)
        engagementManager = UserEngagementManager(this)
        playStoreIntegration = PlayStoreIntegration(this)
        updateManager = UpdateManager(this)
        
        // Initialize all systems
        analyticsManager.initialize()
        playStoreIntegration.initialize()
        updateManager.initialize()
        
        // Initialize Shizuku and DSP
        shizukuIntegration = ShizukuIntegration(this)
        shizukuIntegration.initialize { onShizukuStatusChanged() }
        
        // Initialize Sony Café Mode DSP
        initializeCafeModeDSP()
        
        createNotificationChannel()
        updateStatus()
        
        Log.i(TAG, "CafeModeService created with advanced features")
    }
    
    private fun initializeCafeModeDSP() {
        try {
            cafeModeDSP = CafeModeDSP()
            val result = cafeModeDSP?.init()
            
            if (result == 0) {
                Log.i(TAG, "Sony Café Mode DSP initialized successfully")
                
                // Set default parameters
                cafeModeDSP?.setIntensity(intensity)
                cafeModeDSP?.setSpatialWidth(spatialWidth)
                cafeModeDSP?.setDistance(distance)
                
            } else {
                Log.e(TAG, "Failed to initialize Sony Café Mode DSP: $result")
                cafeModeDSP = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Sony Café Mode DSP initialization", e)
            cafeModeDSP = null
            analyticsManager.trackCrash(e, "DSP Initialization")
        }
    }

    private fun onShizukuStatusChanged() {
        val granted = shizukuIntegration.isPermissionGranted
        Log.d(TAG, "onShizukuStatusChanged called. isPermissionGranted = $granted")
        
        if (granted) {
            Log.d(TAG, "Shizuku permission granted, setting up audio effects")
            shizukuIntegration.grantAudioPermissions()
            setupAudioEffect()
        }
        
        updateStatus()
        analyticsManager.trackShizukuSetup(granted)
    }

    fun forceShizukuCheck() {
        Log.d(TAG, "Manual refresh triggered from UI")
        shizukuIntegration.checkShizukuAvailability()
        analyticsManager.logEvent(AnalyticsManager.EVENT_SHIZUKU_SETUP, mapOf("manual_check" to true))
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
        releaseCafeModeDSP()
        shizukuIntegration.cleanup()
        super.onDestroy()
    }
    
    private fun releaseCafeModeDSP() {
        cafeModeDSP?.release()
        cafeModeDSP = null
        Log.i(TAG, "Sony Café Mode DSP released")
    }

    private fun setupAudioEffect() {
        if (!shizukuIntegration.isPermissionGranted) return
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.MODIFY_AUDIO_SETTINGS) != PackageManager.PERMISSION_GRANTED) return

        try {
            // Setup global audio effect for system-wide processing
            setupGlobalAudioEffect()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up global audio effect", e)
            analyticsManager.trackCrash(e, "Global AudioEffect Setup")
            // Fallback to app-specific effects
            setupFallbackAudioEffect()
        }
    }

    private fun setupGlobalAudioEffect() {
        try {
            // USE GLOBAL SESSION (0) - This intercepts ALL audio streams
            audioEffect = AudioEffect(
                EFFECT_UUID_CAFETONE,           // Our Sony DSP effect
                AudioEffect.EFFECT_TYPE_NULL,   // Base type
                0,                              // Priority
                0                               // Session 0 = GLOBAL (KEY CHANGE!)
            )
            
            audioEffect?.let {
                it.enabled = isEnabled
                setAllParams()
                Log.i(TAG, "Global AudioEffect enabled - processing ALL apps")
            } ?: throw Exception("Failed to create global AudioEffect")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create global AudioEffect", e)
            throw e
        }
    }
    
    private fun setupFallbackAudioEffect() {
        try {
            val descriptors = AudioEffect.queryEffects()
            val cafeToneDescriptor = descriptors?.find { it.uuid == EFFECT_UUID_CAFETONE }
            audioEffect = cafeToneDescriptor?.let { createAudioEffect(it.type, it.uuid) }
                ?: createAudioEffect(AudioEffect.EFFECT_TYPE_EQUALIZER, null)

            audioEffect?.let {
                it.enabled = isEnabled
                setAllParams()
                Log.i(TAG, "Fallback AudioEffect created and configured")
            } ?: Log.e(TAG, "Failed to create fallback AudioEffect")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up fallback audio effect", e)
            analyticsManager.trackCrash(e, "Fallback AudioEffect Setup")
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
        // Set parameters on both AudioEffect and Sony DSP
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) return
                val paramBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val valueBuffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                val method: Method = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                method.invoke(effect, paramBuffer, valueBuffer)
            } catch (e: Exception) {
                // Ignore reflection errors
            }
        }
        
        // Also set on Sony Café Mode DSP
        cafeModeDSP?.let { dsp ->
            when (paramId) {
                0 -> dsp.setIntensity(value)
                1 -> dsp.setSpatialWidth(value)
                2 -> dsp.setDistance(value)
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
        
        val newIsEnabledState = !isEnabled
        isEnabled = newIsEnabledState
        
        // Update AudioEffect
        audioEffect?.enabled = newIsEnabledState
        
        // Update Sony Café Mode DSP
        cafeModeDSP?.setEnabled(newIsEnabledState)
        
        updateStatus(isEnabled = newIsEnabledState)
        
        // Track usage and check for milestones
        if (newIsEnabledState) {
            val milestoneReached = engagementManager.trackCafeModeUsage()
            analyticsManager.trackCafeModeUsage(true, intensity, spatialWidth, distance)
            
            if (milestoneReached) {
                Log.i(TAG, "User reached a new milestone!")
            }
        }
        
        Log.i(TAG, "Sony Café Mode ${if (newIsEnabledState) "enabled" else "disabled"}")
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0.0f, 1.0f)
        setEffectParam(0, intensity)
        analyticsManager.trackParameterChange("intensity", intensity)
        Log.v(TAG, "Intensity set to: $intensity")
    }

    fun setSpatialWidth(value: Float) {
        spatialWidth = value.coerceIn(0.0f, 1.0f)
        setEffectParam(1, spatialWidth)
        analyticsManager.trackParameterChange("spatial_width", spatialWidth)
        Log.v(TAG, "Spatial width set to: $spatialWidth")
    }

    fun setDistance(value: Float) {
        distance = value.coerceIn(0.0f, 1.0f)
        setEffectParam(2, distance)
        analyticsManager.trackParameterChange("distance", distance)
        Log.v(TAG, "Distance set to: $distance")
    }

    fun getIntensity(): Float = intensity
    fun getSpatialWidth(): Float = spatialWidth
    fun getDistance(): Float = distance

    // Advanced feature accessors
    fun getAnalyticsManager(): AnalyticsManager = analyticsManager
    fun getEngagementManager(): UserEngagementManager = engagementManager
    fun getPlayStoreIntegration(): PlayStoreIntegration = playStoreIntegration
    fun getUpdateManager(): UpdateManager = updateManager
    fun getCafeModeDSP(): CafeModeDSP? = cafeModeDSP

    private fun updateStatus(isEnabled: Boolean = this.isEnabled) {
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
            currentStatus.isEnabled -> "Sony Café Mode Active - Transforming Audio"
            else -> "Tap to activate Sony Café Mode"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CaféTone")
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