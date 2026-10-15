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
    fun `scroll claims only a vertical dominant drag`() {
        assertTrue(ReaderMainAxisPolicy.isVerticalDominant(12f, -20f))
        assertFalse(ReaderMainAxisPolicy.isVerticalDominant(-20f, 12f))
    }

    @Test
    fun `equal axes do not start vertical scrolling`() {
        assertFalse(ReaderMainAxisPolicy.isVerticalDominant(16f, -16f))
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
