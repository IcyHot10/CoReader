package com.indeavour.coreader.domain

import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The rules behind the reading stats: what counts as a streak, a reading day, a session, a
 * finished book. Kept free of Firestore and Android so they can be unit tested; the repository
 * only turns the results into writes.
 */
object ReadingStatsCalculator {

    /** Opening a book for a few seconds is not a reading session. */
    const val MIN_SESSION_SECONDS = 60L

    private val DAY_KEY = DateTimeFormatter.ofPattern("MM-dd")

    /** Key of [date] inside a yearly bucket's `dailySeconds` map. */
    fun dayKey(date: LocalDate): String = date.format(DAY_KEY)

    /** Seconds read on [date], looked up in that date's own year. */
    fun secondsOn(document: UserStatsDocument, date: LocalDate): Long =
        document.yearlyStats[date.year.toString()]?.dailySeconds?.get(dayKey(date)) ?: 0L

    fun dayOf(timestampMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()

    data class StoredStreak(val streak: Int, val lastReadingTimestamp: Long)

    /**
     * Lifetime carries the streak; the yearly bucket is the fallback for documents written
     * before it did.
     */
    fun storedStreak(document: UserStatsDocument, yearKey: String): StoredStreak {
        val lifetime = document.lifetimeStats
        val yearly = document.yearlyStats[yearKey]
        val lastReading = if (lifetime.lastReadingTimestamp > 0) {
            lifetime.lastReadingTimestamp
        } else {
            yearly?.lastReadingTimestamp ?: 0L
        }
        val streak = if (lifetime.currentStreak > 0) lifetime.currentStreak else yearly?.currentStreak ?: 0
        return StoredStreak(streak, lastReading)
    }

    /**
     * The streak as it stands on [today]. The stored value is only updated when something is
     * read, so on its own it keeps reporting an old streak after days without reading.
     */
    fun liveStreak(document: UserStatsDocument, today: LocalDate, zone: ZoneId): Int {
        val stored = storedStreak(document, today.year.toString())
        if (stored.lastReadingTimestamp <= 0) return 0
        val lastDay = dayOf(stored.lastReadingTimestamp, zone)
        return if (lastDay < today.minusDays(1)) 0 else stored.streak
    }

    /** Whether the most recent reading happened on [day]. */
    fun readOn(document: UserStatsDocument, day: LocalDate, zone: ZoneId): Boolean {
        val stored = storedStreak(document, day.year.toString())
        if (stored.lastReadingTimestamp <= 0) return false
        return dayOf(stored.lastReadingTimestamp, zone) == day
    }

    /**
     * The streak after reading on [today].
     *
     * Continuity is read from lifetime rather than yearly stats: the yearly bucket is empty on
     * January 1st, which would wipe a streak spanning New Year.
     */
    fun nextStreak(document: UserStatsDocument, today: LocalDate, zone: ZoneId): Int {
        val stored = storedStreak(document, today.year.toString())
        var streak = stored.streak

        if (stored.lastReadingTimestamp > 0) {
            // Calendar days, not fixed 24h spans: across a DST change yesterday's midnight is
            // 23 or 25 hours ago.
            val lastDay = dayOf(stored.lastReadingTimestamp, zone)
            when {
                lastDay == today -> Unit
                lastDay == today.minusDays(1) -> streak++
                else -> streak = 1
            }
        } else {
            streak = 1
        }
        // Reading is being recorded now, so the streak covers at least today.
        return streak.coerceAtLeast(1)
    }

    /**
     * Finishing a book again (a re-read, or paging back and forth over the last page in a later
     * session) is not another book read.
     */
    fun countsAsNewCompletion(newlyCompleted: Boolean, alreadyStamped: Boolean): Boolean =
        newlyCompleted && !alreadyStamped

    /** What one recording of reading time changes in a stats bucket (a year, or lifetime). */
    data class BucketUpdate(
        val secondsDelta: Long,
        val booksCompletedDelta: Long,
        val currentStreak: Int,
        val maxStreak: Int,
        val lastReadingTimestamp: Long,
        val daysReadDelta: Long,
        val sessionsDelta: Long,
        val longestSessionSeconds: Long
    )

    /**
     * [sessionSeconds] is the length of the session that just ended, or 0 when the recording is
     * a mid-session flush.
     */
    fun bucketUpdate(
        previous: UserStats,
        newStreak: Int,
        secondsToSave: Long,
        countCompletion: Boolean,
        sessionSeconds: Long,
        nowMillis: Long,
        zone: ZoneId
    ): BucketUpdate {
        val today = dayOf(nowMillis, zone)
        val firstReadingToday = previous.lastReadingTimestamp <= 0 ||
            dayOf(previous.lastReadingTimestamp, zone) != today

        return BucketUpdate(
            secondsDelta = secondsToSave,
            booksCompletedDelta = if (countCompletion) 1L else 0L,
            currentStreak = newStreak,
            maxStreak = maxOf(previous.maxStreak, newStreak),
            lastReadingTimestamp = nowMillis,
            daysReadDelta = if (firstReadingToday) 1L else 0L,
            sessionsDelta = if (sessionSeconds >= MIN_SESSION_SECONDS) 1L else 0L,
            longestSessionSeconds = maxOf(previous.longestSessionSeconds, sessionSeconds)
        )
    }

    /** "1h 5m", "12 min", "<1 min". */
    fun formatDuration(seconds: Long): String {
        val minutes = seconds / 60
        val hours = minutes / 60
        return when {
            seconds <= 0 -> "0 min"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "$minutes min"
            else -> "<1 min"
        }
    }
}
