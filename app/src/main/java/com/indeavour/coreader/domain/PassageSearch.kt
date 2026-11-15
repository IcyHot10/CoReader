package com.indeavour.coreader.domain

import kotlin.math.ln

/** The plain text of one chapter (or file) of a book. */
data class ChapterText(val title: String, val text: String)

/** A short stretch of a book's text, quoted as evidence. */
data class Passage(val chapter: String, val text: String)

/**
 * The word handling behind [PassageIndex]: which words of a question are worth searching for,
 * and how a word is reduced to a root so that "dies", "died" and "dying" all match.
 */
object PassageSearch {

    internal val WORD = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "her", "was", "one",
        "our", "out", "his", "has", "had", "him", "how", "its", "who", "why", "did", "she", "they",
        "them", "then", "than", "that", "this", "these", "those", "there", "their", "what", "when",
        "where", "which", "while", "with", "would", "could", "should", "about", "after", "again",
        "been", "before", "being", "does", "doing", "from", "have", "having", "here", "into",
        "just", "more", "most", "some", "such", "very", "were", "will", "your", "book", "chapter",
        "happen", "happens", "happened", "reader", "story", "tell", "mean", "means", "say", "says"
    )

    private const val VOWELS = "aeiou"

    /**
     * Reduces a word to a rough root, so different forms of it match each other. This is a
     * light version of what search engines do: it only needs to treat the query and the book
     * the same way, not to produce real words ("loved" and "loves" both become "lov").
     */
    fun stem(word: String): String {
        var s = word.lowercase().removeSuffix("'s").removeSuffix("’s")
        if (s.length <= 3) return s

        // Plurals and third-person verbs.
        s = when {
            s.endsWith("ies") -> // "dies" -> "dy", matching "died" and "dying" below
                s.dropLast(3) + "y"
            s.endsWith("sses") -> s.dropLast(2)
            s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us") && !s.endsWith("is") -> s.dropLast(1)
            else -> s
        }

        // Past tense and "-ing", but only off a root that still has a vowel ("sing" stays).
        for (suffix in listOf("ing", "ed")) {
            val root = s.removeSuffix(suffix)
            // ("y" counts as a vowel here, so "dying" and "trying" reduce to "dy" and "try".)
            if (root.length < s.length && root.length >= 2 && root.any { it in VOWELS || it == 'y' }) {
                s = root
                // "stopped" -> "stopp" -> "stop"; "ll", "ss" and "zz" are real endings.
                if (s.length > 2 && s.last() == s[s.length - 2] && s.last() !in "lsz$VOWELS") s = s.dropLast(1)
                break
            }
        }

        if (s.endsWith("i") && s.length > 1) s = s.dropLast(1) + "y" // "died" -> "di" -> "dy", as "dies" -> "dy"
        if (s.endsWith("e") && s.length > 3) s = s.dropLast(1)       // "loves" -> "love" -> "lov", as "loved" -> "lov"
        return s
    }

    /** The roots of all the words in [text]. */
    internal fun stems(text: String): Set<String> = WORD.findAll(text).map { stem(it.value) }.toSet()

    /** The words of [text] worth searching for, as roots, with whether each looks like a name. */
    internal fun terms(text: String): Map<String, Boolean> {
        val found = linkedMapOf<String, Boolean>()
        for (match in WORD.findAll(text)) {
            val word = match.value
            val plain = word.lowercase().removeSuffix("'s").removeSuffix("’s")
            if (plain.length < 3 || plain in STOP_WORDS) continue
            // Capitalised away from the start of a sentence is the best cheap sign of a name.
            val before = text.substring(0, match.range.first).trimEnd()
            val startsSentence = before.isEmpty() || before.last() in ".!?\"“:"
            val isName = word.first().isUpperCase() && !startsSentence
            val key = stem(word)
            found[key] = (found[key] ?: false) || isName
        }
        return found
    }

    /** Where to cut [text] into pieces of about [size] characters, breaking between words. */
    internal fun splitRanges(text: String, size: Int): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        while (start < text.length) {
            var end = (start + size).coerceAtMost(text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end)
                if (space > start + size / 2) end = space
            }
            ranges += start until end
            start = end
        }
        return ranges
    }

    internal fun split(text: String, size: Int): List<String> =
        splitRanges(text, size).map { text.substring(it.first, it.last + 1).trim() }.filter { it.isNotEmpty() }

    /** One-off search of [chapters]; build a [PassageIndex] to search the same book repeatedly. */
    fun find(
        chapters: List<ChapterText>,
        query: String,
        maxPassages: Int = 6,
        passageChars: Int = 700
    ): List<Passage> = PassageIndex(chapters, passageChars).search(query, maxPassages = maxPassages)
}

