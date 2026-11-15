package com.indeavour.coreader.domain

/**
 * Counts time spent actually reading. Time is supplied by the caller (epoch milliseconds), so
 * the rules can be tested without waiting.
 *
 * Time is counted on each [tick] (a page turn, or leaving the reader) as the gap since the
 * previous one, with two exceptions that are not reading:
 *  - a gap longer than [maxSecondsPerTick] is capped: a page left open that long is the reader
 *    walking away;
 *  - nothing is counted between [pause] and [resume] (the reader is not on screen).
 */
class ReadingSessionTimer(private val maxSecondsPerTick: Long) {

    private var lastTickMillis = 0L
    private var paused = false

    /** Seconds counted but not yet handed over through [takeUnsavedSeconds]. */
    var unsavedSeconds = 0L
        private set

    /** Seconds counted since [start], [resume] or the last [endSession]. */
    var sessionSeconds = 0L
        private set

    val isStarted: Boolean get() = lastTickMillis != 0L

    fun start(nowMillis: Long) {
        lastTickMillis = nowMillis
        unsavedSeconds = 0
        sessionSeconds = 0
        paused = false
    }

    fun tick(nowMillis: Long) {
        if (!isStarted || paused) return
        val elapsed = (nowMillis - lastTickMillis) / 1000
        if (elapsed <= 0) return // Keep the sub-second remainder for the next tick.
        val counted = elapsed.coerceAtMost(maxSecondsPerTick)
        unsavedSeconds += counted
        sessionSeconds += counted
        lastTickMillis = nowMillis
    }

    fun pause() {
        paused = true
    }

    fun resume(nowMillis: Long) {
        if (!isStarted) return
        lastTickMillis = nowMillis
        paused = false
    }

    /** Hands over the seconds counted since the last call, for saving. */
    fun takeUnsavedSeconds(): Long = unsavedSeconds.also { unsavedSeconds = 0 }

    /** Ends the current session and returns how long it was. */
    fun endSession(): Long = sessionSeconds.also { sessionSeconds = 0 }
}
