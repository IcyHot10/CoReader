package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.GroupBook
import com.indeavour.coreader.model.firebase.GroupMember
import com.indeavour.coreader.model.firebase.GroupModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Outcome of a join attempt, so the caller does not have to interpret exception messages. */
sealed interface JoinResult {
    data object Success : JoinResult
    data object NotFound : JoinResult
    data object AlreadyMember : JoinResult
    data class Failed(val message: String?) : JoinResult
}

/**
 * Access to `groups/{code}` (name and membership) and `groupBooks/{code}` (per-member progress).
 *
 * Every mutation addresses the exact field it changes, so two members acting at the same time no
 * longer overwrite each other by writing back a whole map they read moments earlier.
 */
class GroupRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "GroupRepository"
        const val FIELD_BOOK_PROGRESSION = "bookProgression"
        private const val FIELD_GROUP_MEMBERS = "groupMembers"
        private const val FIELD_GROUP_IDS = "groupIDs"
        private const val FIELD_ACTIVE_GROUP = "activeGroup"
    }

    private val annotations = AnnotationRepository(db)

    // ---------------------------------------------------------------- reads

    suspend fun group(code: String): GroupModel? =
        FirestorePaths.group(db, code).get().await().toObject(GroupModel::class.java)

    suspend fun exists(code: String): Boolean =
        FirestorePaths.group(db, code).get().await().exists()

    /** Loads several groups concurrently rather than one blocking round trip each. */
    suspend fun groupsByIds(codes: List<String>): List<GroupModel> = coroutineScope {
        codes.filter { it.isNotBlank() }
            .distinct()
            .map { code -> async { runCatching { group(code) }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
    }

    fun listenGroup(code: String, onChange: (GroupModel?) -> Unit): ListenerRegistration =
        FirestorePaths.group(db, code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Group listener failed for $code", error)
                return@addSnapshotListener
            }
            onChange(snapshot?.toObject(GroupModel::class.java))
        }

    fun listenGroupBooks(code: String, onChange: (GroupBook?) -> Unit): ListenerRegistration =
        FirestorePaths.groupBook(db, code).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "GroupBook listener failed for $code", error)
                onChange(null)
                return@addSnapshotListener
            }
            val book = try {
                snapshot?.toObject(GroupBook::class.java)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize GroupBook $code", e)
                null
            }
            onChange(book)
        }

    suspend fun groupBook(code: String): GroupBook? =
        FirestorePaths.groupBook(db, code).get().await().toObject(GroupBook::class.java)

    // ---------------------------------------------------------------- membership

    suspend fun createGroup(uid: String, groupName: String): String? = try {
        val code = generateUniqueCode()
        val userRef = FirestorePaths.user(db, uid)
        val hasActiveGroup = !userRef.get().await().getString(FIELD_ACTIVE_GROUP).isNullOrBlank()

        // One batch: a failure can no longer leave a user pointing at a group that was never
        // created, or a group with nobody in it.
        db.batch().apply {
            set(
                FirestorePaths.group(db, code),
                GroupModel(
                    groupCode = code,
                    groupName = groupName,
                    groupMembers = mapOf(uid to GroupMember(admin = true))
                )
            )
            update(userRef, FIELD_GROUP_IDS, FieldValue.arrayUnion(code))
            if (!hasActiveGroup) update(userRef, FIELD_ACTIVE_GROUP, code)
        }.commit().await()

        code
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create group", e)
        null
    }

    suspend fun joinGroup(uid: String, code: String): JoinResult = try {
        val groupSnapshot = FirestorePaths.group(db, code).get().await()
        if (!groupSnapshot.exists()) {
            JoinResult.NotFound
        } else {
            val userRef = FirestorePaths.user(db, uid)
            val userSnapshot = userRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val currentGroups = userSnapshot.get(FIELD_GROUP_IDS) as? List<String> ?: emptyList()

            if (currentGroups.contains(code)) {
                JoinResult.AlreadyMember
            } else {
                val hasActiveGroup = !userSnapshot.getString(FIELD_ACTIVE_GROUP).isNullOrBlank()
                db.batch().apply {
                    // Writing just this member's entry means simultaneous joins no longer drop
                    // one another, which a whole-map write could not avoid.
                    update(
                        FirestorePaths.group(db, code),
                        FieldPath.of(FIELD_GROUP_MEMBERS, uid),
                        GroupMember(admin = false)
                    )
                    update(userRef, FIELD_GROUP_IDS, FieldValue.arrayUnion(code))
                    if (!hasActiveGroup) update(userRef, FIELD_ACTIVE_GROUP, code)
                }.commit().await()
                JoinResult.Success
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to join group $code", e)
        JoinResult.Failed(e.message)
    }

    suspend fun setActiveGroup(uid: String, code: String) {
        FirestorePaths.user(db, uid).update(FIELD_ACTIVE_GROUP, code).await()
    }

    suspend fun setAdmin(code: String, memberId: String, isAdmin: Boolean): Boolean = try {
        val groupRef = FirestorePaths.group(db, code)
        val group = groupRef.get().await().toObject(GroupModel::class.java)
        if (group?.groupMembers?.containsKey(memberId) != true) {
            false
        } else {
            groupRef.update(FieldPath.of(FIELD_GROUP_MEMBERS, memberId, "admin"), isAdmin).await()
            true
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to update admin status in $code", e)
        false
    }

    /**
     * Removes a member from the group, their progress bucket and their shared annotations,
     * deleting the group entirely once the last member leaves.
     */
    suspend fun removeMember(code: String, targetUid: String): Boolean = try {
        val groupRef = FirestorePaths.group(db, code)
        val groupBookRef = FirestorePaths.groupBook(db, code)
        val userRef = FirestorePaths.user(db, targetUid)

        val groupSnapshot = groupRef.get().await()
        val userSnapshot = userRef.get().await()
        val group = groupSnapshot.toObject(GroupModel::class.java)
        val isLastMember = group != null && group.groupMembers.keys.all { it == targetUid }

        // Subcollections outlive a deleted parent, so the shared annotations are cleared before
        // the group document is removed.
        val scope = AnnotationScope.Group(code)
        if (isLastMember) {
            annotations.deleteAll(scope)
        } else {
            annotations.deleteAllByOwner(scope, targetUid)
        }

        db.batch().apply {
            if (userSnapshot.exists()) {
                update(userRef, FIELD_GROUP_IDS, FieldValue.arrayRemove(code))
                if (userSnapshot.getString(FIELD_ACTIVE_GROUP) == code) {
                    @Suppress("UNCHECKED_CAST")
                    val remaining = (userSnapshot.get(FIELD_GROUP_IDS) as? List<String> ?: emptyList())
                        .filter { it != code }
                    update(userRef, FIELD_ACTIVE_GROUP, remaining.firstOrNull() ?: "")
                }
            }

            if (groupSnapshot.exists()) {
                if (isLastMember) {
                    delete(groupRef)
                    delete(groupBookRef)
                } else {
                    update(groupRef, FieldPath.of(FIELD_GROUP_MEMBERS, targetUid), FieldValue.delete())
                    update(groupBookRef, FieldPath.of(FIELD_BOOK_PROGRESSION, targetUid), FieldValue.delete())
                }
            }
        }.commit().await()

        true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to remove $targetUid from $code", e)
        false
    }

    // ---------------------------------------------------------------- group books

    /**
     * Adds or updates one member's copy of one book. A merging `set` creates the group book
     * document when absent without reading and rewriting the whole progression map.
     */
    suspend fun upsertGroupBook(code: String, uid: String, bookKey: String, book: BookModel) {
        FirestorePaths.groupBook(db, code).set(
            mapOf(
                "groupCode" to code,
                // Declares the annotation layout this document was created with.
                FirestorePaths.FIELD_ANNOTATION_SCHEMA to FirestorePaths.ANNOTATION_SCHEMA_VERSION,
                FIELD_BOOK_PROGRESSION to mapOf(uid to mapOf(bookKey to book))
            ),
            SetOptions.merge()
        ).await()
    }

    /** Updates individual fields of one member's copy of a book, leaving siblings untouched. */
    suspend fun mergeGroupBookFields(
        code: String,
        uid: String,
        bookKey: String,
        fields: Map<String, Any?>
    ) {
        FirestorePaths.groupBook(db, code).set(
            mapOf(
                "groupCode" to code,
                FIELD_BOOK_PROGRESSION to mapOf(uid to mapOf(bookKey to fields))
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun generateUniqueCode(): String {
        while (true) {
            val code = UUID.randomUUID().toString().substring(0, 6).uppercase()
            if (!FirestorePaths.group(db, code).get().await().exists()) return code
        }
    }
}
