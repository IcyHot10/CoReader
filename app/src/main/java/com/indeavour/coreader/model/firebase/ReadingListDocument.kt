package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

/** Contents of `readingLists/{uid}`; the uid is the document id and is not repeated inside. */
@IgnoreExtraProperties
data class ReadingListDocument(
    val books: Map<String, ReadingListBook> = emptyMap()
)
