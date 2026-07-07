package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class NoteModel(
    val note: String = "",
    val replies: List<ReplyModel> = emptyList()
)

@IgnoreExtraProperties
data class ReplyModel(
    val userId: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

@IgnoreExtraProperties
data class BookModel(
    val title: String = "",
    val author: String = "",
    val progress: String = "",
    val highlights: List<String> = emptyList(),
    val notes: List<NoteModel> = emptyList(),
    @get:PropertyName("isDeleted")
    @set:PropertyName("isDeleted")
    var isDeleted: Boolean = false,
    val addedTimestamp: Long = 0,
    val completedTimestamp: Long = 0
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
                        notes = (data["notes"] as? List<*>)?.mapNotNull { item ->
                            when (item) {
                                is Map<*, *> -> {
                                    val repliesList = (item["replies"] as? List<*>)?.mapNotNull { replyItem ->
                                        when (replyItem) {
                                            is Map<*, *> -> ReplyModel(
                                                userId = replyItem["userId"] as? String ?: "",
                                                text = replyItem["text"] as? String ?: (replyItem["reply"] as? String ?: ""),
                                                timestamp = (replyItem["timestamp"] as? Number)?.toLong() ?: 0L
                                            )
                                            is String -> ReplyModel(text = replyItem) // Compatibility with old string replies
                                            else -> null
                                        }
                                    } ?: emptyList()
                                    
                                    NoteModel(
                                        note = item["note"] as? String ?: "",
                                        replies = repliesList
                                    )
                                }
                                is String -> NoteModel(note = item)
                                else -> null
                            }
                        } ?: emptyList(),
                        isDeleted = data["isDeleted"] as? Boolean ?: (data["deleted"] as? Boolean ?: false),
                        addedTimestamp = (data["addedTimestamp"] as? Number)?.toLong() ?: 0L,
                        completedTimestamp = (data["completedTimestamp"] as? Number)?.toLong() ?: 0L
                    )
                }
                is BookModel -> data
                else -> null
            }
        }
    }
}
