package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlist_items ORDER BY orderIndex ASC")
    fun getAll(): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items ORDER BY orderIndex ASC")
    suspend fun getAllSync(): List<PlaylistItem>

    @Query("SELECT COUNT(*) FROM playlist_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PlaylistItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PlaylistItem>)

    @Update
    suspend fun update(item: PlaylistItem)

    @Update
    suspend fun updateAll(items: List<PlaylistItem>)

    @Delete
    suspend fun delete(item: PlaylistItem)

    @Query("DELETE FROM playlist_items")
    suspend fun clearAll()

    @Transaction
    suspend fun setCurrentlyPlaying(activeId: Long) {
        clearCurrentlyPlaying()
        markActive(activeId)
    }

    @Query("UPDATE playlist_items SET isCurrentlyPlaying = 0")
    suspend fun clearCurrentlyPlaying()

    @Query("UPDATE playlist_items SET isCurrentlyPlaying = 1 WHERE id = :activeId")
    suspend fun markActive(activeId: Long)
}
