package com.indeavour.coreader.repository

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.indeavour.coreader.model.firebase.BookModel
import com.indeavour.coreader.model.firebase.ProfileModel
import com.indeavour.coreader.model.firebase.UserModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Access to `users/{uid}` (private) and `profiles/{uid}` (public display name).
 */
class UserRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val TAG = "UserRepository"
        private const val UNKNOWN = "Unknown User"

        /**
         * Display names are immutable enough to cache for the life of the process, and every
         * group screen asks for the same handful of ids. Shared across repository instances so
         * each ViewModel does not refetch the same names.
         */
        private val usernameCache = ConcurrentHashMap<String, String>()

        fun cachedUsername(uid: String): String? = usernameCache[uid]

        fun defaultUsernameFor(email: String): String =
            email.substringBefore('@').takeIf { it.isNotBlank() } ?: "Reader"
    }

    fun listenUser(
        uid: String,
        onChange: (UserModel?) -> Unit
    ): ListenerRegistration =
        FirestorePaths.user(db, uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "User listener failed", error)
                return@addSnapshotListener
            }
            onChange(snapshot?.toObject(UserModel::class.java))
        }

    fun listenProfile(
        uid: String,
        onChange: (String?) -> Unit
    ): ListenerRegistration =
        FirestorePaths.profile(db, uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Profile listener failed", error)
                return@addSnapshotListener
            }
            val name = snapshot?.getString("username")
            if (!name.isNullOrBlank()) usernameCache[uid] = name
            onChange(name)
        }

    suspend fun user(uid: String): UserModel? =
        FirestorePaths.user(db, uid).get().await().toObject(UserModel::class.java)

    suspend fun book(uid: String, bookKey: String): BookModel? =
        user(uid)?.books?.get(bookKey)

    // ---------------------------------------------------------------- display names

    suspend fun username(uid: String): String {
        if (uid.isBlank()) return UNKNOWN
        usernameCache[uid]?.let { return it }
        return try {
            val profile = FirestorePaths.profile(db, uid).get().await()
                .toObject(ProfileModel::class.java)
            val name = profile?.username?.takeIf { it.isNotBlank() } ?: legacyUsername(uid)
            val resolved = name ?: UNKNOWN
            if (resolved != UNKNOWN) usernameCache[uid] = resolved
            resolved
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load username for $uid", e)
            UNKNOWN
        }
    }

    /** Resolves many names at once; cached ids cost nothing and the rest are fetched in parallel. */
    suspend fun usernames(uids: Collection<String>): Map<String, String> = coroutineScope {
        uids.filter { it.isNotBlank() }
            .distinct()
            .map { uid -> async { uid to username(uid) } }
            .awaitAll()
            .toMap()
    }

    /**
     * Accounts created before display names moved to `profiles` still carry `username` on the
     * user document. Fall back to it so group screens keep working during the transition.
     */
    private suspend fun legacyUsername(uid: String): String? =
        try {
            FirestorePaths.user(db, uid).get().await()
                .getString("username")
                ?.takeIf { it.isNotBlank() }
                ?.let { if (it.contains('@')) defaultUsernameFor(it) else it }
        } catch (e: Exception) {
            // Expected once `users/{uid}` is owner-only: other members simply have no legacy name.
            null
        }

    suspend fun setUsername(uid: String, username: String) {
        FirestorePaths.profile(db, uid)
            .set(mapOf("username" to username), SetOptions.merge())
            .await()
        usernameCache[uid] = username
    }

    /**
     * Backfills the caller's own `profiles/{uid}` document. Only ever called for the signed-in
     * user: writing another member's profile would (correctly) be rejected by rules.
     */
    suspend fun ensureOwnProfile(uid: String, email: String?) {
        try {
            val existing = FirestorePaths.profile(db, uid).get().await()
            if (existing.exists() && !existing.getString("username").isNullOrBlank()) {
                existing.getString("username")?.let { usernameCache[uid] = it }
                return
            }
            val legacy = legacyUsername(uid)
            val name = legacy ?: defaultUsernameFor(email ?: "")
            setUsername(uid, name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure profile for $uid", e)
        }
    }

    /** Creates the private user document and the public profile together. */
    suspend fun createAccount(uid: String, email: String, username: String) {
        db.batch().apply {
            set(
                FirestorePaths.user(db, uid),
                mapOf(
                    "email" to email,
                    "groupIDs" to emptyList<String>(),
                    // Declares the annotation layout this document was created with, so a future
                    // migration can tell new data from anything predating the subcollections.
                    FirestorePaths.FIELD_ANNOTATION_SCHEMA to FirestorePaths.ANNOTATION_SCHEMA_VERSION
                ),
                SetOptions.merge()
            )
            set(
                FirestorePaths.profile(db, uid),
                mapOf("username" to username),
                SetOptions.merge()
            )
        }.commit().await()
        usernameCache[uid] = username
    }

    // ---------------------------------------------------------------- books

    /**
     * Upserts individual fields of `books.{bookKey}` without reading or rewriting the whole map.
     *
     * A merging `set` is used rather than `update` so the entry is created when absent, and
     * sibling fields (and other books) are left untouched — the previous read-modify-write of the
     * entire map silently dropped concurrent edits from other devices.
     */
    suspend fun mergeBookFields(uid: String, bookKey: String, fields: Map<String, Any?>) {
        FirestorePaths.user(db, uid)
            .set(mapOf(FirestorePaths.BOOKS to mapOf(bookKey to fields)), SetOptions.merge())
            .await()
    }

    /**
     * Updates one field of one book. Uses [FieldPath] rather than a dotted string because book
     * keys are "<title>_<author>" and titles routinely contain dots.
     */
    suspend fun updateBookField(uid: String, bookKey: String, field: String, value: Any?) {
        FirestorePaths.user(db, uid)
            .update(FieldPath.of(FirestorePaths.BOOKS, bookKey, field), value)
            .await()
    }

    suspend fun setBookDeleted(uid: String, bookKey: String, deleted: Boolean) {
        mergeBookFields(uid, bookKey, mapOf("isDeleted" to deleted))
    }

    suspend fun setLastHighlightColor(uid: String, color: Int) {
        FirestorePaths.user(db, uid).update("lastHighlightColor", color).await()
    }
}
