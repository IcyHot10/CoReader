package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupReadingListDocument(
    val books: Map<String, GroupReadingListBook> = emptyMap()
)
