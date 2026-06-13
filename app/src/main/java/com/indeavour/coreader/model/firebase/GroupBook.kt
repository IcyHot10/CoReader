package com.indeavour.coreader.model.firebase

data class GroupBook(
    val groupCode: String = "",
    val bookProgression: Map<String, BookModel> = emptyMap(),
)
