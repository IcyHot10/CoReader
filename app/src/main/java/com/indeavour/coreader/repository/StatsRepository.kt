package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Access to `userStats/{uid}`: lifetime totals plus a bucket per calendar year.
 */
class StatsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "StatsRepository"
        private const val FIELD_YEARLY = "yearlyStats"
        private const val FIELD_LIFETIME = "lifetimeStats"

        /** Opening a book for a few seconds is not a reading session. */
        const val MIN_SESSION_SECONDS = 60L

        private val DAY_KEY = DateTimeFormatter.ofPattern("MM-dd")

        /** Key of [date] inside a yearly bucket's `dailySeconds` map. */
        fun dayKey(date: LocalDate): String = date.format(DAY_KEY)

        /** Seconds read on [date], looked up in that date's own year. */
        fun secondsOn(document: UserStatsDocument, date: LocalDate): Long =
            document.yearlyStats[date.year.toString()]?.dailySeconds?.get(dayKey(date)) ?: 0L

        /**
         * The streak as it stands on [today]. The stored value is only updated when something is
         * read, so on its own it keeps reporting an old streak after days without reading.
         */
        fun liveStreak(document: UserStatsDocument, today: LocalDate, zone: ZoneId): Int {
            val (streak, lastReading) = storedStreak(document, today.year.toString())
            if (lastReading <= 0) return 0
            val lastDay = Instant.ofEpochMilli(lastReading).atZone(zone).toLocalDate()
            return if (lastDay < today.minusDays(1)) 0 else streak
        }

        fun readOn(document: UserStatsDocument, day: LocalDate, zone: ZoneId): Boolean {
            val (_, lastReading) = storedStreak(document, day.year.toString())
            if (lastReading <= 0) return false
            return Instant.ofEpochMilli(lastReading).atZone(zone).toLocalDate() == day
        }

        /**
         * Lifetime carries the streak; the yearly bucket is the fallback for documents written
         * before it did.
         */
        private fun storedStreak(document: UserStatsDocument, yearKey: String): Pair<Int, Long> {
            val lifetime = document.lifetimeStats
            val yearly = document.yearlyStats[yearKey]
            val lastReading = if (lifetime.lastReadingTimestamp > 0) {
                lifetime.lastReadingTimestamp
            } else {
                yearly?.lastReadingTimestamp ?: 0L
            }
            val streak = if (lifetime.currentStreak > 0) lifetime.currentStreak else yearly?.currentStreak ?: 0
            return streak to lastReading
        }
    }

    suspend fun stats(uid: String): UserStatsDocument? =
        FirestorePaths.userStats(db, uid).get().await().toObject(UserStatsDocument::class.java)

    /**
     * Records reading time against both the current year and the lifetime totals.
     *
     * Days are the reader's own calendar days ([zone]), so reading late in the evening counts
     * for that day rather than for whichever day it happens to be in UTC.
     *
     * [sessionSeconds] is the length of the session that just ended, or 0 when this call is a
     * mid-session flush.
     *
     * Still a transaction, because the streak has to be derived from the stored value before it
     * can be written back. The write itself addresses individual fields and uses
     * [FieldValue.increment] for the additive counters, so it does not rewrite other years'
     * buckets, and the book's completion timestamp does not rewrite the whole library map.
     */
    suspend fun recordReading(
        uid: String,
        secondsToSave: Long,
        newlyCompleted: Boolean,
        nowMillis: Long,
        bookKey: String?,
        sessionSeconds: Long = 0,
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val statsRef = FirestorePaths.userStats(db, uid)
        val userRef = FirestorePaths.user(db, uid)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val yearKey = today.year.toString()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(statsRef)
            val existing = if (snapshot.exists()) {
                snapshot.toObject(UserStatsDocument::class.java) ?: UserStatsDocument()
            } else {
                UserStatsDocument()
            }

            // Reads must all happen before any write in a transaction.
            var stampCompletion = false
            var countCompletion = newlyCompleted
            if (newlyCompleted && bookKey != null) {
                val books = transaction.get(userRef).get(FirestorePaths.BOOKS) as? Map<*, *>
                val book = books?.get(bookKey) as? Map<*, *>
                val alreadyStamped = ((book?.get("completedTimestamp") as? Number)?.toLong() ?: 0L) > 0L
                stampCompletion = book != null && !alreadyStamped
                // Finishing a book again (a re-read, or scrolling back and forth over the last
                // page in a later session) is not another book read.
                countCompletion = !alreadyStamped
            }

            val yearly = existing.yearlyStats[yearKey] ?: UserStats()
            val lifetime = existing.lifetimeStats
            val newStreak = nextStreak(existing, yearKey, today, zone)

            val yearlyFields = readingFields(
                yearly, newStreak, secondsToSave, countCompletion, sessionSeconds, nowMillis, today, zone
            ) + mapOf("dailySeconds" to mapOf(dayKey(today) to FieldValue.increment(secondsToSave)))
            val lifetimeFields = readingFields(
                lifetime, newStreak, secondsToSave, countCompletion, sessionSeconds, nowMillis, today, zone
            )

            transaction.set(
                statsRef,
                mapOf(FIELD_YEARLY to mapOf(yearKey to yearlyFields), FIELD_LIFETIME to lifetimeFields),
                SetOptions.merge()
            )

            if (stampCompletion && bookKey != null) {
                transaction.update(
                    userRef,
                    FieldPath.of(FirestorePaths.BOOKS, bookKey, "completedTimestamp"),
                    nowMillis
                )
            }
        }.await()
    }

    private fun readingFields(
        previous: UserStats,
        newStreak: Int,
        secondsToSave: Long,
        countCompletion: Boolean,
        sessionSeconds: Long,
        nowMillis: Long,
        today: LocalDate,
        zone: ZoneId
    ): Map<String, Any> {
        val firstReadingToday = previous.lastReadingTimestamp <= 0 ||
            Instant.ofEpochMilli(previous.lastReadingTimestamp).atZone(zone).toLocalDate() != today
        val countsAsSession = sessionSeconds >= MIN_SESSION_SECONDS

        return mapOf(
            "secondsRead" to FieldValue.increment(secondsToSave),
            "booksCompleted" to FieldValue.increment(if (countCompletion) 1L else 0L),
            "currentStreak" to newStreak,
            "maxStreak" to maxOf(previous.maxStreak, newStreak),
            "lastReadingTimestamp" to nowMillis,
            "daysRead" to FieldValue.increment(if (firstReadingToday) 1L else 0L),
            "sessions" to FieldValue.increment(if (countsAsSession) 1L else 0L),
            "longestSessionSeconds" to maxOf(previous.longestSessionSeconds, sessionSeconds)
        )
    }

    /**
     * Continuity is read from lifetime rather than yearly stats: the yearly bucket is empty on
     * January 1st, which would wipe a streak spanning New Year.
     */
    private fun nextStreak(
        document: UserStatsDocument,
        yearKey: String,
        today: LocalDate,
        zone: ZoneId
    ): Int {
        val (stored, lastReading) = storedStreak(document, yearKey)
        var streak = stored

        if (lastReading > 0) {
            // Calendar days, not fixed 24h spans: across a DST change yesterday's midnight is
            // 23 or 25 hours ago.
            val lastDay = Instant.ofEpochMilli(lastReading).atZone(zone).toLocalDate()
            when {
                lastDay == today -> Unit
                lastDay == today.minusDays(1) -> streak++
                else -> streak = 1
            }
        } else {
            streak = 1
        }
        // A reading event is being recorded now, so the streak covers at least today.
        return streak.coerceAtLeast(1)
    }

    /**
     * Counts a highlight or note the reader just made. Plain increments, so unlike
     * [recordReading] this is queued and applied later when the device is offline.
     */
    suspend fun recordAnnotation(
        uid: String,
        isNote: Boolean,
        nowMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        val yearKey = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().year.toString()
        val field = if (isNote) "notesWritten" else "highlightsMade"
        val increment = mapOf(field to FieldValue.increment(1L))
        FirestorePaths.userStats(db, uid)
            .set(
                mapOf(FIELD_YEARLY to mapOf(yearKey to increment), FIELD_LIFETIME to increment),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun safeStats(uid: String): UserStatsDocument? = try {
        stats(uid)
    } catch (e: Exception) {
        Log.e(TAG, "Failed to load stats for $uid", e)
        null
    }

    /**
     * Writes back the lifetime totals when they disagree with what the library actually contains
     * (a book deleted in the cloud, for instance, should stop counting as completed).
     *
     * Only the fields that drifted are written, so the other years' buckets are untouched.
     */
    suspend fun reconcileLifetime(
        uid: String,
        stats: UserStatsDocument,
        currentYear: String,
        booksCompleted: Int,
        currentStreak: Int,
        lastReadingTimestamp: Long,
        maxStreak: Int
    ) {
        val lifetime = stats.lifetimeStats
        val drifted = lifetime.booksCompleted != booksCompleted ||
            lifetime.currentStreak != currentStreak ||
            lifetime.lastReadingTimestamp != lastReadingTimestamp
        if (!drifted) return

        val payload = mutableMapOf<String, Any>(
            FIELD_LIFETIME to mapOf(
                "booksCompleted" to booksCompleted,
                "currentStreak" to currentStreak,
                "lastReadingTimestamp" to lastReadingTimestamp,
                "maxStreak" to maxOf(maxStreak, lifetime.maxStreak)
            )
        )
        stats.yearlyStats[currentYear]?.let { yearly ->
            payload[FIELD_YEARLY] = mapOf(
                currentYear to mapOf("maxStreak" to maxOf(maxStreak, yearly.maxStreak))
            )
        }

        try {
            FirestorePaths.userStats(db, uid).set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reconcile lifetime stats for $uid", e)
        }
    }
}
