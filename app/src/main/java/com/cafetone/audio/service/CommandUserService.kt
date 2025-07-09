package com.cafetone.audio.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * This is a special service required by Shizuku to execute commands.
 * It runs in a separate process with root/shell privileges provided by Shizuku.
 */
class CommandUserService : Service() {
    override fun onBind(intent: Intent): IBinder? {
        // This service does not need a binder for this simple implementation.
        // It's the act of binding to it that grants the process its privileges.
        return null
    }
}