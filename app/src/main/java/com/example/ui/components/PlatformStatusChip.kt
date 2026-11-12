package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.StreamConnectionStatus
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.LiveActiveRed
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.StudioDarkSurfaceVariant
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.YouTubeRed

@Composable
fun PlatformStatusCard(
    platformName: String,
    isYouTube: Boolean,
    status: StreamConnectionStatus,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val platformColor = if (isYouTube) YouTubeRed else FacebookBlue

    val (statusLabel, statusColor) = when (status) {
        StreamConnectionStatus.STREAMING -> Pair("LIVE", SuccessGreen)
        StreamConnectionStatus.CONNECTING -> Pair("CONNECTING", NeonBlue)
        StreamConnectionStatus.RECONNECTING -> Pair("RECONNECTING", WarningAmber)
        StreamConnectionStatus.ERROR -> Pair("ERROR", LiveActiveRed)
        StreamConnectionStatus.IDLE -> Pair("STANDBY", TextSecondary)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(StudioDarkSurfaceVariant)
            .border(1.dp, if (status == StreamConnectionStatus.STREAMING) statusColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform Badge Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.2f))
                        .border(1.dp, platformColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isYouTube) Icons.Default.PlayCircleOutline else Icons.Default.CastConnected,
                        contentDescription = platformName,
                        tint = platformColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = platformName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isYouTube) "RTMPS • Port 443" else "RTMP/RTMPS • Port 443",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (errorMessage != null && status == StreamConnectionStatus.ERROR) {
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(LiveActiveRed.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = LiveActiveRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = errorMessage,
                        color = LiveActiveRed,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
