package com.cafetone.audio.engagement

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AlertDialog

class UserEngagementManager(private val context: Context) {

    companion object {
        private const val TAG = "UserEngagement"
        private const val PREF_NAME = "cafetone_engagement"
        private const val KEY_TUTORIAL_COMPLETED = "tutorial_completed"
        private const val KEY_FIRST_CAFE_MODE_USE = "first_cafe_mode_use"
        private const val KEY_SHIZUKU_TUTORIAL_SHOWN = "shizuku_tutorial_shown"
        private const val KEY_MILESTONE_5_USES = "milestone_5_uses"
        private const val KEY_MILESTONE_25_USES = "milestone_25_uses"
        private const val KEY_MILESTONE_100_USES = "milestone_100_uses"
        private const val KEY_CAFE_MODE_USES = "cafe_mode_uses"
        private const val KEY_FEEDBACK_REQUESTED = "feedback_requested"
        private const val KEY_LAST_FEEDBACK_REQUEST = "last_feedback_request"
    }

    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // FIX: ADDED MISSING METHODS CALLED BY CafeModeService
    fun recordSessionStart() {
        Log.i(TAG, "User session started.")
    }

    fun recordSessionEnd() {
        Log.i(TAG, "User session ended.")
    }


    fun showFirstTimeTutorial(onComplete: () -> Unit = {}) {
        if (preferences.getBoolean(KEY_TUTORIAL_COMPLETED, false)) {
            return
        }
        val tutorial = TutorialDialog(context) {
            preferences.edit().putBoolean(KEY_TUTORIAL_COMPLETED, true).apply()
            Log.i(TAG, "First-time tutorial completed")
            onComplete()
        }
        tutorial.show()
    }

