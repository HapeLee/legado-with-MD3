package io.legado.app.feature.reader.platform

import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import io.legado.app.feature.reader.core.semantic.ReaderSemanticContract
import org.junit.Test

class JsoupReaderPresentationParserTest {

    @Test
    fun blockStylesReachTextAndStayScopedToTheirBlock() {
        val parsed = JsoupReaderPresentationParser.parse(
            "<blockquote style='font-style:italic'><p style='font-weight:bold'>styled</p></blockquote><p>plain</p>",
            0, 20f, 2f,
        )
        val text = parsed.paragraphs.flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Text>()
        assertEquals("styled\nplain\n", parsed.text)
        assertEquals(700, text[0].style.fontWeight)
        assertEquals(true, text[0].style.italic)
        assertEquals(null, text[1].style.fontWeight)
        assertEquals(null, text[1].style.italic)
    }

    @Test
    fun consecutiveBreaksPreserveBlankLineWithoutTrailingPhantom() {
        val parsed = JsoupReaderPresentationParser.parse("甲<br><br>乙<br>", 10, 20f, 2f)
        assertEquals("甲\n\n乙\n", parsed.text)
        assertEquals(3, parsed.paragraphs.size)
        assertEquals("甲", (parsed.paragraphs[0].items.single() as ReaderChapterInlineSource.Text).value)
        assertEquals(12, (parsed.paragraphs[1].items.single() as ReaderChapterInlineSource.BlankLine).chapterPosition)
        assertEquals("乙", (parsed.paragraphs[2].items.single() as ReaderChapterInlineSource.Text).value)
    }

