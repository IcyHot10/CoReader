package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupMember(
    var admin: Boolean = false,
    /**
     * The colour this member is shown in to the rest of the group, as "0xAARRGGBB". Members
     * pick it themselves; until they do it holds the translucent default below.
     */
    var highlightColour: String = "0x66FFFF00"
)

/** The colour a group member's initials are shown on. */
object MemberColour {

    /** Choices offered to a member, as opaque ARGB. */
    val PALETTE: List<Int> = listOf(
        0xFFE53935, // red
        0xFFFB8C00, // orange
        0xFFFDD835, // yellow
        0xFF43A047, // green
        0xFF00ACC1, // cyan
        0xFF1E88E5, // blue
        0xFF8E24AA, // purple
        0xFFD81B60  // pink
    ).map { it.toInt() }

    fun encode(argb: Int): String = "0x%08X".format(argb)

    /**
     * The member's chosen colour, or a stable one derived from their id while they have not
     * chosen: a chosen colour is always stored opaque, which the untouched default is not.
     */
    fun resolve(uid: String, stored: String?): Int {
        val parsed = stored?.removePrefix("0x")?.removePrefix("0X")?.toLongOrNull(16)?.toInt()
        if (parsed != null && (parsed ushr 24) == 0xFF) return parsed
        return PALETTE[(uid.hashCode() and Int.MAX_VALUE) % PALETTE.size]
    }

    /** Initials for a display name: "Sam Lee" gives "SL", "sam" gives "S". */
    fun initials(name: String): String {
        val words = name.trim().split(Regex("[\\s._-]+")).filter { it.isNotEmpty() }
        return words.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    }
}
