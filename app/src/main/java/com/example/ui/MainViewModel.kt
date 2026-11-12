package com.example.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.IBinder
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LiveStudioApplication
import com.example.model.LogEntry
import com.example.model.PlaylistItem
import com.example.model.StreamConnectionStatus
import com.example.model.StreamSettings
import com.example.model.TargetPlatform
import com.example.service.LiveStreamingService
import com.example.service.ServiceStreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlatformCredentialsUi(
    val youtubeUrl: String = "",
    val youtubeKey: String = "",
    val facebookUrl: String = "",
    val facebookKey: String = ""
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LiveStudioApplication
    private val db = app.database
    private val creds = app.credentialsManager
    private val settingsManager = app.settingsManager

    // Service binding
    private var streamingService: LiveStreamingService? = null
    private val _serviceState = MutableStateFlow(ServiceStreamState())
    val streamState: StateFlow<ServiceStreamState> = _serviceState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? LiveStreamingService.LocalBinder
            streamingService = binder?.getService()
            streamingService?.let { s ->
                viewModelScope.launch {
                    s.streamState.collect { state ->
                        _serviceState.value = state
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            streamingService = null
        }
    }

    // Playlist flow
    val playlist: StateFlow<List<PlaylistItem>> = db.playlistDao().getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Logs flow
    val logs: StateFlow<List<LogEntry>> = db.logDao().getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings flow
    val settings: StateFlow<StreamSettings> = settingsManager.settings

    // Credentials State
    private val _credentials = MutableStateFlow(
        PlatformCredentialsUi(
            youtubeUrl = creds.getYouTubeUrl(),
            youtubeKey = creds.getYouTubeStreamKey(),
            facebookUrl = creds.getFacebookUrl(),
            facebookKey = creds.getFacebookStreamKey()
        )
    )
    val credentials: StateFlow<PlatformCredentialsUi> = _credentials.asStateFlow()

    init {
        bindStreamingService()
    }

    private fun bindStreamingService() {
        val intent = Intent(app, LiveStreamingService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startLiveBroadcast() {
        val intent = Intent(app, LiveStreamingService::class.java).apply {
            action = LiveStreamingService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
    }

    fun stopLiveBroadcast() {
        val intent = Intent(app, LiveStreamingService::class.java).apply {
            action = LiveStreamingService.ACTION_STOP
        }
        app.startService(intent)
    }

    fun skipNextVideo() {
        streamingService?.playNextVideo()
    }

    fun skipPrevVideo() {
        streamingService?.playPreviousVideo()
    }

    fun addVideosFromUris(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = db.playlistDao().getAllSync()
            var nextIndex = existing.size

            val newItems = mutableListOf<PlaylistItem>()

            for (uri in uris) {
                // Persist read permissions so foreground service can read files continuously
                try {
                    app.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (ignored: Exception) {
                    // Photo picker URIs might not support persistable flags on some API levels
                }

                var title = "Video Track ${nextIndex + 1}"
                var sizeBytes = 0L

                // Query display name and size from content resolver
                try {
                    app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) title = name
                            }
                            if (sizeIndex != -1) {
                                sizeBytes = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (ignored: Exception) {}

                // Extract duration using MediaMetadataRetriever
                var durationMs = 0L
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(app, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    durationMs = durationStr?.toLongOrNull() ?: 0L
                    retriever.release()
                } catch (ignored: Exception) {}

                newItems.add(
                    PlaylistItem(
                        uriString = uri.toString(),
                        title = title,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        orderIndex = nextIndex++
                    )
                )
            }

            if (newItems.isNotEmpty()) {
                db.playlistDao().insertAll(newItems)
                db.logDao().insert(
                    LogEntry(
                        platform = "SYSTEM",
                        level = "INFO",
                        message = "Added ${newItems.size} video(s) to playlist."
                    )
                )
            }
        }
    }

    fun removePlaylistItem(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().delete(item)
            // Re-index remaining
            val remaining = db.playlistDao().getAllSync()
            val updated = remaining.mapIndexed { index, pi -> pi.copy(orderIndex = index) }
            db.playlistDao().updateAll(updated)
        }
    }

    fun movePlaylistItemUp(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.playlistDao().getAllSync().toMutableList()
            val index = list.indexOfFirst { it.id == item.id }
            if (index > 0) {
                val prev = list[index - 1]
                list[index - 1] = item.copy(orderIndex = index - 1)
                list[index] = prev.copy(orderIndex = index)
                db.playlistDao().updateAll(list)
            }
        }
    }

    fun movePlaylistItemDown(item: PlaylistItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = db.playlistDao().getAllSync().toMutableList()
            val index = list.indexOfFirst { it.id == item.id }
            if (index in 0 until list.size - 1) {
                val next = list[index + 1]
                list[index + 1] = item.copy(orderIndex = index + 1)
                list[index] = next.copy(orderIndex = index)
                db.playlistDao().updateAll(list)
            }
        }
    }

    fun clearPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            db.playlistDao().clearAll()
            db.logDao().insert(
                LogEntry(
                    platform = "SYSTEM",
                    level = "INFO",
                    message = "Playlist cleared."
                )
            )
        }
    }

    fun saveYouTubeCredentials(url: String, key: String) {
        creds.saveYouTubeCredentials(url, key)
        _credentials.value = _credentials.value.copy(
            youtubeUrl = url,
            youtubeKey = key
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.logDao().insert(
                LogEntry(
                    platform = "YOUTUBE",
                    level = "SUCCESS",
                    message = "YouTube credentials updated (Stream key: ${creds.redactKey(key)})"
                )
            )
        }
    }

    fun saveFacebookCredentials(url: String, key: String) {
        creds.saveFacebookCredentials(url, key)
        _credentials.value = _credentials.value.copy(
            facebookUrl = url,
            facebookKey = key
        )
        viewModelScope.launch(Dispatchers.IO) {
            db.logDao().insert(
                LogEntry(
                    platform = "FACEBOOK",
                    level = "SUCCESS",
                    message = "Facebook credentials updated (Stream key: ${creds.redactKey(key)})"
                )
            )
        }
    }

    fun setTargetPlatform(target: TargetPlatform) {
        settingsManager.setTargetPlatform(target)
    }

    fun setQualityPreset(preset: String) {
        settingsManager.setQualityPreset(preset)
    }

    fun updateSettings(newSettings: StreamSettings) {
        settingsManager.updateSettings(newSettings)
    }

    fun setLanguage(lang: String) {
        settingsManager.setLanguage(lang)
    }

    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            db.logDao().clearLogs()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            app.unbindService(serviceConnection)
        } catch (ignored: Exception) {}
    }
}
