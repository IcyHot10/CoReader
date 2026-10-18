package com.indeavour.coreader.model.firebase

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class GroupReadingListComment(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val text: String = "",
    val timestamp: Long = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "username" to username,
        "text" to text,
        "timestamp" to timestamp
    )

    companion object {
        fun fromAny(data: Any?): GroupReadingListComment? = when (data) {
            is GroupReadingListComment -> data
            is Map<*, *> -> GroupReadingListComment(
                id = data["id"] as? String ?: "",
                userId = data["userId"] as? String ?: "",
                username = data["username"] as? String ?: "",
                text = data["text"] as? String ?: "",
                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
            )
            else -> null
        }
    }
}

@IgnoreExtraProperties
data class GroupReadingListBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val addedByUid: String = "",
    val addedByUsername: String = "",
    val addedTimestamp: Long = 0,
    val votes: List<String> = emptyList(),
    val comments: List<GroupReadingListComment> = emptyList()
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to id,
            "title" to title,
            "author" to author,
            "addedByUid" to addedByUid,
            "addedByUsername" to addedByUsername,
            "addedTimestamp" to addedTimestamp,
            "votes" to votes
        )
        if (comments.isNotEmpty()) {
            map["comments"] = comments.map { it.toMap() }
        }
        return map
    }

    companion object {
        fun fromAny(data: Any?): GroupReadingListBook? = when (data) {
            is GroupReadingListBook -> data
            is Map<*, *> -> {
                val votesList = (data["votes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val commentsList = (data["comments"] as? List<*>)?.mapNotNull { GroupReadingListComment.fromAny(it) } ?: emptyList()
                GroupReadingListBook(
                    id = data["id"] as? String ?: "",
                    title = data["title"] as? String ?: "",
                    author = data["author"] as? String ?: "",
                    addedByUid = data["addedByUid"] as? String ?: "",
                    addedByUsername = data["addedByUsername"] as? String ?: "",
                    addedTimestamp = (data["addedTimestamp"] as? Number)?.toLong() ?: 0L,
                    votes = votesList,
                    comments = commentsList
                )
            }
            else -> null
        }
    }
}
