package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageIndexTest {

    // ---------------------------------------------------------------- word roots

    @Test
    fun `different forms of a word share a root`() {
        assertEquals(PassageSearch.stem("dies"), PassageSearch.stem("died"))
        assertEquals(PassageSearch.stem("dies"), PassageSearch.stem("dying"))
        assertEquals(PassageSearch.stem("kill"), PassageSearch.stem("killed"))
        assertEquals(PassageSearch.stem("kill"), PassageSearch.stem("kills"))
        assertEquals(PassageSearch.stem("kill"), PassageSearch.stem("killing"))
        assertEquals(PassageSearch.stem("love"), PassageSearch.stem("loved"))
        assertEquals(PassageSearch.stem("love"), PassageSearch.stem("loves"))
        assertEquals(PassageSearch.stem("stop"), PassageSearch.stem("stopped"))
        assertEquals(PassageSearch.stem("city"), PassageSearch.stem("cities"))
        assertEquals(PassageSearch.stem("betray"), PassageSearch.stem("betrayed"))
    }

    @Test
    fun `names and possessives reduce the same way every time`() {
        assertEquals(PassageSearch.stem("Jessica"), PassageSearch.stem("JESSICA'S"))
        assertEquals(PassageSearch.stem("Jessica"), PassageSearch.stem("jessica’s"))
        assertEquals(PassageSearch.stem("James"), PassageSearch.stem("james"))
    }

    @Test
    fun `short words and words that only look like endings are left alone`() {
        assertEquals("red", PassageSearch.stem("red"))
        assertEquals("sing", PassageSearch.stem("sing"))
        assertEquals("glass", PassageSearch.stem("glass"))
        assertEquals("paris", PassageSearch.stem("Paris"))
    }

    @Test
    fun `different words keep different roots`() {
        assertFalse(PassageSearch.stem("duke") == PassageSearch.stem("dune"))
        assertFalse(PassageSearch.stem("sister") == PassageSearch.stem("mother"))
    }

    // ---------------------------------------------------------------- searching

    private val book = listOf(
        ChapterText("One", "The duke arrived at dawn."),
        ChapterText("Two", "The duke was betrayed by the doctor. Later the duke died in the palace."),
        ChapterText("Three", "Paul mourned the duke for many days.")
    )
    private val index = PassageIndex(book, passageChars = 40)

    @Test
    fun `a question finds text using another form of its words`() {
        val found = index.search("Who betrays the duke, and does he die?")
        assertTrue(found.any { "betrayed" in it.text })
        assertTrue(found.any { "died" in it.text })
    }

    @Test
    fun `the whole book is searched by default`() {
        assertEquals(setOf("One", "Two", "Three"), index.search("duke").map { it.chapter }.toSet())
    }

    @Test
    fun `nothing is found for words the book does not use`() {
        assertTrue(index.search("spaceship").isEmpty())
        assertTrue(PassageIndex(emptyList()).search("duke").isEmpty())
    }

    // ---------------------------------------------------------------- read and unread

    /** Chapter two, read up to the end of its first sentence. */
    private val midChapterTwo: ReadPosition
        get() {
            val text = book[1].text
            return ReadPosition(1, (text.indexOf("Later") - 1).toDouble() / text.length)
        }

    @Test
    fun `searching what has been read never returns text from further on`() {
        val read = index.search("duke died betrayed mourned", PassageIndex.Scope.READ, midChapterTwo)
        assertTrue(read.isNotEmpty())
        assertTrue(read.none { it.chapter == "Three" })
        assertTrue(read.none { "died" in it.text })
        assertTrue(read.any { "betrayed" in it.text })
    }

    @Test
    fun `searching what is unread never returns text already read`() {
        val unread = index.search("duke died betrayed arrived", PassageIndex.Scope.UNREAD, midChapterTwo)
        assertTrue(unread.isNotEmpty())
        assertTrue(unread.none { it.chapter == "One" })
        assertTrue(unread.none { "betrayed" in it.text })
        assertTrue(unread.any { "died" in it.text })
        assertTrue(unread.any { it.chapter == "Three" })
    }

    @Test
    fun `read and unread together cover the book without overlapping`() {
        val read = index.search("duke", PassageIndex.Scope.READ, midChapterTwo, maxPassages = 50)
        val unread = index.search("duke", PassageIndex.Scope.UNREAD, midChapterTwo, maxPassages = 50)
        val readText = read.joinToString(" ") { it.text }
        val unreadText = unread.joinToString(" ") { it.text }
        assertTrue("arrived" in readText && "betrayed" in readText)
        assertTrue("died" in unreadText && "mourned" in unreadText)
        assertFalse("died" in readText || "mourned" in readText)
        assertFalse("arrived" in unreadText || "betrayed" in unreadText)
    }

    @Test
    fun `a passage the reader is partway through is cut at their position`() {
        val single = PassageIndex(listOf(ChapterText("Only", "alpha beta gamma delta")), passageChars = 700)
        val position = ReadPosition(0, 0.5) // 11 of 22 characters: "alpha beta "
        assertEquals(listOf("alpha beta"), single.search("alpha gamma", PassageIndex.Scope.READ, position).map { it.text })
        assertEquals(listOf("gamma delta"), single.search("alpha gamma", PassageIndex.Scope.UNREAD, position).map { it.text })
        // A word on the other side of the position cannot be matched at all.
        assertTrue(single.search("gamma", PassageIndex.Scope.READ, position).isEmpty())
        assertTrue(single.search("alpha", PassageIndex.Scope.UNREAD, position).isEmpty())
    }

    @Test
    fun `at the end of the book nothing is unread, at the start nothing is read`() {
        assertTrue(index.search("duke", PassageIndex.Scope.UNREAD, ReadPosition(2, 1.0)).isEmpty())
        assertTrue(index.search("duke", PassageIndex.Scope.READ, ReadPosition(0, 0.0)).isEmpty())
    }

    @Test
    fun `an unknown position matches nothing on either side`() {
        assertTrue(index.search("duke", PassageIndex.Scope.READ, null).isEmpty())
        assertTrue(index.search("duke", PassageIndex.Scope.UNREAD, null).isEmpty())
        assertTrue(index.search("duke", PassageIndex.Scope.READ, ReadPosition(9, 0.5)).isEmpty())
    }
}
