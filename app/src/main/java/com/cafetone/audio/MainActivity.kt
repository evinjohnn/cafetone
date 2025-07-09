package com.cafetone.audio

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
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
import androidx.lifecycle.Observer
import com.cafetone.audio.analytics.AnalyticsManager
import com.cafetone.audio.databinding.ActivityMainBinding
import com.cafetone.audio.engagement.UserEngagementManager
import com.cafetone.audio.playstore.PlayStoreIntegration
import com.cafetone.audio.service.AppStatus
import com.cafetone.audio.service.CafeModeService
import com.cafetone.audio.test.CafeToneDiagnostic
import com.cafetone.audio.update.UpdateManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
// GUARANTEED FIX: Add missing coroutine imports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    private var analyticsManager: AnalyticsManager? = null
    private var engagementManager: UserEngagementManager? = null
    private var playStoreIntegration: PlayStoreIntegration? = null
    private var updateManager: UpdateManager? = null

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
            checkFirstLaunch()
            engagementManager?.showFirstTimeTutorial(this@MainActivity)
            updateManager?.checkForUpdates(this@MainActivity)
            playStoreIntegration?.requestReviewIfAppropriate(this@MainActivity)

            Log.i(TAG, "Advanced features initialized")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            cafeModeService?.status?.removeObserver(statusObserver)
            cafeModeService = null
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

        val serviceIntent = Intent(this, CafeModeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        if (isBound) {
            cafeModeService?.forceShizukuCheck()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        when (intent?.getStringExtra("action")) {
            "check_update" -> updateManager?.forceRefresh(this)
            "show_feature" -> showFeatureHighlight(intent.getStringExtra("feature"))
            "engagement" -> if (!binding.toggleCafeMode.isChecked) showCafeModeEngagementDialog()
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
            .setMessage("Ready to experience Sony's premium Café Mode audio processing?")
            .setPositiveButton("Enable Café Mode") { _, _ ->
                binding.toggleCafeMode.isChecked = true
                cafeModeService?.toggleCafeMode(this)
            }
            .setNegativeButton("Maybe Later", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        }
    }

    private fun checkFirstLaunch() {
        if (sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)) {
            showGitHubStarDialog()
            sharedPreferences.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            firebaseAnalytics.logEvent("first_open", null)
        }
    }

    private fun showGitHubStarDialog() {
        AlertDialog.Builder(this)
            .setTitle("Support CaféTone")
            .setMessage("Enjoying the app? Please consider starring the project on GitHub.")
            .setPositiveButton("Star on GitHub") { _, _ -> openGitHubUrl() }
            .setNegativeButton("Maybe Later", null)
            .setCancelable(false)
            .show()
    }

    private fun openGitHubUrl() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to open GitHub page", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEventListeners() {
        binding.toggleCafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (binding.toggleCafeMode.isPressed) {
                cafeModeService?.toggleCafeMode(this@MainActivity)
            }
        }
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setIntensity(value / 100f)
                updateIntensityLabel(value.toInt())
            }
        }
        binding.sliderSpatialWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setSpatialWidth(value / 100f)
                updateSpatialWidthLabel(value.toInt())
            }
        }
        binding.sliderDistance.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                cafeModeService?.setDistance(value / 100f)
                updateDistanceLabel(value.toInt())
            }
        }
        binding.btnRefreshStatus.setOnClickListener {
            cafeModeService?.forceShizukuCheck()
        }
        binding.btnShizukuSetup.setOnClickListener {
            engagementManager?.showShizukuTutorial(this@MainActivity)
        }
        binding.btnInfo.setOnClickListener {
            showInfoDialog(this@MainActivity)
        }
        binding.btnSettings.setOnClickListener {
            showSettingsDialog(this@MainActivity)
        }
        binding.btnRefreshStatus.setOnLongClickListener {
            runDiagnosticTest()
            true
        }
    }

    private fun updateSliderUI() {
        cafeModeService?.let {
            val intensity = it.getIntensity() * 100
            val spatialWidth = it.getSpatialWidth() * 100
            val distance = it.getDistance() * 100
            binding.sliderIntensity.value = intensity
            updateIntensityLabel(intensity.toInt())
            binding.sliderSpatialWidth.value = spatialWidth
            updateSpatialWidthLabel(spatialWidth.toInt())
            binding.sliderDistance.value = distance
            updateDistanceLabel(distance.toInt())
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
        updateStatusContent(status)
        binding.toggleCafeMode.isChecked = status.isEnabled
        binding.btnRefreshStatus.visibility = if (status.isShizukuReady) View.GONE else View.VISIBLE
        binding.btnShizukuSetup.visibility = if (status.isShizukuReady) View.GONE else View.VISIBLE
    }

    private fun updateStatusContent(status: AppStatus) {
        when {
            !status.isShizukuReady -> {
                binding.tvStatus.text = "Setup Required"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_warning)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_warning))
                binding.tvStatusSubtitle.text = status.shizukuMessage
            }
            status.isEnabled -> {
                binding.tvStatus.text = "Café Mode Active"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_success))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_active)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_success))
                binding.tvStatusSubtitle.text = "Global audio processing enabled"
            }
            else -> {
                binding.tvStatus.text = "Ready to Use"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.cafe_brown))
                binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_inactive)
                binding.ivStatusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.cafe_brown))
                binding.tvStatusSubtitle.text = "Tap to enable Café Mode"
            }
        }
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
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
            .setMessage("$dspInfo\n\nPerfect for background listening.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSettingsDialog(activity: Activity) {
        val usageStats = analyticsManager?.getUsageStatistics()
        val engagementStats = engagementManager?.getEngagementStats()
        AlertDialog.Builder(activity)
            .setTitle("CaféTone Settings")
            .setMessage("""
                Usage Statistics:
                • Total Launches: ${usageStats?.totalLaunches ?: "N/A"}
                • Café Mode Uses: ${engagementStats?.cafeModeUses ?: "N/A"}
                • Achievements: ${getAchievementCount(engagementStats)}
            """.trimIndent())
            .setPositiveButton("Close", null)
            .show()
    }

    private fun getAchievementCount(stats: com.cafetone.audio.engagement.EngagementStats?): String {
        if (stats == null) return "None yet"
        val achievements = mutableListOf<String>()
        if (stats.milestone5Uses) achievements.add("Enthusiast")
        if (stats.milestone25Uses) achievements.add("Regular")
        if (stats.milestone100Uses) achievements.add("Master")
        return if (achievements.isEmpty()) "None yet" else achievements.joinToString(", ")
    }

    private fun runDiagnosticTest() {
        Toast.makeText(this, "Running diagnostic tests...", Toast.LENGTH_SHORT).show()
        // Use CoroutineScope to run the test off the main thread
        CoroutineScope(Dispatchers.IO).launch {
            val diagnostic = CafeToneDiagnostic(this@MainActivity)
            val result = diagnostic.runDiagnostics()
            // Switch back to the main thread to show the dialog
            runOnUiThread {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Diagnostic Results")
                    .setMessage(result.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.i(TAG, "All permissions granted.")
            } else {
                Log.w(TAG, "Some permissions were denied.")
                Toast.makeText(this, "Some permissions were denied. App may not work as expected.", Toast.LENGTH_LONG).show()
            }
        }
    }
}