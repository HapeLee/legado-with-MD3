package io.legado.app.feature.reader.core.model

import kotlin.test.*
import kotlin.test.Test

class ReaderBookmarkBadgeTest {
    private fun badge(bookmarked: Boolean = true, scroll: Boolean = false, size: Int = 10) =
        ReaderBookmarkBadge.create(bookmarked, scroll, 600, 100f, 30, 2f, size)

    @Test fun matchesViewContentAnchorMarginsAndRibbonRatio() {
        assertEquals(ReaderBookmarkBadge(538f, 104f, 20, 40), badge())
        assertEquals(ReaderBookmarkBadge(518f, 104f, 40, 80), badge(size = 20))
    }

    @Test fun hiddenWithoutBookmarkAndInScrollMode() {
        assertNull(badge(bookmarked = false))
        assertNull(badge(scroll = true))
    }

    @Test fun invalidSizeRetainsLegacyMinimum() {
        assertEquals(2, badge(size = 0)?.widthPx)
        assertEquals(4, badge(size = -10)?.heightPx)
    }

    @Test fun customImageVersionParticipatesInPageStateEquality() {
        val first = badge()!!.copy(imageSource = "badge.png", imageVersion = "1")
        val second = first.copy(imageVersion = "2")
        assertNotEquals(ReaderPageDecoration(bookmarkBadge = first), ReaderPageDecoration(bookmarkBadge = second))
        assertNotEquals(ReaderPageDecoration(bookmarkBadge = first), ReaderPageDecoration())
    }
}
