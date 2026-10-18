package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.model.firebase.GroupReadingListBook
import com.indeavour.coreader.model.firebase.GroupReadingListComment
import kotlinx.coroutines.tasks.await

class GroupReadingListRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "GroupReadingListRepo"
        private const val BOOKS = "books"
    }

    private fun doc(code: String) = db.collection("groupReadingLists").document(code)

    fun listen(code: String, onChange: (List<GroupReadingListBook>) -> Unit): ListenerRegistration =
        doc(code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Failed to listen to group reading list", error)
                return@addSnapshotListener
            }
            val rawBooks = snapshot?.get("books") as? Map<*, *>
            val parsedList = rawBooks?.values?.mapNotNull { GroupReadingListBook.fromAny(it) } ?: emptyList()
            val sortedList = parsedList.sortedWith(
                compareByDescending<GroupReadingListBook> { it.votes.size }
                    .thenByDescending { it.addedTimestamp }
            )
            onChange(sortedList)
        }

    suspend fun put(code: String, book: GroupReadingListBook) {
        if (code.isBlank() || book.id.isBlank()) return
        doc(code)
            .set(mapOf(BOOKS to mapOf(book.id to book.toMap())), SetOptions.merge())
            .await()
    }

    suspend fun remove(code: String, bookId: String) {
        if (code.isBlank() || bookId.isBlank()) return
        doc(code)
            .update("$BOOKS.$bookId", FieldValue.delete())
            .await()
    }

    suspend fun toggleVote(code: String, bookId: String, uid: String, hasVoted: Boolean) {
        if (code.isBlank() || bookId.isBlank() || uid.isBlank()) return
        val fieldPath = "$BOOKS.$bookId.votes"
        val updateValue = if (hasVoted) FieldValue.arrayRemove(uid) else FieldValue.arrayUnion(uid)
        doc(code)
            .update(fieldPath, updateValue)
            .await()
    }

    suspend fun addComment(code: String, bookId: String, comment: GroupReadingListComment) {
        if (code.isBlank() || bookId.isBlank()) return
        val fieldPath = "$BOOKS.$bookId.comments"
        doc(code)
            .update(fieldPath, FieldValue.arrayUnion(comment.toMap()))
            .await()
    }

    suspend fun removeComment(code: String, bookId: String, comment: GroupReadingListComment) {
        if (code.isBlank() || bookId.isBlank()) return
        val fieldPath = "$BOOKS.$bookId.comments"
        doc(code)
            .update(fieldPath, FieldValue.arrayRemove(comment.toMap()))
            .await()
    }
}
