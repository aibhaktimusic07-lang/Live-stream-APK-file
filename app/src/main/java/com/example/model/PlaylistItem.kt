package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist_items")
data class PlaylistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val uriString: String,
    val title: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val orderIndex: Int = 0,
    val isCurrentlyPlaying: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Unknown"
            val mb = sizeBytes.toDouble() / (1024 * 1024)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