    fun showShizukuTutorial(onComplete: () -> Unit = {}) {
        if (preferences.getBoolean(KEY_SHIZUKU_TUTORIAL_SHOWN, false)) {
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Shizuku Setup Required")
            .setMessage("""
                CaféTone needs Shizuku for system-wide audio processing.
                
                Here's how to set it up:
                1. Install Shizuku from Play Store
                2. Enable Wireless Debugging in Developer Options
                3. Start Shizuku service
                4. Grant CaféTone permission in Shizuku
                
                This enables the café mode to work with all your apps like Spotify, YouTube Music, etc.
            """.trimIndent())
            .setPositiveButton("Got it") { _, _ ->
                preferences.edit().putBoolean(KEY_SHIZUKU_TUTORIAL_SHOWN, true).apply()
                onComplete()
            }
            .setNeutralButton("Learn More") { _, _ ->
                preferences.edit().putBoolean(KEY_SHIZUKU_TUTORIAL_SHOWN, true).apply()
                onComplete()
            }
            .show()
    }

    fun trackCafeModeUsage(): Boolean {
        val uses = preferences.getInt(KEY_CAFE_MODE_USES, 0) + 1
        preferences.edit().putInt(KEY_CAFE_MODE_USES, uses).apply()
        Log.v(TAG, "Café mode used $uses times")
        return when {
            uses == 5 && !preferences.getBoolean(KEY_MILESTONE_5_USES, false) -> {
                showMilestone("Café Enthusiast!", "You've used café mode 5 times! 🎵")
                preferences.edit().putBoolean(KEY_MILESTONE_5_USES, true).apply()
                true
            }
            uses == 25 && !preferences.getBoolean(KEY_MILESTONE_25_USES, false) -> {
                showMilestone("Café Regular!", "25 café sessions completed! ☕")
                preferences.edit().putBoolean(KEY_MILESTONE_25_USES, true).apply()
                true
            }
            uses == 100 && !preferences.getBoolean(KEY_MILESTONE_100_USES, false) -> {
                showMilestone("Café Master!", "100 café sessions! You're a true audiophile! 🎧")
                preferences.edit().putBoolean(KEY_MILESTONE_100_USES, true).apply()
                true
            }
            else -> false
        }
    }

    private fun showMilestone(title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Awesome!") { _, _ -> }
            .setNeutralButton("Share") { _, _ ->
                shareAchievement(title, message)
            }
            .show()
        Log.i(TAG, "Milestone shown: $title")
    }

    fun requestFeedbackIfAppropriate(): Boolean {
        val uses = preferences.getInt(KEY_CAFE_MODE_USES, 0)
        val lastRequest = preferences.getLong(KEY_LAST_FEEDBACK_REQUEST, 0)
        val feedbackRequested = preferences.getBoolean(KEY_FEEDBACK_REQUESTED, false)
        val daysSinceLastRequest = (System.currentTimeMillis() - lastRequest) / (1000 * 60 * 60 * 24)
        val shouldRequest = uses >= 10 && (!feedbackRequested || daysSinceLastRequest > 60)
        if (shouldRequest) {
            showFeedbackRequest()
            return true
        }
        return false
    }

    private fun showFeedbackRequest() {
        AlertDialog.Builder(context)
            .setTitle("Help Us Improve CaféTone")
            .setMessage("How's your café mode experience? Your feedback helps us make CaféTone even better!")
            .setPositiveButton("Send Feedback") { _, _ ->
                openFeedbackEmail()
                recordFeedbackRequest()
            }
            .setNegativeButton("Maybe Later") { _, _ -> }
            .setNeutralButton("Rate on Play Store") { _, _ ->
                recordFeedbackRequest()
            }
            .show()
    }

    private fun openFeedbackEmail() {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@cafetone.app"))
                putExtra(Intent.EXTRA_SUBJECT, "CaféTone Feedback")
                putExtra(Intent.EXTRA_TEXT, """
                    Hi CaféTone Team,
                    
                    Here's my feedback about the app:
                    
                    [Please share your thoughts here]
                    
                    ---
                    App Version: ${getAppVersion()}
                    Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}
                    Android: ${android.os.Build.VERSION.RELEASE}
                    Uses: ${preferences.getInt(KEY_CAFE_MODE_USES, 0)}
                """.trimIndent())
            }
            context.startActivity(Intent.createChooser(intent, "Send Feedback"))
            Log.i(TAG, "Feedback email opened")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open feedback email", e)
        }
    }

    fun shareAchievement(title: String, message: String) {
        try {
            val shareText = """
                $message
                
                Transforming my audio experience with CaféTone! 🎵☕
                
                Get it on Google Play: https://play.google.com/store/apps/details?id=${context.packageName}
                
                #CaféTone #AudioExperience #CaféMode
            """.trimIndent()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            context.startActivity(Intent.createChooser(intent, "Share Achievement"))
            Log.i(TAG, "Achievement shared: $title")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share achievement", e)
        }
    }

    fun shareApp() {
        try {
            val shareText = """
                Check out CaféTone! 🎵☕
                
                It transforms your audio to sound like it's coming from speakers in a distant café. Perfect for background listening!
                
                Download: https://play.google.com/store/apps/details?id=${context.packageName}
                
                #CaféTone #AudioExperience
            """.trimIndent()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_SUBJECT, "Try CaféTone - Amazing Audio Experience!")
            }
            context.startActivity(Intent.createChooser(intent, "Share CaféTone"))
            Log.i(TAG, "App shared")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share app", e)
        }
    }

    fun exportUserStats(): String {
        val uses = preferences.getInt(KEY_CAFE_MODE_USES, 0)
        val tutorialCompleted = preferences.getBoolean(KEY_TUTORIAL_COMPLETED, false)
        val milestones = mutableListOf<String>()
        if (preferences.getBoolean(KEY_MILESTONE_5_USES, false)) milestones.add("Café Enthusiast")
        if (preferences.getBoolean(KEY_MILESTONE_25_USES, false)) milestones.add("Café Regular")
        if (preferences.getBoolean(KEY_MILESTONE_100_USES, false)) milestones.add("Café Master")
        return """
            CaféTone Usage Statistics
            ========================
            
            Total Café Sessions: $uses
            Tutorial Completed: ${if (tutorialCompleted) "Yes" else "No"}
            Achievements: ${if (milestones.isEmpty()) "None yet" else milestones.joinToString(", ")}
            
            App Version: ${getAppVersion()}
            Export Date: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
        """.trimIndent()
    }

    fun getEngagementStats(): EngagementStats {
        return EngagementStats(
            cafeModeUses = preferences.getInt(KEY_CAFE_MODE_USES, 0),
            tutorialCompleted = preferences.getBoolean(KEY_TUTORIAL_COMPLETED, false),
            shizukuTutorialShown = preferences.getBoolean(KEY_SHIZUKU_TUTORIAL_SHOWN, false),
            milestone5Uses = preferences.getBoolean(KEY_MILESTONE_5_USES, false),
            milestone25Uses = preferences.getBoolean(KEY_MILESTONE_25_USES, false),
            milestone100Uses = preferences.getBoolean(KEY_MILESTONE_100_USES, false),
            feedbackRequested = preferences.getBoolean(KEY_FEEDBACK_REQUESTED, false),
            lastFeedbackRequest = preferences.getLong(KEY_LAST_FEEDBACK_REQUEST, 0)
        )
    }

    fun resetEngagementData() {
        preferences.edit().clear().apply()
        Log.i(TAG, "Engagement data reset")
    }

    private fun recordFeedbackRequest() {
        preferences.edit()
            .putBoolean(KEY_FEEDBACK_REQUESTED, true)
            .putLong(KEY_LAST_FEEDBACK_REQUEST, System.currentTimeMillis())
            .apply()
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}

class TutorialDialog(private val context: Context, private val onComplete: () -> Unit) {
    fun show() {
        AlertDialog.Builder(context)
            .setTitle("Welcome to CaféTone!")
            .setMessage("""
                🎵 Transform your audio experience!
                
                CaféTone makes your music sound like it's coming from speakers in a distant café - perfect for background listening.
                
                ✨ How it works:
                • Toggle Café Mode ON
                • Adjust Intensity, Spatial Width, and Distance
                • Works with Spotify, YouTube Music, and all your apps!
                
                📱 Setup required:
                You'll need to install Shizuku for system-wide audio processing. Don't worry, we'll guide you through it!
                
                Ready to transform your audio? 🎧☕
            """.trimIndent())
            .setPositiveButton("Let's Go!") { _, _ ->
                onComplete()
            }
            .setCancelable(false)
            .show()
    }
}

data class EngagementStats(
    val cafeModeUses: Int,
    val tutorialCompleted: Boolean,
    val shizukuTutorialShown: Boolean,
    val milestone5Uses: Boolean,
    val milestone25Uses: Boolean,
    val milestone100Uses: Boolean,
    val feedbackRequested: Boolean,
    val lastFeedbackRequest: Long
)