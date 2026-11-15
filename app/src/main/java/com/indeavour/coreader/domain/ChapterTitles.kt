package com.indeavour.coreader.domain

/** One table-of-contents entry, reduced to what chapter naming needs. */
data class TocEntry(
    val href: String,
    val title: String?,
    val children: List<TocEntry> = emptyList()
)

/**
 * Works out which chapter each file of a book belongs to.
 *
 * An EPUB's reading order is a list of files, not chapters: one chapter is often split over
 * several files, and covers, title pages and the like are files too, so counting them gives
 * misleading "Chapter x of y" numbers. The table of contents is what names chapters.
 */
object ChapterTitles {

    /** A file's identity, ignoring any `#fragment` pointing inside it. */
    fun resourceKey(href: String): String = href.substringBefore('#')

    /**
     * The chapter title for each entry of [readingOrder] (hrefs), in the same order.
     *
     * A file without its own table-of-contents entry is treated as a continuation of the last
     * one that had an entry. Files before the first entry get an empty title.
     */
    fun byReadingOrder(toc: List<TocEntry>, readingOrder: List<String>): List<String> {
        val titles = mutableMapOf<String, String>()
        fun visit(entries: List<TocEntry>) {
            for (entry in entries) {
                val title = entry.title?.trim().orEmpty()
                val key = resourceKey(entry.href)
                // Parents are visited first, so a chapter wins over its own sub-sections.
                if (title.isNotEmpty() && key !in titles) titles[key] = title
                visit(entry.children)
            }
        }
        visit(toc)

        var current = ""
        return readingOrder.map { href ->
            titles[resourceKey(href)]?.let { current = it }
            current
        }
    }
}
