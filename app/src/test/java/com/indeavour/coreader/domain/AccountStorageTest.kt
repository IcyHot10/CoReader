package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountStorageTest {

    private val safeName = Regex("[A-Za-z0-9_-]+")

    @Test
    fun `each account gets its own database and folder`() {
        assertNotEquals(AccountStorage.databaseName("alice"), AccountStorage.databaseName("bob"))
        assertNotEquals(AccountStorage.filesFolder("alice"), AccountStorage.filesFolder("bob"))
    }

    @Test
    fun `the same account always gets the same names`() {
        assertEquals(AccountStorage.databaseName("a/b"), AccountStorage.databaseName("a/b"))
        assertEquals("library_db_Xy12", AccountStorage.databaseName("Xy12"))
        assertEquals("Xy12", AccountStorage.filesFolder("Xy12"))
    }

    @Test
    fun `no account is given the shared database from before`() {
        assertNotEquals(AccountStorage.LEGACY_DATABASE, AccountStorage.databaseName("a"))
    }

    @Test
    fun `an id that is not plain letters and digits still gives a safe name`() {
        listOf("../../etc", "a b", "user@example.com", "a-b", "x".repeat(200)).forEach { uid ->
            assertTrue(uid, safeName.matches(AccountStorage.filesFolder(uid)))
            assertTrue(uid, safeName.matches(AccountStorage.databaseName(uid)))
        }
    }

    @Test
    fun `ids differing only in punctuation stay apart`() {
        assertNotEquals(AccountStorage.filesFolder("a.b"), AccountStorage.filesFolder("a/b"))
        assertNotEquals(AccountStorage.filesFolder("a.b"), AccountStorage.filesFolder("ab"))
    }

    @Test
    fun `an empty id is refused`() {
        assertThrows(IllegalArgumentException::class.java) { AccountStorage.filesFolder("") }
    }
}
