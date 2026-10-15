package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class ReplyModel(
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "text" to text,
        "timestamp" to timestamp
    )
}

/**
 * A book in a user's library, or one member's copy of a group book.
 *
 * Highlights and notes used to be embedded here as JSON strings; they now live in an
 * `annotations` subcollection (see [com.indeavour.coreader.repository.AnnotationRepository]),
 * which keeps this document small and bounded.
 */
@IgnoreExtraProperties
data class BookModel(
    val title: String = "",
    val author: String = "",
    val progress: String = "",
    @get:PropertyName("isDeleted")
    @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val addedTimestamp: Long = 0,
    val completedTimestamp: Long = 0
) {
    companion object {
        /**
         * Group progress is read back as an untyped map, so entries are converted by hand.
         */
        fun fromAny(data: Any?): BookModel? = when (data) {
            is BookModel -> data
            is Map<*, *> -> BookModel(
                title = data["title"] as? String ?: "",
                author = data["author"] as? String ?: "",
                progress = data["progress"] as? String ?: "",
                isDeleted = data["isDeleted"] as? Boolean ?: false,
                addedTimestamp = (data["addedTimestamp"] as? Number)?.toLong() ?: 0L,
                completedTimestamp = (data["completedTimestamp"] as? Number)?.toLong() ?: 0L
            )
            else -> null
        }
    }
}
