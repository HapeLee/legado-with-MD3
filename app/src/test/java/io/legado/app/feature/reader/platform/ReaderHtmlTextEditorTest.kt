package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.semantic.SemanticRange
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class ReaderHtmlTextEditorTest {
    @Test
    fun editsAcrossInlineNodesAndPreservesResourceAndAnchorAttributes() {
        val html = "<p id='繁體'><a href='繁體.xhtml'>今天</a><strong>天气很好</strong><img src='繁體.png'></p>"
        val result = ReaderHtmlTextEditor.replace(html, SemanticRange(0, 4), "昨天") as ReaderHtmlTextEditor.Result.Applied
        assertEquals("昨天很好\uFFFC\n", JsoupReaderHtmlParser.semanticText(result.html))
        val doc = Jsoup.parseBodyFragment(result.html)
        assertEquals("繁體", doc.selectFirst("p")!!.id())
        assertEquals("繁體.xhtml", doc.selectFirst("a")!!.attr("href"))
        assertEquals("繁體.png", doc.selectFirst("img")!!.attr("src"))
        assertEquals("很好", doc.selectFirst("strong")!!.text())
    }

    @Test
    fun decodedEntitiesEmojiAndLiteralMarkupRoundTrip() {
        val html = "<p>A&amp;<b>😀B</b>C</p>"
        val result = ReaderHtmlTextEditor.replace(html, SemanticRange(1, 5), "<x>&") as ReaderHtmlTextEditor.Result.Applied
        assertEquals("A<x>&C\n", JsoupReaderHtmlParser.semanticText(result.html))
        assertTrue(Jsoup.parseBodyFragment(result.html).select("x").isEmpty())
    }

    @Test
    fun supportsInsertionAndPartialDeletion() {
        val html = "<p>AB<strong>CD</strong>E</p>"
        val insert = ReaderHtmlTextEditor.replace(html, SemanticRange(2, 2), "😀") as ReaderHtmlTextEditor.Result.Applied
        assertEquals("AB😀CDE\n", JsoupReaderHtmlParser.semanticText(insert.html))
        val delete = ReaderHtmlTextEditor.replace(html, SemanticRange(1, 4), "") as ReaderHtmlTextEditor.Result.Applied
        assertEquals("AE\n", JsoupReaderHtmlParser.semanticText(delete.html))
    }

    @Test
    fun refusesStructuralImagesNormalizedWhitespaceAndBrokenSurrogates() {
        val cases = listOf(
            Triple("<p>AB</p><p>CD</p>", SemanticRange(1, 4), "X"),
            Triple("<p>A<img src='x'>B</p>", SemanticRange(0, 3), "X"),
            Triple("<p>A  B</p>", SemanticRange(0, 3), "X"),
            Triple("<p>😀B</p>", SemanticRange(1, 2), "X"),
            Triple("<p>AB</p>", SemanticRange(0, 2), ""),
            Triple("<p>AB</p>", SemanticRange(0, 1), "X\nY"),
            Triple("<p>AB</p>", SemanticRange(3, 3), "X"),
        )
        cases.forEach { (html, range, replacement) ->
            assertTrue(html, ReaderHtmlTextEditor.replace(html, range, replacement) is ReaderHtmlTextEditor.Result.Unsupported)
            assertTrue(html.contains('<'))
        }
    }
}
