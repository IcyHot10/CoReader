package com.indeavour.coreader.domain

/**
 * How far a reader has got: which file of the book they are in, and how much of that file
 * (0..1) they have read through.
 */
data class ReadPosition(val chapterIndex: Int, val throughChapter: Double)

/**
 * Works out which part of a book's text the reader has already read. Everything shown to the
 * assistant as evidence in spoiler-free mode goes through here, so nothing ahead of the reader
 * can reach it.
 */
object ReadSoFar {

    /**
     * How far through a chapter the reader is once they have read the page in front of them.
     *
     * A reading position marks the top of the visible page, so on its own it would count the
     * page being read as unread; the reader has that text in front of them and asks about it.
     * [pageIndex] is zero-based.
     */
    fun endOfPage(pageIndex: Int, totalPages: Int, progression: Double): Double {
        val start = progression.coerceIn(0.0, 1.0)
        if (totalPages <= 0 || pageIndex < 0) return start
        return ((pageIndex + 1).toDouble() / totalPages).coerceIn(start, 1.0)
    }

    /**
     * The text read so far: every chapter before the current one in full, and the current one
     * cut off at the reader's position. Empty when the position is unknown.
     */
    fun chapters(book: List<ChapterText>, position: ReadPosition?): List<ChapterText> {
        if (position == null || position.chapterIndex !in book.indices) return emptyList()
        val current = book[position.chapterIndex]
        val readChars = (current.text.length * position.throughChapter.coerceIn(0.0, 1.0)).toInt()
        return book.take(position.chapterIndex) + current.copy(text = current.text.take(readChars))
    }

    /**
     * The last [maxChars] of what has been read, reaching back into earlier chapters when the
     * reader is near the start of one.
     */
    fun recentText(book: List<ChapterText>, position: ReadPosition?, maxChars: Int): String {
        val collected = StringBuilder()
        for (chapter in chapters(book, position).asReversed()) {
            if (chapter.text.isEmpty()) continue
            if (collected.isNotEmpty()) collected.insert(0, ' ')
            collected.insert(0, chapter.text.takeLast(maxChars - collected.length))
            if (collected.length >= maxChars) break
        }
        return collected.toString()
    }
}
