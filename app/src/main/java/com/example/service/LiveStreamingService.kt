package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.LiveStudioApplication
import com.example.MainActivity
import com.example.R
import com.example.model.LogEntry
import com.example.model.PlaylistItem
import com.example.model.StreamConnectionStatus
import com.example.streaming.StreamEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ServiceStreamState(
    val isStreaming: Boolean = false,
    val isReconnecting: Boolean = false,
    val currentVideoTitle: String = "",
    val currentVideoIndex: Int = 0,
    val totalVideosInPlaylist: Int = 0,
    val videoPositionSeconds: Double = 0.0,
    val videoDurationSeconds: Double = 0.0,
    val elapsedStreamSeconds: Long = 0L,
    val uploadSpeedKbps: Long = 0L,
    val totalBytesSent: Long = 0L,
    val youtubeStatus: StreamConnectionStatus = StreamConnectionStatus.IDLE,
    val facebookStatus: StreamConnectionStatus = StreamConnectionStatus.IDLE,
    val youtubeError: String? = null,
    val facebookError: String? = null,
    val networkStatus: String = "Online (Wi-Fi)",
    val isNetworkAvailable: Boolean = true
)

class LiveStreamingService : Service(), StreamEngine.StreamEngineListener {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var streamEngine: StreamEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null

    private var durationTimerJob: Job? = null
    private var streamStartTimestamp: Long = 0L

    private val _streamState = MutableStateFlow(ServiceStreamState())
    val streamState: StateFlow<ServiceStreamState> = _streamState.asStateFlow()

    // Playlist tracking
    private var playlist = listOf<PlaylistItem>()
    private var currentPlaylistIndex = 0

