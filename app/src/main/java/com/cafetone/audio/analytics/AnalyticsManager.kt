package com.cafetone.audio.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * Analytics Manager for CaféTone
 * Tracks anonymous usage statistics and app performance
 * Note: Firebase Analytics integration can be added later
 */
class AnalyticsManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREF_NAME = "cafetone_analytics"
        
        // Event types
        const val EVENT_APP_LAUNCH = "app_launch"
        const val EVENT_CAFE_MODE_TOGGLE = "cafe_mode_toggle"
        const val EVENT_INTENSITY_CHANGE = "intensity_change"
        const val EVENT_SPATIAL_WIDTH_CHANGE = "spatial_width_change"
        const val EVENT_DISTANCE_CHANGE = "distance_change"
        const val EVENT_SHIZUKU_SETUP = "shizuku_setup"
        const val EVENT_SETTINGS_OPENED = "settings_opened"
        const val EVENT_ABOUT_OPENED = "about_opened"
        const val EVENT_CRASH_REPORT = "crash_report"
        
        // User properties
        const val PROP_FIRST_LAUNCH_DATE = "first_launch_date"
        const val PROP_TOTAL_LAUNCHES = "total_launches"
        const val PROP_LAST_ACTIVE_DATE = "last_active_date"
        const val PROP_DEVICE_MODEL = "device_model"
        const val PROP_ANDROID_VERSION = "android_version"
        const val PROP_APP_VERSION = "app_version"
    }
    
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Initialize analytics
     */
    fun initialize() {
        // Record first launch
        if (!preferences.contains(PROP_FIRST_LAUNCH_DATE)) {
            preferences.edit()
                .putString(PROP_FIRST_LAUNCH_DATE, dateFormat.format(Date()))
                .putString(PROP_DEVICE_MODEL, "${Build.MANUFACTURER} ${Build.MODEL}")
                .putString(PROP_ANDROID_VERSION, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .putString(PROP_APP_VERSION, getAppVersion())
                .apply()
            Log.i(TAG, "Analytics initialized - first launch recorded")
        }
        
        // Update launch count and last active date
        val launches = preferences.getInt(PROP_TOTAL_LAUNCHES, 0) + 1
        preferences.edit()
            .putInt(PROP_TOTAL_LAUNCHES, launches)
            .putString(PROP_LAST_ACTIVE_DATE, dateFormat.format(Date()))
            .apply()
        
        logEvent(EVENT_APP_LAUNCH, mapOf("launch_number" to launches))
        Log.i(TAG, "Analytics updated - launch #$launches")
    }
    
    /**
     * Log custom event with parameters
     */
    fun logEvent(eventName: String, parameters: Map<String, Any> = emptyMap()) {
        try {
            val eventData = JSONObject().apply {
                put("event", eventName)
                put("timestamp", System.currentTimeMillis())
                put("date", dateFormat.format(Date()))
                
                // Add parameters
                parameters.forEach { (key, value) ->
                    put(key, value)
                }
                
                // Add session info
                put("session_id", getSessionId())
                put("app_version", getAppVersion())
                put("device_model", preferences.getString(PROP_DEVICE_MODEL, "Unknown"))
                put("android_version", preferences.getString(PROP_ANDROID_VERSION, "Unknown"))
            }
            
            // Store event locally (in production, send to analytics service)
            storeEventLocally(eventData)
            
            Log.v(TAG, "Event logged: $eventName with parameters: $parameters")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: $eventName", e)
        }
    }
    
    /**
     * Track Café Mode usage
     */
    fun trackCafeModeUsage(enabled: Boolean, intensity: Float, spatialWidth: Float, distance: Float) {
        logEvent(EVENT_CAFE_MODE_TOGGLE, mapOf(
            "enabled" to enabled,
            "intensity" to intensity,
            "spatial_width" to spatialWidth,
            "distance" to distance
        ))
    }
    
    /**
     * Track parameter changes
     */
    fun trackParameterChange(parameter: String, value: Float) {
        val eventName = when (parameter) {
            "intensity" -> EVENT_INTENSITY_CHANGE
            "spatial_width" -> EVENT_SPATIAL_WIDTH_CHANGE
            "distance" -> EVENT_DISTANCE_CHANGE
            else -> "parameter_change"
        }
        
        logEvent(eventName, mapOf(
            "parameter" to parameter,
            "value" to value
        ))
    }
    
    /**
     * Track Shizuku setup attempts
     */
    fun trackShizukuSetup(success: Boolean, errorMessage: String? = null) {
        val parameters = mutableMapOf<String, Any>(
            "success" to success
        )
        errorMessage?.let { parameters["error"] = it }
        
        logEvent(EVENT_SHIZUKU_SETUP, parameters)
    }
    
    /**
     * Track crash reports
     */
    fun trackCrash(exception: Throwable, context: String? = null) {
        val parameters = mutableMapOf<String, Any>(
            "exception_type" to exception.javaClass.simpleName,
            "message" to (exception.message ?: "Unknown error"),
            "stack_trace" to exception.stackTraceToString()
        )
        context?.let { parameters["context"] = it }
        
        logEvent(EVENT_CRASH_REPORT, parameters)
        Log.e(TAG, "Crash tracked: ${exception.message}")
    }
    
    /**
     * Get usage statistics
     */
    fun getUsageStatistics(): UsageStatistics {
        val firstLaunch = preferences.getString(PROP_FIRST_LAUNCH_DATE, null)
        val totalLaunches = preferences.getInt(PROP_TOTAL_LAUNCHES, 0)
        val lastActive = preferences.getString(PROP_LAST_ACTIVE_DATE, null)
        
        // Calculate days since first launch
        val daysSinceFirstLaunch = if (firstLaunch != null) {
            try {
                val firstDate = dateFormat.parse(firstLaunch)
                val diffMs = Date().time - (firstDate?.time ?: 0)
                (diffMs / (1000 * 60 * 60 * 24)).toInt()
            } catch (e: Exception) {
                0
            }
        } else 0
        
        return UsageStatistics(
            firstLaunchDate = firstLaunch,
            totalLaunches = totalLaunches,
            lastActiveDate = lastActive,
            daysSinceFirstLaunch = daysSinceFirstLaunch,
            deviceModel = preferences.getString(PROP_DEVICE_MODEL, "Unknown") ?: "Unknown",
            androidVersion = preferences.getString(PROP_ANDROID_VERSION, "Unknown") ?: "Unknown",
            appVersion = getAppVersion()
        )
    }
    
    /**
     * Get feature usage statistics (simplified implementation)
     */
    fun getFeatureUsage(): Map<String, Int> {
        // In production, this would query stored events
        return mapOf(
            "cafe_mode_toggles" to getEventCount(EVENT_CAFE_MODE_TOGGLE),
            "intensity_changes" to getEventCount(EVENT_INTENSITY_CHANGE),
            "spatial_changes" to getEventCount(EVENT_SPATIAL_WIDTH_CHANGE),
            "distance_changes" to getEventCount(EVENT_DISTANCE_CHANGE),
            "shizuku_setups" to getEventCount(EVENT_SHIZUKU_SETUP),
            "settings_opened" to getEventCount(EVENT_SETTINGS_OPENED)
        )
    }
    
    /**
     * Clear all analytics data (for privacy/testing)
     */
    fun clearAnalyticsData() {
        preferences.edit().clear().apply()
        Log.i(TAG, "Analytics data cleared")
    }
    
    /**
     * Export analytics data (for user privacy requests)
     */
    fun exportAnalyticsData(): String {
        val stats = getUsageStatistics()
        val features = getFeatureUsage()
        
        return JSONObject().apply {
            put("usage_statistics", JSONObject().apply {
                put("first_launch_date", stats.firstLaunchDate)
                put("total_launches", stats.totalLaunches)
                put("last_active_date", stats.lastActiveDate)
                put("days_since_first_launch", stats.daysSinceFirstLaunch)
                put("device_model", stats.deviceModel)
                put("android_version", stats.androidVersion)
                put("app_version", stats.appVersion)
            })
            put("feature_usage", JSONObject(features))
        }.toString(2)
    }
    
    // Private helper methods
    
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.longVersionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    private fun getSessionId(): String {
        // Simple session ID based on launch time
        return "session_${System.currentTimeMillis() / 1000}"
    }
    
    private fun storeEventLocally(eventData: JSONObject) {
        // Simplified local storage - in production, use proper database or send to analytics service
        val eventsKey = "events_${dateFormat.format(Date()).substring(0, 10)}" // Daily events
        val existingEvents = preferences.getString(eventsKey, "[]")
        
        try {
            // Store only last 100 events per day to avoid excessive storage
            val eventsArray = org.json.JSONArray(existingEvents)
            if (eventsArray.length() >= 100) {
                // Remove oldest event
                eventsArray.remove(0)
            }
            eventsArray.put(eventData)
            
            preferences.edit().putString(eventsKey, eventsArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store event locally", e)
        }
    }
    
    private fun getEventCount(eventType: String): Int {
        // Simplified implementation - count events from stored data
        return preferences.getInt("count_$eventType", 0)
    }
}

/**
 * Usage statistics data class
 */
data class UsageStatistics(
    val firstLaunchDate: String?,
    val totalLaunches: Int,
    val lastActiveDate: String?,
    val daysSinceFirstLaunch: Int,
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String
)