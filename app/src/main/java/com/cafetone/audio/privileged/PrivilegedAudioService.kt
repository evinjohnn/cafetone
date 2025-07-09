package com.cafetone.audio.privileged

import android.annotation.SuppressLint
import android.app.Service // CRITICAL FIX: Import the standard Android Service
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Field
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

// CRITICAL FIX: The service must extend the standard android.app.Service.
// Shizuku will start this standard service inside its own privileged process.
class PrivilegedAudioService : Service() {

    companion object {
        private const val TAG = "PrivilegedAudioService"
        private val EFFECT_UUID_CAFETONE = UUID.fromString("87654321-4321-8765-4321-fedcba098765")
        private val EFFECT_TYPE_NULL: UUID by lazy {
            try {
                val field: Field = AudioEffect::class.java.getField("EFFECT_TYPE_NULL")
                field.get(null) as UUID
            } catch (e: Exception) {
                UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
            }
        }
    }

    private var audioEffect: AudioEffect? = null

    private val binder = object : IPrivilegedAudioService.Stub() {
        override fun create() {
            this@PrivilegedAudioService.create()
        }

        override fun release() {
            this@PrivilegedAudioService.release()
        }

        override fun setEnabled(enabled: Boolean) {
            this@PrivilegedAudioService.setEnabled(enabled)
        }

        override fun setParameter(param: Int, value: Float) {
            this@PrivilegedAudioService.setParameter(param, value)
        }

        override fun isEnabled(): Boolean {
            return audioEffect?.enabled ?: false
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "PrivilegedAudioService created in Shizuku process.")
    }

    private fun create() {
        if (audioEffect != null) {
            Log.w(TAG, "AudioEffect already exists. Releasing first.")
            release()
        }
        try {
            Log.i(TAG, "Attempting to create global AudioEffect (session 0) from privileged process...")
            audioEffect = createAudioEffect(EFFECT_TYPE_NULL, EFFECT_UUID_CAFETONE, 0, 0)
            if (audioEffect != null) {
                Log.i(TAG, "Global AudioEffect created successfully.")
            } else {
                Log.e(TAG, "Failed to create AudioEffect, it's null.")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception creating AudioEffect", e)
        }
    }

    private fun release() {
        try {
            audioEffect?.release()
            Log.i(TAG, "AudioEffect released.")
        } catch (e: Throwable) {
            Log.e(TAG, "Exception releasing AudioEffect", e)
        } finally {
            audioEffect = null
        }
    }

    private fun setEnabled(enabled: Boolean) {
        try {
            audioEffect?.enabled = enabled
            Log.i(TAG, "AudioEffect enabled state set to: $enabled")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to set AudioEffect enabled state.", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun setParameter(paramId: Int, value: Float) {
        audioEffect?.let { effect ->
            try {
                if (!effect.hasControl()) {
                    Log.w(TAG, "Effect doesn't have control, cannot set parameter.")
                    return@let
                }
                val p = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(paramId).array()
                val v = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
                val setParameterMethod = AudioEffect::class.java.getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
                val result = setParameterMethod.invoke(effect, p, v) as Int
                if (result != 0) {
                    Log.w(TAG, "setParameter returned error code: $result for param: $paramId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set parameter on AudioEffect via reflection", e)
            }
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createAudioEffect(type: UUID, uuid: UUID, priority: Int, audioSession: Int): AudioEffect? {
        return try {
            val constructor = AudioEffect::class.java.getDeclaredConstructor(
                UUID::class.java, UUID::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            constructor.isAccessible = true
            constructor.newInstance(type, uuid, priority, audioSession)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating AudioEffect via reflection", e)
            null
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "PrivilegedAudioService is being destroyed.")
        release()
        super.onDestroy()
        // The process will be killed by Shizuku, but we can request an exit.
        System.exit(0)
    }
}