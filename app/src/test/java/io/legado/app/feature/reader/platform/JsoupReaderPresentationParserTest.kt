package io.legado.app.feature.reader.platform

import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.core.layout.ReaderParagraphDecorationKind
import io.legado.app.feature.reader.core.semantic.ReaderSemanticContract
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.help.book.MixedContentSegments
import org.junit.Assert.*
import org.junit.Test

class JsoupReaderPresentationParserTest {
    private fun source(html: String, labels: Map<Int, String>? = null): ReaderChapterSource {
        val appearance = ReaderLocalPresentationResolver.resolve(org.jsoup.Jsoup.parse(html).body().outerHtml())!!
        return ReaderChapterSourceParser.parse(0, "", MixedContentSegments.paragraphs(appearance.content, "", false),
            false, true, castLabels = labels, localPresentation = appearance)
    }
    private fun texts(source: ReaderChapterSource) = source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        .flatMap { it.items }.filterIsInstance<ReaderChapterInlineSource.Text>()
    private fun paragraph(source: ReaderChapterSource, text: String) = source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        .first { it.items.filterIsInstance<ReaderChapterInlineSource.Text>().joinToString("") { item -> item.value }.contains(text) }

    @Test fun blockStylesReachTextAndStayScopedToTheirBlock() {
        val current = source("<blockquote style='font-style:italic'><p style='font-weight:bold'>styled</p></blockquote><p>plain</p>")
        assertEquals("styled\nplain\n", current.semanticContent)
        val items = texts(current)
        assertEquals(700, items[0].style.fontWeight)
        assertEquals(true, items[0].style.italic)
        assertEquals(null, items[1].style.fontWeight)
        assertEquals(null, items[1].style.italic)
    }

    @Test fun consecutiveBreaksPreserveBlankLineWithoutAddingCanonicalCharacters() {
        val current = source("甲<br><br>乙<br>")
        assertEquals("甲\n乙\n", current.semanticContent)
        val blocks = current.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
        assertEquals(1, blocks.count { it.presentation?.blankLine == true })
        assertEquals(listOf(0, 1), current.paragraphs.map { it.ordinal })
    }

    @Test fun quoteAndListProduceDecorationsWithMargins() {
        val current = source("<blockquote>引文内容</blockquote><ul><li>列表项</li></ul>")
        val quote = checkNotNull(paragraph(current, "引文").presentation)
        val list = checkNotNull(paragraph(current, "列表").presentation)
        assertTrue(quote.firstMargin > 0f)
        assertTrue(list.firstMargin > 0f)
        assertEquals(ReaderParagraphDecorationKind.QUOTE, quote.decorations.single().kind)
        assertEquals(ReaderParagraphDecorationKind.BULLET, list.decorations.single().kind)
    }

    @Test fun nestedListItemKeepsSingleMarkerAtItsDepth() {
        for (inner in listOf("内层", "<pre>内层\n续行</pre>")) {
            val current = source("<ul><li>外层<ul><li>$inner</li></ul></li></ul>")
            val bullets = checkNotNull(paragraph(current, "内层").presentation).decorations.filter { it.kind == ReaderParagraphDecorationKind.BULLET }
            assertEquals(1, bullets.size)
            assertEquals(12f, bullets.single().leadingOffsetPx, 0.01f)
            if (inner.startsWith("<pre>")) assertTrue(paragraph(current, "续行").presentation?.decorations.orEmpty()
                .none { it.kind == ReaderParagraphDecorationKind.BULLET })
        }

    }

    @Test fun rubyRetainsLegacyCharactersWithoutSuppressingOtherStyles() {
        for (annotation in listOf("<rt>かん</rt>", "")) {
            val current = source("<p><ruby>漢$annotation</ruby><b>字</b></p>")
            assertEquals(if (annotation.isEmpty()) "漢字\n" else "漢かん字\n", current.semanticContent)
            assertEquals(700, texts(current).single { it.value == "字" }.style.fontWeight)
        }
    }

    @Test fun trimsLeadingParagraphIndentationAndPreservesNestedHeadingScale() {
        val current = source("<h2>\n  <center>标题</center>\n</h2>")
        assertEquals("标题\n", current.semanticContent)
        val text = texts(current).single()
        assertEquals(1.25f, text.style.fontSizeScale, 0.001f)
        assertEquals(700, text.style.fontWeight)
    }

    @Test fun onlyNormalBodyBlocksKeepReaderIndent() {
        val current = source("<p>正文</p><h2>标题</h2><pre>代码</pre><blockquote>引文</blockquote>" +
            "<ul><li>列表</li></ul><table><tr><td>表格</td></tr></table><figure><figcaption>图注</figcaption></figure>")
        assertEquals(true, paragraph(current, "正文").presentation?.preserveBodyIndent)
        for (text in listOf("标题", "代码", "引文", "列表", "表格", "图注")) {
            assertEquals(text, false, paragraph(current, text).presentation?.preserveBodyIndent)
        }
    }

