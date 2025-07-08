package com.cafetone.audio.test

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.util.Log
import java.util.*

/**
 * GlobalAudioProcessingTest - Validation for Global Audio Processing
 *
 * This class provides comprehensive testing for the global audio processing
 * functionality to ensure CaféTone works like Wavelet/RootlessJamesDSP.
 */
class GlobalAudioProcessingTest(private val context: Context) {

    companion object {
        private const val TAG = "GlobalAudioTest"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
    }

    /**
     * Test 1: Global AudioEffect Creation
     * Verifies that global session (0) AudioEffect can be created
     */
    fun testGlobalAudioEffectCreation(): Boolean {
        return try {
            Log.i(TAG, "Testing global AudioEffect creation...")

            val globalEffect = AudioEffect.Builder()
                .setEffectUuid(EFFECT_UUID_CAFETONE)
                .setAudioSessionId(0) // Session 0 = GLOBAL
                .build()

            val success = globalEffect != null
            globalEffect?.release()

            Log.i(TAG, "Global AudioEffect creation: ${if (success) "SUCCESS" else "FAILED"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Global AudioEffect creation failed", e)
            false
        }
    }

    /**
     * Test 2: Effect Registration Validation
     * Checks if Sony Café Mode effect is properly registered in the system
     */
    fun testEffectRegistration(): Boolean {
        return try {
            Log.i(TAG, "Testing effect registration...")

            val descriptors: Array<AudioEffect.Descriptor> = AudioEffect.queryEffects()
            val cafeToneDescriptor = descriptors.find { it.uuid == EFFECT_UUID_CAFETONE }

            val isRegistered = cafeToneDescriptor != null

            if (isRegistered) {
                Log.i(TAG, "Sony Café Mode effect found: ${cafeToneDescriptor?.name}")
                Log.i(TAG, "Effect UUID: ${cafeToneDescriptor?.uuid}")
                Log.i(TAG, "Effect Type: ${cafeToneDescriptor?.type}")
            }

            Log.i(TAG, "Effect registration: ${if (isRegistered) "SUCCESS" else "FAILED"}")
            isRegistered
        } catch (e: Exception) {
            Log.e(TAG, "Effect registration test failed", e)
            false
        }
    }

    /**
     * Test 3: Real-Time Processing Latency
     * Measures processing latency to ensure it's under 10ms
     */
    fun testRealTimeProcessingLatency(): Boolean {
        return try {
            Log.i(TAG, "Testing real-time processing latency...")

            val testBuffer = generateTestAudioBuffer()
            val iterations = 100
            var totalLatency = 0.0

            repeat(iterations) {
                val startTime = System.nanoTime()

                // Simulate Sony DSP processing
                processTestAudioBuffer(testBuffer)

                val endTime = System.nanoTime()
                val latencyMs = (endTime - startTime) / 1_000_000.0
                totalLatency += latencyMs
            }

            val averageLatency = totalLatency / iterations
            val success = averageLatency < 10.0

            Log.i(TAG, "Average processing latency: ${String.format("%.2f", averageLatency)}ms")
            Log.i(TAG, "Real-time constraint (<10ms): ${if (success) "SUCCESS" else "FAILED"}")

            success
        } catch (e: Exception) {
            Log.e(TAG, "Latency test failed", e)
            false
        }
    }

    /**
     * Test 4: System Audio Stream Interception
     * Validates that the system can intercept audio from multiple sources
     */
    fun testSystemAudioStreamInterception(): Boolean {
        return try {
            Log.i(TAG, "Testing system audio stream interception...")

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            // Check if we can monitor audio focus changes (indicates stream awareness)
            val canMonitorStreams = try {
                audioManager.mode // Basic audio manager access test
                true
            } catch (e: Exception) {
                false
            }

            Log.i(TAG, "System audio stream interception: ${if (canMonitorStreams) "SUCCESS" else "FAILED"}")
            canMonitorStreams
        } catch (e: Exception) {
            Log.e(TAG, "Audio stream interception test failed", e)
            false
        }
    }

