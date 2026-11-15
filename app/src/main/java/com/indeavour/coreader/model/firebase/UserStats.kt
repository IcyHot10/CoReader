package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * One bucket of reading stats: either a calendar year or the lifetime totals.
 *
 * Everything after [lastReadingTimestamp] was added later, so it is zero on documents written
 * before then and only counts activity from that point on.
 */
@IgnoreExtraProperties
data class UserStats(
    val secondsRead: Long = 0,
    val booksCompleted: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastReadingTimestamp: Long = 0,
    /** Distinct calendar days with any reading. */
    val daysRead: Int = 0,
    /** Reading sessions long enough to count (see StatsRepository.MIN_SESSION_SECONDS). */
    val sessions: Int = 0,
    val longestSessionSeconds: Long = 0,
    val highlightsMade: Int = 0,
    val notesWritten: Int = 0,
    /**
     * Seconds read per day, keyed "MM-dd". Only kept on yearly buckets, where it is bounded at
     * 366 entries; the lifetime bucket leaves it empty.
     */
    val dailySeconds: Map<String, Long> = emptyMap()
)
