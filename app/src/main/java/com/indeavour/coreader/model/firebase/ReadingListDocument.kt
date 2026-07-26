package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ReadingListDocument(
    val userId: String = "",
    val books: Map<String, ReadingListBook> = emptyMap()
)
