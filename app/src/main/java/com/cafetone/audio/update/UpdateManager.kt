package com.cafetone.audio.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Update Manager for CaféTone
 * Handles version checking, changelog display, and update notifications
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        private const val PREF_NAME = "cafetone_updates"
        
        // Update preferences
        private const val KEY_CURRENT_VERSION = "current_version"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_UPDATE_DISMISSED = "update_dismissed"
        private const val KEY_CHANGELOG_SHOWN = "changelog_shown"
        private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
        
        // Update types
        const val UPDATE_TYPE_MAJOR = "major"
        const val UPDATE_TYPE_MINOR = "minor"
        const val UPDATE_TYPE_PATCH = "patch"
    }
    
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * Initialize update manager
     */
    fun initialize() {
        val currentVersion = getCurrentAppVersion()
        val lastVersion = preferences.getString(KEY_CURRENT_VERSION, null)
        
        // Check if app was updated
        if (lastVersion != null && lastVersion != currentVersion) {
            handleAppUpdate(lastVersion, currentVersion)
        }
        
        // Store current version
        preferences.edit().putString(KEY_CURRENT_VERSION, currentVersion).apply()
        
        Log.i(TAG, "Update manager initialized - version: $currentVersion")
    }
    
    /**
     * Handle app update (show changelog, etc.)
     */
    private fun handleAppUpdate(oldVersion: String, newVersion: String) {
        Log.i(TAG, "App updated from $oldVersion to $newVersion")
        
        // Show changelog if not already shown for this version
        val changelogKey = "${KEY_CHANGELOG_SHOWN}_$newVersion"
        if (!preferences.getBoolean(changelogKey, false)) {
            showChangelogDialog(oldVersion, newVersion)
            preferences.edit().putBoolean(changelogKey, true).apply()
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