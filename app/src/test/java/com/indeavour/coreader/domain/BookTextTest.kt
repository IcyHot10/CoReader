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
    fun `text is decoded with the encoding the file declares`() {
        val xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><p>café</p>"
        assertEquals(xml, BookText.decode(xml.toByteArray(Charsets.ISO_8859_1)))

        val meta = "<html><head><meta charset=\"windows-1252\"></head><body>naïve</body></html>"
        assertEquals(meta, BookText.decode(meta.toByteArray(charset("windows-1252"))))
    }

    @Test
    fun `text with no declared encoding is read as UTF-8`() {
        assertEquals("<p>café – “quoted”</p>", BookText.decode("<p>café – “quoted”</p>".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `an unknown declared encoding falls back to UTF-8`() {
        val xml = "<?xml version=\"1.0\" encoding=\"no-such-charset\"?><p>café</p>"
        assertEquals(xml, BookText.decode(xml.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `byte order marks are recognised and removed`() {
        assertEquals("<p>hi</p>", BookText.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "<p>hi</p>".toByteArray()))
        assertEquals("<p>hi</p>", BookText.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "<p>hi</p>".toByteArray(Charsets.UTF_16LE)))
    }

    @Test
    fun `an escaped entity stays as written`() {
        assertEquals("use &lt; for less-than", BookText.fromHtml("use &amp;lt; for less-than"))
    }
}
