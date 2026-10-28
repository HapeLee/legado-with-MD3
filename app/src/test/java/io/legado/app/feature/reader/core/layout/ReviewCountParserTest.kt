package io.legado.app.feature.reader.core.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ReviewCountParserTest {
    private fun dataUri(svg: String): String =
        "data:image/svg+xml;base64," +
            Base64.getEncoder().encodeToString(svg.toByteArray()) +
            ",{click:\"showCmt('x')\"}"

    @Test
    fun readsCountFromInlineSvgText() {
        val source = dataUri("<svg><text x=\"1\">18</text></svg>")
        assertEquals(18, ReviewCountParser.fromDataUri(source))
        assertTrue(ReviewCountParser.isReviewSvg(source))
    }

    @Test
    fun capsOverflowSuffixAndIgnoresMalformedText() {
        assertEquals(99, ReviewCountParser.fromSvgText("<svg><text>99+</text></svg>"))
        assertNull(ReviewCountParser.fromSvgText("<svg><text>很多</text></svg>"))
        assertNull(ReviewCountParser.fromSvgText("<svg></svg>"))
    }

    @Test
    fun onlyExplicitServerReviewEndpointsAndInlineSvgWithNumberAreRecognized() {
        val server = "https://example.com/chapter_review_svg?book_id=1&img=1,{click:\"showCmt('x')\"}"
        assertEquals(
            "https://example.com/chapter_review_svg?book_id=1&img=1",
            ReviewCountParser.serverReviewUrl(server),
        )
        // 神评/本章说这类接口不能被当成段评。
        assertNull(ReviewCountParser.serverReviewUrl("https://example.com/god_comment?book_id=1"))
        assertFalse(ReviewCountParser.isReviewSvg(server))
        assertFalse(ReviewCountParser.isReviewSvg("https://example.com/img/cover.png"))
        assertFalse(
            ReviewCountParser.isReviewSvg(dataUri("<svg><path d=\"M0 0h10v10z\"/></svg>")),
        )
    }
}
