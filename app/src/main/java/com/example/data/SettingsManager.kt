package com.example.data

import android.content.Context
import com.example.model.StreamSettings
import com.example.model.TargetPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager(context: Context) {

    private val prefs = context.getSharedPreferences("live_studio_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<StreamSettings> = _settings.asStateFlow()

    private fun loadSettings(): StreamSettings {
        val targetName = prefs.getString("target_platform", TargetPlatform.BOTH.name) ?: TargetPlatform.BOTH.name
        val target = try {
            TargetPlatform.valueOf(targetName)
        } catch (e: Exception) {
            TargetPlatform.BOTH
        }

        return StreamSettings(
            resolutionName = prefs.getString("resolution_name", "720p") ?: "720p",
            width = prefs.getInt("width", 1280),
            height = prefs.getInt("height", 720),
            fps = prefs.getInt("fps", 30),
            videoBitrateKbps = prefs.getInt("video_bitrate", 2500),
            audioBitrateKbps = prefs.getInt("audio_bitrate", 128),
            autoRepeatPlaylist = prefs.getBoolean("auto_repeat_playlist", true),
            resumeLastPosition = prefs.getBoolean("resume_last_position", true),
            keepAwake = prefs.getBoolean("keep_awake", true),
            autoReconnect = prefs.getBoolean("auto_reconnect", true),
            targetPlatform = target,
            language = prefs.getString("language", "en") ?: "en"
        )
    }

    fun updateSettings(newSettings: StreamSettings) {
        prefs.edit()
            .putString("resolution_name", newSettings.resolutionName)
            .putInt("width", newSettings.width)
            .putInt("height", newSettings.height)
            .putInt("fps", newSettings.fps)
            .putInt("video_bitrate", newSettings.videoBitrateKbps)
            .putInt("audio_bitrate", newSettings.audioBitrateKbps)
            .putBoolean("auto_repeat_playlist", newSettings.autoRepeatPlaylist)
            .putBoolean("resume_last_position", newSettings.resumeLastPosition)
            .putBoolean("keep_awake", newSettings.keepAwake)
            .putBoolean("auto_reconnect", newSettings.autoReconnect)
            .putString("target_platform", newSettings.targetPlatform.name)
            .putString("language", newSettings.language)
            .apply()

        _settings.value = newSettings
    }

    fun setTargetPlatform(target: TargetPlatform) {
        updateSettings(_settings.value.copy(targetPlatform = target))
    }

    fun setQualityPreset(preset: String) {
        val updated = when (preset) {
            "1080p" -> _settings.value.copy(
                resolutionName = "1080p",
                width = 1920,
                height = 1080,
                videoBitrateKbps = 4500,
                fps = 30
            )
            "480p" -> _settings.value.copy(
                resolutionName = "480p",
                width = 854,
                height = 480,
                videoBitrateKbps = 1200,
                fps = 30
            )
            else -> _settings.value.copy( // 720p default
                resolutionName = "720p",
                width = 1280,
                height = 720,
                videoBitrateKbps = 2500,
                fps = 30
            )
        }
        updateSettings(updated)
    }

    fun setLanguage(lang: String) {
        updateSettings(_settings.value.copy(language = lang))
    }

    fun saveLastPlaybackState(uriString: String?, positionMs: Long) {
        prefs.edit()
            .putString("last_video_uri", uriString)
            .putLong("last_position_ms", positionMs)
            .apply()
    }

    fun getLastPlaybackUri(): String? = prefs.getString("last_video_uri", null)
    fun getLastPlaybackPosition(): Long = prefs.getLong("last_position_ms", 0L)
}
