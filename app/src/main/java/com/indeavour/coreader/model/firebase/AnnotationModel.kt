package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * One highlight or note, stored as its own document under a book's `annotations` subcollection.
 *
 * [locator] stays a Readium `Locator` JSON string so it round-trips through Readium's own
 * serialisation, but [href] and [totalProgression] are denormalised as real fields so annotations
 * can be queried and ordered by position without parsing every document.
 */
@IgnoreExtraProperties
data class AnnotationModel(
    /** Document id. Excluded from the payload; populated from the snapshot on read. */
    @get:Exclude val id: String = "",
    val ownerUid: String = "",
    val type: String = TYPE_HIGHLIGHT,
    val locator: String = "",
    val href: String = "",
    val totalProgression: Double = 0.0,
    val color: Int = DEFAULT_COLOR,
    /** Note body; empty for highlights. */
    val content: String = "",
    /** Replies are bounded per note, so they stay inline and are edited with arrayUnion/arrayRemove. */
    val replies: List<ReplyModel> = emptyList(),
    val createdAt: Long = 0
) {
    @get:Exclude
    val isNote: Boolean get() = type == TYPE_NOTE

    companion object {
        const val TYPE_HIGHLIGHT = "highlight"
        const val TYPE_NOTE = "note"
        const val DEFAULT_COLOR = 0x66FFFF00
    }
}
