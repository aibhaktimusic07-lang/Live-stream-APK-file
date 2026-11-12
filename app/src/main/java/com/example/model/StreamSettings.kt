package com.example.model

enum class TargetPlatform {
    YOUTUBE,
    FACEBOOK,
    BOTH
}

enum class StreamConnectionStatus {
    IDLE,
    CONNECTING,
    STREAMING,
    RECONNECTING,
    ERROR
}

data class StreamSettings(
    val resolutionName: String = "720p",
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val autoRepeatPlaylist: Boolean = true,
    val resumeLastPosition: Boolean = true,
    val keepAwake: Boolean = true,
    val autoReconnect: Boolean = true,
    val targetPlatform: TargetPlatform = TargetPlatform.BOTH,
    val language: String = "en"
)
