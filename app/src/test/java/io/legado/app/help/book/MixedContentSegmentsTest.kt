package io.legado.app.help.book

import io.legado.app.feature.reader.platform.JsoupReaderHtmlParser
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MixedContentSegmentsTest {
    @Test
    fun plainSegmentsTransformIndependentlyAroundRichBlocks() {
        val input = "before\n<usehtml><p>rich</p></usehtml>\nafter"
        val calls = mutableListOf<String>()
        val output = MixedContentSegments.transform(input, plain = { calls += it; it.uppercase() }, rich = { it })
        assertEquals(listOf("before\n", "\nafter"), calls)
        assertEquals("BEFORE\n<usehtml><p>rich</p></usehtml>\nAFTER", output)
    }

    @Test
    fun nodeTransformsPreserveQuotedAnglesPathsCssAndAnchors() {
        val input = "<p id='繁體' style='font-family:繁體' title='x>繁體'><a href='繁體.xhtml'>繁體&amp;<b>內容</b></a><img src='繁體.png'></p><style>.繁體{}</style>"
        val output = MixedContentSegments.transformHtmlText(input) { it.replace("繁體", "简体").replace("內容", "内容") }
        val doc = Jsoup.parseBodyFragment(output)
        assertEquals("简体&内容\uFFFC\n", JsoupReaderHtmlParser.semanticText(output))
        assertEquals("繁體.xhtml", doc.selectFirst("a")!!.attr("href"))
        assertEquals("x>繁體", doc.selectFirst("p")!!.attr("title"))
        assertEquals("font-family:繁體", doc.selectFirst("p")!!.attr("style"))
        assertEquals("繁體.png", doc.selectFirst("img")!!.attr("src"))
        assertEquals(".繁體{}", doc.selectFirst("style")!!.data())
    }

    @Test
    fun legacyImageAttributesRemainProtectedDuringPlainConversion() {
        val text = "<img src='繁體>路徑.png'>繁體內容"
        val converted = MixedContentSegments.transformPlainText(text) { it.replace("繁體", "简体") }
        assertEquals("<img src='繁體>路徑.png'>简体內容", converted)
    }

    @Test
    fun identitySerializationPreservesSemanticIncludingPreformattedNewlines() {
        val html = "<pre>A  B\n C</pre><p>A&amp;<b>B</b></p>"
        assertEquals(JsoupReaderHtmlParser.semanticText(html),
            JsoupReaderHtmlParser.semanticText(MixedContentSegments.transformHtmlText(html) { it }))
        val paragraphs = MixedContentSegments.paragraphs("plain\n<usehtml>$html</usehtml>\ntail", "　　", true)
        assertEquals(listOf("　　plain", "<usehtml>$html</usehtml>", "　　tail"), paragraphs)
    }

    @Test
    fun plainOnlyParagraphsMatchLegacyTrimming() {
        val text = "  A\n\n　B \n C\r\n"
        val legacy = text.split("\n").map { it.trim { c -> c.code <= 0x20 || c == '　' } }
            .filter { it.isNotEmpty() }.map { "　　$it" }
        assertEquals(legacy, MixedContentSegments.paragraphs(text, "　　", true))
        assertEquals(legacy, MixedContentSegments.paragraphs(text, "　　", false))
        assertEquals(listOf("　　A\rB"), MixedContentSegments.paragraphs("A\rB", "　　", true))
    }
}
