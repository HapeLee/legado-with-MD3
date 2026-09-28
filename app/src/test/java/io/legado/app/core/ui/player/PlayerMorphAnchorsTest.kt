package io.legado.app.core.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import io.legado.app.ui.book.readaloud.morph.CapsuleAnchorKind
import io.legado.app.ui.book.readaloud.morph.ReadAloudMorphState
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMorphAnchorsTest {
    private val panel = Rect(300f, 800f, 500f, 864f)
    private val cover = Rect(304f, 804f, 360f, 860f)
    private fun state() = ReadAloudMorphState(Animatable(0f), Density(1f)).apply {
        reportScreenBounds(Rect(0f, 0f, 1080f, 1920f))
        reportCoverEnd(Rect(200f, 300f, 700f, 800f), 8f)
    }

    private fun ReadAloudMorphState.anchors(kind: CapsuleAnchorKind = CapsuleAnchorKind.HomeBar) {
        reportPanelStart(panel, 32f, anchorKind = kind)
        reportCoverStart(cover, 28f, anchorKind = kind)
    }

    private class Frames(val beforeFrame: (Int) -> Unit = {}) : MonotonicFrameClock {
        var count = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            beforeFrame(++count)
            return onFrame(count * 16_666_667L)
        }
    }

    @Test
    fun missingCapsuleStopsWaitingAndCanFadeInWithoutCoordinates() = runTest {
        val morph = state()
        val clock = Frames()
        assertFalse(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(PLAYER_ANCHOR_WAIT_FRAMES, clock.count)
        morph.useFadeOnlyOpening()
        morph.anchors() // 迟到的布局不能改变本次展开。
        morph.progress.snapTo(0.5f)
        morph.anchors()
        assertFalse(morph.hasCapsuleAnchors)
        assertNull(morph.panelFrame())
        assertNull(morph.coverFrame())
        withContext(clock) { morph.animateTo(1f) }
        morph.anchors() // 完全展开后接收真实返回位置。
        assertTrue(morph.hasCapsuleAnchors)
        morph.progress.snapTo(0.5f)
        assertNotNull(morph.panelFrame())
    }

    @Test
    fun realAnchorMustStayStableForTwoFramesBeforeOpening() = runTest {
        val morph = state()
        val clock = Frames { if (it == 4) morph.anchors() }
        assertTrue(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(6, clock.count)
    }

    @Test
    fun staleGlobalAnchorCannotStartAHomeCapsuleTransition() = runTest {
        val morph = state().apply {
            anchors(CapsuleAnchorKind.Global)
            expectCapsuleAnchors(CapsuleAnchorKind.HomeBar)
        }
        val clock = Frames()
        assertFalse(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(PLAYER_ANCHOR_WAIT_FRAMES, clock.count)
    }

    @Test
    fun disabledCapsuleDoesNotWaitForAnAnchor() = runTest {
        val clock = Frames()
        // 即使播放页目标也尚未布局，关闭胶囊仍直接淡入。
        val morph = ReadAloudMorphState(Animatable(0f), Density(1f))
        assertFalse(withContext(clock) { awaitPlayerMorphAnchors(morph, false) })
        assertEquals(0, clock.count)
    }

    @Test
    fun missingPlayerLayoutAlsoHasABoundedWait() = runTest {
        val morph = ReadAloudMorphState(Animatable(0f), Density(1f))
        val clock = Frames()
        assertFalse(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(PLAYER_ANCHOR_WAIT_FRAMES, clock.count)
    }

    @Test
    fun unlinkedCoverDoesNotWaitForAFlightEndpoint() = runTest {
        val morph = ReadAloudMorphState(Animatable(0f), Density(1f)).apply {
            reportScreenBounds(Rect(0f, 0f, 1080f, 1920f))
            reportCapsuleCoverLinked(false)
            anchors()
        }
        val clock = Frames()
        assertTrue(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(3, clock.count)
    }

    @Test
    fun linkedCoverStillWaitsForTheRealFlightEndpoint() = runTest {
        val morph = ReadAloudMorphState(Animatable(0f), Density(1f)).apply {
            reportScreenBounds(Rect(0f, 0f, 1080f, 1920f))
            anchors()
        }
        val clock = Frames { if (it == 4) morph.reportCoverEnd(Rect(200f, 300f, 700f, 800f), 8f) }
        assertTrue(withContext(clock) { awaitPlayerMorphAnchors(morph, true) })
        assertEquals(5, clock.count)
    }
}
