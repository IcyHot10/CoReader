package com.indeavour.coreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raising the database version without a migration stops the app opening anyone's library.
 * These fail first, at build time, rather than on a reader's phone.
 */
class AppRoomDatabaseMigrationsTest {

    private val steps = AppRoomDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }

    @Test
    fun `every version since migrations began can be upgraded to the next`() {
        val missing = (AppRoomDatabase.FIRST_MIGRATED_VERSION until AppRoomDatabase.VERSION)
            .map { it to it + 1 }
            .filterNot { it in steps }
        assertTrue("No migration for $missing", missing.isEmpty())
    }

    @Test
    fun `no migration is registered twice or goes past the current version`() {
        assertEquals(steps.size, steps.toSet().size)
        assertTrue(steps.all { (_, to) -> to <= AppRoomDatabase.VERSION })
    }
}