    inner class LocalBinder : Binder() {
        fun getService(): LiveStreamingService = this@LiveStreamingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        streamEngine = StreamEngine(applicationContext, this)
        initNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startBroadcast()
            ACTION_STOP -> stopBroadcast()
            ACTION_NEXT -> playNextVideo()
            ACTION_PREV -> playPreviousVideo()
        }
        return START_STICKY
    }

    private fun initNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val caps = connectivityManager?.getNetworkCapabilities(network)
                val type = when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Online (Wi-Fi)"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Online (Cellular 4G/5G)"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Online (Ethernet)"
                    else -> "Online"
                }

                _streamState.value = _streamState.value.copy(
                    isNetworkAvailable = true,
                    networkStatus = type
                )

                log("SYSTEM", "INFO", "Network connected: $type")

                // Auto-reconnect if we were disconnected while active
                val settings = LiveStudioApplication.instance.settingsManager.settings.value
                if (_streamState.value.isStreaming && settings.autoReconnect) {
                    // Check if reconnection needed
                }
            }

            override fun onLost(network: Network) {
                _streamState.value = _streamState.value.copy(
                    isNetworkAvailable = false,
                    networkStatus = "Offline (No Connection)"
                )
                log("SYSTEM", "WARNING", "Network connection lost! Broadcast paused until connection resumes.")
            }
        })
    }

    fun startBroadcast() {
        serviceScope.launch {
            val app = LiveStudioApplication.instance
            val db = app.database
            val settings = app.settingsManager.settings.value
            val creds = app.credentialsManager

            val items = db.playlistDao().getAllSync()
            if (items.isEmpty()) {
                log("SYSTEM", "ERROR", "Cannot start broadcast: Playlist is empty. Add videos first.")
                return@launch
            }

            playlist = items
            _streamState.value = _streamState.value.copy(totalVideosInPlaylist = items.size)

            // Resume or start from index 0
            if (settings.resumeLastPosition) {
                val lastUri = app.settingsManager.getLastPlaybackUri()
                val foundIndex = items.indexOfFirst { it.uriString == lastUri }
                currentPlaylistIndex = if (foundIndex >= 0) foundIndex else 0
            } else {
                currentPlaylistIndex = 0
            }

            val currentItem = items[currentPlaylistIndex]
            val videoUri = Uri.parse(currentItem.uriString)

            db.playlistDao().setCurrentlyPlaying(currentItem.id)

            // Build platform full endpoints
            val ytUrl = creds.getYouTubeUrl()
            val ytKey = creds.getYouTubeStreamKey()
            val ytEndpoint = if (ytKey.isNotEmpty()) {
                if (ytUrl.endsWith("/")) "$ytUrl$ytKey" else "$ytUrl/$ytKey"
            } else null

            val fbUrl = creds.getFacebookUrl()
            val fbKey = creds.getFacebookStreamKey()
            val fbEndpoint = if (fbKey.isNotEmpty()) {
                if (fbUrl.endsWith("/")) "$fbUrl$fbKey" else "$fbUrl/$fbKey"
            } else null

            // Start foreground notification immediately
            startForegroundServiceWithNotification(currentItem.title)

            // Acquire WakeLock if enabled
            if (settings.keepAwake) {
                acquireWakeLock()
            }

            streamStartTimestamp = System.currentTimeMillis()
            startDurationTimer()

            _streamState.value = _streamState.value.copy(
                isStreaming = true,
                currentVideoTitle = currentItem.title,
                currentVideoIndex = currentPlaylistIndex + 1,
                elapsedStreamSeconds = 0L
            )

            log("SYSTEM", "INFO", "Starting 24/7 broadcast: Playing '${currentItem.title}'")

            val success = withContext(Dispatchers.IO) {
                streamEngine?.startStreaming(videoUri, settings, ytEndpoint, fbEndpoint) ?: false
            }

            if (!success) {
                log("SYSTEM", "ERROR", "Engine failed to initialize with video file.")
            }
        }
    }

    fun stopBroadcast() {
        serviceScope.launch {
            streamEngine?.stop()
            stopDurationTimer()
            releaseWakeLock()

            _streamState.value = _streamState.value.copy(
                isStreaming = false,
                isReconnecting = false,
                elapsedStreamSeconds = 0L,
                uploadSpeedKbps = 0L,
                youtubeStatus = StreamConnectionStatus.IDLE,
                facebookStatus = StreamConnectionStatus.IDLE
            )

            LiveStudioApplication.instance.database.playlistDao().clearCurrentlyPlaying()

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun playNextVideo() {
        serviceScope.launch {
            val items = LiveStudioApplication.instance.database.playlistDao().getAllSync()
            if (items.isEmpty()) return@launch
            playlist = items

            val settings = LiveStudioApplication.instance.settingsManager.settings.value

            if (currentPlaylistIndex + 1 < items.size) {
                currentPlaylistIndex++
            } else if (settings.autoRepeatPlaylist) {
                // Loop back to beginning for 24/7 continuous stream
                currentPlaylistIndex = 0
                log("SYSTEM", "INFO", "Playlist reached end. Auto-repeating from beginning (24/7 continuous mode).")
            } else {
                log("SYSTEM", "INFO", "Playlist finished. Stop streaming.")
                stopBroadcast()
                return@launch
            }

            val nextItem = items[currentPlaylistIndex]
            val uri = Uri.parse(nextItem.uriString)

            LiveStudioApplication.instance.database.playlistDao().setCurrentlyPlaying(nextItem.id)
            LiveStudioApplication.instance.settingsManager.saveLastPlaybackState(nextItem.uriString, 0L)

            _streamState.value = _streamState.value.copy(
                currentVideoTitle = nextItem.title,
                currentVideoIndex = currentPlaylistIndex + 1
            )

            updateNotification(nextItem.title)

            withContext(Dispatchers.IO) {
                streamEngine?.switchVideo(uri)
            }
        }
    }

    fun playPreviousVideo() {
        serviceScope.launch {
            val items = LiveStudioApplication.instance.database.playlistDao().getAllSync()
            if (items.isEmpty()) return@launch
            playlist = items

            if (currentPlaylistIndex > 0) {
                currentPlaylistIndex--
            } else {
                currentPlaylistIndex = items.size - 1
            }

            val prevItem = items[currentPlaylistIndex]
            val uri = Uri.parse(prevItem.uriString)

            LiveStudioApplication.instance.database.playlistDao().setCurrentlyPlaying(prevItem.id)
            _streamState.value = _streamState.value.copy(
                currentVideoTitle = prevItem.title,
                currentVideoIndex = currentPlaylistIndex + 1
            )

            updateNotification(prevItem.title)

            withContext(Dispatchers.IO) {
                streamEngine?.switchVideo(uri)
            }
        }
    }

    private fun startForegroundServiceWithNotification(videoTitle: String) {
        val notification = buildNotification(videoTitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(videoTitle: String) {
        val notification = buildNotification(videoTitle)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(videoTitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LiveStreamingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, LiveStreamingService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            2,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val durationFormatted = formatDuration(_streamState.value.elapsedStreamSeconds)
        val speed = _streamState.value.uploadSpeedKbps

        return NotificationCompat.Builder(this, LiveStudioApplication.CHANNEL_ID_STREAMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_streaming_title))
            .setContentText("Playing: $videoTitle • $durationFormatted • $speed kbps")
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_next, "Next Video", nextPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - streamStartTimestamp) / 1000
                val progress = streamEngine?.getProgress() ?: Pair(0.0, 0.0)

                _streamState.value = _streamState.value.copy(
                    elapsedStreamSeconds = elapsed,
                    videoPositionSeconds = progress.first,
                    videoDurationSeconds = progress.second
                )

                // Periodically refresh notification status every 5 seconds
                if (elapsed % 5 == 0L && _streamState.value.isStreaming) {
                    updateNotification(_streamState.value.currentVideoTitle)
                }
            }
        }
    }

    private fun stopDurationTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LiveStudio::BroadcastWakeLock").apply {
                setReferenceCounted(false)
                acquire(24 * 60 * 60 * 1000L) // 24 hours max safety
            }
            log("SYSTEM", "INFO", "CPU Wake Lock acquired for 24/7 background streaming.")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                log("SYSTEM", "INFO", "CPU Wake Lock released.")
            }
        }
        wakeLock = null
    }

    // StreamEngineListener Callbacks
    override fun onYouTubeStatus(status: StreamConnectionStatus, error: String?) {
        _streamState.value = _streamState.value.copy(
            youtubeStatus = status,
            youtubeError = error
        )
    }

    override fun onFacebookStatus(status: StreamConnectionStatus, error: String?) {
        _streamState.value = _streamState.value.copy(
            facebookStatus = status,
            facebookError = error
        )
    }

    override fun onOverallStatus(isStreaming: Boolean, isReconnecting: Boolean) {
        _streamState.value = _streamState.value.copy(
            isStreaming = isStreaming,
            isReconnecting = isReconnecting
        )
    }

    override fun onBitrateUpdate(uploadSpeedKbps: Long, totalBytesSent: Long) {
        _streamState.value = _streamState.value.copy(
            uploadSpeedKbps = uploadSpeedKbps,
            totalBytesSent = totalBytesSent
        )
    }

    override fun onVideoFinished() {
        playNextVideo()
    }

    override fun onLog(platform: String, level: String, message: String) {
        log(platform, level, message)
    }

    private fun log(platform: String, level: String, message: String) {
        serviceScope.launch {
            LiveStudioApplication.instance.database.logDao().insert(
                LogEntry(
                    platform = platform,
                    level = level,
                    message = message
                )
            )
        }
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDurationTimer()
        releaseWakeLock()
        streamEngine?.stop()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 2471
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_NEXT = "com.example.service.ACTION_NEXT"
        const val ACTION_PREV = "com.example.service.ACTION_PREV"
    }
}
