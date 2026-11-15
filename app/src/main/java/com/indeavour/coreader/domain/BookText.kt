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

    private val DECLARED_CHARSET = Regex("(?i)(?:encoding|charset)\\s*=\\s*[\"']?\\s*([a-z0-9._:-]+)")

    /**
     * Decodes a chapter file to text using the encoding it declares, either in its XML
     * declaration or a `<meta charset>`; UTF-8 when it declares none, or one that is unknown.
     * Nearly every EPUB is UTF-8, but an older one that is not would otherwise come out garbled.
     */
    fun decode(bytes: ByteArray): String {
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            else -> {
                // The declaration is plain ASCII near the top of the file, whatever the encoding.
                val top = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
                val name = DECLARED_CHARSET.find(top)?.groupValues?.get(1)
                try {
                    name?.let { java.nio.charset.Charset.forName(it) } ?: Charsets.UTF_8
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
            }
        }
        return String(bytes, charset).removePrefix("\uFEFF")
    }

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
}
