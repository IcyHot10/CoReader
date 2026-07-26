package com.indeavour.coreader.model.firebase

data class ReadingListDocument(
    val userId: String = "",
    val books: Map<String, ReadingListBook> = emptyMap()
)
