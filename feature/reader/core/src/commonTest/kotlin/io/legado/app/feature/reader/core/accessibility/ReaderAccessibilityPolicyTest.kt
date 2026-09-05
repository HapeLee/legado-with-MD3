package io.legado.app.feature.reader.core.accessibility

import io.legado.app.feature.reader.core.model.ReaderBookmarkBadge
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageDecoration
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderAccessibilityPolicyTest {
    @Test
    fun `current Canvas page is exposed as one text node with available directions`() {
        val previous = page(0, "上一页")
        val current = page(1, "标题\n正文")
        val next = page(2, "下一页")

        val snapshot = ReaderAccessibilityPolicy.snapshot(ReaderPageWindow(previous, current, next))!!

        assertEquals("标题\n正文", snapshot.text)
        assertTrue(snapshot.canGoPrevious)
        assertTrue(snapshot.canGoNext)
        assertFalse(snapshot.isBookmarked)
    }

    @Test
    fun `boundaries and bookmark state follow the visible page`() {
        val badge = ReaderBookmarkBadge.create(true, false, 100, 10f, 10, 1f, 10)!!
        val current = page(0, "正文", ReaderPageDecoration(bookmarkBadge = badge))
        val snapshot = ReaderAccessibilityPolicy.snapshot(ReaderPageWindow(current = current))!!

        assertFalse(snapshot.canGoPrevious)
        assertFalse(snapshot.canGoNext)
        assertTrue(snapshot.isBookmarked)
        assertNull(ReaderAccessibilityPolicy.snapshot(ReaderPageWindow()))
    }

    private fun page(index: Int, text: String, decoration: ReaderPageDecoration = ReaderPageDecoration()) = ReaderPage(
        id = ReaderPageId(0, index),
        chapterTitle = "章节",
        text = text,
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 0f,
        contentBottomPx = 100f,
        elements = emptyList(),
        revision = index.toLong(),
        decoration = decoration,
    )
}
