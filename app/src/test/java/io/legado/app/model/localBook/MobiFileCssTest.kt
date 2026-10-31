package io.legado.app.model.localBook

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MobiFileCssTest {
    @Test
    fun linkedAndEmbeddedRulesFollowDocumentOrder() {
        val embedded = "<style>p { font-style: italic }</style>"
        val linked = "<link rel='stylesheet' href='kindle:flow:0001?mime=text/css'>"
        for ((sheets, expected) in listOf(embedded + linked to "normal", linked + embedded to "italic")) {
            val document = Jsoup.parse("<head>$sheets</head><body><p>Body</p></body>")
            val loaded = mutableListOf<String>()
            val channel = MobiFile.format(document) { href ->
                loaded += href
                "p { font-style: normal }"
            }
            assertEquals(listOf("kindle:flow:0001?mime=text/css"), loaded)
            val paragraph = Jsoup.parseBodyFragment(channel).selectFirst("p")!!
            assertTrue(paragraph.attr("style").contains("font-style: $expected"))
        }
    }

    @Test
    fun displayCascadeDeterminesWhichTextReachesTheReader() {
        val hidden = "<style>p { display: none }</style>"
        val linked = "<link rel='stylesheet' href='kindle:flow:0001?mime=text/css'>"
        val visible = MobiFile.format(Jsoup.parse("<head>$hidden$linked</head><body><p>Body</p></body>")) {
            "p { display: block }"
        }
        val invisible = MobiFile.format(Jsoup.parse("<head>$linked$hidden</head><body><p>Body</p></body>")) {
            "p { display: block }"
        }
        assertEquals("Body", Jsoup.parseBodyFragment(visible).text())
        assertEquals("", invisible)
    }

    @Test
    fun embeddedOnlyFormattingPreservesElementStylePrecedence() {
        val document = Jsoup.parse(
            "<head><style>p {font-weight: bold; font-style: italic}</style></head>" +
                "<body><p style='font-weight: normal'>Body</p></body>"
        )
        val paragraph = Jsoup.parseBodyFragment(MobiFile.format(document)).selectFirst("p")!!
        assertTrue(paragraph.attr("style").contains("font-weight: normal"))
        assertTrue(paragraph.attr("style").contains("font-style: italic"))
    }

    @Test
    fun mobiKeepsItsExistingRuleBudget() {
        val rules = (0..2000).joinToString("") { ".unused$it {font-weight: normal}" }
        val document = Jsoup.parse(
            "<head><style>$rules</style><style>p {font-style: italic}</style></head><body><p>Body</p></body>"
        )
        val paragraph = Jsoup.parseBodyFragment(MobiFile.format(document)).selectFirst("p")!!
        assertTrue(paragraph.attr("style").contains("font-style: italic"))
    }
}
