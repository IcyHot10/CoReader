package com.indeavour.coreader.domain

import kotlin.math.abs

/**
 * Remembers where the reader was before they jumped elsewhere in the book (slider, note, table
 * of contents), so they can be offered a way back.
 *
 * Positions are progressions through the whole book, 0..1. [T] is whatever the caller needs to
 * navigate back with.
 */
class ReturnPoint<T : Any>(private val tolerance: Double = 0.0005) {

    /** The place to return to, or null while the reader is simply reading. */
    var anchor: T? = null
        private set

    private var anchorProgression: Double? = null

    /** Whether the anchor lies ahead of where the jump landed; unknown until it lands. */
    private var anchorIsAhead: Boolean? = null

    val isActive: Boolean get() = anchor != null

    /**
     * Call just before jumping away from [from]. The first jump sets the return point; further
     * jumps keep it, so it always leads back to where reading actually stopped.
     *
     * Returns true if a new return point was set.
     */
    fun mark(from: T, fromProgression: Double?, targetProgression: Double? = null): Boolean {
        if (anchor != null) {
            // Another jump: work out afresh which way "back" is once it lands, so that jumping
            // straight past the return point is not mistaken for having read back to it.
            anchorIsAhead = null
            return false
        }
        if (targetProgression != null && fromProgression != null &&
            abs(targetProgression - fromProgression) < tolerance
        ) {
            return false // Not going anywhere worth coming back from.
        }
        anchor = from
        anchorProgression = fromProgression
        anchorIsAhead = null
        return true
    }

    fun clear() {
        anchor = null
        anchorProgression = null
        anchorIsAhead = null
    }

    /**
     * Reports the reader's position after each page change. Clears the return point once they
     * have read (or paged) their way back to it, and returns true when that happens.
     */
    fun onProgress(current: Double): Boolean {
        if (anchor == null) return false
        val target = anchorProgression ?: return false
        val isAhead = anchorIsAhead
        if (isAhead == null) {
            // Wait for the jump to land before deciding which way "back" is.
            if (abs(current - target) > tolerance) anchorIsAhead = current < target
            return false
        }
        val reached = if (isAhead) current >= target - tolerance else current <= target + tolerance
        if (reached) clear()
        return reached
    }
}
