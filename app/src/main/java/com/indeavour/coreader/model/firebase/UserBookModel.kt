package com.indeavour.coreader.model.firebase


data class UserBookModel(
    val userId: String,
    val bookId: String,
    val progress: Double
)