    @Test
    fun quoteAndListProduceDecorationsWithMargins() {
        val parsed = JsoupReaderPresentationParser.parse(
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
    fun nestedListItemKeepsSingleMarkerAtItsDepth() {
        val parsed = JsoupReaderPresentationParser.parse(
            "<ul><li>外层<ul><li>内层</li></ul></li></ul>", 0, 20f, 2f
        )
        val inner = parsed.paragraphs.first {
            it.items.filterIsInstance<ReaderChapterInlineSource.Text>().any { t -> t.value.contains("内层") }
        }
        val bullets = inner.decorations.filter { it.kind == ReaderParagraphDecorationKind.BULLET }
        assertEquals(1, bullets.size)
        assertEquals(24f, bullets.single().leadingOffsetPx, 0.01f)
    }

    @Test
    fun rubyAnnotationsStayOutOfSemanticStream() {
        val parsed = JsoupReaderPresentationParser.parse("<p><ruby>漢<rt>かん</rt></ruby>字</p>", 0, 20f, 2f)
        assertEquals("漢字\n", parsed.text)
    }

    @Test
    fun trimsLeadingParagraphIndentationAndPreservesNestedHeadingScale() {
        val parsed = JsoupReaderPresentationParser.parse("<h2>\n  <center>标题</center>\n</h2>", 0, 20f, 2f)
        assertEquals("标题\n", parsed.text)
        val textItem = parsed.paragraphs.single().items.single() as ReaderChapterInlineSource.Text
        assertEquals("标题", textItem.value)
        assertEquals(1.25f, textItem.style.fontSizeScale, 0.001f)
        assertEquals(700, textItem.style.fontWeight)
    }

    @Test
    fun onlyNormalBodyBlocksKeepReaderIndent() {
        val parsed = JsoupReaderPresentationParser.parse(
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
    fun footnotesAndEmptyAnchorsReferToCanonicalPositions() {
        val html = "<p>Sentence <a id='ref_1' href='#note_1'>1</a> end.</p><a id='page_13'></a><p id='note_1'>Footnote</p>"
        val canonical = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(org.jsoup.Jsoup.parse(html).body().outerHtml())
        val appearance = checkNotNull(ReaderLocalPresentationResolver.resolve(html, canonical, "", true))
        val values = io.legado.app.help.book.MixedContentSegments.paragraphs(canonical, "", false)
        val source = ReaderChapterSourceParser.parse(0, "", values, false, true, localPresentation = appearance)
        assertEquals(source.semanticContent.indexOf("1"), source.anchorPositions["ref_1"])
        assertEquals(source.semanticContent.indexOf("Footnote"), source.anchorPositions["page_13"])
        assertEquals(source.semanticContent.indexOf("Footnote"), source.anchorPositions["note_1"])
    }
    @Test
    fun goldenProjectionAndInlineOffsets() {
        val fixtures = listOf(
            "甲乙" to "甲乙\n",
            "甲😀乙" to "甲😀乙\n",
            "A&amp;B" to "A&B\n",
            "<p>A</p><p>B</p>" to "A\nB\n",
            "<strong>A</strong>B" to "AB\n",
            "A<img src='x'>B" to "A\uFFFCB\n",
            "A<img>B" to "AB\n",
            "<p></p>" to "",
            "<hr>" to "\n",
            "<ruby>漢<rt>かん</rt><rp>(</rp></ruby>" to "漢\n",
            "<a id='n'></a>正文" to "正文\n",
            "<pre>A  B</pre>" to "A  B\n",
            "A<br><br>B" to "A\n\nB\n",
        )
        fixtures.forEach { (html, expected) ->
            for ((size, density) in listOf(16f to 1f, 24f to 3f)) {
                val parsed = JsoupReaderPresentationParser.parse(html, 7, size, density)
                assertEquals(html, expected, parsed.text)
                assertEquals(emptyList<ReaderSemanticContract.Violation>(),
                    ReaderSemanticContract.validateInlines(parsed.text, parsed.paragraphs.flatMap { it.items }, 7))
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

        }
    }

    @Test
    fun visualWrappersAndAttributesPreserveSemanticAndAnchors() {
        val fragments = listOf(
            "<p>A&amp;<a id='n'>😀B</a></p>",
            "<p style='text-align:center;color:red'><span><strong>A&amp;</strong><a class='x' id='n'><em>😀B</em></a></span></p>",
        )
        fragments.forEach { html ->
            assertEquals("A&😀B\n", JsoupReaderPresentationParser.parse(html, 0, 10f, 1f).text)

        }
    }



    @Test
    fun appearanceAndCssCannotChangeCanonicalTextParagraphsOrAnchors() {
        val fixture = io.legado.app.support.ReaderSemanticFixtures
        val doc = org.jsoup.Jsoup.parse(fixture.html)
        val canonical = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(doc.body().outerHtml())
        val values = io.legado.app.help.book.MixedContentSegments.paragraphs(canonical, "", false)
        assertEquals(fixture.paragraphs, values)
        io.legado.app.model.localBook.LocalBookSemanticHtml.inlineDocumentCssSemantics(doc) { fixture.css }
        val presentation = checkNotNull(ReaderLocalPresentationResolver.resolve(doc.body().outerHtml(), canonical, "", true))
        for (enabled in listOf(false, true)) {
            val source = ReaderChapterSourceParser.parse(0, "", values, false, enabled,
                localPresentation = presentation.takeIf { enabled })
            assertEquals(fixture.body, source.semanticContent)
            assertEquals(fixture.paragraphs.indices.toList(), source.paragraphs.map { it.ordinal })
            if (enabled) assertEquals(fixture.body.indexOf("乙"), source.anchorPositions["note"])
            assertTrue(ReaderSemanticContract.validate(source).isEmpty())
        }
        assertTrue(presentation.spans.any { it.style.italic == true })
        assertTrue(presentation.spans.any { it.style.fontWeight == 700 })
    }

    @Test
    fun changedBodyAndRoleLabelsDropOnlyAppearanceWithoutGuessingOffsets() {
        val html = "<p id='n'><b>“甲”</b></p><p>乙</p>"
        val canonical = io.legado.app.model.localBook.LocalBookSemanticHtml.plainText(org.jsoup.Jsoup.parse(html).body().outerHtml())
        val values = io.legado.app.help.book.MixedContentSegments.paragraphs(canonical, "", false)
        val presentation = checkNotNull(ReaderLocalPresentationResolver.resolve(html, canonical, "", true))
        val edited = ReaderChapterSourceParser.parse(0, "", listOf("changed"), false, true, localPresentation = presentation)
        assertEquals("changed\n", edited.semanticContent)
        assertTrue(edited.anchorPositions.isEmpty())
        val labels = mapOf(0 to "<<甲（池）>>")
        val plain = ReaderChapterSourceParser.parse(0, "", values, false, true, castLabels = labels)
        val styled = ReaderChapterSourceParser.parse(0, "", values, false, true, castLabels = labels, localPresentation = presentation)
        assertEquals(plain, styled)
        assertEquals("“<<甲（池）>>甲”\n乙\n", styled.semanticContent)
        assertEquals(listOf("“甲”", "乙"), ReaderChapterSourceParser.castAnchorText(values, true))
        assertEquals(null, ReaderLocalPresentationResolver.resolve("<p>other</p>", canonical, "", true))
    }
}