    @Test fun footnotesAndEmptyAnchorsReferToCanonicalPositions() {
        val current = source("<p>Sentence <a id='ref_1' href='#note_1'>1</a> end.</p><a id='page_13'></a><p id='note_1'>Footnote</p>")
        assertEquals(current.semanticContent.indexOf("1"), current.anchorPositions["ref_1"])
        assertEquals(current.semanticContent.indexOf("Footnote"), current.anchorPositions["page_13"])
        assertEquals(current.semanticContent.indexOf("Footnote"), current.anchorPositions["note_1"])
        assertEquals("#note_1", texts(current).single { it.value == "1" }.style.link)
    }

    @Test fun goldenProjectionAndInlineOffsets() {
        for ((html, expected) in listOf(
            "甲乙" to "甲乙\n", "甲😀乙" to "甲😀乙\n", "A&amp;B" to "A&B\n",
            "<p>A</p><p>B</p>" to "A\nB\n", "<strong>A</strong>B" to "AB\n",
            "A<img src='x'>B" to "A\uFFFCB\n", "A<img>B" to "A<img>B\n", "<p></p>" to "", "<hr>" to "",
            "<ruby>漢<rt>かん</rt><rp>(</rp></ruby>" to "漢かん(\n",
            "<a id='n'></a>正文" to "正文\n", "<pre>A  B</pre>" to "A  B\n", "A<br><br>B" to "A\nB\n",
        )) {
            val current = source(html)
            assertEquals(html, expected, current.semanticContent)
            assertTrue(html, ReaderSemanticContract.validate(current).isEmpty())
        }
    }

    @Test fun visualWrappersAndAttributesPreserveSemanticAndAnchors() {
        for (html in listOf("<p>A&amp;<a id='n'>😀B</a></p>",
            "<p style='text-align:center;color:red'><span><strong>A&amp;</strong><a class='x' id='n'><em>😀B</em></a></span></p>")) {
            val current = source(html)
            assertEquals("A&😀B\n", current.semanticContent)
            assertEquals(2, current.anchorPositions["n"])
        }
    }

    @Test fun appearanceAndCssCannotChangeCanonicalTextParagraphsOrAnchors() {
        val fixture = io.legado.app.support.ReaderSemanticFixtures
        val doc = org.jsoup.Jsoup.parse(fixture.html)
        io.legado.app.model.localBook.LocalBookSemanticHtml.inlineDocumentCssSemantics(doc) { fixture.css }
        val current = source(doc.body().outerHtml())
        assertEquals(fixture.body, current.semanticContent)
        assertEquals(fixture.paragraphs.indices.toList(), current.paragraphs.map { it.ordinal })
        assertEquals(fixture.body.indexOf("乙"), current.anchorPositions["note"])
        assertTrue(texts(current).any { it.style.italic == true })
        assertTrue(texts(current).any { it.style.fontWeight == 700 })
        assertTrue(ReaderSemanticContract.validate(current).isEmpty())
    }

    @Test fun knownEditsAndRoleLabelsPreserveUnaffectedAppearance() {
        val html = "<p id='n'><b>“甲”</b></p><p><a href='#n'>乙</a></p>"
        val appearance = ReaderLocalPresentationResolver.resolve(html)!!
        val offset = appearance.content.indexOf("甲")
        val map = TextProjection(appearance.content.length)
        map.replace(offset, offset + 1, 2)
        val changed = appearance.content.replaceRange(offset, offset + 1, "替换")
        val mapped = appearance.remapOrNull(map, changed)!!
        val edited = ReaderChapterSourceParser.parse(0, "", MixedContentSegments.paragraphs(changed, "", false), false, true, localPresentation = mapped)
        assertEquals(700, texts(edited).first { it.value.contains("替换") }.style.fontWeight)
        val current = source(html, mapOf(0 to "<<甲（池）>>"))
        assertEquals("“<<甲（池）>>甲”\n乙\n", current.semanticContent)
        assertEquals(700, texts(current).first { it.value.contains("甲”") }.style.fontWeight)
        assertEquals("#n", texts(current).single { it.value == "乙" }.style.link)
        assertEquals(0, current.anchorPositions["n"])
        assertNull(appearance.bind("unrelated text"))
        // A copied group followed by an ambiguous replacement must not acquire its neighbour's link.
        val mixed = ReaderLocalPresentationResolver.resolve("<b>甲</b><a href='#n'>乙丙</a>")!!
        assertEquals("甲乙丙", mixed.content)
        val composed = TextProjection(mixed.content.length)
        composed.rebuild(listOf(TextProjection.Piece(1, 2), TextProjection.Piece(0, 3, 1), TextProjection.Piece(2, 3)))
        composed.replace(0, 2, 1)
        val result = mixed.remapOrNull(composed, "替丙")!!
        assertTrue(result.spans.none { it.start == 0 })
        assertEquals("#n", result.spans.single().style.link)
    }
}
