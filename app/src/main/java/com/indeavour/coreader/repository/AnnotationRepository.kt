package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.indeavour.coreader.model.firebase.AnnotationModel
import com.indeavour.coreader.model.firebase.ReplyModel
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.UUID

/** Which book a set of annotations belongs to: a user's own copy, or a group's shared copy. */
sealed interface AnnotationScope {
    data class Personal(val uid: String) : AnnotationScope
    data class Group(val groupCode: String) : AnnotationScope
}

/**
 * Highlights and notes, one document each, under
 * `users/{uid}/books/{bookDocId}/annotations` or `groupBooks/{code}/books/{bookDocId}/annotations`.
 *
 * They used to be JSON strings inside arrays on the owning document, which meant adding a
 * highlight rewrote the whole document, deleting one required parsing every entry, and a group
 * document grew as members x books x annotations toward the 1 MiB ceiling.
 */
class AnnotationRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "AnnotationRepository"
        private const val BATCH_LIMIT = 450
    }

    private fun collection(scope: AnnotationScope, bookKey: String): CollectionReference =
        when (scope) {
            is AnnotationScope.Personal -> FirestorePaths.personalAnnotations(db, scope.uid, bookKey)
            is AnnotationScope.Group -> FirestorePaths.groupAnnotations(db, scope.groupCode, bookKey)
        }

    /**
     * The parent of an annotations subcollection. Firestore does not return documents that exist
     * only implicitly as subcollection parents, so a small marker is written here to keep a
     * book's annotations discoverable (used when scrubbing a removed member's contributions).
     */
    private fun bookDoc(scope: AnnotationScope, bookKey: String): DocumentReference =
        collection(scope, bookKey).parent!!

    private fun booksCollection(scope: AnnotationScope): CollectionReference =
        owningDocument(scope).collection(FirestorePaths.BOOKS)

    private fun owningDocument(scope: AnnotationScope): DocumentReference =
        when (scope) {
            is AnnotationScope.Personal -> FirestorePaths.user(db, scope.uid)
            is AnnotationScope.Group -> FirestorePaths.groupBook(db, scope.groupCode)
        }

    // ---------------------------------------------------------------- reads

    /**
     * Streams every annotation on one book. In a group that is all members' annotations, already
     * flat, instead of walking each member's embedded arrays.
     */
    fun listen(
        scope: AnnotationScope,
        bookKey: String,
        onChange: (List<AnnotationModel>) -> Unit
    ): ListenerRegistration =
        collection(scope, bookKey)
            .orderBy("totalProgression", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Annotation listener failed for $bookKey", error)
                    return@addSnapshotListener
                }
                onChange(
                    snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(AnnotationModel::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Skipping malformed annotation ${doc.id}", e)
                            null
                        }
                    } ?: emptyList()
                )
            }

    // ---------------------------------------------------------------- writes

    suspend fun add(scope: AnnotationScope, bookKey: String, annotation: AnnotationModel) {
        val id = annotation.id.ifBlank { newId() }
        commit { batch ->
            batch.set(bookDoc(scope, bookKey), bookMarker(bookKey), SetOptions.merge())
            batch.set(collection(scope, bookKey).document(id), annotation)
        }
    }

    private fun bookMarker(bookKey: String): Map<String, Any> = mapOf("bookKey" to bookKey)

    /**
     * Removes every annotation a member contributed to a group, used when they leave or are
     * removed. Equality on `ownerUid` is served by the automatic single-field index.
     */
    suspend fun deleteAllByOwner(scope: AnnotationScope, ownerUid: String) {
        if (ownerUid.isBlank()) return
        forEachBook(scope) { bookKey ->
            deleteMatching(collection(scope, bookKey).whereEqualTo("ownerUid", ownerUid))
        }
    }

    /**
     * Removes every annotation in a scope, along with the book markers.
     *
     * Deleting a document in Firestore does not delete its subcollections, so this has to run
     * before the owning group document goes away or the annotations are orphaned and
     * unreachable. Being client-driven it is best-effort: a very large group interrupted
     * mid-delete leaves a remainder that only a server-side recursive delete can finish.
     */
    suspend fun deleteAll(scope: AnnotationScope) {
        forEachBook(scope) { bookKey ->
            deleteMatching(collection(scope, bookKey))
            try {
                bookDoc(scope, bookKey).delete().await()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete book marker for $bookKey", e)
            }
        }
    }

    private suspend fun forEachBook(scope: AnnotationScope, action: suspend (String) -> Unit) {
        try {
            val books = booksCollection(scope).get().await()
            for (book in books.documents) {
                val bookKey = book.getString("bookKey") ?: continue
                action(bookKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enumerate books for $scope", e)
        }
    }

    private suspend fun deleteMatching(query: Query) {
        try {
            val matches = query.get().await()
            matches.documents.chunked(BATCH_LIMIT).forEach { chunk ->
                commit { batch -> chunk.forEach { batch.delete(it.reference) } }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete annotations", e)
        }
    }

    suspend fun delete(scope: AnnotationScope, bookKey: String, annotationId: String) {
        if (annotationId.isBlank()) return
        collection(scope, bookKey).document(annotationId).delete().await()
    }

    suspend fun addReply(
        scope: AnnotationScope,
        bookKey: String,
        annotationId: String,
        reply: ReplyModel
    ) {
        if (annotationId.isBlank()) return
        collection(scope, bookKey).document(annotationId)
            .update("replies", FieldValue.arrayUnion(reply.toMap()))
            .await()
    }

    suspend fun removeReply(
        scope: AnnotationScope,
        bookKey: String,
        annotationId: String,
        reply: ReplyModel
    ) {
        if (annotationId.isBlank()) return
        collection(scope, bookKey).document(annotationId)
            .update("replies", FieldValue.arrayRemove(reply.toMap()))
            .await()
    }

    fun newId(): String = UUID.randomUUID().toString()

    private suspend fun commit(block: (WriteBatch) -> Unit) {
        val batch = db.batch()
        block(batch)
        batch.commit().await()
    }
}

/** Position fields denormalised out of a Readium locator for querying and ordering. */
data class LocatorFields(val href: String, val totalProgression: Double) {
    companion object {
        fun of(locatorJson: String): LocatorFields = try {
            val obj = JSONObject(locatorJson)
            LocatorFields(
                href = obj.optString("href", ""),
                totalProgression = obj.optJSONObject("locations")
                    ?.optDouble("totalProgression", 0.0) ?: 0.0
            )
        } catch (e: Exception) {
            LocatorFields("", 0.0)
        }
    }
}
