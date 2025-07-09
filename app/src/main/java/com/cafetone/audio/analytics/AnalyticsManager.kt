package com.cafetone.audio.analytics

import android.content.Context
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsManager(private val context: Context) {

    companion object {
        private const val TAG = "AnalyticsManager"
        private const val PREF_NAME = "cafetone_analytics"
        const val EVENT_APP_LAUNCH = "app_launch"
        const val EVENT_CAFE_MODE_TOGGLE = "cafe_mode_toggle"
        const val EVENT_INTENSITY_CHANGE = "intensity_change"
        const val EVENT_SPATIAL_WIDTH_CHANGE = "spatial_width_change"
        const val EVENT_DISTANCE_CHANGE = "distance_change"
        const val EVENT_SHIZUKU_SETUP = "shizuku_setup"
        const val EVENT_SETTINGS_OPENED = "settings_opened"
        const val EVENT_ABOUT_OPENED = "about_opened"
        const val EVENT_CRASH_REPORT = "crash_report"
        const val PROP_FIRST_LAUNCH_DATE = "first_launch_date"
        const val PROP_TOTAL_LAUNCHES = "total_launches"
        const val PROP_LAST_ACTIVE_DATE = "last_active_date"
        const val PROP_DEVICE_MODEL = "device_model"
        const val PROP_ANDROID_VERSION = "android_version"
        const val PROP_APP_VERSION = "app_version"
    }

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun initialize() {
        if (!preferences.contains(PROP_FIRST_LAUNCH_DATE)) {
            preferences.edit()
                .putString(PROP_FIRST_LAUNCH_DATE, dateFormat.format(Date()))
                .putString(PROP_DEVICE_MODEL, "${Build.MANUFACTURER} ${Build.MODEL}")
                .putString(PROP_ANDROID_VERSION, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                .putString(PROP_APP_VERSION, getAppVersion())
                .apply()
            Log.i(TAG, "Analytics initialized - first launch recorded")
        }

        val launches = preferences.getInt(PROP_TOTAL_LAUNCHES, 0) + 1
        preferences.edit()
            .putInt(PROP_TOTAL_LAUNCHES, launches)
            .putString(PROP_LAST_ACTIVE_DATE, dateFormat.format(Date()))
            .apply()

        logEvent(EVENT_APP_LAUNCH, mapOf("launch_number" to launches))
        Log.i(TAG, "Analytics updated - launch #$launches")
    }

    fun logEvent(eventName: String, parameters: Map<String, Any> = emptyMap()) {
        try {
            val eventData = JSONObject().apply {
                put("event", eventName)
                put("timestamp", System.currentTimeMillis())
                put("date", dateFormat.format(Date()))
                parameters.forEach { (key, value) -> put(key, value) }
                put("session_id", getSessionId())
                put("app_version", getAppVersion())
                put("device_model", preferences.getString(PROP_DEVICE_MODEL, "Unknown"))
                put("android_version", preferences.getString(PROP_ANDROID_VERSION, "Unknown"))
            }
            storeEventLocally(eventData)
            Log.v(TAG, "Event logged: $eventName with parameters: $parameters")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: $eventName", e)
        }
    }

    fun trackCafeModeUsage(enabled: Boolean, intensity: Float, spatialWidth: Float, distance: Float) {
        logEvent(EVENT_CAFE_MODE_TOGGLE, mapOf(
            "enabled" to enabled,
            "intensity" to intensity,
            "spatial_width" to spatialWidth,
            "distance" to distance
        ))
    }

    fun trackParameterChange(parameter: String, value: Float) {
        val eventName = when (parameter) {
            "intensity" -> EVENT_INTENSITY_CHANGE
            "spatial_width" -> EVENT_SPATIAL_WIDTH_CHANGE
            "distance" -> EVENT_DISTANCE_CHANGE
            else -> "parameter_change"
        }
        logEvent(eventName, mapOf("parameter" to parameter, "value" to value))
    }

    fun trackShizukuSetup(success: Boolean, errorMessage: String? = null) {
        val parameters = mutableMapOf<String, Any>("success" to success)
        errorMessage?.let { parameters["error"] = it }
        logEvent(EVENT_SHIZUKU_SETUP, parameters)
    }

    // FIX: Renamed 'trackCrash' to 'logError' and adjusted signature
    fun logError(contextString: String, exception: Throwable) {
        val parameters = mutableMapOf<String, Any>(
            "exception_type" to exception.javaClass.simpleName,
            "message" to (exception.message ?: "Unknown error"),
            "stack_trace" to exception.stackTraceToString(),
            "context" to contextString
        )
        logEvent(EVENT_CRASH_REPORT, parameters)
        Log.e(TAG, "Error tracked: ${exception.message} in context: $contextString")
    }

    fun getUsageStatistics(): UsageStatistics {
        val firstLaunch = preferences.getString(PROP_FIRST_LAUNCH_DATE, null)
        val totalLaunches = preferences.getInt(PROP_TOTAL_LAUNCHES, 0)
        val lastActive = preferences.getString(PROP_LAST_ACTIVE_DATE, null)
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

    fun getFeatureUsage(): Map<String, Int> {
        return mapOf(
            "cafe_mode_toggles" to getEventCount(EVENT_CAFE_MODE_TOGGLE),
            "intensity_changes" to getEventCount(EVENT_INTENSITY_CHANGE),
            "spatial_changes" to getEventCount(EVENT_SPATIAL_WIDTH_CHANGE),
            "distance_changes" to getEventCount(EVENT_DISTANCE_CHANGE),
            "shizuku_setups" to getEventCount(EVENT_SHIZUKU_SETUP),
            "settings_opened" to getEventCount(EVENT_SETTINGS_OPENED)
        )
    }

    fun clearAnalyticsData() {
        preferences.edit().clear().apply()
        Log.i(TAG, "Analytics data cleared")
    }

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
            put("feature_usage", JSONObject(features.mapKeys { it.key }))
        }.toString(2)
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getSessionId(): String {
        return "session_${System.currentTimeMillis() / 1000}"
    }

    private fun storeEventLocally(eventData: JSONObject) {
        val eventsKey = "events_${dateFormat.format(Date()).substring(0, 10)}"
        val existingEvents = preferences.getString(eventsKey, "[]")
        try {
            val eventsArray = org.json.JSONArray(existingEvents)
            if (eventsArray.length() >= 100) {
                eventsArray.remove(0)
            }
            eventsArray.put(eventData)
            preferences.edit().putString(eventsKey, eventsArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store event locally", e)
        }
    }

    private fun getEventCount(eventType: String): Int {
        // This is a simple counter, a more robust solution would parse the stored JSON
        val currentCount = preferences.getInt("count_$eventType", 0)
        preferences.edit().putInt("count_$eventType", currentCount + 1).apply()
        return currentCount
    }
}

data class UsageStatistics(
    val firstLaunchDate: String?,
    val totalLaunches: Int,
    val lastActiveDate: String?,
    val daysSinceFirstLaunch: Int,
    val deviceModel: String,
    val androidVersion: String,
    val appVersion: String
)