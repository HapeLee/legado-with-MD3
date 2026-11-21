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
    fun topStartKeepsTheSameGapAsTopEnd() {
        val topStart = ReaderBookmarkBadge.create(
             true, false, 600, 100f, 30, 2f, 10,
            position = ReaderBookmarkBadgePosition.TOP_START, contentLeftPaddingPx = 0,
        )
        // 左锚点 = 左内边距 + 6dp 留白；右锚点仍按旧公式。
        assertEquals(ReaderBookmarkBadge(12f, 100f, 20, 40), topStart)
    }

    @Test
    fun customPositionUsesOffsetsFromContentTopLeft() {
        val custom = ReaderBookmarkBadge.create(
             true, false, 600, 100f, 30, 2f, 10,
            position = ReaderBookmarkBadgePosition.CUSTOM,
            contentLeftPaddingPx = 10,
            offsetXDp = 40,
            offsetYDp = 120,
            pageHeightPx = 1000,
        )
        // 正文区左上角 (10, 100) + 偏移 (40dp, 120dp) * density 2。
        assertEquals(ReaderBookmarkBadge(90f, 340f, 20, 40), custom)
    }

    @Test
    fun customPositionIsClampedInsideThePage() {
        val custom = ReaderBookmarkBadge.create(
             true, false, 600, 100f, 30, 2f, 10,
            position = ReaderBookmarkBadgePosition.CUSTOM,
            offsetXDp = 900,
            offsetYDp = 900,
            pageHeightPx = 1000,
        )
        // 页面右下边界 = (600 - 20, 1000 - 40)，偏移越界也不能被裁掉。
        assertEquals(ReaderBookmarkBadge(580f, 960f, 20, 40), custom)
    }

    @Test
    fun unknownPositionPreferenceFallsBackToHistoricalTopEnd() {
        assertEquals(ReaderBookmarkBadgePosition.TOP_END, ReaderBookmarkBadgePosition.fromPreference(null))
        assertEquals(ReaderBookmarkBadgePosition.TOP_END, ReaderBookmarkBadgePosition.fromPreference("left"))
        assertEquals(
            ReaderBookmarkBadgePosition.CUSTOM,
            ReaderBookmarkBadgePosition.fromPreference("custom"),
        )
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
