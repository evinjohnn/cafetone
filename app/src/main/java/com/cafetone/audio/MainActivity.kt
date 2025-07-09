package com.cafetone.audio

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.databinding.ActivityMainBinding
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
import com.cafetone.audio.service.AppStatus
import com.cafetone.audio.service.CafeModeService
import com.cafetone.audio.test.GlobalAudioProcessingTest
import com.cafetone.audio.test.CafeToneDiagnostic
import com.cafetone.audio.update.UpdateManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

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
        updateStatusUI(status)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CafeModeService.LocalBinder
            cafeModeService = binder.getService()
            isBound = true
            Log.i(TAG, "Service connected")

            analyticsManager = cafeModeService?.getAnalyticsManager()
            engagementManager = cafeModeService?.getEngagementManager()
            playStoreIntegration = cafeModeService?.getPlayStoreIntegration()
            updateManager = cafeModeService?.getUpdateManager()

            cafeModeService?.status?.observe(this@MainActivity, statusObserver)
            updateSliderUI()

            engagementManager?.showFirstTimeTutorial(this@MainActivity)
            updateManager?.checkForUpdates(this@MainActivity)
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

        firebaseAnalytics = Firebase.analytics
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupEventListeners()
        checkPermissions()
        handleNotificationIntent(intent)
        checkFirstLaunch()

        val serviceIntent = Intent(this, CafeModeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            firebaseAnalytics.logEvent("app_launch", bundleOf(
                "version" to (packageInfo.versionName ?: "1.0"),
                "version_code" to PackageInfoCompat.getLongVersionCode(packageInfo)
            ))
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Failed to get package info", e)
        }

        Log.i(TAG, "MainActivity created")
    }

    override fun onResume() {
        super.onResume()
        if (isBound) {
            Log.d(TAG, "onResume: Forcing Shizuku status check.")
            cafeModeService?.forceShizukuCheck()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent?.let {
            when (it.getStringExtra("action")) {
                "check_update" -> {
                    updateManager?.forceRefresh(this)
                    firebaseAnalytics.logEvent("notification_action", bundleOf("action_type" to "check_update"))
                }
                "show_feature" -> {
                    val feature = it.getStringExtra("feature")
                    showFeatureHighlight(feature)
                    firebaseAnalytics.logEvent("notification_action", bundleOf(
                        "action_type" to "show_feature",
                        "feature" to (feature ?: "unknown")
                    ))
                }
                "engagement" -> {
                    if (!binding.toggleCafeMode.isChecked) {
                        showCafeModeEngagementDialog()
                    }
                    firebaseAnalytics.logEvent("notification_action", bundleOf("action_type" to "engagement"))
                }
            }
        }
    }

    private fun showFeatureHighlight(feature: String?) {
        val message = when (feature) {
            "global_processing" -> "🎵 Try the new global audio processing!"
            "new_sliders" -> "🎛️ Check out the enhanced audio controls!"
            else -> "🎉 Discover new features in CaféTone!"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showCafeModeEngagementDialog() {
        AlertDialog.Builder(this)
            .setTitle("Transform Your Audio! 🎵")
            .setMessage("Ready to experience Sony's premium Café Mode audio processing? Enable it now and hear the difference!")
            .setPositiveButton("Enable Café Mode") { _, _ ->
                binding.toggleCafeMode.isChecked = true
                cafeModeService?.toggleCafeMode(this)
                firebaseAnalytics.logEvent("engagement_cafe_mode_enabled", bundleOf("source" to "notification_dialog"))
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
        if (sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)) {
            showGitHubStarDialog()
            sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            firebaseAnalytics.logEvent(FirebaseAnalytics.Event.FIRST_LAUNCH, bundleOf("timestamp" to System.currentTimeMillis()))
        }
    }

    private fun showGitHubStarDialog() {
        AlertDialog.Builder(this)
            .setTitle("Support CaféTone")
            .setMessage("Enjoying the app? Please consider starring the project on GitHub. It's a free way to show your support!")
            .setPositiveButton("Star on GitHub") { _, _ ->
                openGitHubUrl()
                firebaseAnalytics.logEvent("github_star_clicked", bundleOf("source" to "first_launch_dialog"))
            }
            .setNegativeButton("Maybe Later") { _, _ ->
                firebaseAnalytics.logEvent("github_star_dismissed", bundleOf("source" to "first_launch_dialog"))
            }
            .setCancelable(false)
            .show()
    }

    private fun openGitHubUrl() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open GitHub URL", e)
            Toast.makeText(this, "Failed to open GitHub page", Toast.LENGTH_SHORT).show()
            firebaseAnalytics.logEvent("github_open_failed", bundleOf("error" to (e.message ?: "unknown")))
        }
    }

    private fun setupEventListeners() {
        binding.toggleCafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (binding.toggleCafeMode.isPressed) {
                cafeModeService?.toggleCafeMode(this@MainActivity)
                firebaseAnalytics.logEvent("cafe_mode_toggled", bundleOf("enabled" to isChecked.toString()))
                analyticsManager?.logEvent(AnalyticsManager.EVENT_CAFE_MODE_TOGGLE, mapOf("enabled" to isChecked))
            }
        }

        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setIntensity(value / 100f)
                updateIntensityLabel(value.toInt())
                firebaseAnalytics.logEvent("slider_adjusted", bundleOf("slider_name" to "intensity", "slider_value" to value.toDouble()))
            }
        }

        binding.sliderSpatialWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setSpatialWidth(value / 100f)
                updateSpatialWidthLabel(value.toInt())
                firebaseAnalytics.logEvent("slider_adjusted", bundleOf("slider_name" to "spatial_width", "slider_value" to value.toDouble()))
            }
        }

        binding.sliderDistance.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setDistance(value / 100f)
                updateDistanceLabel(value.toInt())
                firebaseAnalytics.logEvent("slider_adjusted", bundleOf("slider_name" to "distance", "slider_value" to value.toDouble()))
            }
        }

        binding.btnRefreshStatus.setOnClickListener {
            Toast.makeText(this, "Refreshing Shizuku status...", Toast.LENGTH_SHORT).show()
            cafeModeService?.forceShizukuCheck()
            firebaseAnalytics.logEvent("status_refresh_clicked", null)
        }

        binding.btnShizukuSetup.setOnClickListener {
            engagementManager?.showShizukuTutorial(this@MainActivity)
            firebaseAnalytics.logEvent("shizuku_setup_clicked", null)
            analyticsManager?.logEvent(AnalyticsManager.EVENT_SHIZUKU_SETUP, mapOf("manual_trigger" to true))
        }

        binding.btnInfo.setOnClickListener {
            showInfoDialog(this@MainActivity)
            firebaseAnalytics.logEvent("about_clicked", null)
            analyticsManager?.logEvent(AnalyticsManager.EVENT_ABOUT_OPENED)
        }

        binding.btnSettings.setOnClickListener {
            showSettingsDialog(this@MainActivity)
            firebaseAnalytics.logEvent("settings_clicked", null)
            analyticsManager?.logEvent(AnalyticsManager.EVENT_SETTINGS_OPENED)
        }
        
        // Add diagnostic button functionality
        binding.btnRefreshStatus.setOnLongClickListener {
            runDiagnosticTest()
            true
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

    private fun updateIntensityLabel(value: Int) { binding.tvIntensityValue.text = "$value%" }
    private fun updateSpatialWidthLabel(value: Int) { binding.tvSpatialWidthValue.text = "$value%" }
    private fun updateDistanceLabel(value: Int) { binding.tvDistanceValue.text = "$value%" }

    private fun updateStatusUI(status: AppStatus) {
        // Animate status changes
        binding.tvStatus.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                updateStatusContent(status)
                binding.tvStatus.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
        
        // Update toggle without animation interference
        binding.toggleCafeMode.isChecked = status.isEnabled
        
        // Handle refresh button visibility
        binding.btnRefreshStatus.visibility = if (status.isShizukuReady) View.GONE else View.VISIBLE
        
        // Show engagement tutorials based on status
        if (isBound && !status.isShizukuReady) {
            engagementManager?.showShizukuTutorial(this)
        }
    }
    
    private fun updateStatusContent(status: AppStatus) {
        when {
            !status.isShizukuReady -> {
                binding.tvStatus.text = "Setup Required"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_warning)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_warning))
                binding.tvStatusSubtitle.visibility = View.VISIBLE
                binding.tvStatusSubtitle.text = status.shizukuMessage
                binding.tvStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
            }
            status.isEnabled -> {
                binding.tvStatus.text = "Café Mode Active"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_active)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_success))
                binding.tvStatusSubtitle.visibility = View.VISIBLE
                binding.tvStatusSubtitle.text = "Audio processing enabled"
                binding.tvStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_success))
            }
            else -> {
                binding.tvStatus.text = "Ready to Use"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.cafe_brown))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_inactive)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.cafe_brown))
                binding.tvStatusSubtitle.visibility = View.VISIBLE
                binding.tvStatusSubtitle.text = "Tap to enable Café Mode"
                binding.tvStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.cafe_warm))
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

    private fun showInfoDialog(activity: Activity) {
        val dspInfo = cafeModeService?.getCafeModeDSP()?.getStatusInfo() ?: "DSP Not Available"

        AlertDialog.Builder(activity)
            .setTitle("About Sony Café Mode")
            .setMessage("""
                CaféTone transforms your audio to sound like it's coming from speakers in a distant café using Sony's advanced audio processing technology.
                
                $dspInfo
                
                Perfect for background listening while working, studying, or relaxing.
                
                Developed with love for audio enthusiasts 🎵☕
            """.trimIndent())
            .setPositiveButton("OK", null)
            .setNeutralButton("Share App") { _, _ -> engagementManager?.shareApp() }
            .setNegativeButton("Star on GitHub") { _, _ ->
                openGitHubUrl()
                firebaseAnalytics.logEvent("github_star_clicked", bundleOf("source" to "about_dialog"))
            }
            .show()
    }

    private fun showSettingsDialog(activity: Activity) {
        val usageStats = analyticsManager?.getUsageStatistics()
        val engagementStats = engagementManager?.getEngagementStats()

        AlertDialog.Builder(activity)
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
            .setNeutralButton("Rate App") { _, _ -> playStoreIntegration?.showReviewRequest(this) }
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

    private fun runDiagnosticTest() {
        Toast.makeText(this, "Running CaféTone diagnostic tests...", Toast.LENGTH_SHORT).show()
        
        Thread {
            val diagnostic = CafeToneDiagnostic(this)
            val result = diagnostic.runDiagnostics()
            
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("CaféTone Diagnostic Results")
                    .setMessage("""
                        ${result.overallStatus}
                        
                        Test Results:
                        ${if (result.shizukuAvailable) "✅" else "❌"} Shizuku Available
                        ${if (result.shizukuPermissionGranted) "✅" else "❌"} Shizuku Permission Granted
                        ${if (result.dspInitialized) "✅" else "❌"} DSP Initialized
                        ${if (result.audioPermissionsGranted) "✅" else "❌"} Audio Permissions Granted
                        
                        ${when {
                            !result.shizukuAvailable -> "Install Shizuku from Play Store and enable it in settings."
                            !result.shizukuPermissionGranted -> "Grant Shizuku permission when prompted."
                            !result.dspInitialized -> "DSP initialization failed - this may be expected in emulator."
                            !result.audioPermissionsGranted -> "Audio permissions will be granted automatically via Shizuku."
                            else -> "All systems ready! Try enabling Café Mode."
                        }}
                    """.trimIndent())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private fun runGlobalProcessingTests() {
        Toast.makeText(this, "Running global audio processing tests...", Toast.LENGTH_SHORT).show()
        firebaseAnalytics.logEvent("global_processing_test_started", null)

        Thread {
            val testSuite = GlobalAudioProcessingTest(this)
            val results = testSuite.runCompleteTestSuite()
            runOnUiThread { showTestResults(results) }
        }.start()
    }

    private fun showTestResults(results: GlobalAudioProcessingTest.TestResults) {
        val statusIcon = if (results.overallSuccessRate >= 80.0f) "✅" else "⚠️"
        val status = if (results.overallSuccessRate >= 80.0f) "READY" else "NEEDS SETUP"

        firebaseAnalytics.logEvent("global_processing_test_completed", bundleOf(
            "success_rate" to results.overallSuccessRate.toDouble(),
            "status" to status
        ))

        AlertDialog.Builder(this)
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
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i(TAG, "All permissions granted")
                Toast.makeText(this, "Permissions granted! CaféTone is ready.", Toast.LENGTH_SHORT).show()
                firebaseAnalytics.logEvent("permissions_granted", bundleOf("count" to permissions.size.toLong()))
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "Some permissions denied. App may not work properly.", Toast.LENGTH_LONG).show()
                firebaseAnalytics.logEvent("permissions_denied", bundleOf("denied_count" to grantResults.count { it != PackageManager.PERMISSION_GRANTED }.toLong()))
            }
        }
    }
}