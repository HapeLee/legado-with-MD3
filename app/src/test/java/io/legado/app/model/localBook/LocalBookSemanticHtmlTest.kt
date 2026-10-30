package io.legado.app.model.localBook

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookSemanticHtmlTest {

    @Test
    fun cutFragmentHandlesSingleLineCompressedHtml() {
        val body = Jsoup.parse("<body><p>one</p><p id=\"c2\">two</p><p>three</p></body>").body()
        LocalBookSemanticHtml.cutFragment(body, "c2", null)
        assertEquals("two three", body.text().trim().replace("\\s+".toRegex(), " "))
    }

    @Test
    fun cutFragmentHandlesEmptyAnchor() {
        val body = Jsoup.parse("<body><p>one<a id=\"next\"></a>two</p></body>").body()
        LocalBookSemanticHtml.cutFragment(body, null, "next")
        // Text before the empty anchor stays in the previous chapter.
        assertTrue(body.text().contains("one"))
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
        val first = body.select("p").first()!!
        assertTrue(first.attr("style").contains("text-align: center"))
        assertTrue(first.attr("style").contains("font-weight: bold"))
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
}
