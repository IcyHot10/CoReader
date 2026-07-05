package com.indeavour.coreader.model.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_activity")
data class ReadingActivity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "book_id") val bookId: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long, // Start of the day or exact time
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @ColumnInfo(name = "is_completed_event") val isCompletedEvent: Boolean = false
)
