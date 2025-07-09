package com.cafetone.audio.service

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
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

class CafeModeService : Service() {
    private val binder = LocalBinder()
    private lateinit var cafeModeDSP: CafeModeDSP
    private var isEnabled = false

    private lateinit var analyticsManager: AnalyticsManager
    private lateinit var userEngagementManager: UserEngagementManager
    private lateinit var playStoreIntegration: PlayStoreIntegration
    private lateinit var updateManager: UpdateManager
    private lateinit var shizukuIntegration: ShizukuIntegration

    private var privilegedService: IPrivilegedAudioService? = null
    private var isPrivilegedServiceBound = false

    private val _status = MutableLiveData<AppStatus>()
    val status: LiveData<AppStatus> = _status

    companion object {
        private const val TAG = "CafeModeService"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "CafeModeServiceChannel"
        private const val PRIVILEGED_SERVICE_TAG = "CafeTonePrivilegedService"
    }

    private val privilegedServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "PrivilegedAudioService connected.")
            isPrivilegedServiceBound = true
            privilegedService = IPrivilegedAudioService.Stub.asInterface(service)
            if (isEnabled) {
                startProcessing()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "PrivilegedAudioService disconnected.")
            isPrivilegedServiceBound = false
            privilegedService = null
            if (isEnabled) {
                stopProcessing()
                isEnabled = false // Force off since service died
                updateStatus()
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
            }
            updateStatus()
        }

        createNotificationChannel()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), foregroundServiceType)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun bindPrivilegedService() {
        if (isPrivilegedServiceBound || !shizukuIntegration.isPermissionGranted) return
        try {
            val componentName = ComponentName(packageName, "com.cafetone.audio.privileged.PrivilegedAudioService")
            val serviceArgs = Shizuku.UserServiceArgs(componentName)
                .tag(PRIVILEGED_SERVICE_TAG)
                .daemon(false)
            Shizuku.bindUserService(serviceArgs, privilegedServiceConnection)
            Log.i(TAG, "Attempting to bind to PrivilegedAudioService...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind privileged service", e)
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
            Log.i(TAG, "Unbound from PrivilegedAudioService.")
        }
    }

    private fun startProcessing() {
        if (privilegedService == null || !isPrivilegedServiceBound) {
            Log.w(TAG, "Cannot start processing, privileged service not bound. Retrying bind.")
            bindPrivilegedService()
            return
        }
        try {
            privilegedService?.create()
            setAllParams()
            privilegedService?.setEnabled(true)
            isEnabled = true
            Log.i(TAG, "SUCCESS: Audio processing started via Privileged Service.")
        } catch (e: Exception) {
            Log.e(TAG, "ERROR: Failed to start processing via privileged service.", e)
            isEnabled = false
        }
    }

    private fun stopProcessing() {
        try {
            privilegedService?.setEnabled(false)
            privilegedService?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing", e)
        }
        isEnabled = false
    }

    private fun setAllParams() {
        setIntensity(cafeModeDSP.getIntensity())
        setSpatialWidth(cafeModeDSP.getSpatialWidth())
        setDistance(cafeModeDSP.getDistance())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopProcessing()
            updateStatus()
        }
        return START_STICKY
    }

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
            analyticsManager.logEvent("audio_processing_stopped")
        } else {
            if (shizukuIntegration.isPermissionGranted) {
                startProcessing()
                if (isEnabled) {
                    analyticsManager.logEvent("audio_processing_started")
                    userEngagementManager.trackCafeModeUsage(activity)
                }
            } else {
                Log.e(TAG, "Cannot toggle on, Shizuku permission not granted.")
                shizukuIntegration.checkShizukuAvailability() // Prompt for permission again
            }
        }
        updateStatus()
    }

    fun setIntensity(value: Float) {
        cafeModeDSP.setIntensity(value)
        privilegedService?.setParameter(CafeModeDSP.PARAM_INTENSITY, value)
        updateStatus()
    }

    fun setSpatialWidth(value: Float) {
        cafeModeDSP.setSpatialWidth(value)
        privilegedService?.setParameter(CafeModeDSP.PARAM_SPATIAL_WIDTH, value)
        updateStatus()
    }

    fun setDistance(value: Float) {
        cafeModeDSP.setDistance(value)
        privilegedService?.setParameter(CafeModeDSP.PARAM_DISTANCE, value)
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