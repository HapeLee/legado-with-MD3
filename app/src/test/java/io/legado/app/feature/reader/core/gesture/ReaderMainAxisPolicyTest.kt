package io.legado.app.feature.reader.core.gesture

import io.legado.app.feature.reader.core.transition.ReaderTransitionMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderMainAxisPolicyTest {

    @Test
    fun `paging accepts diagonal drags in either direction`() {
        for (mode in ReaderTransitionMode.entries.filter { it != ReaderTransitionMode.SCROLL }) {
            for ((x, y) in listOf(-20f to 12f, 12f to -20f, -12f to 16f, 16f to -16f)) {
                val pull = PullBookmarkGesture.drag(
                    x, y, 1000f, true, true, false, PullBookmarkDefaults.config(80f),
                )
                assertFalse(pull.isCandidate)
                assertTrue(ReaderMainAxisPolicy.canStartPageTurn(x, mode))
            }
        }
    }

    @Test
    fun `scroll claims a diagonal drag once its vertical component passes slop`() {
        val slop = 24f
        // 斜滑 |dx| >= |dy|：Y 分量过了 slop 就认领为纵向滚动（修的就是这个落空）。
        assertTrue(ReaderMainAxisPolicy.isVerticalScroll(-30f, slop))
        assertTrue(ReaderMainAxisPolicy.isVerticalScroll(24f, slop))
        // 45° 斜滑（两轴等长）同样认领：此前按主方向判定会落空。
        assertTrue(ReaderMainAxisPolicy.isVerticalScroll(-16f, 16f))
        // 纯纵滑照常认领；横向手势的 Y 抖动达不到 slop，仍不认领。
        assertTrue(ReaderMainAxisPolicy.isVerticalScroll(100f, slop))
        assertFalse(ReaderMainAxisPolicy.isVerticalScroll(5f, slop))
        assertFalse(ReaderMainAxisPolicy.isVerticalScroll(0f, slop))
        assertFalse(ReaderMainAxisPolicy.isVerticalScroll(-23f, slop))
    }

    @Test
    fun `pure vertical drags do not start page turns`() {
        for (mode in ReaderTransitionMode.entries) {
            assertFalse(ReaderMainAxisPolicy.canStartPageTurn(0f, mode))
        }
    }

    @Test
    fun `scroll mode never captures a horizontal page turn`() {
        assertFalse(ReaderMainAxisPolicy.canStartPageTurn(-20f, ReaderTransitionMode.SCROLL))
        assertFalse(ReaderMainAxisPolicy.canStartPageTurn(20f, ReaderTransitionMode.SCROLL))
    }

    @Test
    fun `bookmark hands off a vertical dominant diagonal to paging`() {
        val config = PullBookmarkDefaults.config(80f)
        val initialPull = PullBookmarkGesture.drag(10f, 90f, 1000f, true, true, false, config)
        assertTrue(PullBookmarkGesture.claim(false, initialPull).isDragging)

        val diagonal = PullBookmarkGesture.drag(-100f, 140f, 1000f, true, true, false, config)
        val claim = PullBookmarkGesture.claim(false, diagonal)
        assertFalse(claim.isDragging)
        assertTrue(claim.isReleased)
        assertTrue(ReaderMainAxisPolicy.canStartPageTurn(-100f, ReaderTransitionMode.SLIDE))
    }
}
