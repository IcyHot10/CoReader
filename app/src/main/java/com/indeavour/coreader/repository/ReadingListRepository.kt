package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.model.firebase.ReadingListBook
import com.indeavour.coreader.model.firebase.ReadingListDocument
import kotlinx.coroutines.tasks.await

/**
 * Access to `readingLists/{uid}`. Book ids are generated UUIDs, so dotted field paths are safe
 * here in a way they are not for the "<title>_<author>" keys used elsewhere.
 */
class ReadingListRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "ReadingListRepository"
        private const val BOOKS = "books"
    }

    fun listen(uid: String, onChange: (List<ReadingListBook>) -> Unit): ListenerRegistration =
        FirestorePaths.readingList(db, uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Reading list listener failed", error)
                return@addSnapshotListener
            }
            val document = snapshot?.toObject(ReadingListDocument::class.java)
            onChange(document?.books?.values?.sortedBy { it.position } ?: emptyList())
        }

    /** A merging `set` both creates the document and adds the entry, so no fallback is needed. */
    suspend fun put(uid: String, book: ReadingListBook) {
        FirestorePaths.readingList(db, uid)
            .set(mapOf(BOOKS to mapOf(book.id to book)), SetOptions.merge())
            .await()
    }

    suspend fun remove(uid: String, bookId: String) {
        if (bookId.isBlank()) return
        FirestorePaths.readingList(db, uid)
            .update("$BOOKS.$bookId", FieldValue.delete())
            .await()
    }

    suspend fun updateFields(uid: String, bookId: String, fields: Map<String, Any?>) {
        if (bookId.isBlank()) return
        FirestorePaths.readingList(db, uid)
            .set(mapOf(BOOKS to mapOf(bookId to fields)), SetOptions.merge())
            .await()
    }

    /** Swaps two entries' positions in a single atomic write. */
    suspend fun swapPositions(
        uid: String,
        firstId: String,
        firstPosition: Int,
        secondId: String,
        secondPosition: Int
    ) {
        FirestorePaths.readingList(db, uid)
            .set(
                mapOf(
                    BOOKS to mapOf(
                        firstId to mapOf("position" to secondPosition),
                        secondId to mapOf("position" to firstPosition)
                    )
                ),
                SetOptions.merge()
            )
            .await()
    }
}