/**
 * A book's text cut into passages and prepared for searching, so that it only has to be read
 * through once however many questions are asked.
 *
 * Finding the parts of a book relevant to a question lets an answer be written from, and
 * checked against, what the book actually says rather than what a model remembers. It is
 * keyword matching: a passage scores higher the more of the query's words it contains, with
 * rarer words (and names) counting for more.
 */
class PassageIndex(private val book: List<ChapterText>, passageChars: Int = 700) {

    /** Which part of the book a search may look in, relative to the reader's position. */
    enum class Scope { WHOLE_BOOK, READ, UNREAD }

    private class Entry(val chapter: Int, val start: Int, val end: Int, val text: String, val stems: Set<String>)

    private val entries: List<Entry> = book.flatMapIndexed { index, chapter ->
        PassageSearch.splitRanges(chapter.text, passageChars).mapNotNull { range ->
            val text = chapter.text.substring(range.first, range.last + 1).trim()
            if (text.isEmpty()) null else Entry(index, range.first, range.last + 1, text, PassageSearch.stems(text))
        }
    }

    /** In how many passages each word root appears; rare words say more about a passage. */
    private val passagesContaining: Map<String, Int> =
        entries.flatMap { it.stems }.groupingBy { it }.eachCount()

    /**
     * Up to [maxPassages] passages that best match [query], in the order they appear in the
     * book. With [Scope.READ] or [Scope.UNREAD], only text on that side of [position] is
     * searched or returned; a passage the position falls inside is cut at it, so nothing from
     * the wrong side can come back. An unknown position matches nothing.
     */
    fun search(
        query: String,
        scope: Scope = Scope.WHOLE_BOOK,
        position: ReadPosition? = null,
        maxPassages: Int = 6
    ): List<Passage> {
        val terms = PassageSearch.terms(query)
        if (terms.isEmpty() || entries.isEmpty()) return emptyList()

        val visible: List<Entry> = when (scope) {
            Scope.WHOLE_BOOK -> entries
            else -> {
                if (position == null || position.chapterIndex !in book.indices) return emptyList()
                val chapterText = book[position.chapterIndex].text
                val readChars = (chapterText.length * position.throughChapter.coerceIn(0.0, 1.0)).toInt()
                entries.mapNotNull { entry -> clip(entry, scope == Scope.READ, position.chapterIndex, readChars, chapterText) }
            }
        }

        val weights = terms.mapValues { (term, isName) ->
            val rarity = ln(1.0 + entries.size.toDouble() / (1 + (passagesContaining[term] ?: 0)))
            if (isName) rarity * 2 else rarity
        }

        return visible.withIndex()
            .map { (order, entry) ->
                Triple(order, entry, weights.entries.sumOf { (term, weight) -> if (term in entry.stems) weight else 0.0 })
            }
            .filter { it.third > 0.0 }
            .sortedWith(compareByDescending<Triple<Int, Entry, Double>> { it.third }.thenBy { it.first })
            .take(maxPassages)
            .sortedBy { it.first }
            .map { Passage(book[it.second.chapter].title, it.second.text) }
    }

    /** The part of [entry] on the wanted side of the reader's position, or null if none of it is. */
    private fun clip(entry: Entry, wantRead: Boolean, chapter: Int, readChars: Int, chapterText: String): Entry? {
        if (entry.chapter != chapter) {
            return entry.takeIf { (entry.chapter < chapter) == wantRead }
        }
        val from = if (wantRead) entry.start else maxOf(entry.start, readChars)
        val to = if (wantRead) minOf(entry.end, readChars) else entry.end
        if (from >= to) return null
        if (from == entry.start && to == entry.end) return entry
        // The position falls inside this passage: keep only the wanted part, and match on only that.
        val text = chapterText.substring(from, to).trim()
        return if (text.isEmpty()) null else Entry(entry.chapter, from, to, text, PassageSearch.stems(text))
    }
}
