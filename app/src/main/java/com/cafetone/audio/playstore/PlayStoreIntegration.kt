package com.cafetone.audio.playstore

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.launch

/**
 * Google Play Store Integration for CaféTone
 * Handles in-app reviews, updates, and Play Store interactions
 */
class PlayStoreIntegration(private val context: Context) {
    
    companion object {
        private const val TAG = "PlayStoreIntegration"
        private const val REVIEW_REQUEST_CODE = 1001
        private const val UPDATE_REQUEST_CODE = 1002
        
        // Preferences keys
        private const val PREF_NAME = "cafetone_playstore"
        private const val KEY_APP_LAUNCHES = "app_launches"
        private const val KEY_LAST_REVIEW_REQUEST = "last_review_request"
        private const val KEY_REVIEW_REQUESTED = "review_requested"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    }
    
    private val reviewManager: ReviewManager = ReviewManagerFactory.create(context)
    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Initialize Play Store integration
     * Call this when app starts
     */
    fun initialize() {
        incrementAppLaunches()
        checkForUpdates()
        
        Log.i(TAG, "Play Store integration initialized")
    }
    
    /**
     * Request in-app review if conditions are met
     * Smart timing: after 3-5 uses, not too frequently
     */
    fun requestReviewIfAppropriate(activity: Activity) {
        val launches = getAppLaunches()
        val lastReviewRequest = preferences.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        val reviewRequested = preferences.getBoolean(KEY_REVIEW_REQUESTED, false)
        val currentTime = System.currentTimeMillis()
        val daysSinceLastRequest = (currentTime - lastReviewRequest) / (1000 * 60 * 60 * 24)
        
        // Conditions for showing review request:
        // 1. App launched 3-5 times OR more than 10 times
        // 2. Haven't requested review in last 30 days
        // 3. Never permanently dismissed
        val shouldRequest = when {
            reviewRequested && daysSinceLastRequest < 30 -> false
            launches in 3..5 -> true
            launches > 10 && daysSinceLastRequest > 30 -> true
            else -> false
        }
        
        if (shouldRequest) {
            Log.i(TAG, "Requesting in-app review (launches: $launches)")
            showInAppReview(activity)
        } else {
            Log.v(TAG, "Review request not appropriate (launches: $launches, days since last: $daysSinceLastRequest)")
        }
    }
    
    /**
     * Force show review request (for manual triggers)
     */
    fun showReviewRequest(activity: Activity) {
        Log.i(TAG, "Manual review request triggered")
        showInAppReview(activity)
    }
    
    /**
     * Show in-app review dialog
     */
    private fun showInAppReview(activity: Activity) {
        val request = reviewManager.requestReviewFlow()
        
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo: ReviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                
                flow.addOnCompleteListener { flowTask ->
                    if (flowTask.isSuccessful) {
                        Log.i(TAG, "In-app review flow completed successfully")
                        recordReviewRequest()
                    } else {
                        Log.e(TAG, "In-app review flow failed", flowTask.exception)
                        // Fallback to Play Store
                        openPlayStore(activity)
                    }
                }
            } else {
                Log.e(TAG, "Failed to request review flow", task.exception)
                // Fallback to Play Store
                openPlayStore(activity)
            }
        }
    }
    
    /**
     * Open Play Store app page for manual review
     */
    fun openPlayStore(activity: Activity) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://details?id=${context.packageName}")
                setPackage("com.android.vending")
            }
            activity.startActivity(intent)
            Log.i(TAG, "Opened Play Store app page")
        } catch (e: Exception) {
            // Fallback to web browser
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                }
                activity.startActivity(intent)
                Log.i(TAG, "Opened Play Store web page")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store", e2)
            }
        }
    }
    
    /**
     * Check for app updates
     */
    fun checkForUpdates(activity: Activity? = null) {
        val lastCheck = preferences.getLong(KEY_LAST_UPDATE_CHECK, 0)
        val currentTime = System.currentTimeMillis()
        val hoursSinceLastCheck = (currentTime - lastCheck) / (1000 * 60 * 60)
        
        // Check for updates every 24 hours
        if (hoursSinceLastCheck < 24 && lastCheck != 0L) {
            Log.v(TAG, "Update check skipped (last check: ${hoursSinceLastCheck}h ago)")
            return
        }
        
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            preferences.edit().putLong(KEY_LAST_UPDATE_CHECK, currentTime).apply()
            
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                Log.i(TAG, "App update available")
                
                if (activity != null) {
                    when {
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) -> {
                            // High priority update - immediate
                            startImmediateUpdate(activity, appUpdateInfo)
                        }
                        appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                            // Regular update - flexible
                            startFlexibleUpdate(activity, appUpdateInfo)
                        }
                    }
                }
            } else {
                Log.v(TAG, "No app update available")
            }
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to check for updates", exception)
        }
    }
    
    /**
     * Start immediate update flow
     */
    private fun startImmediateUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.IMMEDIATE,
                activity,
                UPDATE_REQUEST_CODE
            )
            Log.i(TAG, "Started immediate update flow")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start immediate update", e)
        }
    }
    
    /**
     * Start flexible update flow
     */
    private fun startFlexibleUpdate(activity: Activity, appUpdateInfo: AppUpdateInfo) {
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                AppUpdateType.FLEXIBLE,
                activity,
                UPDATE_REQUEST_CODE
            )
            Log.i(TAG, "Started flexible update flow")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start flexible update", e)
        }
    }
    
    /**
     * Get update availability status
     */
    fun getUpdateStatus(callback: (Boolean, String) -> Unit) {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            val updateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            val statusMessage = when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_AVAILABLE -> "Update available"
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> "App is up to date"
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> "Update in progress"
                else -> "Update status unknown"
            }
            callback(updateAvailable, statusMessage)
        }.addOnFailureListener {
            callback(false, "Unable to check for updates")
        }
    }
    
    /**
     * Get Play Store ratings and reviews info
     */
    fun getPlayStoreInfo(): PlayStoreInfo {
        return PlayStoreInfo(
            packageName = context.packageName,
            playStoreUrl = "https://play.google.com/store/apps/details?id=${context.packageName}",
            appLaunches = getAppLaunches(),
            lastReviewRequest = preferences.getLong(KEY_LAST_REVIEW_REQUEST, 0)
        )
    }
    
    /**
     * Record app launch
     */
    private fun incrementAppLaunches() {
        val launches = getAppLaunches() + 1
        preferences.edit().putInt(KEY_APP_LAUNCHES, launches).apply()
        Log.v(TAG, "App launches: $launches")
    }
    
    /**
     * Get number of app launches
     */
    private fun getAppLaunches(): Int {
        return preferences.getInt(KEY_APP_LAUNCHES, 0)
    }
    
    /**
     * Record review request
     */
    private fun recordReviewRequest() {
        preferences.edit()
            .putLong(KEY_LAST_REVIEW_REQUEST, System.currentTimeMillis())
            .putBoolean(KEY_REVIEW_REQUESTED, true)
            .apply()
    }
    
    /**
     * Reset review request flag (for testing)
     */
    fun resetReviewRequest() {
        preferences.edit()
            .putBoolean(KEY_REVIEW_REQUESTED, false)
            .putLong(KEY_LAST_REVIEW_REQUEST, 0)
            .apply()
        Log.i(TAG, "Review request flag reset")
    }
}

/**
 * Play Store information data class
 */
data class PlayStoreInfo(
    val packageName: String,
    val playStoreUrl: String,
    val appLaunches: Int,
    val lastReviewRequest: Long
)