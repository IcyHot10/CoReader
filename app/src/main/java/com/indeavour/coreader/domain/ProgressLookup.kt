package com.indeavour.coreader.domain

import kotlin.math.abs

object ProgressLookup {

    /**
     * Index of the value in [sortedValues] (ascending) closest to [wanted], or -1 if the list
     * is empty. Used to turn a slider position into the nearest known position in the book.
     */
    fun nearestIndex(sortedValues: List<Double>, wanted: Double): Int {
        if (sortedValues.isEmpty()) return -1
        val found = sortedValues.binarySearch(wanted)
        if (found >= 0) return found
        val after = (-found - 1).coerceIn(0, sortedValues.lastIndex)
        val before = (after - 1).coerceAtLeast(0)
        return if (abs(sortedValues[before] - wanted) <= abs(sortedValues[after] - wanted)) before else after
    }
}
