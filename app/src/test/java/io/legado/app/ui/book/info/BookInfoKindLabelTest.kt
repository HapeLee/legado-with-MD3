package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookInfoKindLabelTest {

    @Test
    fun splitsLabelAndHttpLink() {
        assertEquals(
            "中文" to "https://example.com/dm22",
            parseKindTagLabel("中文|https://example.com/dm22")
        )
    }

    @Test
    fun splitsLabelAndRootRelativeLink() {
        assertEquals(
            "4K" to "/dm22?page=1",
            parseKindTagLabel("4K|/dm22?page=1")
        )
    }

    @Test
    fun keepsPlainSeparatorLabelWhole() {
        val (text, url) = parseKindTagLabel("4K|HDR")
        assertEquals("4K|HDR", text)
        assertNull(url)
    }

    @Test
    fun keepsLabelWhenSuffixIsNotALink() {
        val (text, url) = parseKindTagLabel("连载|日更")
        assertEquals("连载|日更", text)
        assertNull(url)
    }

    @Test
    fun ignoresLinkWithoutText() {
        val (text, url) = parseKindTagLabel("|https://example.com")
        assertEquals("|https://example.com", text)
        assertNull(url)
    }

    @Test
    fun ignoresTrailingSeparator() {
        val (text, url) = parseKindTagLabel("标签|")
        assertEquals("标签|", text)
        assertNull(url)
    }

    @Test
    fun keepsLabelWithoutSeparator() {
        assertEquals("连载" to null, parseKindTagLabel("连载"))
    }
}
