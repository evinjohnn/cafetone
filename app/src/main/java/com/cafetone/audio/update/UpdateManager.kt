package com.cafetone.audio.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import com.cafetone.audio.BuildConfig // <-- FIX: Add missing import
import com.cafetone.audio.R

/**
 * Enhanced UpdateManager with Firebase Remote Config and Push Notifications
 * Handles app updates, version checking, and update notifications
 */
class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_REQUEST_CODE = 1001

        // Remote Config keys
        private const val KEY_FORCE_UPDATE_VERSION = "force_update_version"
        private const val KEY_RECOMMENDED_UPDATE_VERSION = "recommended_update_version"
        private const val KEY_UPDATE_TITLE = "update_title"
        private const val KEY_UPDATE_MESSAGE = "update_message"
        private const val KEY_UPDATE_FEATURES = "update_features"
        private const val KEY_ENABLE_UPDATE_NOTIFICATIONS = "enable_update_notifications"
        private const val KEY_UPDATE_CHECK_INTERVAL_HOURS = "update_check_interval_hours"

        // Preferences
        private const val PREFS_UPDATE = "update_prefs"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_DISMISSED_VERSION = "dismissed_version"
        private const val KEY_UPDATE_NOTIFICATION_ENABLED = "update_notification_enabled"
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
    private val prefs = context.getSharedPreferences(PREFS_UPDATE, Context.MODE_PRIVATE)

    init {
        setupRemoteConfig()
    }

    private fun setupRemoteConfig() {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour for production, can be 0 for debug
        }

        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set default values
        val defaults = mapOf(
            KEY_FORCE_UPDATE_VERSION to 0L,
            KEY_RECOMMENDED_UPDATE_VERSION to 0L,
            KEY_UPDATE_TITLE to "Update Available",
            KEY_UPDATE_MESSAGE to "A new version of CaféTone is available with exciting improvements!",
            KEY_UPDATE_FEATURES to "• Enhanced audio processing\n• Improved performance\n• Bug fixes",
            KEY_ENABLE_UPDATE_NOTIFICATIONS to true,
            KEY_UPDATE_CHECK_INTERVAL_HOURS to 24L
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    /**
     * Initialize update checking and fetch remote config
     */
    fun initialize() {
        fetchRemoteConfig()
        schedulePeriodicUpdateCheck()
    }

    private fun fetchRemoteConfig() {
        remoteConfig.fetchAndActivate()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Remote config fetched successfully")
                    checkForUpdatesWithRemoteConfig()
                } else {
                    Log.e(TAG, "Failed to fetch remote config", task.exception)
                }
            }
    }

    /**
     * Check for updates using both Play Store and Remote Config
     */
    fun checkForUpdates(activity: Activity) {
        // First check Play Store updates
        checkPlayStoreUpdate(activity)

        // Then check remote config for additional update logic
        checkForUpdatesWithRemoteConfig()
    }

    private fun checkPlayStoreUpdate(activity: Activity) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    Log.i(TAG, "Play Store update available")

                    // Check if immediate update is required
                    val forceUpdateVersion = remoteConfig.getLong(KEY_FORCE_UPDATE_VERSION)
                    val currentVersion = BuildConfig.VERSION_CODE.toLong()

                    if (currentVersion < forceUpdateVersion) {
                        // Force immediate update
                        startImmediateUpdate(activity, appUpdateInfo)
                    } else {
                        // Show flexible update dialog
                        showUpdateDialog(activity, appUpdateInfo)
                    }
                } else {
                    Log.d(TAG, "No Play Store update available")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to check for Play Store updates", exception)
            }
    }

    private fun checkForUpdatesWithRemoteConfig() {
        val currentVersion = BuildConfig.VERSION_CODE.toLong()
        val forceUpdateVersion = remoteConfig.getLong(KEY_FORCE_UPDATE_VERSION)
        val recommendedUpdateVersion = remoteConfig.getLong(KEY_RECOMMENDED_UPDATE_VERSION)

        Log.d(TAG, "Version check - Current: $currentVersion, Force: $forceUpdateVersion, Recommended: $recommendedUpdateVersion")

        when {
            currentVersion < forceUpdateVersion -> {
                Log.i(TAG, "Force update required")
                // This will be handled by Play Store update check
            }
            currentVersion < recommendedUpdateVersion -> {
                Log.i(TAG, "Recommended update available")
                // Show recommendation if not dismissed
                val dismissedVersion = prefs.getLong(KEY_DISMISSED_VERSION, 0)
                if (dismissedVersion < recommendedUpdateVersion) {
                    showRecommendedUpdateNotification()
                }
            }
            else -> {
                Log.d(TAG, "App is up to date")
            }
        }

        // Update last check time
        prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, System.currentTimeMillis()).apply()
    }

    private fun startImmediateUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.IMMEDIATE,
                activity,
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start immediate update", e)
        }
    }

    private fun showUpdateDialog(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        val title = remoteConfig.getString(KEY_UPDATE_TITLE)
        val message = remoteConfig.getString(KEY_UPDATE_MESSAGE)
        val features = remoteConfig.getString(KEY_UPDATE_FEATURES)

        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage("$message\n\n🎵 What's New:\n$features")
            .setPositiveButton("Update Now") { _, _ ->
                startFlexibleUpdate(activity, appUpdateInfo)
            }
            .setNegativeButton("Later") { _, _ ->
                // User dismissed update
                val recommendedVersion = remoteConfig.getLong(KEY_RECOMMENDED_UPDATE_VERSION)
                prefs.edit().putLong(KEY_DISMISSED_VERSION, recommendedVersion).apply()
            }
            .setCancelable(false)
            .show()
    }

    private fun startFlexibleUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.FLEXIBLE,
                activity,
                UPDATE_REQUEST_CODE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start flexible update", e)
            // Fallback to Play Store
            openPlayStore(activity)
        }
    }

    private fun showRecommendedUpdateNotification() {
        if (!remoteConfig.getBoolean(KEY_ENABLE_UPDATE_NOTIFICATIONS)) {
            return
        }

        val notificationEnabled = prefs.getBoolean(KEY_UPDATE_NOTIFICATION_ENABLED, true)
        if (!notificationEnabled) {
            return
        }

        Log.i(TAG, "Recommended update notification would be shown here")
        // This will be handled by FCM when the notification is sent from server
    }

    private fun schedulePeriodicUpdateCheck() {
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val intervalHours = remoteConfig.getLong(KEY_UPDATE_CHECK_INTERVAL_HOURS)
        val intervalMs = intervalHours * 60 * 60 * 1000

        if (System.currentTimeMillis() - lastCheck > intervalMs) {
            Log.d(TAG, "Scheduled update check triggered")
            checkForUpdatesWithRemoteConfig()
        }
    }

    private fun openPlayStore(activity: Activity) {
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            activity.startActivity(playStoreIntent)
        } catch (e: Exception) {
            // Fallback to web browser
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
            activity.startActivity(webIntent)
        }
    }

    /**
     * Handle update result from Activity
     */
    fun handleUpdateResult(requestCode: Int, resultCode: Int) {
        if (requestCode == UPDATE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    Log.i(TAG, "Update flow completed successfully")
                }
                Activity.RESULT_CANCELED -> {
                    Log.i(TAG, "Update flow cancelled by user")
                }
                else -> {
                    Log.w(TAG, "Update flow completed with result: $resultCode")
                }
            }
        }
    }

    /**
     * Enable or disable update notifications
     */
    fun setUpdateNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_NOTIFICATION_ENABLED, enabled).apply()
        Log.i(TAG, "Update notifications ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Get update notification preference
     */
    fun areUpdateNotificationsEnabled(): Boolean {
        return prefs.getBoolean(KEY_UPDATE_NOTIFICATION_ENABLED, true)
    }

    /**
     * Force refresh remote config and check for updates
     */
    fun forceRefresh(activity: Activity) {
        remoteConfig.fetch(0) // Fetch immediately
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    remoteConfig.activate()
                    checkForUpdates(activity)
                } else {
                    Log.e(TAG, "Failed to force refresh remote config", task.exception)
                }
            }
    }

    /**
     * Get current update status
     */
    fun getUpdateStatus(): String {
        val currentVersion = BuildConfig.VERSION_CODE.toLong()
        val forceUpdateVersion = remoteConfig.getLong(KEY_FORCE_UPDATE_VERSION)
        val recommendedUpdateVersion = remoteConfig.getLong(KEY_RECOMMENDED_UPDATE_VERSION)
        val lastCheck = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)

        return when {
            currentVersion < forceUpdateVersion -> "Force Update Required"
            currentVersion < recommendedUpdateVersion -> "Update Recommended"
            else -> "Up to Date (last checked: ${if (lastCheck > 0) java.text.SimpleDateFormat("MM/dd HH:mm").format(lastCheck) else "Never"})"
        }
    }
}