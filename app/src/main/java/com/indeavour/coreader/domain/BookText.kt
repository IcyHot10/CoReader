package com.indeavour.coreader.domain

/** Turns a chapter's HTML into the plain text a reader has seen. */
object BookText {

    private val HIDDEN_BLOCKS = Regex("(?is)<(head|script|style)\\b.*?</\\1\\s*>")
    private val COMMENTS = Regex("(?s)<!--.*?-->")
    private val TAGS = Regex("<[^>]*>")
    private val NUMERIC_ENTITY = Regex("&#(x?)([0-9a-fA-F]+);")
    private val WHITESPACE = Regex("\\s+")

    private val NAMED_ENTITIES = mapOf(
        "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'",
        "&mdash;" to "—", "&ndash;" to "–", "&hellip;" to "…",
        "&lsquo;" to "‘", "&rsquo;" to "’", "&ldquo;" to "“", "&rdquo;" to "”"
    )

    /** The readable text of [html]: no markup, scripts or styles, whitespace collapsed. */
    fun fromHtml(html: String): String {
        var text = html.replace(HIDDEN_BLOCKS, " ").replace(COMMENTS, " ").replace(TAGS, " ")
        NAMED_ENTITIES.forEach { (entity, character) -> text = text.replace(entity, character) }
        text = NUMERIC_ENTITY.replace(text) { match ->
            val code = match.groupValues[2].toIntOrNull(if (match.groupValues[1].isEmpty()) 10 else 16)
            if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else " "
        }
        // Last, so that "&amp;lt;" comes out as the literal text "&lt;" rather than "<".
        text = text.replace("&amp;", "&")
        return WHITESPACE.replace(text, " ").trim()
    }

    /**
     * The last [maxChars] of what has been read of a chapter: its text up to [progression]
     * (0..1 through the chapter), so nothing ahead of the reader is included.
     */
    fun readSoFar(html: String, progression: Double, maxChars: Int): String {
        val text = fromHtml(html)
        val read = text.take((text.length * progression.coerceIn(0.0, 1.0)).toInt())
        return read.takeLast(maxChars)
    }
}
