package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class BookModel(
    val title: String = "",
    val author: String = "",
    val progress: String = "",
    val highlights: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    @get:PropertyName("isDeleted")
    @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false
) {
    companion object {
        fun fromAny(data: Any?): BookModel? {
            return when (data) {
                is Map<*, *> -> {
                    BookModel(
                        title = data["title"] as? String ?: "",
                        author = data["author"] as? String ?: "",
                        progress = data["progress"] as? String ?: "",
                        highlights = (data["highlights"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        notes = (data["notes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        isDeleted = data["isDeleted"] as? Boolean ?: (data["deleted"] as? Boolean ?: false)
                    )
                }
                is BookModel -> data
                else -> null
            }
        }
    }
}
