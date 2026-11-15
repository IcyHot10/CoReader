package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadSoFarTest {

    private val book = listOf(
        ChapterText("One", "0123456789"),
        ChapterText("Two", "abcdefghij"),
        ChapterText("Three", "ABCDEFGHIJ")
    )

    // ---------------------------------------------------------------- end of the visible page

    @Test
    fun `the page in front of the reader counts as read`() {
        // Third of ten pages: the position marks its top (20%), reading it takes them to 30%.
        assertEquals(0.3, ReadSoFar.endOfPage(pageIndex = 2, totalPages = 10, progression = 0.2), 1e-9)
    }

    @Test
    fun `the last page reads to the end of the chapter`() {
        assertEquals(1.0, ReadSoFar.endOfPage(pageIndex = 9, totalPages = 10, progression = 0.9), 1e-9)
    }

    @Test
    fun `the end of the page is never before its start`() {
        // Page numbers and position can disagree briefly while a chapter is laid out.
        assertEquals(0.8, ReadSoFar.endOfPage(pageIndex = 0, totalPages = 10, progression = 0.8), 1e-9)
    }

    @Test
    fun `without page information the position itself is used`() {
        assertEquals(0.4, ReadSoFar.endOfPage(pageIndex = 0, totalPages = 0, progression = 0.4), 1e-9)
        assertEquals(0.4, ReadSoFar.endOfPage(pageIndex = -1, totalPages = 10, progression = 0.4), 1e-9)
    }

    // ---------------------------------------------------------------- what has been read

    @Test
    fun `earlier chapters are read in full and the current one up to the position`() {
        assertEquals(
            listOf(ChapterText("One", "0123456789"), ChapterText("Two", "abcde")),
            ReadSoFar.chapters(book, ReadPosition(chapterIndex = 1, throughChapter = 0.5))
        )
    }

    @Test
    fun `nothing after the position is included`() {
        val read = ReadSoFar.chapters(book, ReadPosition(1, 0.5))
        assertTrue(read.none { it.title == "Three" })
        assertTrue(read.none { "f" in it.text })
    }

    @Test
    fun `at the very start nothing has been read`() {
        assertEquals(listOf(ChapterText("One", "")), ReadSoFar.chapters(book, ReadPosition(0, 0.0)))
    }

    @Test
    fun `an unknown position means nothing is treated as read`() {
        assertTrue(ReadSoFar.chapters(book, null).isEmpty())
        assertTrue(ReadSoFar.chapters(book, ReadPosition(7, 0.5)).isEmpty())
        assertTrue(ReadSoFar.chapters(book, ReadPosition(-1, 0.5)).isEmpty())
    }

    @Test
    fun `a position outside the chapter is clamped`() {
        assertEquals("0123456789", ReadSoFar.chapters(book, ReadPosition(0, 4.0)).single().text)
        assertEquals("", ReadSoFar.chapters(book, ReadPosition(0, -1.0)).single().text)
    }

    // ---------------------------------------------------------------- recent text

    @Test
    fun `recent text is the end of what has been read`() {
        assertEquals("cde", ReadSoFar.recentText(book, ReadPosition(1, 0.5), maxChars = 3))
    }

    @Test
    fun `recent text reaches back into the previous chapter near the start of one`() {
        assertEquals("6789 ab", ReadSoFar.recentText(book, ReadPosition(1, 0.2), maxChars = 7))
    }

    @Test
    fun `recent text never includes anything unread`() {
        val recent = ReadSoFar.recentText(book, ReadPosition(1, 0.5), maxChars = 1000)
        assertEquals("0123456789 abcde", recent)
    }

    @Test
    fun `recent text is empty when nothing has been read`() {
        assertEquals("", ReadSoFar.recentText(book, ReadPosition(0, 0.0), maxChars = 100))
        assertEquals("", ReadSoFar.recentText(book, null, maxChars = 100))
    }
}
