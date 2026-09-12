package io.legado.app.feature.reader.core.transition

import io.legado.app.constant.PageAnim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPageTransitionTest {
    @Test
    fun `programmatic turn does not preempt a running page animation`() {
        assertFalse(ReaderProgrammaticTurnPolicy.shouldAccept(animationRunning = true))
        assertTrue(ReaderProgrammaticTurnPolicy.shouldAccept(animationRunning = false))
    }

    @Test
    fun `settle duration scales with the remaining legacy scroll distance`() {
        // 基准 360：点击与按键共用同一收尾基准（产品确认保留，不回退旧 View 的 300）。
        assertEquals(360, ReaderPageTransitionPolicy.settleDurationMillis(0f, -800f, 800f))
        assertEquals(90, ReaderPageTransitionPolicy.settleDurationMillis(-600f, -800f, 800f))
        assertEquals(270, ReaderPageTransitionPolicy.settleDurationMillis(-600f, 0f, 800f))
        assertEquals(0, ReaderPageTransitionPolicy.settleDurationMillis(-800f, -800f, 800f))
    }

    @Test
    fun `both settle overloads share the same base duration`() {
        // 两个重载的默认基准必须一致：分叉过一次（360 / 300），测试便无法同时代表生产路径。
        assertEquals(
            ReaderPageTransitionPolicy.settleDurationMillis(0f, -800f, 800f),
            ReaderPageTransitionPolicy.settleDurationMillis(
                ReaderTransitionMode.FADE, 0f, -800f, 800f,
            ),
        )
    }

    @Test
    fun `fade settle uses the full base duration instead of scaling it`() {
        assertEquals(
            360,
            ReaderPageTransitionPolicy.settleDurationMillis(
                ReaderTransitionMode.FADE, -720f, -800f, 800f,
            ),
        )
        assertEquals(
            360,
            ReaderPageTransitionPolicy.settleDurationMillis(
                ReaderTransitionMode.FADE, -720f, 0f, 800f,
            ),
        )
        // 非 FADE 模式按剩余位移折算：360 * 80 / 800。
        assertEquals(
            36,
            ReaderPageTransitionPolicy.settleDurationMillis(
                ReaderTransitionMode.SLIDE, -720f, -800f, 800f,
            ),
        )
        assertEquals(
            0,
            ReaderPageTransitionPolicy.settleDurationMillis(
                ReaderTransitionMode.FADE, -800f, -800f, 800f,
            ),
        )
    }

    @Test
    fun `capturing direction at touch slop does not jump the visible page`() {
        val drag = ReaderHorizontalDrag.capture(-24f)!!
        val captured = drag.transition(-24f, 800f, true, true)
        assertEquals(ReaderTurnDirection.NEXT, captured.direction)
        assertEquals(0f, captured.offsetPx)
        assertTrue(captured.dragging)

        val moved = drag.transition(-124f, 800f, true, true)
        assertEquals(-100f, moved.offsetPx)
    }

    @Test
    fun `reversing past the captured origin never changes the locked page direction`() {
        val drag = ReaderHorizontalDrag.capture(-24f)!!
        val reversed = drag.transition(30f, 800f, true, true)
        assertEquals(ReaderTurnDirection.NEXT, reversed.direction)
        assertEquals(0f, reversed.offsetPx)
    }

    @Test
    fun `drag locks direction and clamps to page extent`() {
        val transition = ReaderPageTransitionPolicy.drag(
            deltaPx = -1200f,
            pageExtentPx = 800f,
            hasPrevious = true,
            hasNext = true,
        )

        assertEquals(ReaderTurnDirection.NEXT, transition.direction)
        assertEquals(-800f, transition.offsetPx)
        assertEquals(1f, transition.progress)
    }

    @Test
    fun `drag at chapter boundary does not start`() {
        val transition = ReaderPageTransitionPolicy.drag(
            deltaPx = 100f,
            pageExtentPx = 800f,
            hasPrevious = false,
            hasNext = true,
        )

        assertFalse(transition.dragging)
        assertEquals(ReaderTurnDirection.PREVIOUS, transition.direction)
        assertEquals(0f, transition.offsetPx)
    }

    @Test
    fun `release commits by distance or matching velocity`() {
        val shortDrag = ReaderPageTransitionPolicy.drag(-100f, 800f, true, true)
        assertFalse(ReaderPageTransitionPolicy.release(shortDrag, 0f).commit)
        assertTrue(ReaderPageTransitionPolicy.release(shortDrag, -1200f).commit)

        val longDrag = ReaderPageTransitionPolicy.drag(400f, 800f, true, true)
        assertTrue(ReaderPageTransitionPolicy.release(longDrag, 0f).commit)
    }

    @Test
    fun `cover and slide preserve legacy page placement`() {
        val transition = ReaderPageTransitionPolicy.drag(-200f, 800f, true, true)

        val cover = transition.transforms(ReaderTransitionMode.COVER)
        assertEquals(-200f, cover.current.translationX)
        assertEquals(0f, cover.next?.translationX)
        assertTrue(cover.currentOnTop)

        val slide = transition.transforms(ReaderTransitionMode.SLIDE)
        assertEquals(-200f, slide.current.translationX)
        assertEquals(600f, slide.next?.translationX)
        assertTrue(slide.currentOnTop)
    }

    @Test
    fun `previous slide keeps incoming page above current like legacy delegate`() {
        val slide = ReaderPageTransitionPolicy.drag(200f, 800f, true, true)
            .transforms(ReaderTransitionMode.SLIDE)

        assertEquals(200f, slide.current.translationX)
        assertEquals(-600f, slide.previous?.translationX)
        assertFalse(slide.currentOnTop)
    }

    @Test
    fun `previous cover slides above stationary current page`() {
        val cover = ReaderPageTransitionPolicy.drag(200f, 800f, true, true)
            .transforms(ReaderTransitionMode.COVER)
        assertEquals(-600f, cover.previous?.translationX)
        assertEquals(0f, cover.current.translationX)
        assertFalse(cover.currentOnTop)
    }

    @Test
    fun `fade maps setting and overlays destination without moving pages`() {
        assertEquals(ReaderTransitionMode.FADE, ReaderTransitionMode.fromPageAnim(PageAnim.fadePageAnim))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(PageAnim.noAnim))
        for (delta in listOf(-200f, 200f)) {
            val fade = ReaderPageTransitionPolicy.drag(delta, 800f, true, true)
                .transforms(ReaderTransitionMode.FADE)
            val destination = fade.previous ?: fade.next!!
            assertEquals(0f, destination.translationX)
            assertEquals(0.25f, destination.alpha)
            assertEquals(1f, fade.current.alpha)
            assertFalse(fade.currentOnTop)
        }
    }

    @Test
    fun `all legacy page animation values map to their Canvas modes`() {
        assertEquals(ReaderTransitionMode.COVER, ReaderTransitionMode.fromPageAnim(PageAnim.coverPageAnim))
        assertEquals(ReaderTransitionMode.SLIDE, ReaderTransitionMode.fromPageAnim(PageAnim.slidePageAnim))
        assertEquals(ReaderTransitionMode.SIMULATION, ReaderTransitionMode.fromPageAnim(PageAnim.simulationPageAnim))
        assertEquals(ReaderTransitionMode.SCROLL, ReaderTransitionMode.fromPageAnim(PageAnim.scrollPageAnim))
        assertEquals(ReaderTransitionMode.FADE, ReaderTransitionMode.fromPageAnim(PageAnim.fadePageAnim))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(PageAnim.noAnim))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(Int.MIN_VALUE))
        assertEquals(ReaderTransitionMode.NONE, ReaderTransitionMode.fromPageAnim(Int.MAX_VALUE))
    }

    @Test
    fun `cancel never commits even beyond distance and velocity thresholds`() {
        val drag = ReaderPageTransitionPolicy.drag(-700f, 800f, true, true)
        val decision = ReaderPageTransitionPolicy.release(drag, -2000f, cancelled = true, lastDragDeltaPx = -20f)
        assertFalse(decision.commit)
        assertEquals(0f, decision.targetOffsetPx)
    }

    @Test
    fun `reversing after long drag returns page instead of turning opposite page`() {
        val drag = ReaderPageTransitionPolicy.drag(-700f, 800f, true, true)
        assertFalse(ReaderPageTransitionPolicy.release(drag, -1000f, lastDragDeltaPx = 10f).commit)
        val reversed = ReaderPageTransitionPolicy.drag(40f, 800f, true, true, drag.direction)
        assertEquals(ReaderTurnDirection.NEXT, reversed.direction)
        assertEquals(0f, reversed.offsetPx)
    }

    @Test
    fun `horizontal forward drag commits while fade retains its distance threshold`() {
        val drag = ReaderPageTransitionPolicy.drag(-30f, 800f, true, true)
        assertTrue(ReaderPageTransitionPolicy.release(drag, 0f, lastDragDeltaPx = -5f).commit)
        assertFalse(ReaderPageTransitionPolicy.release(drag, 0f, commitProgress = 0.1f).commit)
        val fadeDrag = ReaderPageTransitionPolicy.drag(-80f, 800f, true, true)
        assertTrue(ReaderPageTransitionPolicy.release(fadeDrag, 0f, commitProgress = 0.1f).commit)
    }
}
