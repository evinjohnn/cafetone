package com.cafetone.audio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.cafetone.audio.databinding.ActivityMainBinding
import com.cafetone.audio.service.AppStatus
import com.cafetone.audio.service.CafeModeService
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
import com.cafetone.audio.update.UpdateManager
import com.cafetone.audio.test.GlobalAudioProcessingTest
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import com.cafetone.audio.BuildConfig

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PREFS_NAME = "cafetone_prefs"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val GITHUB_URL = "https://github.com/evinjohnignatious/cafetone"
    }

    private lateinit var binding: ActivityMainBinding
    private var cafeModeService: CafeModeService? = null
    private var isBound = false
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var sharedPreferences: SharedPreferences

    // Advanced features (accessed via service)
    private var analyticsManager: AnalyticsManager? = null
    private var engagementManager: UserEngagementManager? = null
    private var playStoreIntegration: PlayStoreIntegration? = null
    private var updateManager: UpdateManager? = null

    // Observer for the service status
    private val statusObserver = Observer<AppStatus> { status ->
        if (status != null) {
            updateStatusUI(status)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CafeModeService.LocalBinder
            cafeModeService = binder.getService()
            isBound = true
            Log.i(TAG, "Service connected")

            // Access advanced features through service
            analyticsManager = cafeModeService?.getAnalyticsManager()
            engagementManager = cafeModeService?.getEngagementManager()
            playStoreIntegration = cafeModeService?.getPlayStoreIntegration()
            updateManager = cafeModeService?.getUpdateManager()

            cafeModeService?.status?.observe(this@MainActivity, statusObserver)
            updateSliderUI()

            // Show first-time tutorial if needed
            engagementManager?.showFirstTimeTutorial()

            // Check for app updates
            updateManager?.checkForUpdates(this@MainActivity)

            // Request review if appropriate
            playStoreIntegration?.requestReviewIfAppropriate(this@MainActivity)

            Log.i(TAG, "Advanced features initialized")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cafeModeService?.status?.removeObserver(statusObserver)
            cafeModeService = null
            analyticsManager = null
            engagementManager = null
            playStoreIntegration = null
            updateManager = null
            isBound = false
            Log.i(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Analytics
        try {
            firebaseAnalytics = Firebase.analytics
            Log.i(TAG, "Firebase Analytics initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Analytics", e)
            // Create a dummy analytics instance to prevent crashes
            firebaseAnalytics = Firebase.analytics
        }

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupEventListeners()
        checkPermissions()

        // Handle intent from notification
        handleNotificationIntent(intent)

        // Check for first launch and show GitHub star dialog
        checkFirstLaunch()

        val serviceIntent = Intent(this, CafeModeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Log app launch event
        firebaseAnalytics.logEvent("app_launch") {
            param("version", packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0")
            param("version_code", packageManager.getPackageInfo(packageName, 0).versionCode.toLong())
        }

        Log.i(TAG, "MainActivity created")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            when (it.getStringExtra("action")) {
                "check_update" -> {
                    // User clicked update notification
                    updateManager?.forceRefresh(this)

                    firebaseAnalytics.logEvent("notification_action") {
                        param("action_type", "check_update")
                    }
                }
                "show_feature" -> {
                    // User clicked feature announcement
                    val feature = it.getStringExtra("feature")
                    showFeatureHighlight(feature)

                    firebaseAnalytics.logEvent("notification_action") {
                        param("action_type", "show_feature")
                        param("feature", feature ?: "unknown")
                    }
                }
                "engagement" -> {
                    // User clicked engagement notification
                    if (!binding.toggleCafeMode.isChecked) {
                        // Suggest enabling café mode
                        showCafeModeEngagementDialog()
                    }

                    firebaseAnalytics.logEvent("notification_action") {
                        param("action_type", "engagement")
                    }
                }
            }
        }
    }

    private fun showFeatureHighlight(feature: String?) {
        when (feature) {
            "global_processing" -> {
                Toast.makeText(this, "🎵 Try the new global audio processing!", Toast.LENGTH_LONG).show()
                // Maybe highlight the toggle or show a tutorial
            }
            "new_sliders" -> {
                Toast.makeText(this, "🎛️ Check out the enhanced audio controls!", Toast.LENGTH_LONG).show()
                // Maybe highlight the controls section
            }
            else -> {
                Toast.makeText(this, "🎉 Discover new features in CaféTone!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCafeModeEngagementDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Transform Your Audio! 🎵")
            .setMessage("Ready to experience Sony's premium Café Mode audio processing? Enable it now and hear the difference!")
            .setPositiveButton("Enable Café Mode") { _, _ ->
                binding.toggleCafeMode.isChecked = true
                cafeModeService?.toggleCafeMode()

                firebaseAnalytics.logEvent("engagement_cafe_mode_enabled") {
                    param("source", "notification_dialog")
                }
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun checkFirstLaunch() {
        val isFirstLaunch = sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)

        if (isFirstLaunch) {
            showGitHubStarDialog()
            // Mark as not first launch anymore
            sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()

            // Log first launch event
            firebaseAnalytics.logEvent("first_launch") {
                param("timestamp", System.currentTimeMillis())
            }
        }
    }

    private fun showGitHubStarDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Support CaféTone")
            .setMessage("Enjoying the app? Please consider starring the project on GitHub. It's a free way to show your support!")
            .setPositiveButton("Star on GitHub") { _, _ ->
                openGitHubUrl()

                // Log GitHub star button click
                firebaseAnalytics.logEvent("github_star_clicked") {
                    param("source", "first_launch_dialog")
                }
            }
            .setNegativeButton("Maybe Later") { _, _ ->
                // Log dialog dismissed
                firebaseAnalytics.logEvent("github_star_dismissed") {
                    param("source", "first_launch_dialog")
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun openGitHubUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open GitHub URL", e)
            Toast.makeText(this, "Failed to open GitHub page", Toast.LENGTH_SHORT).show()

            // Log error
            firebaseAnalytics.logEvent("github_open_failed") {
                param("error", e.message ?: "unknown")
            }
        }
    }

    private fun setupEventListeners() {
        binding.toggleCafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (binding.toggleCafeMode.isPressed) {
                cafeModeService?.toggleCafeMode()

                // Log cafe mode toggle event to Firebase
                firebaseAnalytics.logEvent("cafe_mode_toggled") {
                    param("enabled", isChecked.toString())
                }

                // Log toggle event to internal analytics
                analyticsManager?.logEvent(AnalyticsManager.EVENT_CAFE_MODE_TOGGLE, mapOf(
                    "enabled" to isChecked
                ))
            }
        }

        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setIntensity(value / 100f)
                updateIntensityLabel(value.toInt())

                // Log slider adjustment to Firebase
                firebaseAnalytics.logEvent("slider_adjusted") {
                    param("slider_name", "intensity")
                    param("slider_value", value.toDouble())
                }
            }
        }

        binding.sliderSpatialWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setSpatialWidth(value / 100f)
                updateSpatialWidthLabel(value.toInt())

                // Log slider adjustment to Firebase
                firebaseAnalytics.logEvent("slider_adjusted") {
                    param("slider_name", "spatial_width")
                    param("slider_value", value.toDouble())
                }
            }
        }

        binding.sliderDistance.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setDistance(value / 100f)
                updateDistanceLabel(value.toInt())

                // Log slider adjustment to Firebase
                firebaseAnalytics.logEvent("slider_adjusted") {
                    param("slider_name", "distance")
                    param("slider_value", value.toDouble())
                }
            }
        }

        binding.btnRefreshStatus.setOnClickListener {
            Toast.makeText(this, "Refreshing Shizuku status...", Toast.LENGTH_SHORT).show()
            cafeModeService?.forceShizukuCheck()

            // Log refresh action
            firebaseAnalytics.logEvent("status_refresh_clicked") {}
        }

        binding.btnShizukuSetup.setOnClickListener {
            showShizukuSetupDialog()

            // Log Shizuku setup button click
            firebaseAnalytics.logEvent("shizuku_setup_clicked") {}
            analyticsManager?.logEvent(AnalyticsManager.EVENT_SHIZUKU_SETUP, mapOf("manual_trigger" to true))
        }

        binding.btnInfo.setOnClickListener {
            showInfoDialog()

            // Log about button click
            firebaseAnalytics.logEvent("about_clicked") {}
            analyticsManager?.logEvent(AnalyticsManager.EVENT_ABOUT_OPENED)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog()

            // Log settings button click
            firebaseAnalytics.logEvent("settings_clicked") {}
            analyticsManager?.logEvent(AnalyticsManager.EVENT_SETTINGS_OPENED)
        }
    }

    private fun updateSliderUI() {
        cafeModeService?.let {
            binding.sliderIntensity.value = it.getIntensity() * 100
            binding.sliderSpatialWidth.value = it.getSpatialWidth() * 100
            binding.sliderDistance.value = it.getDistance() * 100
            updateIntensityLabel((it.getIntensity() * 100).toInt())
            updateSpatialWidthLabel((it.getSpatialWidth() * 100).toInt())
            updateDistanceLabel((it.getDistance() * 100).toInt())
        }
    }

    private fun updateIntensityLabel(value: Int) {
        binding.tvIntensityValue.text = "$value%"
    }

    private fun updateSpatialWidthLabel(value: Int) {
        binding.tvSpatialWidthValue.text = "$value%"
    }

    private fun updateDistanceLabel(value: Int) {
        binding.tvDistanceValue.text = "$value%"
    }

    private fun updateStatusUI(status: AppStatus) {
        binding.toggleCafeMode.isChecked = status.isEnabled
        binding.btnRefreshStatus.visibility = if (status.isShizukuReady) View.GONE else View.VISIBLE

        when {
            !status.isShizukuReady -> {
                binding.tvStatus.text = "Shizuku Required"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.orange_500))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_warning)
                binding.tvStatusSubtitle.visibility = View.VISIBLE
                binding.tvStatusSubtitle.text = status.shizukuMessage

                // Show Shizuku tutorial if not ready
                engagementManager?.showShizukuTutorial()
            }
            status.isEnabled -> {
                binding.tvStatus.text = "Sony Café Mode Active"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_500))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_active)
                binding.tvStatusSubtitle.visibility = View.GONE
            }
            else -> {
                binding.tvStatus.text = "Café Mode Inactive"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_inactive)
                binding.tvStatusSubtitle.visibility = View.GONE
            }
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
        }

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }

    private fun showShizukuSetupDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Shizuku Setup")
            .setMessage("""
                CaféTone requires Shizuku for system-wide audio processing. Please install and start the Shizuku service.
                
                This enables Sony Café Mode to work with all your apps like Spotify, YouTube Music, etc.
            """.trimIndent())
            .setPositiveButton("Open Play Store") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInfoDialog() {
        val dspInfo = cafeModeService?.getCafeModeDSP()?.getStatusInfo() ?: "DSP Not Available"

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Sony Café Mode")
            .setMessage("""
                CaféTone transforms your audio to sound like it's coming from speakers in a distant café using Sony's advanced audio processing technology.
                
                $dspInfo
                
                Perfect for background listening while working, studying, or relaxing.
                
                Developed with love for audio enthusiasts 🎵☕
            """.trimIndent())
            .setPositiveButton("OK", null)
            .setNeutralButton("Share App") { _, _ ->
                engagementManager?.shareApp()
            }
            .setNegativeButton("Star on GitHub") { _, _ ->
                openGitHubUrl()

                // Log GitHub star button click from about dialog
                firebaseAnalytics.logEvent("github_star_clicked") {
                    param("source", "about_dialog")
                }
            }
            .show()
    }

    private fun showSettingsDialog() {
        val usageStats = analyticsManager?.getUsageStatistics()
        val engagementStats = engagementManager?.getEngagementStats()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("CaféTone Settings")
            .setMessage("""
                Usage Statistics:
                • Total App Launches: ${usageStats?.totalLaunches ?: 0}
                • Café Mode Uses: ${engagementStats?.cafeModeUses ?: 0}
                • Days Since Install: ${usageStats?.daysSinceFirstLaunch ?: 0}
                
                Advanced Features:
                • Tutorial Completed: ${if (engagementStats?.tutorialCompleted == true) "Yes" else "No"}
                • Achievements: ${getAchievementCount(engagementStats)}
                
                App Version: ${usageStats?.appVersion ?: "Unknown"}
            """.trimIndent())
            .setPositiveButton("Close", null)
            .setNeutralButton("Rate App") { _, _ ->
                playStoreIntegration?.showReviewRequest(this)
            }
            .setNegativeButton("Share Stats") { _, _ ->
                val stats = engagementManager?.exportUserStats() ?: "No stats available"
                shareText("My CaféTone Statistics", stats)
            }
            .show()
    }

    private fun getAchievementCount(stats: com.cafetone.audio.engagement.EngagementStats?): String {
        if (stats == null) return "0"

        val achievements = mutableListOf<String>()
        if (stats.milestone5Uses) achievements.add("Café Enthusiast")
        if (stats.milestone25Uses) achievements.add("Café Regular")
        if (stats.milestone100Uses) achievements.add("Café Master")

        return if (achievements.isEmpty()) "None yet" else achievements.joinToString(", ")
    }

    private fun shareText(title: String, text: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, title)
            }
            startActivity(Intent.createChooser(intent, title))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share text", e)
            Toast.makeText(this, "Failed to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun runGlobalProcessingTests() {
        Toast.makeText(this, "Running global audio processing tests...", Toast.LENGTH_SHORT).show()

        // Log test execution
        firebaseAnalytics.logEvent("global_processing_test_started") {}

        Thread {
            val testSuite = GlobalAudioProcessingTest(this)
            val results = testSuite.runCompleteTestSuite()

            runOnUiThread {
                showTestResults(results)
            }
        }.start()
    }

    private fun showTestResults(results: GlobalAudioProcessingTest.TestResults) {
        val statusIcon = if (results.overallSuccessRate >= 80.0f) "✅" else "⚠️"
        val status = if (results.overallSuccessRate >= 80.0f) "READY" else "NEEDS SETUP"

        // Log test results
        firebaseAnalytics.logEvent("global_processing_test_completed") {
            param("success_rate", results.overallSuccessRate.toDouble())
            param("status", status)
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("$statusIcon Global Processing Test Results")
            .setMessage("""
                Overall Status: $status (${String.format("%.1f", results.overallSuccessRate)}%)
                
                Test Results:
                ${if (results.globalEffectCreation) "✅" else "❌"} Global AudioEffect Creation
                ${if (results.effectRegistration) "✅" else "❌"} Effect Registration  
                ${if (results.realTimeLatency) "✅" else "❌"} Real-Time Latency (<10ms)
                ${if (results.streamInterception) "✅" else "❌"} Stream Interception
                ${if (results.spotifyCompatibility) "✅" else "❌"} Spotify Compatibility
                
                ${if (results.overallSuccessRate >= 80.0f)
                "🎉 CaféTone is working like Wavelet/RootlessJamesDSP!"
            else
                "Configure Shizuku permissions for full functionality."}
            """.trimIndent())
            .setPositiveButton("OK", null)
            .setNeutralButton("Share Results") { _, _ ->
                val testReport = "CaféTone Global Processing Test: $status (${String.format("%.1f", results.overallSuccessRate)}%)"
                shareText("CaféTone Test Results", testReport)
            }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.i(TAG, "All permissions granted")
                Toast.makeText(this, "Permissions granted! CaféTone is ready.", Toast.LENGTH_SHORT).show()

                // Log permissions granted
                firebaseAnalytics.logEvent("permissions_granted") {
                    param("count", permissions.size.toLong())
                }
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show()

                // Log permissions denied
                firebaseAnalytics.logEvent("permissions_denied") {
                    param("denied_count", grantResults.count { it != PackageManager.PERMISSION_GRANTED }.toLong())
                }
            }
        }
    }
}