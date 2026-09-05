package io.legado.app.feature.reader.core.transition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCurlGeometryTest {
    @Test fun curlShadowColorsSoftenTheLegacyGradientDrawables() {
        // 刻意偏离原版峰值（0xFF/0x80/0xB0）：折缝全不透明黑边观感偏重，
        // 峰值 alpha 按约六成调轻，色相与渐变结构不变。
        assertEquals(0x99111111.toInt(), ReaderCurlVisualPolicy.backShadowDarkArgb)
        assertEquals(0x4D111111.toInt(), ReaderCurlVisualPolicy.frontShadowDarkArgb)
        assertEquals(0x73333333.toInt(), ReaderCurlVisualPolicy.folderShadowDarkArgb)
    }

    @Test fun choosesTouchedCornerAndProducesFiniteBezierFrame() {
        val frame = PageCurlGeometry.calculate(1080f, 1920f, 800f, 1400f)
        assertNotNull(frame)
        frame!!
        assertEquals(CurlPoint(1080f, 1920f), frame.corner)
        assertTrue(frame.isValid)
        assertTrue(frame.touchToCornerDistance > 0f)
    }

    @Test fun mirrorLinearPartIsAReflection() {
        val frame = PageCurlGeometry.calculate(1080f, 1920f, 700f, 1300f)
        assertNotNull(frame)
        val mirror = frame!!.mirror
        val determinant = mirror.scaleX * mirror.scaleY - mirror.skewX * mirror.skewY
        assertEquals(-1f, determinant, .001f)
    }

    @Test fun invalidViewportHasNoFrame() {
        assertEquals(null, PageCurlGeometry.calculate(0f, 1920f, 1f, 1f))
    }

    @Test fun legacyMiddleZonesChooseStableCurlCorners() {
        val height = 900f
        assertEquals(height, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.PREVIOUS, 100f, 120f, height))
        assertEquals(1f, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 350f, 430f, height))
        assertEquals(height, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 500f, 430f, height))
        assertEquals(100f, ReaderCurlTouchPolicy.dragY(ReaderTurnDirection.NEXT, 100f, 100f, height))
    }

    @Test fun programmaticSimulationUsesLegacyTopAndBottomAnchors() {
        assertEquals(900f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.PREVIOUS, 20f, 900f))
        assertEquals(1f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.NEXT, 200f, 900f))
        assertEquals(810f, ReaderCurlTouchPolicy.programmaticY(ReaderTurnDirection.NEXT, 700f, 900f))
    }

    @Test fun programmaticSimulationUsesLegacyHorizontalTouchAnchors() {
        assertEquals(0f, ReaderCurlTouchPolicy.programmaticX(ReaderTurnDirection.PREVIOUS, 1000f))
        assertEquals(900f, ReaderCurlTouchPolicy.programmaticX(ReaderTurnDirection.NEXT, 1000f))
    }

    @Test fun simulationSettleCrossesTheLegacyHorizontalViewportBoundary() {
        assertEquals(1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.PREVIOUS, true, 1000f))
        assertEquals(-1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.PREVIOUS, false, 1000f))
        assertEquals(-1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.NEXT, true, 1000f))
        assertEquals(1000f, ReaderCurlTouchPolicy.settledX(ReaderTurnDirection.NEXT, false, 1000f))
    }

    @Test fun curlGeometryFollowsTouchBeyondTheLeftEdgeUntilTheFoldLeavesThePage() {
        val corner = CurlPoint(1080f, 1920f)
        val near = PageCurlGeometry.calculate(1080f, 1920f, -270f, 1919f, corner)!!
        val beyond = PageCurlGeometry.calculate(1080f, 1920f, -540f, 1919f, corner)!!
        // 收尾动画把触点送出页外后，几何必须继续跟随原始触点滑动，
        // 折页边缘随之滑出左缘，而不是收敛在页内冻结到结束帧。
        assertEquals(-540f, beyond.touch.x, .001f)
        assertTrue(beyond.isValid)
        assertTrue(beyond.start1.x < near.start1.x)
        assertTrue(beyond.start1.x < 0f)
    }

    @Test fun curlGeometryKeepsTheInPageTouchGuardWhileReleasingOffPageTouches() {
        assertEquals(.1f, PageCurlGeometry.calculate(1080f, 1920f, 0f, 1919f, CurlPoint(1080f, 1920f))!!.touch.x, .001f)
        val topExit = PageCurlGeometry.calculate(1080f, 1920f, -540f, 1f, CurlPoint(1080f, 0f))!!
        assertTrue(topExit.isValid)
        assertTrue(topExit.start1.x < 0f)
    }

    @Test fun simulationSettleDurationUsesItsFullLegacyCurlTravel() {
        assertEquals(570, ReaderCurlTouchPolicy.settleDurationMillis(900f, -1000f, 1000f))
        assertEquals(300, ReaderCurlTouchPolicy.settleDurationMillis(0f, 1000f, 1000f))
        assertEquals(0, ReaderCurlTouchPolicy.settleDurationMillis(1000f, 1000f, 1000f))
    }

    @Test
    fun simulationCurlSnapsAwayFromTheDegenerateCornerAndKeepsAVisibleFinalFrame() {
        assertEquals(960f, ReaderCurlTouchPolicy.dragX(ReaderTurnDirection.NEXT, 999f, 1000f))
        assertEquals(40f, ReaderCurlTouchPolicy.dragX(ReaderTurnDirection.PREVIOUS, 1f, 1000f))
        assertEquals(90, ReaderCurlTouchPolicy.settleDurationMillis(999f, 1000f, 1000f))
    }

    @Test
    fun simulationCurlRevealsTheSafeSnapContinuouslyFromTheTouchedCorner() {
        assertEquals(
            1000f,
            ReaderCurlTouchPolicy.revealX(ReaderTurnDirection.NEXT, 960f, 1000f, 0f)
        )
        assertEquals(
            980f,
            ReaderCurlTouchPolicy.revealX(ReaderTurnDirection.NEXT, 960f, 1000f, .5f)
        )
        assertEquals(
            40f,
            ReaderCurlTouchPolicy.revealX(ReaderTurnDirection.PREVIOUS, 80f, 1000f, .5f)
        )
    }

    @Test
    fun cancelledPreviousCurlReturnsPastTheLeftEdge() {
        assertEquals(
            -1000f, ReaderCurlTouchPolicy.settledX(
                ReaderTurnDirection.PREVIOUS,
                committed = false,
                pageWidth = 1000f,
            )
        )
    }

    @Test fun simulationCornerStaysLockedWhileTouchCrossesTheViewportMidpoint() {
        val corner = CurlPoint(1080f, 0f)
        val first = PageCurlGeometry.calculate(1080f, 1920f, 900f, 300f, corner)!!
        val crossed = PageCurlGeometry.calculate(1080f, 1920f, 300f, 100f, corner)!!
        assertEquals(corner, first.corner)
        assertEquals(corner, crossed.corner)
    }

    @Test fun curlSettleAnchorMatchesTheCapturedLegacyCorner() {
        assertEquals(0f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.NEXT, 200f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.NEXT, 700f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.cornerY(ReaderTurnDirection.PREVIOUS, 200f, 900f))
        assertEquals(1f, ReaderCurlTouchPolicy.settledY(0f, 900f))
        assertEquals(900f, ReaderCurlTouchPolicy.settledY(900f, 900f))
    }
}
