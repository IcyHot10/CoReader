package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.domain.ReadingStatsCalculator
import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import kotlinx.coroutines.tasks.await
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
     * Records reading time against both the current year and the lifetime totals.
     *
     * Days are the reader's own calendar days ([zone]), so reading late in the evening counts
     * for that day rather than for whichever day it happens to be in UTC.
     *
     * [sessionSeconds] is the length of the session that just ended, or 0 when this call is a
     * mid-session flush.
     *
     * What changes is decided by [ReadingStatsCalculator]; this only reads the current values
     * and writes the result. It is a transaction because the streak has to be derived from the
     * stored value before it can be written back. The write addresses individual fields and
     * uses [FieldValue.increment] for the additive counters, so it does not rewrite other
     * years' buckets, and the book's completion timestamp does not rewrite the library map.
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
        val today = ReadingStatsCalculator.dayOf(nowMillis, zone)
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
                countCompletion = ReadingStatsCalculator.countsAsNewCompletion(newlyCompleted, alreadyStamped)
            }

            val yearly = existing.yearlyStats[yearKey] ?: UserStats()
            val newStreak = ReadingStatsCalculator.nextStreak(existing, today, zone)

            fun fieldsFor(bucket: UserStats): Map<String, Any> = ReadingStatsCalculator
                .bucketUpdate(bucket, newStreak, secondsToSave, countCompletion, sessionSeconds, nowMillis, zone)
                .toFields()

            val yearlyFields = fieldsFor(yearly) + mapOf(
                "dailySeconds" to mapOf(
                    ReadingStatsCalculator.dayKey(today) to FieldValue.increment(secondsToSave)
                )
            )

            transaction.set(
                statsRef,
                mapOf(
                    FIELD_YEARLY to mapOf(yearKey to yearlyFields),
                    FIELD_LIFETIME to fieldsFor(existing.lifetimeStats)
                ),
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

    private fun ReadingStatsCalculator.BucketUpdate.toFields(): Map<String, Any> = mapOf(
        "secondsRead" to FieldValue.increment(secondsDelta),
        "booksCompleted" to FieldValue.increment(booksCompletedDelta),
        "currentStreak" to currentStreak,
        "maxStreak" to maxStreak,
        "lastReadingTimestamp" to lastReadingTimestamp,
        "daysRead" to FieldValue.increment(daysReadDelta),
        "sessions" to FieldValue.increment(sessionsDelta),
        "longestSessionSeconds" to longestSessionSeconds
    )

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
        val yearKey = ReadingStatsCalculator.dayOf(nowMillis, zone).year.toString()
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
