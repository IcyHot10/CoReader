package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressLookupTest {

    private val positions = listOf(0.0, 0.25, 0.5, 0.75)

    @Test
    fun `an empty book has no position`() {
        assertEquals(-1, ProgressLookup.nearestIndex(emptyList(), 0.5))
    }

    @Test
    fun `an exact match is returned`() {
        assertEquals(2, ProgressLookup.nearestIndex(positions, 0.5))
    }

    @Test
    fun `the closer neighbour wins`() {
        assertEquals(1, ProgressLookup.nearestIndex(positions, 0.3))
        assertEquals(2, ProgressLookup.nearestIndex(positions, 0.45))
    }

    @Test
    fun `values outside the range snap to the ends`() {
        assertEquals(0, ProgressLookup.nearestIndex(positions, -1.0))
        assertEquals(3, ProgressLookup.nearestIndex(positions, 1.0))
    }

    @Test
    fun `a single position is always the answer`() {
        assertEquals(0, ProgressLookup.nearestIndex(listOf(0.0), 0.9))
    }
}
