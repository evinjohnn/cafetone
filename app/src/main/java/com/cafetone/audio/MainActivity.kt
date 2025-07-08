package com.cafetone.audio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private lateinit var binding: ActivityMainBinding
    private var cafeModeService: CafeModeService? = null
    private var isBound = false

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

            cafeModeService?.status?.observe(this@MainActivity, statusObserver)
            updateSliderUI()
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

        setupEventListeners()
        checkPermissions()

        val serviceIntent = Intent(this, CafeModeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupEventListeners() {
        binding.toggleCafeMode.setOnCheckedChangeListener { _, _ ->
            if (binding.toggleCafeMode.isPressed) {
                cafeModeService?.toggleCafeMode()
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
            Toast.makeText(this, "Refreshing Shizuku status...", Toast.LENGTH_SHORT).show()
            cafeModeService?.forceShizukuCheck()
        }

        binding.btnShizukuSetup.setOnClickListener { showShizukuSetupDialog() }
        binding.btnInfo.setOnClickListener { showInfoDialog() }
        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
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
            }
            status.isEnabled -> {
                binding.tvStatus.text = "Café Mode Active"
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
            .setMessage("CaféTone requires Shizuku for system-wide audio processing. Please install and start the Shizuku service.")
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
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Café Mode")
            .setMessage("Café Mode transforms your audio to sound like it's coming from speakers in a distant café.")
            .setPositiveButton("OK", null)
            .show()
    }
}