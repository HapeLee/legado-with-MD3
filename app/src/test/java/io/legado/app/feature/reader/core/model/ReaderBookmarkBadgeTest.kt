package io.legado.app.feature.reader.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderBookmarkBadgeTest {
    private fun badge(bookmarked: Boolean = true, scroll: Boolean = false, size: Int = 10) =
        ReaderBookmarkBadge.create(bookmarked, scroll, 600, 100f, 30, 2f, size)

    @Test
    fun topEdgeAnchorsToHeaderBottomAndPreservesRibbonRatio() {
        // headerBottom = 100f -> topPx = 100
        assertEquals(ReaderBookmarkBadge(538f, 100f, 20, 40), badge())
        assertEquals(ReaderBookmarkBadge(518f, 100f, 40, 80), badge(size = 20))
    }

    @Test
    fun hiddenWithoutBookmarkInScrollModeOrAtZeroSize() {
        assertNull(badge(bookmarked = false))
        assertNull(badge(scroll = true))
        assertNull(badge(size = 0))
    }

    @Test
    fun nonPositiveSizeDoesNotCreateBadge() {
        assertNull(badge(size = -10))
    }

    @Test fun customImageVersionParticipatesInPageStateEquality() {
        val first = badge()!!.copy(imageSource = "badge.png", imageVersion = "1")
        val second = first.copy(imageVersion = "2")
        assertNotEquals(ReaderPageDecoration(bookmarkBadge = first), ReaderPageDecoration(bookmarkBadge = second))
        assertNotEquals(ReaderPageDecoration(bookmarkBadge = first), ReaderPageDecoration())
    }
}
