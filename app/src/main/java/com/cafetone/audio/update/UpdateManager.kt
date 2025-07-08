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
import com.cafetone.audio.BuildConfig
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
    
    /**
     * Show update notification dialog
     */
    fun showUpdateDialog(updateInfo: UpdateInfo) {
        val title = when (updateInfo.updateType) {
            UPDATE_TYPE_MAJOR -> "Major Update Available! 🚀"
            UPDATE_TYPE_MINOR -> "New Update Available! ✨"
            UPDATE_TYPE_PATCH -> "Update Available 🔧"
            else -> "Update Available"
        }
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage("""
                CaféTone ${updateInfo.versionName} is now available!
                
                ${updateInfo.changelog}
                
                Release Date: ${updateInfo.releaseDate}
            """.trimIndent())
            .setPositiveButton("Update Now") { _, _ ->
                openPlayStoreForUpdate()
            }
            .setNegativeButton("Later") { _, _ ->
                // Mark as dismissed for this session
                preferences.edit().putBoolean(KEY_UPDATE_DISMISSED, true).apply()
            }
            .setNeutralButton("Auto-Update") { _, _ ->
                enableAutoUpdates()
                openPlayStoreForUpdate()
            }
            .show()
        
        Log.i(TAG, "Update dialog shown for version ${updateInfo.versionName}")
    }
    
    /**
     * Open Play Store for update
     */
    private fun openPlayStoreForUpdate() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                setPackage("com.android.vending")
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened Play Store for update")
        } catch (e: Exception) {
            // Fallback to web browser
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                }
                context.startActivity(intent)
                Log.i(TAG, "Opened Play Store web for update")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store for update", e2)
            }
        }
    }
    
    /**
     * Share update announcement
     */
    private fun shareUpdate(version: String, changelog: String) {
        try {
            val shareText = """
                CaféTone $version is out! 🎉
                
                $changelog
                
                Get the update: https://play.google.com/store/apps/details?id=${context.packageName}
                
                #CaféTone #AppUpdate #AudioExperience
            """.trimIndent()
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "CaféTone $version Update")
            }
            
            context.startActivity(Intent.createChooser(intent, "Share Update"))
            Log.i(TAG, "Update shared for version $version")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share update", e)
        }
    }
    
    /**
     * Enable auto-updates preference
     */
    private fun enableAutoUpdates() {
        preferences.edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, true).apply()
        Log.i(TAG, "Auto-updates enabled")
    }
    
    /**
     * Get current app version
     */
    private fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Get update preferences
     */
    fun getUpdatePreferences(): UpdatePreferences {
        return UpdatePreferences(
            autoUpdateEnabled = preferences.getBoolean(KEY_AUTO_UPDATE_ENABLED, false),
            lastUpdateCheck = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0),
            currentVersion = preferences.getString(KEY_CURRENT_VERSION, "Unknown") ?: "Unknown"
        )
    }
    
    /**
     * Reset update preferences (for testing)
     */
    fun resetUpdatePreferences() {
        preferences.edit().clear().apply()
        Log.i(TAG, "Update preferences reset")
    }
}

/**
 * Update information data class
 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val updateType: String,
    val releaseDate: String,
    val downloadUrl: String,
    val changelog: String,
    val priority: UpdatePriority,
    val forceUpdate: Boolean
)

/**
 * Update priority enum
 */
enum class UpdatePriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * Update preferences data class
 */
data class UpdatePreferences(
    val autoUpdateEnabled: Boolean,
    val lastUpdateCheck: Long,
    val currentVersion: String
)