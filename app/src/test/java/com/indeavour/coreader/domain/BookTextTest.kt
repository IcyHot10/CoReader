package com.indeavour.coreader.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BookTextTest {

    @Test
    fun `markup is removed and whitespace collapsed`() {
        assertEquals(
            "Chapter One It was a dark night.",
            BookText.fromHtml("<h1>Chapter One</h1>\n\n  <p>It was a <em>dark</em>\n night.</p>")
        )
    }

    @Test
    fun `head, scripts, styles and comments are not text`() {
        val html = """
            <html><head><title>Hidden</title><style>p { color: red; }</style></head>
            <body><!-- note --><script>var x = "<p>no</p>";</script><p>Shown</p></body></html>
        """.trimIndent()
        assertEquals("Shown", BookText.fromHtml(html))
    }

    @Test
    fun `entities are decoded`() {
        assertEquals("Tom & Jerry “quoted” – it’s… é A", BookText.fromHtml("Tom &amp; Jerry &ldquo;quoted&rdquo; &ndash; it&rsquo;s&hellip; &#233; &#x41;"))
        assertEquals("a b", BookText.fromHtml("a&nbsp;b"))
    }

    @Test
    fun `an escaped entity stays as written`() {
        assertEquals("use &lt; for less-than", BookText.fromHtml("use &amp;lt; for less-than"))
    }

    @Test
    fun `only what has been read is returned`() {
        val html = "<p>0123456789</p>"
        assertEquals("01234", BookText.readSoFar(html, 0.5, maxChars = 100))
        assertEquals("", BookText.readSoFar(html, 0.0, maxChars = 100))
        assertEquals("0123456789", BookText.readSoFar(html, 1.0, maxChars = 100))
    }

    @Test
    fun `the most recent text is kept when there is too much`() {
        assertEquals("789", BookText.readSoFar("<p>0123456789</p>", 1.0, maxChars = 3))
    }

    @Test
    fun `progress outside the chapter is clamped`() {
        assertEquals("0123456789", BookText.readSoFar("<p>0123456789</p>", 7.0, maxChars = 100))
        assertEquals("", BookText.readSoFar("<p>0123456789</p>", -1.0, maxChars = 100))
    }
}
