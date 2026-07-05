package com.indeavour.coreader.dao.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.indeavour.coreader.model.room.ReadingActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingActivityDao {
    @Insert
    suspend fun insert(activity: ReadingActivity)

    @Query("SELECT * FROM reading_activity ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ReadingActivity>>

    @Query("SELECT * FROM reading_activity WHERE timestamp >= :startTime")
    suspend fun getSince(startTime: Long): List<ReadingActivity>

    @Query("SELECT SUM(duration_seconds) FROM reading_activity WHERE timestamp >= :startTime")
    suspend fun getTotalDurationSince(startTime: Long): Long?

    @Query("SELECT COUNT(DISTINCT book_id) FROM reading_activity WHERE is_completed_event = 1 AND timestamp >= :startTime")
    suspend fun getBooksCompletedSince(startTime: Long): Int
}
