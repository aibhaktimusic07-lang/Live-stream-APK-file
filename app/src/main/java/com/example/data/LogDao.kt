package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.model.LogEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM streaming_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 200): Flow<List<LogEntry>>

    @Query("SELECT * FROM streaming_logs ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LogEntry): Long

    @Query("DELETE FROM streaming_logs")
    suspend fun clearLogs()
}
