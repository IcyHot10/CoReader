package com.indeavour.coreader.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestorePathsTest {

    @Test
    fun `ordinary book keys are used as they are`() {
        assertEquals("Dune_Frank Herbert", FirestorePaths.bookDocId("Dune_Frank Herbert"))
    }

    @Test
    fun `slashes cannot split the document path`() {
        assertEquals("Fahrenheit 9%2F11_Author", FirestorePaths.bookDocId("Fahrenheit 9/11_Author"))
    }

    @Test
    fun `different titles never collapse onto the same id`() {
        assertNotEquals(FirestorePaths.bookDocId("A/B_X"), FirestorePaths.bookDocId("A%2FB_X"))
    }

    @Test
    fun `ids Firestore forbids are escaped`() {
        assertEquals("%00", FirestorePaths.bookDocId(""))
        assertEquals("%2E", FirestorePaths.bookDocId("."))
        assertEquals("%2E%2E", FirestorePaths.bookDocId(".."))
        assertEquals("%5F__name__", FirestorePaths.bookDocId("__name__"))
    }

    @Test
    fun `very long keys are kept within the id size limit`() {
        assertTrue(FirestorePaths.bookDocId("x".repeat(5000)).length <= 400)
    }
}
