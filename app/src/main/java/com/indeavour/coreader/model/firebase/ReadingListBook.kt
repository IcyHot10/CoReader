package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ReadingListBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val addedTimestamp: Long = 0,
    val position: Int = 0,
    val isSuggestion: Boolean = false,
    val isDismissed: Boolean = false
)
