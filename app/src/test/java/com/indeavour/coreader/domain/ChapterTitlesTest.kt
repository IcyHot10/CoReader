package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTitlesTest {

    @Test
    fun `each file gets the title of its table of contents entry`() {
        val toc = listOf(TocEntry("ch1.xhtml", "One"), TocEntry("ch2.xhtml", "Two"))
        assertEquals(
            listOf("One", "Two"),
            ChapterTitles.byReadingOrder(toc, listOf("ch1.xhtml", "ch2.xhtml"))
        )
    }

    @Test
    fun `a chapter split over several files keeps its title`() {
        val toc = listOf(TocEntry("ch1.xhtml", "One"), TocEntry("ch2.xhtml", "Two"))
        assertEquals(
            listOf("One", "One", "One", "Two"),
            ChapterTitles.byReadingOrder(toc, listOf("ch1.xhtml", "ch1_split1.xhtml", "ch1_split2.xhtml", "ch2.xhtml"))
        )
    }

    @Test
    fun `files before the first chapter have no title`() {
        val toc = listOf(TocEntry("ch1.xhtml", "One"))
        assertEquals(
            listOf("", "", "One"),
            ChapterTitles.byReadingOrder(toc, listOf("cover.xhtml", "title.xhtml", "ch1.xhtml"))
        )
    }

    @Test
    fun `fragments are ignored when matching files`() {
        val toc = listOf(TocEntry("book.xhtml#chapter-1", "One"))
        assertEquals(listOf("One"), ChapterTitles.byReadingOrder(toc, listOf("book.xhtml")))
    }

    @Test
    fun `a chapter wins over its own sub-sections in the same file`() {
        val toc = listOf(
            TocEntry(
                "ch1.xhtml", "One",
                children = listOf(TocEntry("ch1.xhtml#part-a", "Part A"), TocEntry("ch1.xhtml#part-b", "Part B"))
            )
        )
        assertEquals(listOf("One"), ChapterTitles.byReadingOrder(toc, listOf("ch1.xhtml")))
    }

    @Test
    fun `sub-sections in their own files are named`() {
        val toc = listOf(
            TocEntry("part1.xhtml", "Part I", children = listOf(TocEntry("ch1.xhtml", "One")))
        )
        assertEquals(
            listOf("Part I", "One"),
            ChapterTitles.byReadingOrder(toc, listOf("part1.xhtml", "ch1.xhtml"))
        )
    }

    @Test
    fun `blank and missing titles are skipped`() {
        val toc = listOf(TocEntry("ch1.xhtml", "  One  "), TocEntry("ch2.xhtml", "   "), TocEntry("ch3.xhtml", null))
        assertEquals(
            listOf("One", "One", "One"),
            ChapterTitles.byReadingOrder(toc, listOf("ch1.xhtml", "ch2.xhtml", "ch3.xhtml"))
        )
    }

    @Test
    fun `a position belongs to a book only if it points at one of its files`() {
        val files = listOf("text/ch1.xhtml", "text/ch2.xhtml")
        assertEquals(true, ChapterTitles.contains(files, "text/ch2.xhtml"))
        assertEquals(true, ChapterTitles.contains(files, "text/ch2.xhtml#para-9"))
        assertEquals(false, ChapterTitles.contains(files, "OEBPS/part0007.html"))
        assertEquals(false, ChapterTitles.contains(emptyList(), "text/ch1.xhtml"))
    }

    @Test
    fun `a book with no table of contents has no titles`() {
        assertEquals(listOf("", ""), ChapterTitles.byReadingOrder(emptyList(), listOf("a.xhtml", "b.xhtml")))
    }
}
