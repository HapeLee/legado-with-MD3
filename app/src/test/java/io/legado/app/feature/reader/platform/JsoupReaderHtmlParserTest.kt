package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsoupReaderHtmlParserTest {

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
}
