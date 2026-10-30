package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupReaderHtmlParserTest {

    @Test
    fun blockStylesReachTextAndStayScopedToTheirBlock() {
        val parsed = JsoupReaderHtmlParser.parse(
            "<blockquote style='font-style:italic'><p style='font-weight:bold'>styled</p></blockquote><p>plain</p>",
            0, 20f, 2f,
        )
        val text = parsed.paragraphs.flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Text>()
        assertEquals("styled\nplain\n", parsed.semantic)
        assertEquals(700, text[0].style.fontWeight)
        assertEquals(true, text[0].style.italic)
        assertEquals(null, text[1].style.fontWeight)
        assertEquals(null, text[1].style.italic)
    }

    @Test
    fun semanticAndParagraphsShareOneCursor() {
        val html = "<h2>甲&amp;&#x4E59;</h2><p><a href=\"https://example\">链接</a><img src=\"pic\"></p><blockquote>末</blockquote>"
        val semantic = JsoupReaderHtmlParser.semanticText(html)
        val parsed = JsoupReaderHtmlParser.parse(html, 7, 20f, 2f)
        assertTrue(semantic.contains("甲&乙"))
        assertTrue(semantic.contains('\uFFFC'))
        assertEquals(semantic, parsed.semantic)
        // Every inline item offset matches the semantic string.
        for (paragraph in parsed.paragraphs) {
            for (item in paragraph.items) {
                when (item) {
                    is ReaderChapterInlineSource.Text -> {
                        assertEquals(
                            item.value,
                            parsed.semantic.substring(
                                item.chapterPosition - 7,
                                item.chapterPosition - 7 + item.value.length
                            )
                        )
                    }
                    is ReaderChapterInlineSource.Image -> {
                        assertEquals('\uFFFC', parsed.semantic[item.chapterPosition - 7])
                    }
                    else -> {}
                }
            }
        }
    }

    @Test
    fun consecutiveBreaksPreserveBlankLineWithoutTrailingPhantom() {
        val parsed = JsoupReaderHtmlParser.parse("甲<br><br>乙<br>", 10, 20f, 2f)
        assertEquals("甲\n\n乙\n", parsed.semantic)
        assertEquals(3, parsed.paragraphs.size)
        assertEquals("甲", (parsed.paragraphs[0].items.single() as ReaderChapterInlineSource.Text).value)
        assertEquals(12, (parsed.paragraphs[1].items.single() as ReaderChapterInlineSource.BlankLine).chapterPosition)
        assertEquals("乙", (parsed.paragraphs[2].items.single() as ReaderChapterInlineSource.Text).value)
    }

    @Test
    fun quoteAndListProduceDecorationsWithMargins() {
        val parsed = JsoupReaderHtmlParser.parse(
            "<blockquote>引文内容</blockquote><ul><li>列表项</li></ul>", 0, 20f, 2f
        )
        val quote = parsed.paragraphs.first {
            it.items.filterIsInstance<ReaderChapterInlineSource.Text>().any { t -> t.value.contains("引文") }
        }
        val list = parsed.paragraphs.first {
            it.items.filterIsInstance<ReaderChapterInlineSource.Text>().any { t -> t.value.contains("列表") }
        }
        assertTrue(quote.firstLineMarginPx > 0f)
        assertTrue(list.firstLineMarginPx > 0f)
        assertEquals(ReaderParagraphDecorationKind.QUOTE, quote.decorations.single().kind)
        assertEquals(ReaderParagraphDecorationKind.BULLET, list.decorations.single().kind)
    }

    @Test
    fun nestedListMarkersCarryIncreasingOffsets() {
        val parsed = JsoupReaderHtmlParser.parse(
            "<ul><li>外层<ul><li>内层</li></ul></li></ul>", 0, 20f, 2f
        )
        val inner = parsed.paragraphs.first {
            it.items.filterIsInstance<ReaderChapterInlineSource.Text>().any { t -> t.value.contains("内层") }
        }
        val bullets = inner.decorations.filter { it.kind == ReaderParagraphDecorationKind.BULLET }
        assertTrue(bullets.size >= 2)
        assertTrue(bullets.zipWithNext().all { (outer, nested) -> nested.leadingOffsetPx > outer.leadingOffsetPx })
    }

    @Test
    fun rubyAnnotationsStayOutOfSemanticStream() {
        val parsed = JsoupReaderHtmlParser.parse("<p><ruby>漢<rt>かん</rt></ruby>字</p>", 0, 20f, 2f)
        assertEquals("漢字\n", parsed.semantic)
    }

    @Test
    fun trimsLeadingParagraphIndentationAndPreservesNestedHeadingScale() {
        val parsed = JsoupReaderHtmlParser.parse("<h2>\n  <center>标题</center>\n</h2>", 0, 20f, 2f)
        assertEquals("标题\n", parsed.semantic)
        val textItem = parsed.paragraphs.single().items.single() as ReaderChapterInlineSource.Text
        assertEquals("标题", textItem.value)
        assertEquals(1.25f, textItem.style.fontSizeScale, 0.001f)
        assertEquals(700, textItem.style.fontWeight)
    }

    @Test
    fun onlyNormalBodyBlocksKeepReaderIndent() {
        val parsed = JsoupReaderHtmlParser.parse(
            "<p>正文</p><h2>标题</h2><pre>代码</pre><blockquote>引文</blockquote>" +
                "<ul><li>列表</li></ul><table><tr><td>表格</td></tr></table><figure><figcaption>图注</figcaption></figure>",
            0, 20f, 2f,
        )

        fun preserveOf(text: String) = parsed.paragraphs.first { paragraph ->
            paragraph.items.filterIsInstance<ReaderChapterInlineSource.Text>()
                .any { it.value.contains(text) }
        }.preserveBodyIndent

        assertTrue(preserveOf("正文"))
        assertEquals(false, preserveOf("标题"))
        assertEquals(false, preserveOf("代码"))
        assertEquals(false, preserveOf("引文"))
        assertEquals(false, preserveOf("列表"))
        assertEquals(false, preserveOf("表格"))
        assertEquals(false, preserveOf("图注"))
    }

    @Test
    fun findsBidirectionalFootnoteAndEmptyPageAnchorPositions() {
        val bodyHtml = "<p>Sentence end.<sup><a href=\"#note_1\" id=\"ref_1\">1</a></sup> Next <a id=\"page_13\"/>word</p>"
        val noteHtml = "<p><span><strong><a href=\"#ref_1\" id=\"note_1\">1</a></strong></span> Footnote body text</p>"
        val bodyParsed = JsoupReaderHtmlParser.parse(bodyHtml, 100, 20f, 2f)
        val refPos = JsoupReaderHtmlParser.findAnchorPosition(bodyHtml, "ref_1", 100)!!
        val pagePos = JsoupReaderHtmlParser.findAnchorPosition(bodyHtml, "page_13", 100)!!
        val notePos = JsoupReaderHtmlParser.findAnchorPosition(noteHtml, "note_1", 500)!!

        assertEquals('1', bodyParsed.semantic[refPos - 100])
        assertTrue(bodyParsed.semantic.substring(pagePos - 100).startsWith("word"))
        assertEquals(500, notePos)
    }
}
