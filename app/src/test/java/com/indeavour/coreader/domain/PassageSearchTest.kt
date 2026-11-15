package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageSearchTest {

    private val book = listOf(
        ChapterText("One", "Paul lived on Caladan with his mother Jessica. The sea was grey that morning."),
        ChapterText("Two", "The duke spoke of spice and of the desert planet. Jessica listened in silence."),
        ChapterText("Three", "A storm crossed the dunes. Nothing moved in the sand for many hours.")
    )

    private fun find(query: String, maxPassages: Int = 6, passageChars: Int = 700) =
        PassageSearch.find(book, query, maxPassages, passageChars)

    @Test
    fun `passages mentioning the subject are found`() {
        assertEquals(listOf("One", "Two"), find("Who is Jessica?").map { it.chapter })
    }

    @Test
    fun `passages come back in the order of the book`() {
        // "Jessica" appears in both; "duke" only in the second, which therefore scores higher
        // but is still listed second.
        assertEquals(listOf("One", "Two"), find("Jessica and the duke").map { it.chapter })
    }

    @Test
    fun `the best matches are kept when there are too many`() {
        assertEquals(listOf("Two"), find("Jessica and the duke", maxPassages = 1).map { it.chapter })
    }

    @Test
    fun `nothing is returned when the book does not mention it`() {
        assertTrue(find("Where is Gandalf?").isEmpty())
    }

    @Test
    fun `a question made only of filler words finds nothing`() {
        assertTrue(find("What does this mean?").isEmpty())
        assertTrue(find("").isEmpty())
    }

    @Test
    fun `matching ignores case and possessives`() {
        assertEquals(listOf("One", "Two"), find("JESSICA'S role").map { it.chapter })
    }

    @Test
    fun `an empty book has no passages`() {
        assertTrue(PassageSearch.find(emptyList(), "Jessica").isEmpty())
        assertTrue(PassageSearch.find(listOf(ChapterText("One", "")), "Jessica").isEmpty())
    }

    @Test
    fun `long chapters are cut into passages between words`() {
        val pieces = PassageSearch.split("alpha beta gamma delta epsilon zeta eta theta", size = 16)
        assertTrue(pieces.size > 1)
        assertEquals("alpha beta gamma delta epsilon zeta eta theta", pieces.joinToString(" "))
        assertTrue(pieces.all { it.length <= 16 })
    }

    @Test
    fun `only the matching part of a long chapter is quoted`() {
        val long = ChapterText("Long", "filler words here ".repeat(100) + "Stilgar raised his hand. " + "more filler text ".repeat(100))
        val found = PassageSearch.find(listOf(long), "Stilgar", passageChars = 200)
        assertEquals(1, found.size)
        assertTrue("Stilgar" in found.single().text)
        assertTrue(found.single().text.length <= 200)
    }

    @Test
    fun `names are recognised by their capital letter away from a sentence start`() {
        val terms = PassageSearch.terms("Where did Jessica go? Desert winds rose.")
        assertEquals(true, terms["jessica"])
        // "Desert" only has a capital because it starts a sentence.
        assertEquals(false, terms["desert"])
        assertFalse("where" in terms)
    }
}
