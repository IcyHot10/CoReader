package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ImportFileNamesTest {

    @Test
    fun `two imports of the same file name are stored separately`() {
        assertNotEquals(
            ImportFileNames.book("book.epub", "id-1"),
            ImportFileNames.book("book.epub", "id-2")
        )
    }

    @Test
    fun `the stored name does not depend on the original name`() {
        assertEquals("book_id-1.epub", ImportFileNames.book("Dune.epub", "id-1"))
        assertEquals("book_id-1.epub", ImportFileNames.book("Some other book.epub", "id-1"))
    }

    @Test
    fun `the extension is kept in lower case`() {
        assertEquals("book_id-1.epub", ImportFileNames.book("Dune.EPUB", "id-1"))
        assertEquals("book_id-1.pdf", ImportFileNames.book("paper.v2.pdf", "id-1"))
    }

    @Test
    fun `a missing or unusable extension falls back to epub`() {
        assertEquals("book_id-1.epub", ImportFileNames.book("Dune", "id-1"))
        assertEquals("book_id-1.epub", ImportFileNames.book("Dune.", "id-1"))
        assertEquals("book_id-1.epub", ImportFileNames.book("", "id-1"))
        assertEquals("book_id-1.epub", ImportFileNames.book("a.b/../c", "id-1"))
        assertEquals("book_id-1.epub", ImportFileNames.book("Dune.averylongextension", "id-1"))
    }

    @Test
    fun `a book and its cover share the import id but not a name`() {
        assertEquals("cover_id-1", ImportFileNames.cover("id-1"))
        assertNotEquals(ImportFileNames.book("x.epub", "id-1"), ImportFileNames.cover("id-1"))
    }
}
