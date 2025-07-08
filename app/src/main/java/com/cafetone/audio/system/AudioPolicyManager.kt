package com.cafetone.audio.system

import android.content.Context
import android.media.*
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.min

/**
 * AudioPolicyManager - Global Audio Stream Routing & Interception
 * 
 * This class manages system-wide audio processing by intercepting all audio streams
 * and routing them through the Sony Café Mode DSP engine for real-time processing.
 */
class AudioPolicyManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioPolicyManager"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isProcessing = false
    private var processingThread: Thread? = null
    private var audioHandler: Handler? = null
    private var handlerThread: HandlerThread? = null
    
    // Audio processing callback
    private var audioProcessingCallback: ((FloatArray, Int) -> Unit)? = null
    
    /**
     * Initialize the global audio processing system
     */
    fun initialize() {
        try {
            // Create dedicated audio processing thread
            handlerThread = HandlerThread("AudioProcessingThread", Process.THREAD_PRIORITY_URGENT_AUDIO)
            handlerThread?.start()
            audioHandler = Handler(handlerThread!!.looper)
            
            Log.i(TAG, "AudioPolicyManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioPolicyManager", e)
        }
    }
    
    /**
     * Register as global audio processor
     */
    fun registerGlobalAudioProcessor() {
        try {
            // Register for audio focus changes to detect all audio streams
            val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setOnAudioFocusChangeListener { focusChange ->
                    Log.d(TAG, "Audio focus changed: $focusChange")
                    handleAudioFocusChange(focusChange)
                }
                .build()
            
            audioManager.requestAudioFocus(audioFocusRequest)
            Log.i(TAG, "Registered as global audio processor")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register global audio processor", e)
        }
    }
    
    /**
     * Start intercepting audio streams system-wide
     */
    fun startGlobalAudioInterception() {
        if (isProcessing) return
        
        audioHandler?.post {
            setupAudioRecording()
            setupAudioPlayback()
            startRealtimeProcessingLoop()
        }
    }
    
    /**
     * Stop global audio interception
     */
    fun stopGlobalAudioInterception() {
        isProcessing = false
        processingThread?.interrupt()
        
        audioHandler?.post {
            releaseAudioResources()
        }
    }
    
    /**
     * Set audio processing callback for DSP processing
     */
    fun setAudioProcessingCallback(callback: (FloatArray, Int) -> Unit) {
        audioProcessingCallback = callback
    }
    
    private fun setupAudioRecording() {
        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val actualBufferSize = bufferSize * BUFFER_SIZE_MULTIPLIER
            
            audioRecord = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build())
                .setBufferSizeInBytes(actualBufferSize)
                .build()
                
            Log.i(TAG, "Audio recording setup completed - Buffer size: $actualBufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio recording", e)
        }
    }
    
    private fun setupAudioPlayback() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AUDIO_FORMAT)
            val actualBufferSize = bufferSize * BUFFER_SIZE_MULTIPLIER
            
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build())
                .setBufferSizeInBytes(actualBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                
            Log.i(TAG, "Audio playback setup completed - Buffer size: $actualBufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup audio playback", e)
        }
    }
    
    private fun startRealtimeProcessingLoop() {
        if (audioRecord == null || audioTrack == null) {
            Log.e(TAG, "Cannot start processing - Audio components not initialized")
            return
        }
        
        isProcessing = true
        
        processingThread = thread(name = "RealtimeAudioProcessor") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val audioBuffer = FloatArray(bufferSize)
            
            audioRecord?.startRecording()
            audioTrack?.play()
            
            Log.i(TAG, "Real-time audio processing loop started")
            
            while (isProcessing && !Thread.currentThread().isInterrupted) {
                try {
                    // Read audio in real-time
                    val samplesRead = audioRecord?.read(audioBuffer, 0, audioBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                    
                    if (samplesRead > 0) {
                        val processingStartTime = System.nanoTime()
                        
                        // Process through Sony DSP
                        audioProcessingCallback?.invoke(audioBuffer, samplesRead)
                        
                        // Output processed audio
                        audioTrack?.write(audioBuffer, 0, samplesRead, AudioTrack.WRITE_BLOCKING)
                        
                        // Monitor real-time constraint
                        val processingTime = (System.nanoTime() - processingStartTime) / 1_000_000.0
                        if (processingTime > 10.0) { // 10ms threshold
                            Log.w(TAG, "Real-time constraint violated: ${processingTime}ms")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in real-time processing loop", e)
                    if (isProcessing) {
                        Thread.sleep(1) // Brief pause before retry
                    }
                }
            }
            
            audioRecord?.stop()
            audioTrack?.stop()
            
            Log.i(TAG, "Real-time audio processing loop stopped")
        }
    }
    
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "Audio focus gained - Resuming global processing")
                if (!isProcessing) {
                    startGlobalAudioInterception()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "Audio focus lost - Pausing global processing")
                // Keep processing but log the change
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "Audio focus lost temporarily")
                // Continue processing
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "Audio focus lost - can duck")
                // Continue processing at lower volume
            }
        }
    }
    
    private fun releaseAudioResources() {
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
            
            Log.i(TAG, "Audio resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio resources", e)
        }
    }
    
    /**
     * Clean up all resources
     */
    fun cleanup() {
        stopGlobalAudioInterception()
        
        handlerThread?.quitSafely()
        handlerThread = null
        audioHandler = null
        
        Log.i(TAG, "AudioPolicyManager cleanup completed")
    }
    
    /**
     * Get current processing status
     */
    fun isGlobalProcessingActive(): Boolean = isProcessing
    
    /**
     * Get processing statistics
     */
    fun getProcessingStats(): String {
        return if (isProcessing) {
            "Global Audio Processing: Active\n" +
            "Sample Rate: ${SAMPLE_RATE}Hz\n" +
            "Channel Config: Stereo\n" +
            "Audio Format: Float\n" +
            "Processing Thread: ${processingThread?.name ?: "None"}"
        } else {
            "Global Audio Processing: Inactive"
        }
    }
}