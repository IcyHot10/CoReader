package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupBook(
    val groupCode: String = "",
    val bookProgression: Map<String, Map<String, Any>> = emptyMap(),
) {
    fun getBookModel(userId: String, bookKey: String): BookModel? {
        val data = bookProgression[userId]?.get(bookKey) ?: return null
        return BookModel.fromAny(data)
    }
}
