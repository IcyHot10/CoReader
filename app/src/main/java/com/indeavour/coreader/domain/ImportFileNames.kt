package com.indeavour.coreader.domain

/**
 * Names for the files an imported book is stored under.
 *
 * Neither the file name a book arrives with nor its title is unique: two different books can
 * both be `book.epub`, or share a title. Naming the stored copy after either made the second
 * import overwrite the first, so both are named after an id generated for that import instead.
 */
object ImportFileNames {

    private const val DEFAULT_EXTENSION = "epub"
    private const val MAX_EXTENSION_LENGTH = 8

    /**
     * The stored name for a book imported as [originalName]. Only the extension is carried over,
     * as a hint to whatever opens the file; anything that is not a plain extension is replaced.
     */
    fun book(originalName: String, importId: String): String =
        "book_$importId.${extension(originalName)}"

    /** The stored name for that book's cover, without an extension. */
    fun cover(importId: String): String = "cover_$importId"

    private fun extension(name: String): String {
        val candidate = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val isPlain = candidate.isNotEmpty()
            && candidate.length <= MAX_EXTENSION_LENGTH
            && candidate.all { it in 'a'..'z' || it in '0'..'9' }
        return if (isPlain) candidate else DEFAULT_EXTENSION
    }
}
