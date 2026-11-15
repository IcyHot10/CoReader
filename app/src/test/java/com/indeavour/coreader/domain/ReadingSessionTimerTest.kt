package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSessionTimerTest {

    private val timer = ReadingSessionTimer(maxSecondsPerTick = 300)
    private val t0 = 1_000_000_000L

    private fun seconds(n: Long) = t0 + n * 1000

    @Test
    fun `nothing is counted before the session starts`() {
        assertFalse(timer.isStarted)
        timer.tick(seconds(60))
        assertEquals(0L, timer.unsavedSeconds)
    }

    @Test
    fun `time between page turns is counted`() {
        timer.start(t0)
        assertTrue(timer.isStarted)
        timer.tick(seconds(40))
        timer.tick(seconds(100))
        assertEquals(100L, timer.unsavedSeconds)
        assertEquals(100L, timer.sessionSeconds)
    }

    @Test
    fun `a page left open is capped`() {
        timer.start(t0)
        timer.tick(seconds(2 * 60 * 60))
        assertEquals(300L, timer.unsavedSeconds)
    }

    @Test
    fun `time away from the reader is not counted`() {
        timer.start(t0)
        timer.tick(seconds(30))
        timer.pause()

        // Closed an hour later without ever coming back.
        timer.tick(seconds(3630))
        assertEquals(30L, timer.unsavedSeconds)

        // Came back: counting restarts from the moment of return.
        timer.resume(seconds(4000))
        timer.tick(seconds(4020))
        assertEquals(50L, timer.unsavedSeconds)
    }

    @Test
    fun `fractions of a second are not lost between quick page turns`() {
        timer.start(t0)
        timer.tick(t0 + 600)
        timer.tick(t0 + 1200)
        assertEquals(1L, timer.unsavedSeconds)
    }

    @Test
    fun `taking unsaved seconds hands them over once`() {
        timer.start(t0)
        timer.tick(seconds(45))
        assertEquals(45L, timer.takeUnsavedSeconds())
        assertEquals(0L, timer.takeUnsavedSeconds())
        // The session keeps its running length across saves.
        assertEquals(45L, timer.sessionSeconds)
    }

    @Test
    fun `ending a session reports its length and starts the next from zero`() {
        timer.start(t0)
        timer.tick(seconds(90))
        timer.takeUnsavedSeconds()
        timer.tick(seconds(150))

        assertEquals(150L, timer.endSession())
        assertEquals(0L, timer.sessionSeconds)

        timer.pause()
        timer.resume(seconds(1000))
        timer.tick(seconds(1020))
        assertEquals(20L, timer.endSession())
    }

    @Test
    fun `starting again resets everything`() {
        timer.start(t0)
        timer.tick(seconds(50))
        timer.pause()

        timer.start(seconds(500))
        timer.tick(seconds(510))
        assertEquals(10L, timer.unsavedSeconds)
        assertEquals(10L, timer.sessionSeconds)
    }

    @Test
    fun `resuming a session that never started does nothing`() {
        timer.resume(seconds(10))
        assertFalse(timer.isStarted)
    }
}
