package com.cafetone.audio.service

data class AppStatus(
    val isEnabled: Boolean = false,
    val isShizukuReady: Boolean = false,
    val shizukuMessage: String = "",
    val intensity: Float = 0.5f,
    val spatialWidth: Float = 1.0f,
    val distance: Float = 1.0f
)
