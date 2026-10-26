package io.legado.app.ui.book.explore

import io.legado.app.ui.main.MainRouteExploreShow
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreShowPageParamTest {

    @Test
    fun legacySavedRoutesKeepSourceRulePagination() {
        val route = Json.decodeFromString<MainRouteExploreShow>(
            """{"title":"List","sourceUrl":"source","exploreUrl":"https://example.com/list"}""",
        )
        assertFalse(route.usePageParamFallback)
    }

    @Test
    fun tagRoutesKeepTheirPaginationModeAfterRestoration() {
        val route = MainRouteExploreShow("Tag", "source", "/tag", usePageParamFallback = true)
        assertEquals(route, Json.decodeFromString<MainRouteExploreShow>(Json.encodeToString(route)))
    }

    // withPageParam

    @Test
    fun appendsParamOnSecondPageForPlainUrl() {
        assertEquals(
            "https://example.com/dm22?page=2",
            "https://example.com/dm22".withPageParam(2, "page")
        )
    }

    @Test
    fun keepsFirstPageUntouched() {
        assertEquals(
            "https://example.com/dm22",
            "https://example.com/dm22".withPageParam(1, "page")
        )
    }

    @Test
    fun replacesExistingParamFromSecondPage() {
        assertEquals(
            "https://example.com/list?page=3&sort=hot",
            "https://example.com/list?page=5&sort=hot".withPageParam(3, "page")
        )
    }

    @Test
    fun keepsFragmentAtEnd() {
        assertEquals(
            "https://example.com/tag?page=2#frag",
            "https://example.com/tag#frag".withPageParam(2, "page")
        )
    }

    @Test
    fun customParamDoesNotTouchOtherParam() {
        assertEquals(
            "https://example.com/list?page=3&from=2",
            "https://example.com/list?page=3".withPageParam(2, "from")
        )
        assertEquals(
            "https://example.com/list?page=3&from=4",
            "https://example.com/list?page=3&from=9".withPageParam(4, "from")
        )
    }

    @Test
    fun skipsEmbeddedRuleUrl() {
        val url = "https://example.com/list-{{page}}.html"
        assertEquals(url, url.withPageParam(2, "page"))
    }

    @Test
    fun skipsPageRangeRuleUrl() {
        val url = "https://example.com/list-<1,9,99>.html"
        assertEquals(url, url.withPageParam(2, "page"))
    }

    @Test
    fun skipsUrlWithOptions() {
        val url = """https://example.com/list,{"method":"POST","body":"type=1&page=2"}"""
        assertEquals(url, url.withPageParam(3, "page"))
    }

    @Test
    fun skipsJavascriptRules() {
        val url = "@js: 'https://example.com/list?page=' + page"
        assertFalse(url.supportsParamPagination())
        assertEquals(url, url.withPageParam(2, "page"))
    }

    @Test
    fun skipsOptionsWithWhitespaceAfterComma() {
        val url = """https://example.com/list, {"method":"POST","body":"type=1"}"""
        assertFalse(url.supportsParamPagination())
        assertEquals(url, url.withPageParam(2, "page"))
    }

    @Test
    fun skipsOptionsWithNewlineAfterComma() {
        val url = "https://example.com/list,\n{\"headers\":{\"Referer\":\"https://example.com\"}}"
        assertEquals(url, url.withPageParam(2, "page"))
    }

    @Test
    fun keepsNullAndBlankAsIs() {
        assertNull(null.withPageParam(2, "page"))
        assertEquals("", "".withPageParam(2, "page"))
    }

    // supportsParamPagination

    @Test
    fun plainUrlSupportsParamPagination() {
        assertTrue("https://example.com/dm22".supportsParamPagination())
        assertTrue("https://example.com/dm22?page=1".supportsParamPagination())
    }

    @Test
    fun ruleSyntaxDoesNotSupportParamPagination() {
        assertFalse("https://example.com/{{page}}.html".supportsParamPagination())
        assertFalse("https://example.com/<1,9>.html".supportsParamPagination())
        assertFalse("""https://example.com/,{"charset":"gbk"}""".supportsParamPagination())
        assertFalse("<js>page</js>".supportsParamPagination())
    }

    @Test
    fun nullAndBlankDoNotSupportParamPagination() {
        assertFalse(null.supportsParamPagination())
        assertFalse("".supportsParamPagination())
    }
}
