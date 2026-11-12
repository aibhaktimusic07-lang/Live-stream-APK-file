package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PlaylistItem
import com.example.model.StreamConnectionStatus
import com.example.model.StreamSettings
import com.example.model.TargetPlatform
import com.example.service.ServiceStreamState
import com.example.ui.MainViewModel
import com.example.ui.components.LiveBadge
import com.example.ui.components.PlatformStatusCard
import com.example.ui.theme.LiveActiveRed
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.StudioDarkSurface
import com.example.ui.theme.StudioDarkSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToPlaylist: () -> Unit,
    onNavigateToPlatforms: () -> Unit,
    modifier: Modifier = Modifier
) {
    val streamState by viewModel.streamState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val playlist by viewModel.playlist.collectAsState()

    val isLive = streamState.isStreaming
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "YouTube & Facebook Multistreaming",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveBadge(isLive = isLive, isReconnecting = streamState.isReconnecting)
                Spacer(modifier = Modifier.width(8.dp))
                // Quick Language Toggle
                IconButton(
                    onClick = {
                        val nextLang = if (settings.language == "hi") "en" else "hi"
                        viewModel.setLanguage(nextLang)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(StudioDarkSurfaceVariant)
                ) {
                    Text(
                        text = if (settings.language == "hi") "EN" else "हिं",
                        color = NeonBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target Platform Selection Segment
        Text(
            text = stringResource(R.string.dashboard_target_platforms),
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioDarkSurfaceVariant)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TargetPlatformButton(
                label = stringResource(R.string.platform_youtube),
                selected = settings.targetPlatform == TargetPlatform.YOUTUBE,
                onClick = { viewModel.setTargetPlatform(TargetPlatform.YOUTUBE) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            TargetPlatformButton(
                label = stringResource(R.string.platform_facebook),
                selected = settings.targetPlatform == TargetPlatform.FACEBOOK,
                onClick = { viewModel.setTargetPlatform(TargetPlatform.FACEBOOK) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(4.dp))
            TargetPlatformButton(
                label = stringResource(R.string.platform_both),
                selected = settings.targetPlatform == TargetPlatform.BOTH,
                onClick = { viewModel.setTargetPlatform(TargetPlatform.BOTH) },
                modifier = Modifier.weight(1.3f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Live Broadcast Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF161A2B),
                            Color(0xFF0F121F)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isLive) LiveActiveRed.copy(alpha = 0.5f) else NeonBlue.copy(alpha = 0.25f),
                    RoundedCornerShape(20.dp)
                )
                .padding(18.dp)
        ) {
            Column {
                // Header row inside hero card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isLive) LiveActiveRed else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dashboard_duration),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Resolution chip
                    Text(
                        text = "${settings.resolutionName} • ${settings.fps}FPS",
                        color = NeonBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonBlue.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Big Duration Display
                Text(
                    text = formatSeconds(streamState.elapsedStreamSeconds),
                    color = TextPrimary,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Currently Playing Track Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VideoLibrary,
                                    contentDescription = null,
                                    tint = NeonPurple,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.dashboard_current_video),
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (streamState.totalVideosInPlaylist > 0) {
                                Text(
                                    text = "Track ${streamState.currentVideoIndex} of ${streamState.totalVideosInPlaylist}",
                                    color = NeonPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (streamState.currentVideoTitle.isNotEmpty()) {
                                streamState.currentVideoTitle
                            } else if (playlist.isNotEmpty()) {
                                playlist.first().title
                            } else {
                                stringResource(R.string.dashboard_no_video)
                            },
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Video progress
                        val currentSec = streamState.videoPositionSeconds
                        val totalSec = streamState.videoDurationSeconds
                        val progress = if (totalSec > 0) (currentSec / totalSec).toFloat().coerceIn(0f, 1f) else 0f

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = NeonPurple,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDoubleSeconds(currentSec),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatDoubleSeconds(totalSec),
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time Telemetry Row (Speed & Network)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TelemetryItem(
                        icon = Icons.Default.Speed,
                        label = stringResource(R.string.dashboard_upload_speed),
                        value = "${streamState.uploadSpeedKbps} kbps",
                        accentColor = NeonBlue
                    )
                    TelemetryItem(
                        icon = Icons.Default.Wifi,
                        label = stringResource(R.string.dashboard_network),
                        value = streamState.networkStatus,
                        accentColor = if (streamState.isNetworkAvailable) SuccessGreen else LiveActiveRed
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Primary Start/Stop Live Button
                if (!isLive) {
                    Button(
                        onClick = { viewModel.startLiveBroadcast() },
                        enabled = playlist.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_live_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonBlue,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_start_live),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.stopLiveBroadcast() },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("stop_live_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveActiveRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.btn_stop_live),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Skip to next video button
                        Button(
                            onClick = { viewModel.skipNextVideo() },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("skip_next_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioDarkSurfaceVariant,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Track",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Platform Connection Status Cards
        if (settings.targetPlatform == TargetPlatform.YOUTUBE || settings.targetPlatform == TargetPlatform.BOTH) {
            PlatformStatusCard(
                platformName = stringResource(R.string.platform_youtube),
                isYouTube = true,
                status = streamState.youtubeStatus,
                errorMessage = streamState.youtubeError,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPlatforms() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (settings.targetPlatform == TargetPlatform.FACEBOOK || settings.targetPlatform == TargetPlatform.BOTH) {
            PlatformStatusCard(
                platformName = stringResource(R.string.platform_facebook),
                isYouTube = false,
                status = streamState.facebookStatus,
                errorMessage = streamState.facebookError,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPlatforms() }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Quick tip reminder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(StudioDarkSurfaceVariant.copy(alpha = 0.5f))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = NeonBlue,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.dashboard_quick_tip),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun TargetPlatformButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) NeonBlue else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun TelemetryItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accentColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatSeconds(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}

private fun formatDoubleSeconds(seconds: Double): String {
    val sec = seconds.toLong()
    val m = sec / 60
    val s = sec % 60
    return String.format("%02d:%02d", m, s)
}
