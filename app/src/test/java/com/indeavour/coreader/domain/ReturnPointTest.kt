package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnPointTest {

    private val returnPoint = ReturnPoint<String>(tolerance = 0.0005)

    @Test
    fun `nothing to return to while simply reading`() {
        assertFalse(returnPoint.isActive)
        assertNull(returnPoint.anchor)
        assertFalse(returnPoint.onProgress(0.4))
    }

    @Test
    fun `the first jump sets the return point`() {
        assertTrue(returnPoint.mark("page-50", fromProgression = 0.5, targetProgression = 0.2))
        assertEquals("page-50", returnPoint.anchor)
    }

    @Test
    fun `later jumps keep the original return point`() {
        returnPoint.mark("page-50", 0.5, 0.2)
        returnPoint.onProgress(0.2)

        assertFalse(returnPoint.mark("page-20", 0.2, 0.1))
        assertEquals("page-50", returnPoint.anchor)
    }

    @Test
    fun `a jump to the same spot sets nothing`() {
        assertFalse(returnPoint.mark("page-50", 0.5, 0.5001))
        assertFalse(returnPoint.isActive)
    }

    @Test
    fun `a jump with an unknown target still sets the return point`() {
        assertTrue(returnPoint.mark("page-50", 0.5, targetProgression = null))
    }

    @Test
    fun `a stray report from the old page does not clear it`() {
        returnPoint.mark("page-50", 0.5, 0.2)
        assertFalse(returnPoint.onProgress(0.5))
        assertTrue(returnPoint.isActive)
    }

    @Test
    fun `reading forward back to the page clears it`() {
        returnPoint.mark("page-50", 0.5, 0.2)
        assertFalse(returnPoint.onProgress(0.2))  // jump lands
        assertFalse(returnPoint.onProgress(0.35)) // still behind
        assertTrue(returnPoint.onProgress(0.5))
        assertFalse(returnPoint.isActive)
    }

    @Test
    fun `paging back after peeking ahead clears it`() {
        returnPoint.mark("page-50", 0.5, 0.8)
        assertFalse(returnPoint.onProgress(0.8))
        assertFalse(returnPoint.onProgress(0.65))
        assertTrue(returnPoint.onProgress(0.5))
    }

    @Test
    fun `jumping straight past the return point does not clear it`() {
        returnPoint.mark("page-50", 0.5, 0.2)
        returnPoint.onProgress(0.2)

        // Second jump, from 20% to 80%: over the return point, not back to it.
        returnPoint.mark("page-20", 0.2, 0.8)
        assertFalse(returnPoint.onProgress(0.8))
        assertTrue(returnPoint.isActive)
        assertEquals("page-50", returnPoint.anchor)

        // Paging back from there does reach it.
        assertTrue(returnPoint.onProgress(0.5))
    }

    @Test
    fun `clearing forgets the return point`() {
        returnPoint.mark("page-50", 0.5, 0.2)
        returnPoint.clear()
        assertFalse(returnPoint.isActive)
        assertTrue(returnPoint.mark("page-30", 0.3, 0.9))
        assertEquals("page-30", returnPoint.anchor)
    }

    @Test
    fun `a return point with no known position is only cleared by hand`() {
        returnPoint.mark("somewhere", fromProgression = null, targetProgression = 0.2)
        assertFalse(returnPoint.onProgress(0.2))
        assertFalse(returnPoint.onProgress(0.9))
        assertTrue(returnPoint.isActive)
    }
}
