package com.indeavour.coreader.domain

import java.security.MessageDigest

/**
 * Names for what is kept on the device separately for each account: its library database and
 * the folder its imported books and covers go in.
 *
 * Everything on the device used to be shared, so whoever signed in next saw the previous
 * person's library, progress and reading history.
 */
object AccountStorage {

    /** The database every account shared before storage was split up. */
    const val LEGACY_DATABASE = "library_db"

    /** Parent, inside the app's files folder, of every account's own folder. */
    const val FILES_ROOT = "accounts"

    private const val MAX_PLAIN_LENGTH = 64

    fun databaseName(uid: String): String = "${LEGACY_DATABASE}_${key(uid)}"

    fun filesFolder(uid: String): String = key(uid)

    /**
     * The account id in a form that is safe as part of a file name. Ids are normally plain
     * letters and digits and are used as they are. Anything else is hashed; the `-` in the
     * prefix cannot occur in a plain id, so the two forms never collide.
     */
    private fun key(uid: String): String {
        require(uid.isNotEmpty()) { "An account id is required" }
        val isPlain = uid.length <= MAX_PLAIN_LENGTH
            && uid.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        if (isPlain) return uid
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        return "h-" + digest.joinToString("") { "%02x".format(it) }
    }
}
