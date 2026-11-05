package io.legado.app.model.localBook

import io.legado.app.data.entities.BookChapter
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.platform.AndroidReaderHtmlSemanticTextResolver
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookSemanticHtmlTest {
    @Test
    fun canonicalImagesKeepLegacySrcWithoutLeakingAppearanceAttributesIntoText() {
        val doc = Jsoup.parseBodyFragment("<p>A<img alt='x > y' src='pic.jpg' width='20'>B</p>")
        LocalBookSemanticHtml.sanitizeImages(doc.select("img"), preserveAppearance = false)
        val plain = LocalBookSemanticHtml.plainText(doc.body().outerHtml())
        assertEquals("A<img src=\"pic.jpg\">B", plain.trim { it.isWhitespace() || it == '　' })
        val source = ReaderChapterSourceParser.parse(0, "", listOf(plain.trim()), false, false)
        assertEquals(1, source.blocks.filterIsInstance<ReaderChapterSourceBlock.Paragraph>()
            .flatMap { it.items }.filterIsInstance<io.legado.app.feature.reader.core.source.ReaderChapterInlineSource.Image>().size)
        assertTrue(!source.semanticContent.contains("x > y"))
    }
    @Test
    fun encodedTocFragmentsSelectAndCutTheCorrectChapter() = runBlocking {
        val chapters = listOf(
            BookChapter(index = 0, bookUrl = "b", title = "first", url = "Text/book.xhtml#first"),
            BookChapter(index = 1, bookUrl = "b", title = "next", url = "Text/book.xhtml#page%2013"),
        )
        assertEquals(1 to 0, LocalBookSemanticHtml.resolveInternalLinkTarget("#page%2013", chapters, 0))
        val body = Jsoup.parseBodyFragment("<p id='first'>one</p><p id='page 13'>two</p><p id='last'>three</p>").body()
        LocalBookSemanticHtml.cutChapterBody(body, "page%2013", "last")
        assertEquals("two", body.text())
    }

    @Test
    fun readerLinkRoutingUsesTheBooksOriginAndChapterBase() {
        assertEquals("https://example.test/book/next.html", LocalBookSemanticHtml.resolveReaderLink(
            "next.html", false, "https://example.test/book/current.html"))
        assertEquals("https://example.test/next", LocalBookSemanticHtml.resolveReaderLink(
            "/next", false, "https://example.test/book/current.html"))
        assertEquals("https://example.test/book/next%20chapter.html", LocalBookSemanticHtml.resolveReaderLink(
            "next chapter.html", false, "https://example.test/book/current.html, {\"method\":\"POST\"}"))
        assertEquals("https://example.test/book/current.php?next=2", LocalBookSemanticHtml.resolveReaderLink(
            "?next=2", false, "https://example.test/book/current.php?id=1#old"))
        assertEquals("https://example.test/book/current.php?next=2", LocalBookSemanticHtml.resolveReaderLink(
            "?next=2", false, "https://example.test/book/current.php  , {\"method\":\"POST\"}"))
        assertEquals("https://example.test/book/current.html#note", LocalBookSemanticHtml.resolveReaderLink(
            "#note", false, "https://example.test/book/current.html"))
        assertEquals("next.html", LocalBookSemanticHtml.resolveReaderLink("next.html", true, "Text/current.html"))
        assertEquals(null, LocalBookSemanticHtml.resolveReaderLink("javascript:evil()", false, "https://example.test"))
    }

    @Test
    fun classifiesLinksBeforeResolvingBookResources() = runBlocking {
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
    fun chapterCuttingUsesHiddenBoundariesBeforeFiltering() {
        for (anchor in listOf("<h2 id='two'>Second heading</h2>",
            "<h2 id='two' style='display:none'>Second heading</h2>",
            "<a id='two' style='display:none'></a>")) {
            val body = Jsoup.parseBodyFragment("<p>First</p>$anchor<p>Second</p><p id='last'>Third</p>").body()
            LocalBookSemanticHtml.inlineCssSemantics(body,
                LocalBookSemanticHtml.parseCssRules("h2, a {font-weight:bold}"))
            LocalBookSemanticHtml.cutChapterBody(body, "two", "last")
            assertTrue(body.text().contains("Second"))
            assertTrue(!body.text().contains("First") && !body.text().contains("Third"))
            assertTrue(body.select("[style*=display:none]").isEmpty())
        }
    }

    @Test
    fun cutFragmentHandlesEmptyAnchor() {
        val body = Jsoup.parse("<body><p>one<a id=\"next\"></a>two</p><p>three</p></body>").body()
        LocalBookSemanticHtml.cutChapterBody(body, null, "next")
        // Text before the empty anchor stays in the previous chapter; trailing siblings are dropped.
        assertEquals("one", body.text().trim())
    }

    @Test
    fun cutFragmentRemovesEmptyWrapperWhenEndAnchorStartsNextHeading() {
        val body = Jsoup.parse(
            "<body><p>one</p><div class=\"ch\"><h2><a id=\"next\"></a>Chapter 2</h2><p>two</p></div></body>"
        ).body()
        LocalBookSemanticHtml.cutChapterBody(body, null, "next")
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
            "p { text-align: center; color: red; } .note { font-weight: bold; } #t { font-style: italic; display: none; margin: 30px; }"
        )
        assertEquals(3, rules.size)
        val body = Jsoup.parseBodyFragment("<p class=\"note\">hi</p><p id=\"t\">gone</p>").body()
        LocalBookSemanticHtml.inlineCssSemantics(body, rules)
        val paragraphs = body.select("p")
        assertEquals(2, paragraphs.size)
        val first = paragraphs.first()!!
        assertTrue(first.attr("style").contains("text-align: center"))
        assertTrue(first.attr("style").contains("font-weight: bold"))
        assertEquals("text-align: center; font-style: italic", paragraphs.last()!!.attr("style"))
    }

    @Test
    fun resolvesRelativeEpubHrefsAndMatchesTargetChapters() = runBlocking {
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
        assertEquals(2 to 0, LocalBookSemanticHtml.resolveInternalLinkTarget("OEBPS/Text/part01.xhtml#ch2", chapters, 0))
        assertEquals(2 to 0, LocalBookSemanticHtml.resolveInternalLinkTarget("part01.xhtml#ch2", chapters, 0))
        assertEquals(3 to 0, LocalBookSemanticHtml.resolveInternalLinkTarget("filepos:0000001000", chapters, 0))
        for ((positions, expected) in listOf(
            mapOf(2 to mapOf("note" to 17)) to (2 to 17),
            emptyMap<Int, Map<String, Int>>() to null,
            mapOf(1 to mapOf("note" to 3), 2 to mapOf("note" to 17)) to null,
        )) {
            assertEquals(expected, LocalBookSemanticHtml.resolveInternalLinkTarget(
                "part01.xhtml#note", chapters, 1) { positions[it.index].orEmpty() })
        }
    }

    @Test
    fun presentationImagesKeepWhitelistedAttributesAndDropHandlers() {
        val img = Jsoup.parseBodyFragment(
            "<img src='pic.jpg' alt='cover' width='20' height='30' class='big' " +
                "style='border:0' onerror='boom()' recindex='7'>"
        ).selectFirst("img")!!
        LocalBookSemanticHtml.sanitizeImage(img, preserveAppearance = true)
        assertEquals("pic.jpg", img.attr("src"))
        assertEquals("cover", img.attr("alt"))
        assertEquals("20", img.attr("width"))
        assertEquals("big", img.attr("class"))
        assertEquals("border:0", img.attr("style"))
        assertEquals("", img.attr("onerror"))
        assertEquals("", img.attr("recindex"))
    }

    @Test
    fun legacyAlignmentOnlyRewritesBlockContainers() {
        val doc = Jsoup.parseBodyFragment(
            "<p align='center'>text</p><img src='pic.jpg' align='left'><table align='center'><tr><td>x</td></tr></table>"
        )
        LocalBookSemanticHtml.inlineCssSemantics(doc.body(), emptyList())
        val p = doc.selectFirst("p")!!
        assertEquals("text-align: center", p.attr("style"))
        assertEquals("", p.attr("align"))
        // Image/table alignment is not a text-align instruction: keep the legacy attribute as is.
        assertEquals("left", doc.selectFirst("img")!!.attr("align"))
        assertEquals("", doc.selectFirst("img")!!.attr("style"))
        assertEquals("center", doc.selectFirst("table")!!.attr("align"))
    }

    @Test
    fun relativeHrefKeepsLiteralPlusInResourceNames() {
        assertEquals(
            "OEBPS/Text/a+b.xhtml",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/current.xhtml", "a+b.xhtml"),
        )
        // Already-escaped hrefs keep one level of escaping, exactly like the previous decoder.
        assertEquals(
            "OEBPS/Text/a%20b.xhtml",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/current.xhtml", "a%20b.xhtml"),
        )
        assertEquals(
            "OEBPS/Text/a b.xhtml",
            LocalBookSemanticHtml.resolveRelativeHref("OEBPS/Text/current.xhtml", "a b.xhtml"),
        )
    }

    @Test
    fun inlineCssUsesIndexedCandidatesButKeepsSpecificityAndOrder() {
        val rules = LocalBookSemanticHtml.parseCssRules(
            """
            p { font-weight: normal; }
            .a { font-style: italic; }
            #main { text-align: center; }
            p.a { font-weight: bold; }
            .a.b { text-decoration: underline; }
            """.trimIndent()
        )
        val doc = Jsoup.parseBodyFragment("<p id='main' class='a b'>x</p><p class='b'>y</p>")
        LocalBookSemanticHtml.inlineCssSemantics(doc.body(), rules)
        val first = doc.select("p")[0].attr("style")
        // `p.a` (11) must override `p` (1) for the same property.
        assertTrue(first.contains("font-weight: bold"))
        assertTrue(first.contains("font-style: italic"))
        assertTrue(first.contains("text-align: center"))
        assertTrue(first.contains("text-decoration: underline"))
        // `.a.b` must not leak into an element that only carries `b`.
        val second = doc.select("p")[1].attr("style")
        assertEquals("font-weight: normal", second)
    }

    @Test
    fun cssRuleBudgetStopsParsingEarly() {
        val rules = LocalBookSemanticHtml.parseCssRules(
            (1..50).joinToString("\n") { ".c$it { font-weight: normal; }" },
            maxRules = 5,
        )
        assertEquals(5, rules.size)
    }
}
