package com.cafetone.audio

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cafetone.audio.databinding.ActivityMainBinding
import com.cafetone.audio.service.CafeModeService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 100
    }
    
    private lateinit var binding: ActivityMainBinding
    private var cafeModeService: CafeModeService? = null
    private var isBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CafeModeService.LocalBinder
            cafeModeService = binder.getService()
            isBound = true
            Log.i(TAG, "Service connected")
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            cafeModeService = null
            isBound = false
            Log.i(TAG, "Service disconnected")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    override fun onStart() {
        super.onStart()
        bindService()
    }
    
    override fun onStop() {
        super.onStop()
        unbindService()
    }
    
    /**
     * Setup UI components and event listeners
     */
    private fun setupUI() {
        // Master toggle
        binding.toggleCafeMode.setOnCheckedChangeListener { _, isChecked ->
            if (isBound) {
                cafeModeService?.let { service ->
                    if (isChecked != service.isEnabled()) {
                        service.toggleCafeMode()
                        updateToggleState()
                    }
                }
            }
        }
        
        // Intensity slider
        binding.sliderIntensity.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isBound) {
                cafeModeService?.setIntensity(value / 100f)
                updateIntensityLabel(value.toInt())
            }
        }
        
        // Spatial width slider
        binding.sliderSpatialWidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isBound) {
                cafeModeService?.setSpatialWidth(value / 100f)
                updateSpatialWidthLabel(value.toInt())
            }
        }
        
        // Distance slider
        binding.sliderDistance.addOnChangeListener { _, value, fromUser ->
            if (fromUser && isBound) {
                cafeModeService?.setDistance(value / 100f)
                updateDistanceLabel(value.toInt())
            }
        }
        
        // Settings button
        binding.btnSettings.setOnClickListener {
            // TODO: Open settings activity
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }
        
        // Info button
        binding.btnInfo.setOnClickListener {
            showInfoDialog()
        }
    }
    
    /**
     * Check and request necessary permissions
     */
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }
    
    /**
     * Bind to the Café Mode service
     */
    private fun bindService() {
        val intent = Intent(this, CafeModeService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Unbind from the service
     */
    private fun unbindService() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
    
    /**
     * Update UI with current service state
     */
    private fun updateUI() {
        if (!isBound) return
        
        cafeModeService?.let { service ->
            // Update toggle state
            binding.toggleCafeMode.isChecked = service.isEnabled()
            
            // Update sliders
            binding.sliderIntensity.value = (service.getIntensity() * 100).toFloat()
            binding.sliderSpatialWidth.value = (service.getSpatialWidth() * 100).toFloat()
            binding.sliderDistance.value = (service.getDistance() * 100).toFloat()
            
            // Update labels
            updateIntensityLabel((service.getIntensity() * 100).toInt())
            updateSpatialWidthLabel((service.getSpatialWidth() * 100).toInt())
            updateDistanceLabel((service.getDistance() * 100).toInt())
            
            // Update status including Shizuku status
            updateStatus(service.isEnabled(), service.isShizukuReady(), service.getShizukuStatus())
        }
    }
    
    /**
     * Update toggle button state
     */
    private fun updateToggleState() {
        if (isBound) {
            cafeModeService?.let { service ->
                binding.toggleCafeMode.isChecked = service.isEnabled()
                updateStatus(service.isEnabled())
            }
        }
    }
    
    /**
     * Update intensity label
     */
    private fun updateIntensityLabel(value: Int) {
        binding.tvIntensityValue.text = "$value%"
    }
    
    /**
     * Update spatial width label
     */
    private fun updateSpatialWidthLabel(value: Int) {
        binding.tvSpatialWidthValue.text = "$value%"
    }
    
    /**
     * Update distance label
     */
    private fun updateDistanceLabel(value: Int) {
        binding.tvDistanceValue.text = "$value%"
    }
    
    /**
     * Update status display
     */
    private fun updateStatus(enabled: Boolean) {
        if (enabled) {
            binding.tvStatus.text = "Café Mode Active"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green_500))
            binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_active)
        } else {
            binding.tvStatus.text = "Café Mode Inactive"
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
            binding.ivStatusIcon.setImageResource(R.drawable.ic_cafe_inactive)
        }
    }
    
    /**
     * Show information dialog
     */
    private fun showInfoDialog() {
        val message = """
            Café Mode transforms your audio to sound like it's coming from speakers in a distant café.
            
            Features:
            • Psychoacoustic distance simulation
            • Spatial audio widening
            • Café-like frequency response
            • Early reflections and room acoustics
            
            Adjust the sliders to fine-tune the effect to your preference.
        """.trimIndent()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("About Café Mode")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            
            if (allGranted) {
                Log.i(TAG, "All permissions granted")
            } else {
                Log.w(TAG, "Some permissions denied")
                Toast.makeText(this, "Some permissions are required for full functionality", Toast.LENGTH_LONG).show()
            }
        }
    }
} 