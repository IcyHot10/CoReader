package com.indeavour.coreader.model.firebase

data class BookModel(
    val title: String = "",
    val author: String = "",
    val progress: String = "",
    val isDeleted: Boolean = false
)
