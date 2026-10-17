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
    }

    suspend fun stats(uid: String): UserStatsDocument? =
        FirestorePaths.userStats(db, uid).get().await().toObject(UserStatsDocument::class.java)

    /**
     * Records a reading session against both the current year and the lifetime totals.
     *
     * Still a transaction, because the streak has to be derived from the stored value before it
     * can be written back. The write itself now addresses individual fields and uses
     * [FieldValue.increment] for the additive counters, so it no longer rewrites every year's
     * bucket, and the book's completion timestamp no longer rewrites the whole library map.
     */
    suspend fun recordReading(
        uid: String,
        secondsToSave: Long,
        newlyCompleted: Boolean,
        nowMillis: Long,
        bookKey: String?,
        zone: ZoneId = ZoneId.of("UTC")
    ) {
        val statsRef = FirestorePaths.userStats(db, uid)
        val userRef = FirestorePaths.user(db, uid)
        val yearKey = LocalDate.now(zone).year.toString()

        db.runTransaction { transaction ->
            val snapshot = transaction.get(statsRef)
            val existing = if (snapshot.exists()) {
                snapshot.toObject(UserStatsDocument::class.java) ?: UserStatsDocument()
            } else {
                UserStatsDocument()
            }

            // Reads must all happen before any write in a transaction.
            val completionKey: String? = if (newlyCompleted && bookKey != null) {
                val books = transaction.get(userRef).get(FirestorePaths.BOOKS) as? Map<*, *>
                val book = books?.get(bookKey) as? Map<*, *>
                val alreadyStamped = ((book?.get("completedTimestamp") as? Number)?.toLong() ?: 0L) > 0L
                if (book != null && !alreadyStamped) bookKey else null
            } else {
                null
            }

            val yearly = existing.yearlyStats[yearKey] ?: UserStats()
            val newStreak = nextStreak(existing, yearly, nowMillis, zone)

            transaction.set(
                statsRef,
                mapOf(
                    FIELD_YEARLY to mapOf(yearKey to streakFields(yearly, newStreak, secondsToSave, newlyCompleted, nowMillis)),
                    FIELD_LIFETIME to streakFields(existing.lifetimeStats, newStreak, secondsToSave, newlyCompleted, nowMillis)
                ),
                SetOptions.merge()
            )

            if (completionKey != null) {
                transaction.update(
                    userRef,
                    FieldPath.of(FirestorePaths.BOOKS, completionKey, "completedTimestamp"),
                    nowMillis
                )
            }
        }.await()
    }

    private fun streakFields(
        previous: UserStats,
        newStreak: Int,
        secondsToSave: Long,
        newlyCompleted: Boolean,
        nowMillis: Long
    ): Map<String, Any> = mapOf(
        "secondsRead" to FieldValue.increment(secondsToSave),
        "booksCompleted" to FieldValue.increment(if (newlyCompleted) 1L else 0L),
        "currentStreak" to newStreak,
        "maxStreak" to maxOf(previous.maxStreak, newStreak),
        "lastReadingTimestamp" to nowMillis
    )

    /**
     * Continuity is read from lifetime rather than yearly stats: the yearly bucket is empty on
     * January 1st, which would wipe a streak spanning New Year. Yearly is the fallback for
     * documents written before lifetime carried the streak.
     */
    private fun nextStreak(
        document: UserStatsDocument,
        yearly: UserStats,
        nowMillis: Long,
        zone: ZoneId
    ): Int {
        val lifetime = document.lifetimeStats
        val lastReading = if (lifetime.lastReadingTimestamp > 0) {
            lifetime.lastReadingTimestamp
        } else {
            yearly.lastReadingTimestamp
        }
        var streak = if (lifetime.currentStreak > 0) lifetime.currentStreak else yearly.currentStreak

        if (lastReading > 0) {
            // Calendar days, not fixed 24h spans: across a DST change yesterday's midnight is
            // 23 or 25 hours ago.
            val today = LocalDate.now(zone)
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
