package com.example.streaming

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.model.StreamConnectionStatus
import com.example.model.StreamSettings
import com.example.model.TargetPlatform
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import java.io.IOException

class StreamEngine(
    private val context: Context,
    private val listener: StreamEngineListener
) {

    interface StreamEngineListener {
        fun onYouTubeStatus(status: StreamConnectionStatus, error: String? = null)
        fun onFacebookStatus(status: StreamConnectionStatus, error: String? = null)
        fun onOverallStatus(isStreaming: Boolean, isReconnecting: Boolean)
        fun onBitrateUpdate(uploadSpeedKbps: Long, totalBytesSent: Long)
        fun onVideoFinished()
        fun onLog(platform: String, level: String, message: String)
    }

    private var multiFromFile: MultiFromFile? = null
    private var isStreaming = false
    private var isReconnecting = false

    private var currentYtUrl: String? = null
    private var currentFbUrl: String? = null
    private var currentSettings: StreamSettings? = null
    private var activeVideoUri: Uri? = null

    // Track platform states
    private var ytConnected = false
    private var fbConnected = false

    // Index 0 = YouTube, Index 1 = Facebook
    private val ytChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            listener.onLog("YOUTUBE", "INFO", "Connecting to YouTube Live...")
            listener.onYouTubeStatus(StreamConnectionStatus.CONNECTING)
        }

        override fun onConnectionSuccess() {
            ytConnected = true
            listener.onLog("YOUTUBE", "SUCCESS", "Connected to YouTube Live! Broadcasting live.")
            listener.onYouTubeStatus(StreamConnectionStatus.STREAMING)
            updateOverallState()
        }

        override fun onConnectionFailed(reason: String) {
            ytConnected = false
            listener.onLog("YOUTUBE", "ERROR", "YouTube connection failed: $reason")
            listener.onYouTubeStatus(StreamConnectionStatus.ERROR, reason)
            updateOverallState()
        }

        override fun onDisconnect() {
            ytConnected = false
            listener.onLog("YOUTUBE", "INFO", "YouTube Live disconnected.")
            listener.onYouTubeStatus(StreamConnectionStatus.IDLE)
            updateOverallState()
        }

        override fun onAuthError() {
            ytConnected = false
            listener.onLog("YOUTUBE", "ERROR", "YouTube stream key or authentication rejected.")
            listener.onYouTubeStatus(StreamConnectionStatus.ERROR, "Authentication error")
            updateOverallState()
        }

        override fun onAuthSuccess() {
            listener.onLog("YOUTUBE", "INFO", "YouTube authentication accepted.")
        }

        override fun onNewBitrate(bitrate: Long) {
            val speedKbps = bitrate / 1000
            val totalBytes = multiFromFile?.getStreamClient(MultiType.RTMP, 0)?.getBytesSend() ?: 0L
            listener.onBitrateUpdate(speedKbps, totalBytes)
        }

        override fun onStreamingStats(status: StreamingStatsReport) {
            // Stats received from streamer
        }
    }

    private val fbChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            listener.onLog("FACEBOOK", "INFO", "Connecting to Facebook Live...")
            listener.onFacebookStatus(StreamConnectionStatus.CONNECTING)
        }

        override fun onConnectionSuccess() {
            fbConnected = true
            listener.onLog("FACEBOOK", "SUCCESS", "Connected to Facebook Live! Broadcasting live.")
            listener.onFacebookStatus(StreamConnectionStatus.STREAMING)
            updateOverallState()
        }

        override fun onConnectionFailed(reason: String) {
            fbConnected = false
            listener.onLog("FACEBOOK", "ERROR", "Facebook connection failed: $reason")
            listener.onFacebookStatus(StreamConnectionStatus.ERROR, reason)
            updateOverallState()
        }

        override fun onDisconnect() {
            fbConnected = false
            listener.onLog("FACEBOOK", "INFO", "Facebook Live disconnected.")
            listener.onFacebookStatus(StreamConnectionStatus.IDLE)
            updateOverallState()
        }

        override fun onAuthError() {
            fbConnected = false
            listener.onLog("FACEBOOK", "ERROR", "Facebook stream key or authentication rejected.")
            listener.onFacebookStatus(StreamConnectionStatus.ERROR, "Authentication error")
            updateOverallState()
        }

        override fun onAuthSuccess() {
            listener.onLog("FACEBOOK", "INFO", "Facebook authentication accepted.")
        }

        override fun onNewBitrate(bitrate: Long) {
            val speedKbps = bitrate / 1000
            val totalBytes = multiFromFile?.getStreamClient(MultiType.RTMP, 1)?.getBytesSend() ?: 0L
            listener.onBitrateUpdate(speedKbps, totalBytes)
        }

        override fun onStreamingStats(status: StreamingStatsReport) {
            // Stats received from streamer
        }
    }

    private val videoDecoderListener = VideoDecoderInterface {
        listener.onLog("SYSTEM", "INFO", "Video playback completed for current track.")
        listener.onVideoFinished()
    }

    private val audioDecoderListener = AudioDecoderInterface {
        // Audio decoder finished
    }

    @Synchronized
    fun startStreaming(
        videoUri: Uri,
        settings: StreamSettings,
        ytEndpoint: String?,
        fbEndpoint: String?
    ): Boolean {
        stop()

        this.currentSettings = settings
        this.activeVideoUri = videoUri
        this.currentYtUrl = ytEndpoint
        this.currentFbUrl = fbEndpoint

        listener.onLog("SYSTEM", "INFO", "Initializing multi-stream encoder engine (${settings.resolutionName}, ${settings.videoBitrateKbps}kbps)...")

        try {
            val rtmpCheckers = arrayOf(ytChecker, fbChecker)
            val streamer = MultiFromFile(
                context,
                videoDecoderListener,
                audioDecoderListener,
                rtmpCheckers,
                emptyArray(),
                emptyArray(),
                emptyArray()
            )
            this.multiFromFile = streamer

            // Prepare video and audio decoders
            val videoPrepared = streamer.prepareVideo(
                context,
                videoUri,
                settings.videoBitrateKbps * 1024,
                settings.fps
            )

            if (!videoPrepared) {
                listener.onLog("SYSTEM", "ERROR", "Failed to prepare video track from MP4 file.")
                return false
            }

            val audioPrepared = streamer.prepareAudio(
                context,
                videoUri,
                settings.audioBitrateKbps * 1024
            )

            if (!audioPrepared) {
                listener.onLog("SYSTEM", "WARNING", "No AAC audio track prepared or audio error; proceeding.")
            }

            // Playlist mode handling:
            // If single video in loop mode or continuous loop:
            streamer.setLoopMode(false) // Let videoDecoderListener trigger playlist advance!

            var anyLaunched = false

            // Start YouTube if configured
            if (settings.targetPlatform == TargetPlatform.YOUTUBE || settings.targetPlatform == TargetPlatform.BOTH) {
                if (!ytEndpoint.isNullOrEmpty()) {
                    listener.onYouTubeStatus(StreamConnectionStatus.CONNECTING)
                    streamer.startStream(MultiType.RTMP, 0, ytEndpoint)
                    anyLaunched = true
                } else {
                    listener.onYouTubeStatus(StreamConnectionStatus.ERROR, "YouTube stream key not set")
                }
            }

            // Start Facebook if configured
            if (settings.targetPlatform == TargetPlatform.FACEBOOK || settings.targetPlatform == TargetPlatform.BOTH) {
                if (!fbEndpoint.isNullOrEmpty()) {
                    listener.onFacebookStatus(StreamConnectionStatus.CONNECTING)
                    streamer.startStream(MultiType.RTMP, 1, fbEndpoint)
                    anyLaunched = true
                } else {
                    listener.onFacebookStatus(StreamConnectionStatus.ERROR, "Facebook stream key not set")
                }
            }

            isStreaming = anyLaunched
            listener.onOverallStatus(isStreaming, false)
            return anyLaunched

        } catch (e: Exception) {
            Log.e("StreamEngine", "Error starting stream", e)
            listener.onLog("SYSTEM", "ERROR", "Stream initialization error: ${e.message}")
            stop()
            return false
        }
    }

    @Synchronized
    fun switchVideo(nextVideoUri: Uri): Boolean {
        this.activeVideoUri = nextVideoUri
        val streamer = multiFromFile ?: return false
        return try {
            listener.onLog("SYSTEM", "INFO", "Seamlessly switching to next video track in playlist...")
            streamer.replaceVideoFile(context, nextVideoUri)
            try {
                streamer.replaceAudioFile(context, nextVideoUri)
            } catch (ignored: Exception) {}
            true
        } catch (e: Exception) {
            listener.onLog("SYSTEM", "ERROR", "Error replacing video track: ${e.message}. Restarting engine...")
            val s = currentSettings
            val yt = currentYtUrl
            val fb = currentFbUrl
            if (s != null) {
                startStreaming(nextVideoUri, s, yt, fb)
            } else {
                false
            }
        }
    }

    @Synchronized
    fun stop() {
        try {
            multiFromFile?.stopStream()
        } catch (e: Exception) {
            Log.e("StreamEngine", "Error stopping stream", e)
        }
        multiFromFile = null
        isStreaming = false
        isReconnecting = false
        ytConnected = false
        fbConnected = false
        listener.onYouTubeStatus(StreamConnectionStatus.IDLE)
        listener.onFacebookStatus(StreamConnectionStatus.IDLE)
        listener.onOverallStatus(false, false)
        listener.onLog("SYSTEM", "INFO", "Broadcast stopped.")
    }

    fun getProgress(): Pair<Double, Double> {
        val streamer = multiFromFile ?: return Pair(0.0, 0.0)
        return try {
            Pair(streamer.videoTime, streamer.videoDuration)
        } catch (e: Exception) {
            Pair(0.0, 0.0)
        }
    }

    private fun updateOverallState() {
        val active = ytConnected || fbConnected
        listener.onOverallStatus(active, isReconnecting)
    }
}
