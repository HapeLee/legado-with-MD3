package io.legado.app.feature.reader.platform

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewBubbleSourcePolicyTest {

    private fun dataUri(svg: String): String =
        "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())

    @Test
    fun readsDisplayTextOption() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            "https://example.com/a.png,{\"type\":\"qd\",\"displayText\":\"18\"}",
            null,
            null,
        )
        assertEquals("18", spec?.text)
        assertEquals(18, spec?.count)
        assertEquals("normal", spec?.status)
    }

    @Test
    fun readsCountFromCreateSvgScript() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            "https://example.com/a.png,{\"click\":\"createSvg2('a','b','c',27)\"}",
            null,
            null,
        )
        assertEquals("27", spec?.text)
        assertEquals(27, spec?.count)
    }

    @Test
    fun keepsPlusSuffixInDisplayText() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            "https://example.com/a.png,{\"num\":\"99+\"}",
            null,
            null,
        )
        assertEquals("99+", spec?.text)
        assertEquals(99, spec?.count)
    }

    @Test
    fun readsEmphasisStatusAndColor() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            "https://example.com/a.png,{\"type\":\"cmt\",\"num\":\"7\",\"status\":\"emphasis\",\"displayColor\":\"ff0000\"}",
            null,
            null,
        )
        assertEquals("emphasis", spec?.status)
        assertEquals("#ff0000", spec?.color)
    }

    @Test
    fun readsTextFromInlineSvg() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            dataUri("<svg><text x=\"1\">23</text></svg>"),
            null,
            null,
        )
        assertEquals("23", spec?.text)
    }

    @Test
    fun recognizesTypeSemanticsWithoutText() {
        val spec = ReviewBubbleSourcePolicy.resolve(
            "https://example.com/a.png,{\"type\":\"paragraph\"}",
            null,
            null,
        )
        assertEquals(null, spec?.text)
        assertTrue(spec != null)
    }

    @Test
    fun ignoresPlainImage() {
        assertNull(ReviewBubbleSourcePolicy.resolve("https://example.com/cover.png", null, null))
    }

    @Test
    fun ignoresFullWidthStyle() {
        assertNull(
            ReviewBubbleSourcePolicy.resolve(
                "https://example.com/a.png,{\"style\":\"FULL\",\"type\":\"qd\"}",
                null,
                null,
            )
        )
    }

    @Test
    fun isImplicitInlineCommentForBareSemanticImage() {
        val parsed = io.legado.app.help.ImageSourceOptions.parse(
            "https://example.com/a.png,{\"click\":\"showCmt('x')\"}"
        )!!
        assertTrue(ReviewBubbleSourcePolicy.isImplicitInlineComment(parsed, null))
        assertFalse(
            ReviewBubbleSourcePolicy.isImplicitInlineComment(
                io.legado.app.help.ImageSourceOptions.parse(
                    "https://example.com/a.png,{\"style\":\"FULL\",\"click\":\"showCmt('x')\"}"
                )!!,
                null,
            )
        )
    }
}
