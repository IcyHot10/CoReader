package com.indeavour.coreader.repository

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single place where Firestore collection and document paths are defined.
 *
 * Schema:
 *
 *   users/{uid}                                               private profile + book map
 *   users/{uid}/books/{bookDocId}/annotations/{annotationId}   personal highlights and notes
 *   profiles/{uid}                                            publicly readable display name
 *   groups/{code}                                             name + members
 *   groupBooks/{code}                                         per-member reading progress
 *   groupBooks/{code}/books/{bookDocId}/annotations/{id}       shared highlights and notes
 *   userStats/{uid}                                           lifetime + yearly reading stats
 *   readingLists/{uid}                                        to-read list and suggestions
 */
object FirestorePaths {

    const val USERS = "users"
    const val PROFILES = "profiles"
    const val GROUPS = "groups"
    const val GROUP_BOOKS = "groupBooks"
    const val USER_STATS = "userStats"
    const val READING_LISTS = "readingLists"

    const val BOOKS = "books"
    const val ANNOTATIONS = "annotations"

    /**
     * The annotation layout a document was written with. Stamped on new user and group book
     * documents so a future layout change can tell them apart from older data.
     */
    const val ANNOTATION_SCHEMA_VERSION = 2
    const val FIELD_ANNOTATION_SCHEMA = "annotationsSchemaVersion"

    fun user(db: FirebaseFirestore, uid: String): DocumentReference =
        db.collection(USERS).document(uid)

    fun profile(db: FirebaseFirestore, uid: String): DocumentReference =
        db.collection(PROFILES).document(uid)

    fun group(db: FirebaseFirestore, code: String): DocumentReference =
        db.collection(GROUPS).document(code)

    fun groupBook(db: FirebaseFirestore, code: String): DocumentReference =
        db.collection(GROUP_BOOKS).document(code)

    fun userStats(db: FirebaseFirestore, uid: String): DocumentReference =
        db.collection(USER_STATS).document(uid)

    fun readingList(db: FirebaseFirestore, uid: String): DocumentReference =
        db.collection(READING_LISTS).document(uid)

    fun personalAnnotations(db: FirebaseFirestore, uid: String, bookKey: String): CollectionReference =
        user(db, uid).collection(BOOKS).document(bookDocId(bookKey)).collection(ANNOTATIONS)

    fun groupAnnotations(db: FirebaseFirestore, code: String, bookKey: String): CollectionReference =
        groupBook(db, code).collection(BOOKS).document(bookDocId(bookKey)).collection(ANNOTATIONS)

    /**
     * Book keys are "<title>_<author>" and so may contain characters Firestore forbids in a
     * document id. Percent-encode those rather than stripping them, so two different titles can
     * never collapse onto the same id.
     *
     * Document id rules: no "/", not "." or "..", must not match __.*__, max 1500 bytes.
     */
    fun bookDocId(bookKey: String): String {
        val encoded = buildString(bookKey.length) {
            for (c in bookKey) {
                when (c) {
                    '%' -> append("%25")
                    '/' -> append("%2F")
                    else -> append(c)
                }
            }
        }
        val safe = when {
            encoded.isEmpty() -> "%00"
            encoded == "." -> "%2E"
            encoded == ".." -> "%2E%2E"
            encoded.startsWith("__") && encoded.endsWith("__") -> "%5F$encoded"
            else -> encoded
        }
        // 1500 bytes is the hard limit; truncating by chars keeps us well inside it.
        return if (safe.length > 400) safe.take(400) else safe
    }
}
