package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import org.junit.Assert.assertEquals
import org.junit.Test

/** Golden strings deliberately include the existing HTML-block separator. */
class JsoupReaderHtmlParserContractTest {
    @Test
    fun goldenProjectionAndInlineOffsets() {
        val fixtures = listOf(
            "甲乙" to "甲乙\n",
            "甲😀乙" to "甲😀乙\n",
            "A&amp;B" to "A&B\n",
            "<p>A</p><p>B</p>" to "A\nB\n",
            "<strong>A</strong>B" to "AB\n",
            "A<img src='x'>B" to "A\uFFFCB\n",
            "<ruby>漢<rt>かん</rt><rp>(</rp></ruby>" to "漢\n",
            "<a id='n'></a>正文" to "正文\n",
            "<pre>A  B</pre>" to "A  B\n",
            "A<br><br>B" to "A\n\nB\n",
        )
        fixtures.forEach { (html, expected) ->
            for ((size, density) in listOf(16f to 1f, 24f to 3f)) {
                val parsed = JsoupReaderHtmlParser.parse(html, 7, size, density)
                assertEquals(html, expected, parsed.semantic)
                parsed.paragraphs.flatMap { it.items }.forEach { item ->
                    val pos = item.chapterPosition - 7
                    when (item) {
                        is ReaderChapterInlineSource.Text -> assertEquals(item.value, expected.substring(pos, pos + item.value.length))
                        is ReaderChapterInlineSource.Image -> assertEquals('\uFFFC', expected[pos])
                        is ReaderChapterInlineSource.BlankLine -> assertEquals('\n', expected[pos])
                        else -> Unit
                    }
                }
            }
            val source = ReaderChapterSourceParser.parse(0, "title", listOf("<usehtml>$html</usehtml>"), true, true, AndroidReaderHtmlSemanticTextResolver)
            assertEquals(expected + "\n", source.semanticContent)
            assertEquals(source.semanticContent.length, source.characterCount)
        }
    }

    @Test
    fun visualWrappersAndAttributesPreserveSemanticAndAnchors() {
        val fragments = listOf(
            "<p>A&amp;<a id='n'>😀B</a></p>",
            "<p style='text-align:center;color:red'><span><strong>A&amp;</strong><a class='x' id='n'><em>😀B</em></a></span></p>",
        )
        fragments.forEach { html ->
            assertEquals("A&😀B\n", JsoupReaderHtmlParser.semanticText(html))
            assertEquals(2, JsoupReaderHtmlParser.findAnchorPosition(html, "n"))
        }
    }

    @Test
    fun plainTextTitleImagesAndPageBreakKeepExistingCoordinates() {
        val source = ReaderChapterSourceParser.parse(0, "标题", listOf("甲😀乙", "[newpage]", "A<img src=\"x\">B"), true, true)
        assertEquals("甲😀乙\nA\uFFFCB\n", source.semanticContent)
        assertEquals("标题\n", source.semanticTitle)
        assertEquals(9, source.characterCount)
    }
}
