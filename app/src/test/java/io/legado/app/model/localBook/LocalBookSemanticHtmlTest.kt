package io.legado.app.model.localBook

import io.legado.app.data.entities.BookChapter
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import io.legado.app.feature.reader.platform.JsoupReaderHtmlParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookSemanticHtmlTest {

    @Test
    fun classifiesLinksBeforeResolvingBookResources() {
        for (link in listOf("chapter.xhtml#n", "#n", "filepos:000123", "kindle:pos:fid:1:off:0")) {
            assertEquals(link, LocalBookSemanticHtml.LinkKind.INTERNAL, LocalBookSemanticHtml.classifyLink(link))
        }
        for (link in listOf("https://example.com", "//example.com/a", "mailto:a@b", "tel:123")) {
            assertTrue(link, LocalBookSemanticHtml.isExternalLink(link))
            assertEquals(link, LocalBookSemanticHtml.resolveRelativeHref("Text/book.xhtml", link))
        }
        for (link in listOf("javascript:alert(1)", "data:text/html,x", "unknown:thing", "filepos:", "kindle:pos:fid:1", "", "java\nscript:alert(1)")) {
            assertEquals(link, LocalBookSemanticHtml.LinkKind.UNSUPPORTED, LocalBookSemanticHtml.classifyLink(link))
            assertEquals(null, LocalBookSemanticHtml.resolveRelativeHref("Text/book.xhtml", link))
        }
        val chapters = listOf(BookChapter(index = 0, url = "example.com/a", bookUrl = "b", title = "A"))
        assertEquals(null, LocalBookSemanticHtml.resolveInternalLinkTarget("//example.com/a", chapters, 0))
        assertEquals(null, LocalBookSemanticHtml.resolveInternalLinkTarget("javascript:alert(1)", chapters, 0))
    }

    @Test
    fun documentCssUsesOnlyReferencedSheetsInDocumentOrder() {
        val styles = mapOf(
            "z.css" to "p { font-weight: bold; }",
            "a.css" to "p { font-weight: normal; }",
            "other-chapter.css" to "p { display: none; }",
        )
        val document = Jsoup.parse(
            "<head><link rel='stylesheet' href='z.css'><link rel='stylesheet' href='a.css'></head><body><p>Body</p></body>"
        )
        val loaded = mutableListOf<String>()
        LocalBookSemanticHtml.inlineDocumentCssSemantics(document) { href ->
            loaded += href
            styles[href]
        }
        assertEquals(listOf("z.css", "a.css"), loaded)
        assertEquals("Body", document.body().text())
        assertEquals("font-weight: normal", document.selectFirst("p")!!.attr("style"))
    }

    @Test
    fun embeddedCssAndLinkedCssShareDocumentOrderWithoutOverridingInlineStyle() {
        val document = Jsoup.parse(
            "<head><style>p {font-style: italic}</style><link rel='stylesheet' href='book.css'>" +
                "<style>p {font-weight: bold}</style></head>" +
                "<body><p style='font-weight: normal'>Body</p></body>"
        )
        LocalBookSemanticHtml.inlineDocumentCssSemantics(document) {
            "p {font-style: normal; font-weight: normal}"
        }
        val style = document.selectFirst("p")!!.attr("style")
        assertTrue(style.contains("font-style: normal"))
        assertTrue(style.contains("font-weight: normal"))
    }

    @Test
    fun mixedChannelPreservesPreformattedLineBreaksOnOneTransportLine() {
        val body = Jsoup.parseBodyFragment("<pre>first\n  second\r\nthird</pre>").body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body)
        assertEquals(1, channel.lines().size)
        assertEquals("first\n  second\nthird\n", JsoupReaderHtmlParser.semanticText(
            channel.removePrefix("<usehtml>").removeSuffix("</usehtml>")
        ))
    }

    @Test
    fun cutFragmentHandlesSingleLineCompressedHtml() {
        val body = Jsoup.parse("<body><p>one</p><p id=\"c2\">two</p><p>three</p></body>").body()
        LocalBookSemanticHtml.cutFragment(body, "c2", null)
        assertEquals("two three", body.text().trim().replace("\\s+".toRegex(), " "))
    }

    @Test
    fun cutFragmentHandlesEmptyAnchor() {
        val body = Jsoup.parse("<body><p>one<a id=\"next\"></a>two</p><p>three</p></body>").body()
        LocalBookSemanticHtml.cutFragment(body, null, "next")
        // Text before the empty anchor stays in the previous chapter; trailing siblings are dropped.
        assertEquals("one", body.text().trim())
    }

    @Test
    fun cutFragmentRemovesEmptyWrapperWhenEndAnchorStartsNextHeading() {
        val body = Jsoup.parse(
            "<body><p>one</p><div class=\"ch\"><h2><a id=\"next\"></a>Chapter 2</h2><p>two</p></div></body>"
        ).body()
        LocalBookSemanticHtml.cutFragment(body, null, "next")
        assertEquals("one", body.text().trim())
        assertTrue(body.select("div, h2").isEmpty())
    }

    @Test
    fun sanitizeImagesKeepsSemanticAttributes() {
        val doc = Jsoup.parseBodyFragment(
            "<img src=\"a.jpg\" width=\"10\" height=\"20\" class=\"c\" style=\"x\" onclick=\"evil()\" data-x=\"1\">"
        )
        LocalBookSemanticHtml.sanitizeImages(doc.select("img"))
        val img = doc.select("img").first()!!
        assertEquals("a.jpg", img.attr("src"))
        assertEquals("10", img.attr("width"))
        assertEquals("", img.attr("onclick"))
        assertEquals("", img.attr("data-x"))
    }

    @Test
    fun parseCssRulesSupportsTagClassAndId() {
        val rules = LocalBookSemanticHtml.parseCssRules(
            "p { text-align: center; color: red; } .note { font-weight: bold; } #t { display: none; }"
        )
        assertEquals(3, rules.size)
        val body = Jsoup.parseBodyFragment("<p class=\"note\">hi</p><p id=\"t\">gone</p>").body()
        LocalBookSemanticHtml.inlineCssSemantics(body, rules)
        val paragraphs = body.select("p")
        assertEquals(1, paragraphs.size)
        val first = paragraphs.first()!!
        assertTrue(first.attr("style").contains("text-align: center"))
        assertTrue(first.attr("style").contains("font-weight: bold"))
    }

    @Test
    fun emitMixedChannelUnwrapsContainerDivAndPreservesInlineSemantics() {
        val body = Jsoup.parseBodyFragment(
            "<div class=\"chapter\"><p>plain paragraph</p><p><ruby>漢<rt>かん</rt></ruby>字</p><blockquote>quote</blockquote></div>"
        ).body()
        val lines = LocalBookSemanticHtml.emitMixedChannel(body, null).split("\n")
        assertEquals("plain paragraph", lines[0])
        assertTrue(lines[1].startsWith("<usehtml>") && lines[1].contains("<ruby>"))
        assertTrue(lines[2].startsWith("<usehtml>") && lines[2].contains("<blockquote>"))
    }

    @Test
    fun emitMixedChannelSeparatesPlainAndSemanticBlocks() {
        val body = Jsoup.parseBodyFragment("<p>plain</p><blockquote>quote</blockquote>").body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, null)
        assertTrue(channel.contains("plain"))
        assertTrue(channel.contains("<usehtml>"))
        assertTrue(channel.contains("quote"))
    }

    @Test
    fun dropsSingleBlockTitleRepeatingChapterTitle() {
        val body = Jsoup.parseBodyFragment("<h1>Chapter One</h1><p>Story begins.</p>").body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "Chapter One")
        assertTrue(channel.contains("Story begins."))
        assertTrue(!channel.contains("Chapter One"))
    }

    @Test
    fun dropsTwoBlockTitleSplitAcrossHeadingLines() {
        val body = Jsoup.parseBodyFragment(
            "<h1>CHAPTER I.</h1><h2>The Period</h2><p>It was the best of times.</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "CHAPTER I. The Period")
        assertTrue(channel.contains("best of times"))
        assertTrue(!channel.contains("CHAPTER I."))
        assertTrue(!channel.contains("The Period"))
    }

    @Test
    fun dropsHeadingWithLineBreakInsideSingleBlock() {
        // Exact markup shape from the Dickens sample: one h2, title split by <br/>.
        val body = Jsoup.parseBodyFragment(
            "<div class=\"chapter\"><h2><a id=\"link2H_4_0002\"/>CHAPTER I.<br/>The Period</h2></div>" +
                "<p>It was the best of times.</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "CHAPTER I. The Period")
        assertTrue(channel.contains("best of times"))
        assertTrue(!channel.contains("CHAPTER I."))
        assertTrue(!channel.contains("The Period"))
    }

    @Test
    fun dropsHeadingSharingDivWithStoryText() {
        // Exact Dickens shape: one h2 with a line break, sharing its section
        // div with the story paragraph that follows it.
        val body = Jsoup.parseBodyFragment(
            "<div class=\"chapter\"><h2><a id=\"link2H_4_0002\"/>CHAPTER I.<br/>The Period</h2>" +
                "<p>It was the best of times.</p></div>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "CHAPTER I. The Period")
        assertTrue(channel.contains("best of times"))
        assertTrue(!channel.contains("CHAPTER I."))
        assertTrue(!channel.contains("The Period"))
    }

    @Test
    fun keepsInlineWordSplitAcrossElements() {
        val body = Jsoup.parseBodyFragment(
            "<p>The <span>Period</span>icals go on</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "The Period")
        assertTrue(channel.contains("Period"))
        assertTrue(channel.contains("icals"))
    }

    @Test
    fun keepsTitleAfterLeadingImage() {
        val body = Jsoup.parseBodyFragment(
            "<div><img src=\"x.png\"/><h1>Title</h1></div><p>Body</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "Title")
        assertTrue(channel.contains("Title"))
        assertTrue(channel.contains("Body"))
    }

    @Test
    fun dropsTitleSplitAcrossThreeHeadingLines() {
        val body = Jsoup.parseBodyFragment(
            "<h1>Book</h1><h2>the</h2><h3>First</h3><p>Once upon a time.</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "Book the First")
        assertTrue(channel.contains("Once upon a time."))
        assertTrue(!channel.contains(">Book<"))
        assertTrue(!channel.contains(">First<"))
    }

    @Test
    fun dropsTitleOnlyBody() {
        val body = Jsoup.parseBodyFragment("<h1>Lone Title</h1>").body()
        assertEquals("", LocalBookSemanticHtml.emitMixedChannel(body, "Lone Title"))
    }

    @Test
    fun keepsBlocksDivergingFromTitlePrefix() {
        val body = Jsoup.parseBodyFragment(
            "<h1>CHAPTER I.</h1><p>Something else entirely.</p>"
        ).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "CHAPTER I. The Period")
        assertTrue(channel.contains("CHAPTER I."))
        assertTrue(channel.contains("Something else entirely."))
    }

    @Test
    fun keepsGenuinelyDifferentSubheading() {
        val body = Jsoup.parseBodyFragment("<h1>Prologue</h1><p>Story begins.</p>").body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, "Chapter One")
        assertTrue(channel.contains("Prologue"))
        assertTrue(channel.contains("Story begins."))
    }

    @Test
    fun emptyBodyEmitsBlankChannelForTitlePageFallback() {
        val body = Jsoup.parseBodyFragment("").body()
        assertEquals("", LocalBookSemanticHtml.emitMixedChannel(body, "Book the First"))
    }

    @Test
    fun resolvesRelativeEpubHrefsAndMatchesTargetChapters() {
        assertEquals(
            "OEBPS/Text/ch02.xhtml#sec1",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/toc.xhtml", "ch02.xhtml#sec1")
        )
        assertEquals(
            "OEBPS/Text/ch01.xhtml#fn1",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/ch01.xhtml", "#fn1")
        )
        assertEquals(
            "https://example.com",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/ch01.xhtml", "https://example.com")
        )

        val chapters = listOf(
            BookChapter(url = "OEBPS/Text/toc.xhtml", title = "TOC", index = 0),
            BookChapter(url = "OEBPS/Text/part01.xhtml#ch1", title = "Chapter 1", index = 1, startFragmentId = "ch1"),
            BookChapter(url = "OEBPS/Text/part01.xhtml#ch2", title = "Chapter 2", index = 2, startFragmentId = "ch2"),
            BookChapter(url = "3:filepos:0000001000", title = "Mobi Ch", index = 3)
        )
        assertEquals(2, LocalBookSemanticHtml.resolveInternalLinkTarget("OEBPS/Text/part01.xhtml#ch2", chapters, 0))
        assertEquals(2, LocalBookSemanticHtml.resolveInternalLinkTarget("part01.xhtml#ch2", chapters, 0))
        assertEquals(3, LocalBookSemanticHtml.resolveInternalLinkTarget("filepos:0000001000", chapters, 0))
    }

    @Test
    fun resolvesBidirectionalFootnoteAndEmptyPageAnchorsThroughMixedChannel() {
        val rawHtml = """
            <body>
              <p><span>First paragraph with a footnote call.</span><sup><a href="#note_6" id="ref_6">6</a></sup><span> Following sentence with an inline <a id="page_13"/>page marker.</span></p>
              <p>Plain middle paragraph.</p>
              <p><span><strong><a href="#ref_6" id="note_6">6</a></strong></span><span> Endnote explanation text at the end of the chapter.</span></p>
            </body>
        """.trimIndent()
        val body = Jsoup.parse(rawHtml).body()
        val channel = LocalBookSemanticHtml.emitMixedChannel(body, null)
        val source = ReaderChapterSourceParser.parse(
            chapterIndex = 0,
            title = "",
            paragraphs = channel.split("\n").filter { it.isNotBlank() },
            includeTitle = false,
            adaptSpecialStyle = true,
            htmlSemanticTextResolver = AndroidReaderHtmlSemanticTextResolver,
        )
        fun anchorPos(id: String): Int? =
            source.blocks.filterIsInstance<ReaderChapterSourceBlock.Html>()
                .firstNotNullOfOrNull { block ->
                    JsoupReaderHtmlParser.findAnchorPosition(block.value, id, block.chapterPosition)
                }

        val refPos = anchorPos("ref_6")
        val notePos = anchorPos("note_6")
        val pagePos = anchorPos("page_13")
        assertNotNull(refPos)
        assertNotNull(notePos)
        assertNotNull(pagePos)
        assertTrue(notePos!! > refPos!!)
        assertTrue(source.semanticContent.substring(refPos).startsWith("6 Following"))
        assertTrue(source.semanticContent.substring(notePos).startsWith("6 Endnote"))
        assertTrue(source.semanticContent.substring(pagePos!!).startsWith("page marker."))
    }
}