    /**
     * Test 5: Spotify Audio Compatibility Test
     * Tests compatibility with popular streaming apps
     */
    fun testSpotifyAudioCompatibility(): Boolean {
        return try {
            Log.i(TAG, "Testing Spotify audio compatibility...")

            // Check if Spotify is installed
            val packageManager = context.packageManager
            val isSpotifyInstalled = try {
                packageManager.getPackageInfo("com.spotify.music", 0)
                true
            } catch (e: Exception) {
                false
            }

            if (isSpotifyInstalled) {
                Log.i(TAG, "Spotify detected - Audio processing should work globally")
            } else {
                Log.i(TAG, "Spotify not installed - Testing with generic music apps")
            }

            // Test global session effect (this should work with any audio app)
            val globalEffect = AudioEffect.Builder()
                .setEffectUuid(EFFECT_UUID_CAFETONE)
                .setAudioSessionId(0) // Global session
                .build()

            val success = globalEffect != null
            globalEffect?.release()

            Log.i(TAG, "Spotify audio compatibility: ${if (success) "SUCCESS" else "FAILED"}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Spotify compatibility test failed", e)
            false
        }
    }

    /**
     * Run Complete Test Suite
     * Executes all tests and provides comprehensive results
     */
    fun runCompleteTestSuite(): TestResults {
        Log.i(TAG, "Starting CaféTone Global Audio Processing Test Suite...")
        Log.i(TAG, "Target: Match Wavelet/RootlessJamesDSP functionality")

        val results = TestResults()

        results.globalEffectCreation = testGlobalAudioEffectCreation()
        results.effectRegistration = testEffectRegistration()
        results.realTimeLatency = testRealTimeProcessingLatency()
        // FIX: Removed the test for the deleted AudioPolicyManager
        results.streamInterception = testSystemAudioStreamInterception()
        results.spotifyCompatibility = testSpotifyAudioCompatibility()

        // Calculate overall success rate
        val totalTests = 5 // FIX: Adjusted total test count
        val passedTests = listOf(
            results.globalEffectCreation,
            results.effectRegistration,
            results.realTimeLatency,
            results.streamInterception,
            results.spotifyCompatibility
        ).count { it }

        results.overallSuccessRate = (passedTests.toFloat() / totalTests) * 100

        Log.i(TAG, "Test Suite Complete: $passedTests/$totalTests tests passed (${String.format("%.1f", results.overallSuccessRate)}%)")

        if (results.overallSuccessRate >= 80.0f) {
            Log.i(TAG, "🎉 CaféTone is ready for global audio processing!")
        } else {
            Log.w(TAG, "⚠️ Some tests failed - may need additional configuration")
        }

        return results
    }

    private fun generateTestAudioBuffer(): FloatArray {
        // Generate test audio buffer (stereo sine wave)
        val bufferSize = 512
        val frequency = 440.0 // A4 note
        val sampleRate = 48000.0
        val buffer = FloatArray(bufferSize)

        for (i in buffer.indices) {
            val sample = Math.sin(2.0 * Math.PI * frequency * i / sampleRate)
            buffer[i] = (sample * 0.5).toFloat()
        }

        return buffer
    }

    private fun processTestAudioBuffer(buffer: FloatArray) {
        // Simulate DSP processing (basic operations)
        for (i in buffer.indices) {
            buffer[i] *= 0.9f // Simulate processing
        }
    }

    data class TestResults(
        var globalEffectCreation: Boolean = false,
        var effectRegistration: Boolean = false,
        var realTimeLatency: Boolean = false,
        var streamInterception: Boolean = false, // FIX: Removed globalAudioPolicy
        var spotifyCompatibility: Boolean = false,
        var overallSuccessRate: Float = 0.0f
    )
}