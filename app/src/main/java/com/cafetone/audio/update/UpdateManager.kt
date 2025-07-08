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
     * Show changelog dialog
     */
    private fun showChangelogDialog(oldVersion: String, newVersion: String) {
        val changelog = getChangelogForVersion(newVersion)
        
        AlertDialog.Builder(context)
            .setTitle("CaféTone Updated! 🎉")
            .setMessage("""
                Welcome to CaféTone $newVersion!
                
                $changelog
                
                Thank you for using CaféTone! 🎵☕
            """.trimIndent())
            .setPositiveButton("Awesome!") { _, _ -> }
            .setNeutralButton("Share Update") { _, _ ->
                shareUpdate(newVersion, changelog)
            }
            .show()
        
        Log.i(TAG, "Changelog shown for version $newVersion")
    }
    
    /**
     * Get changelog for specific version
     */
    private fun getChangelogForVersion(version: String): String {
        // In production, this would fetch from server or local changelog file
        return when {
            version.startsWith("1.1") -> """
                ✨ What's New:
                • Enhanced Sony Café Mode audio effects
                • Improved spatial audio processing
                • Better Shizuku integration
                • Performance optimizations
                • Bug fixes and stability improvements
            """.trimIndent()
            
            version.startsWith("1.2") -> """
                🎵 New Features:
                • Advanced reverb engine with café acoustics
                • Dynamic multi-band compression
                • Enhanced distance simulation
                • Improved user interface
                • Better Play Store integration
            """.trimIndent()
            
            version.startsWith("1.3") -> """
                🚀 Major Update:
                • Complete Sony audio effect chain
                • Real-time DSP processing
                • Advanced spatial widening
                • New user engagement features
                • Analytics and crash reporting
            """.trimIndent()
            
            else -> """
                🔧 Updates:
                • Bug fixes and improvements
                • Enhanced audio processing
                • Better user experience
            """.trimIndent()
        }
    }
    
    /**
     * Check for manual updates (via Play Store API or custom endpoint)
     */
    fun checkForUpdates(callback: (UpdateInfo?) -> Unit) {
        val lastCheck = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastCheck = (currentTime - lastCheck) / (1000 * 60 * 60)
        
        // Don't check too frequently
        if (hoursSinceLastCheck < 6 && lastCheck != 0L) {
            Log.v(TAG, "Update check skipped (last check: ${hoursSinceLastCheck}h ago)")
            callback(null)
            return
        }
        
        // Update last check time
        preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, currentTime).apply()
        
        // In production, this would check a remote endpoint or Play Store API
        // For now, simulate update check
        checkForUpdatesSimulated(callback)
    }
    
    /**
     * Simulated update check (replace with real implementation)
     */
    private fun checkForUpdatesSimulated(callback: (UpdateInfo?) -> Unit) {
        // Simulate network delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val currentVersion = getCurrentAppVersion()
            
            // Simulate update availability (10% chance for demo)
            val updateAvailable = (0..9).random() == 0
            
            if (updateAvailable) {
                val updateInfo = UpdateInfo(
                    versionName = "1.4.0",
                    versionCode = 140,
                    updateType = UPDATE_TYPE_MINOR,
                    releaseDate = dateFormat.format(Date()),
                    downloadUrl = "https://play.google.com/store/apps/details?id=${context.packageName}",
                    changelog = """
                        🎵 New in v1.4.0:
                        • New audio presets
                        • Improved UI design
                        • Better battery optimization
                        • Bug fixes
                    """.trimIndent(),
                    priority = UpdatePriority.NORMAL,
                    forceUpdate = false
                )
                
                Log.i(TAG, "Update available: ${updateInfo.versionName}")
                callback(updateInfo)
            } else {
                Log.v(TAG, "No updates available")
                callback(null)
            }
        }, 1000)
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