package com.indeavour.coreader.model.firebase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemberColourTest {

    @Test
    fun `initials come from the first two words`() {
        assertEquals("SL", MemberColour.initials("Sam Lee"))
        assertEquals("SL", MemberColour.initials("  sam   lee jones "))
        assertEquals("S", MemberColour.initials("sam"))
        assertEquals("JD", MemberColour.initials("john.doe"))
        assertEquals("JD", MemberColour.initials("john_doe-smith"))
    }

    @Test
    fun `an empty name has a placeholder`() {
        assertEquals("?", MemberColour.initials(""))
        assertEquals("?", MemberColour.initials("   "))
    }

    @Test
    fun `a chosen colour round trips through storage`() {
        MemberColour.PALETTE.forEach { colour ->
            assertEquals(colour, MemberColour.resolve("any-user", MemberColour.encode(colour)))
        }
    }

    @Test
    fun `colours are stored as eight hex digits`() {
        assertEquals("0xFF1E88E5", MemberColour.encode(0xFF1E88E5.toInt()))
    }

    @Test
    fun `members who have not chosen get a stable palette colour`() {
        val untouchedDefault = GroupMember().highlightColour
        val first = MemberColour.resolve("user-a", untouchedDefault)
        assertTrue(first in MemberColour.PALETTE)
        assertEquals(first, MemberColour.resolve("user-a", untouchedDefault))
        assertEquals(first, MemberColour.resolve("user-a", null))
        assertEquals(first, MemberColour.resolve("user-a", "not a colour"))
    }
}
