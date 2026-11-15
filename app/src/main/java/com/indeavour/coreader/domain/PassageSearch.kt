package com.indeavour.coreader.domain

import kotlin.math.ln

/** The plain text of one chapter (or file) of a book. */
data class ChapterText(val title: String, val text: String)

/** A short stretch of a book's text, quoted as evidence. */
data class Passage(val chapter: String, val text: String)

/**
 * Finds the parts of a book's text most relevant to a question, so an answer can be written
 * from, and checked against, what the book actually says rather than what a model remembers.
 *
 * This is plain keyword matching: the text is cut into passages, and a passage scores higher
 * the more of the query's words it contains, with rarer words (and names) counting for more.
 */
object PassageSearch {

    private val WORD = Regex("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?")

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "her", "was", "one",
        "our", "out", "his", "has", "had", "him", "how", "its", "who", "why", "did", "she", "they",
        "them", "then", "than", "that", "this", "these", "those", "there", "their", "what", "when",
        "where", "which", "while", "with", "would", "could", "should", "about", "after", "again",
        "been", "before", "being", "does", "doing", "from", "have", "having", "here", "into",
        "just", "more", "most", "some", "such", "very", "were", "will", "your", "book", "chapter",
        "happen", "happens", "happened", "reader", "story", "tell", "mean", "means", "say", "says"
    )

    /** The words of [text] worth searching for, lower-cased, with whether each looks like a name. */
    internal fun terms(text: String): Map<String, Boolean> {
        val found = linkedMapOf<String, Boolean>()
        for (match in WORD.findAll(text)) {
            val word = match.value
            val key = word.lowercase().removeSuffix("'s").removeSuffix("’s")
            if (key.length < 3 || key in STOP_WORDS) continue
            // Capitalised away from the start of a sentence is the best cheap sign of a name.
            val before = text.substring(0, match.range.first).trimEnd()
            val startsSentence = before.isEmpty() || before.last() in ".!?\"“\n:"
            val isName = word.first().isUpperCase() && !startsSentence
            found[key] = (found[key] ?: false) || isName
        }
        return found
    }

    /** Cuts [text] into pieces of about [size] characters, breaking between words. */
    internal fun split(text: String, size: Int): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + size).coerceAtMost(text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end)
                if (space > start + size / 2) end = space
            }
            text.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let { pieces += it }
            start = end
        }
        return pieces
    }

    /**
     * Up to [maxPassages] passages from [chapters] that best match [query], in the order they
     * appear in the book. Empty when nothing in the text matches.
     */
    fun find(
        chapters: List<ChapterText>,
        query: String,
        maxPassages: Int = 6,
        passageChars: Int = 700
    ): List<Passage> {
        val terms = terms(query)
        if (terms.isEmpty()) return emptyList()

        data class Candidate(val order: Int, val passage: Passage, val words: Set<String>)

        val candidates = mutableListOf<Candidate>()
        for (chapter in chapters) {
            for (piece in split(chapter.text, passageChars)) {
                val words = WORD.findAll(piece)
                    .map { it.value.lowercase().removeSuffix("'s").removeSuffix("’s") }
                    .toSet()
                candidates += Candidate(candidates.size, Passage(chapter.title, piece), words)
            }
        }
        if (candidates.isEmpty()) return emptyList()

        // A word found in few passages says more about a passage than one found everywhere.
        val weights = terms.mapValues { (term, isName) ->
            val containing = candidates.count { term in it.words }
            val rarity = ln(1.0 + candidates.size.toDouble() / (1 + containing))
            if (isName) rarity * 2 else rarity
        }

        return candidates
            .map { candidate -> candidate to weights.entries.sumOf { (term, weight) -> if (term in candidate.words) weight else 0.0 } }
            .filter { it.second > 0.0 }
            .sortedWith(compareByDescending<Pair<Candidate, Double>> { it.second }.thenBy { it.first.order })
            .take(maxPassages)
            .sortedBy { it.first.order }
            .map { it.first.passage }
    }
}
