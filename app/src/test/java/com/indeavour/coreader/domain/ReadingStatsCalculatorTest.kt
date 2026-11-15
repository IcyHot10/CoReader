package com.indeavour.coreader.domain

import com.indeavour.coreader.model.firebase.UserStats
import com.indeavour.coreader.model.firebase.UserStatsDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadingStatsCalculatorTest {

    // Two hours ahead of UTC, so a late evening here is still the same day in UTC but an early
    // morning here is the previous day in UTC.
    private val zone = ZoneId.of("Africa/Johannesburg")

    private fun at(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun lifetime(streak: Int, lastRead: LocalDate?, hour: Int = 12) = UserStatsDocument(
        lifetimeStats = UserStats(
            currentStreak = streak,
            lastReadingTimestamp = lastRead?.let { at(it, hour) } ?: 0L
        )
    )

    private val today: LocalDate = LocalDate.of(2026, 6, 15)

    // ---------------------------------------------------------------- nextStreak

    @Test
    fun `first ever reading starts a streak of one`() {
        assertEquals(1, ReadingStatsCalculator.nextStreak(UserStatsDocument(), today, zone))
    }

    @Test
    fun `reading again on the same day keeps the streak`() {
        assertEquals(4, ReadingStatsCalculator.nextStreak(lifetime(4, today), today, zone))
    }

    @Test
    fun `reading the day after extends the streak`() {
        assertEquals(5, ReadingStatsCalculator.nextStreak(lifetime(4, today.minusDays(1)), today, zone))
    }

    @Test
    fun `missing a day restarts the streak`() {
        assertEquals(1, ReadingStatsCalculator.nextStreak(lifetime(4, today.minusDays(2)), today, zone))
    }

    @Test
    fun `streak carries over new year`() {
        val newYearsDay = LocalDate.of(2027, 1, 1)
        val document = lifetime(10, LocalDate.of(2026, 12, 31))
        assertEquals(11, ReadingStatsCalculator.nextStreak(document, newYearsDay, zone))
    }

    @Test
    fun `days are the reader's local days not UTC days`() {
        // 01:00 local on the 15th is 23:00 UTC on the 14th. Read at 23:00 local on the 14th
        // (21:00 UTC on the 14th): in UTC both fall on the 14th, locally they are consecutive days.
        val document = lifetime(3, today.minusDays(1), hour = 23)
        assertEquals(4, ReadingStatsCalculator.nextStreak(document, today, zone))
    }

    @Test
    fun `streak falls back to the yearly bucket on older documents`() {
        val document = UserStatsDocument(
            yearlyStats = mapOf(
                "2026" to UserStats(currentStreak = 6, lastReadingTimestamp = at(today.minusDays(1)))
            )
        )
        assertEquals(7, ReadingStatsCalculator.nextStreak(document, today, zone))
    }

    // ---------------------------------------------------------------- liveStreak / readOn

    @Test
    fun `live streak is the stored streak when read today or yesterday`() {
        assertEquals(4, ReadingStatsCalculator.liveStreak(lifetime(4, today), today, zone))
        assertEquals(4, ReadingStatsCalculator.liveStreak(lifetime(4, today.minusDays(1)), today, zone))
    }

    @Test
    fun `live streak is zero once a day has been missed`() {
        assertEquals(0, ReadingStatsCalculator.liveStreak(lifetime(4, today.minusDays(2)), today, zone))
    }

    @Test
    fun `live streak is zero with no reading at all`() {
        assertEquals(0, ReadingStatsCalculator.liveStreak(UserStatsDocument(), today, zone))
    }

    @Test
    fun `readOn tells today from yesterday`() {
        assertTrue(ReadingStatsCalculator.readOn(lifetime(1, today), today, zone))
        assertFalse(ReadingStatsCalculator.readOn(lifetime(1, today.minusDays(1)), today, zone))
        assertFalse(ReadingStatsCalculator.readOn(UserStatsDocument(), today, zone))
    }

    // ---------------------------------------------------------------- bucketUpdate

    private fun update(
        previous: UserStats = UserStats(),
        newStreak: Int = 1,
        seconds: Long = 120,
        countCompletion: Boolean = false,
        sessionSeconds: Long = 0,
        now: Long = at(today)
    ) = ReadingStatsCalculator.bucketUpdate(previous, newStreak, seconds, countCompletion, sessionSeconds, now, zone)

    @Test
    fun `first reading of a day counts a day read`() {
        assertEquals(1L, update().daysReadDelta)
        assertEquals(1L, update(previous = UserStats(lastReadingTimestamp = at(today.minusDays(1)))).daysReadDelta)
    }

    @Test
    fun `reading again the same day does not count another day`() {
        val previous = UserStats(lastReadingTimestamp = at(today, hour = 8))
        assertEquals(0L, update(previous = previous, now = at(today, hour = 20)).daysReadDelta)
    }

    @Test
    fun `sessions shorter than a minute are not counted`() {
        assertEquals(0L, update(sessionSeconds = 0).sessionsDelta)
        assertEquals(0L, update(sessionSeconds = 59).sessionsDelta)
        assertEquals(1L, update(sessionSeconds = 60).sessionsDelta)
    }

    @Test
    fun `longest session and best streak only ever grow`() {
        val previous = UserStats(longestSessionSeconds = 900, maxStreak = 7)
        val shorter = update(previous = previous, newStreak = 2, sessionSeconds = 300)
        assertEquals(900L, shorter.longestSessionSeconds)
        assertEquals(7, shorter.maxStreak)

        val longer = update(previous = previous, newStreak = 8, sessionSeconds = 1200)
        assertEquals(1200L, longer.longestSessionSeconds)
        assertEquals(8, longer.maxStreak)
    }

    @Test
    fun `update carries the time read and the completion`() {
        val result = update(seconds = 345, countCompletion = true, newStreak = 3)
        assertEquals(345L, result.secondsDelta)
        assertEquals(1L, result.booksCompletedDelta)
        assertEquals(3, result.currentStreak)
        assertEquals(at(today), result.lastReadingTimestamp)
        assertEquals(0L, update(countCompletion = false).booksCompletedDelta)
    }

    // ---------------------------------------------------------------- completion

    @Test
    fun `a book only counts as completed the first time`() {
        assertTrue(ReadingStatsCalculator.countsAsNewCompletion(newlyCompleted = true, alreadyStamped = false))
        assertFalse(ReadingStatsCalculator.countsAsNewCompletion(newlyCompleted = true, alreadyStamped = true))
        assertFalse(ReadingStatsCalculator.countsAsNewCompletion(newlyCompleted = false, alreadyStamped = false))
    }

    // ---------------------------------------------------------------- daily seconds

    @Test
    fun `seconds for a day are looked up in that day's year`() {
        val document = UserStatsDocument(
            yearlyStats = mapOf(
                "2025" to UserStats(dailySeconds = mapOf("12-31" to 600L)),
                "2026" to UserStats(dailySeconds = mapOf("01-01" to 300L))
            )
        )
        assertEquals(600L, ReadingStatsCalculator.secondsOn(document, LocalDate.of(2025, 12, 31)))
        assertEquals(300L, ReadingStatsCalculator.secondsOn(document, LocalDate.of(2026, 1, 1)))
        assertEquals(0L, ReadingStatsCalculator.secondsOn(document, LocalDate.of(2026, 1, 2)))
    }

    @Test
    fun `day keys are zero padded month and day`() {
        assertEquals("03-07", ReadingStatsCalculator.dayKey(LocalDate.of(2026, 3, 7)))
    }

    // ---------------------------------------------------------------- formatting

    @Test
    fun `durations are formatted for people`() {
        assertEquals("0 min", ReadingStatsCalculator.formatDuration(0))
        assertEquals("<1 min", ReadingStatsCalculator.formatDuration(59))
        assertEquals("1 min", ReadingStatsCalculator.formatDuration(60))
        assertEquals("59 min", ReadingStatsCalculator.formatDuration(3599))
        assertEquals("1h 0m", ReadingStatsCalculator.formatDuration(3600))
        assertEquals("2h 5m", ReadingStatsCalculator.formatDuration(2 * 3600 + 5 * 60 + 30))
    }
}
