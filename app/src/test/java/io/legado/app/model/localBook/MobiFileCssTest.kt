package io.legado.app.model.localBook

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiFileCssTest {
    @Test
    fun canonicalMobiTextRetainsTheLegacyWholeDocumentProjection() {
        val doc = Jsoup.parse("<head><style>p { font-style: italic }</style></head><body><p>Body</p></body>")
        val canonical = MobiFile.format(doc)
        assertEquals(listOf("p { font-style: italic }", "Body"), canonical.lines().map { it.trim() }.filter { it.isNotEmpty() })
        val html = MobiFile.presentationHtml(doc)
        val appearance = io.legado.app.feature.reader.platform.ReaderLocalPresentationResolver.resolve(html, decodeEntities = false)!!
        assertEquals(canonical, appearance.content)
        assertTrue(appearance.spans.any { it.style.italic == true &&
            appearance.content.substring(it.start, it.endExclusive).contains("Body") })
    }
    @Test
    fun linkedAndEmbeddedRulesFollowDocumentOrder() {
        val embedded = "<style>p { font-style: italic }</style>"
        val linked = "<link rel='stylesheet' href='kindle:flow:0001?mime=text/css'>"
        for ((sheets, expected) in listOf(embedded + linked to "normal", linked + embedded to "italic")) {
            val document = Jsoup.parse("<head>$sheets</head><body><p>Body</p></body>")
            val loaded = mutableListOf<String>()
            val channel = MobiFile.presentationHtml(document) { href ->
                loaded += href
                "p { font-style: normal }"
            }
            assertEquals(listOf("kindle:flow:0001?mime=text/css"), loaded)
            val paragraph = Jsoup.parseBodyFragment(channel).selectFirst("p")!!
            assertTrue(paragraph.attr("style").contains("font-style: $expected"))
        }
    }

    @Test
    fun displayCascadeNeverRemovesCanonicalText() {
        val hidden = "<style>p { display: none }</style>"
        val linked = "<link rel='stylesheet' href='kindle:flow:0001?mime=text/css'>"
        val visible = MobiFile.presentationHtml(Jsoup.parse("<head>$hidden$linked</head><body><p>Body</p></body>")) {
            "p { display: block }"
        }
        val invisible = MobiFile.presentationHtml(Jsoup.parse("<head>$linked$hidden</head><body><p>Body</p></body>")) {
            "p { display: block }"
        }
        assertEquals("Body", Jsoup.parseBodyFragment(visible).text())
        assertEquals("Body", Jsoup.parseBodyFragment(invisible).text())
    }

    @Test
    fun embeddedOnlyFormattingPreservesElementStylePrecedence() {
        val document = Jsoup.parse(
            "<head><style>p {font-weight: bold; font-style: italic}</style></head>" +
                "<body><p style='font-weight: normal'>Body</p></body>"
        )
        val paragraph = Jsoup.parseBodyFragment(MobiFile.presentationHtml(document)).selectFirst("p")!!
        assertTrue(paragraph.attr("style").contains("font-weight: normal"))
        assertTrue(paragraph.attr("style").contains("font-style: italic"))
    }

    @Test
    fun mobiKeepsItsExistingRuleBudget() {
        val rules = (0..2000).joinToString("") { ".unused$it {font-weight: normal}" }
        val document = Jsoup.parse(
            "<head><style>$rules</style><style>p {font-style: italic}</style></head><body><p>Body</p></body>"
        )
        val paragraph = Jsoup.parseBodyFragment(MobiFile.presentationHtml(document)).selectFirst("p")!!
        assertTrue(paragraph.attr("style").contains("font-style: italic"))
    }
}
