package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class ReadingListBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val addedTimestamp: Long = 0,
    val position: Int = 0,
    // Pinned so Firestore stores "isSuggestion"/"isDismissed" (matching the field updates in
    // ReadingListViewModel) rather than stripping the "is" prefix from the getter.
    @get:PropertyName("isSuggestion")
    @set:PropertyName("isSuggestion")
    var isSuggestion: Boolean = false,
    @get:PropertyName("isDismissed")
    @set:PropertyName("isDismissed")
    var isDismissed: Boolean = false,
    /** Why the AI agent suggested this book; blank for books the user added themselves. */
    val reason: String = ""
)
