package io.legado.app.ui.book.readaloud.morph

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 形变几何：两端对齐、双曲线错开、遮罩延后。
 *
 * 这些数字直接决定观感，改动必须显式可见。
 */
class PlayerMorphFramesTest {

    private val start = Rect(left = 600f, top = 1800f, right = 1080f, bottom = 1936f)
    private val end = Rect(left = 0f, top = 0f, right = 1080f, bottom = 2400f)

    @Test
    fun `home transition waits for home capsule instead of stale global coordinates`() {
        val state = ReadAloudMorphState(Animatable(0f), Density(1f))
        state.reportScreenBounds(end)
        state.reportCoverEnd(end, 8f)
        state.reportPanelStart(start, 24f, anchorKind = CapsuleAnchorKind.Global)
        state.reportCoverStart(start, 24f, anchorKind = CapsuleAnchorKind.Global)
        state.expectCapsuleAnchors(CapsuleAnchorKind.HomeBar)
        assertTrue(!state.hasCapsuleAnchors)
        assertNull(state.panelFrame())
        assertNull(state.coverFrame())
        val home = Rect(1000f, 1850f, 1064f, 1914f)
        state.reportPanelStart(home, 32f, anchorKind = CapsuleAnchorKind.HomeBar)
        assertTrue(!state.hasCapsuleAnchors)
        state.reportCoverStart(home, 28f, anchorKind = CapsuleAnchorKind.HomeBar)
        assertTrue(state.hasCapsuleAnchors)
        assertEquals(home, state.panelFrame()!!.bounds)
        assertEquals(home, state.coverFrame()!!.bounds)
    }

    @Test
    fun `disabling capsule while expanded removes geometry before collapse`() {
        val state = ReadAloudMorphState(Animatable(1f), Density(1f))
        state.reportScreenBounds(end)
        state.reportCoverEnd(end, 8f)
        state.reportPanelStart(start, 24f)
        state.reportCoverStart(start, 24f)
        state.clearStartAnchors()
        assertTrue(!state.hasCapsuleAnchors)
        assertNull(state.panelFrame())
        assertNull(state.coverFrame())
    }

    @Test
    fun `missing capsule geometry does not invent a collapse destination`() {
        val state = ReadAloudMorphState(Animatable(0.5f), Density(1f))
        state.reportScreenBounds(end)
        state.reportCoverEnd(end, 8f)
        assertNull(state.panelFrame())
        assertNull(state.coverFrame())
    }

    @Test
    fun `closing a page without cover fades the capsule cover in at its actual location`() =
        runBlocking {
            val state = ReadAloudMorphState(Animatable(1f), Density(1f))
            state.reportCoverStart(start, 24f)
            state.reportCoverPageVisible(false)
            // 封面页甚至未测量时也应能从胶囊端点渐显，而不是最后一帧才出现。
            state.progress.snapTo(0.075f)
            assertEquals(start, state.coverFrame()!!.bounds)
            assertEquals(0f, computeMorphFlyingCoverAlpha(0.9f, false), 0f)
            assertEquals(0f, computeMorphFlyingCoverAlpha(0.15f, false), 0f)
            assertEquals(0.5f, computeMorphFlyingCoverAlpha(0.075f, false), 0.001f)
            assertTrue(computeMorphFlyingCoverAlpha(0.01f, false) > 0.98f)
            assertEquals(1f, computeMorphFlyingCoverAlpha(0.5f, true), 0f)
            assertEquals(0f, computeMorphFlyingCoverAlpha(0f, false), 0f)
            assertEquals(0f, computeMorphFlyingCoverAlpha(1f, true), 0f)
        }

    @Test
    fun `offscreen pager cover does not replace the measured endpoint or collapse mode`() =
        runBlocking {
            val state = ReadAloudMorphState(Animatable(1f), Density(1f))
            state.reportCoverEnd(end, 8f)
            state.reportCoverPageVisible(false)
            state.reportCoverEnd(start, 20f)
            assertEquals(end, state.coverEndBounds)
            state.progress.snapTo(0.6f)
            state.reportCoverPageVisible(true)
            assertTrue(!state.coverPageVisible)
            // 预测性返回取消、恢复展开后，重新进入封面页可以恢复正常飞行。
            state.progress.snapTo(1f)
            state.reportCoverPageVisible(true)
            assertTrue(state.coverPageVisible)
            // 最终测量先于可见性事件到达时，也不会丢掉返回封面页的目标布局。
            assertEquals(start, state.coverEndBounds)
            assertEquals(20f, state.coverEndCornerRadiusPx, 0f)
        }

    @Test
    fun `capsule appearing during playback supplies the real collapse destination`() = runBlocking {
        val state = ReadAloudMorphState(Animatable(1f), Density(1f))
        state.reportScreenBounds(end)
        state.reportCoverEnd(Rect(250f, 300f, 750f, 1000f), 8f)
        val capsulePanel = Rect(800f, 1700f, 1000f, 1764f)
        val capsuleCover = Rect(808f, 1708f, 856f, 1756f)
        state.reportPanelStart(capsulePanel, 32f)
        state.reportCoverStart(capsuleCover, 24f)

        assertEquals(capsulePanel, state.panelStartBounds)
        assertEquals(capsuleCover, state.coverStartBounds)
        state.progress.snapTo(0.5f)
        state.reportPanelStart(start, 20f)
        state.reportCoverStart(start, 20f)
        assertEquals(capsulePanel, state.panelStartBounds)
        assertEquals(capsuleCover, state.coverStartBounds)
        state.progress.snapTo(0f)
        assertEquals(capsulePanel, state.panelFrame()!!.bounds)
        assertEquals(capsuleCover, state.coverFrame()!!.bounds)
    }

    @Test
    fun `rotation keeps updating without layout while morph geometry stays frozen`() = runBlocking {
        val state = ReadAloudMorphState(Animatable(0f), Density(1f))
        state.reportCoverStart(start, 24f, 10f)
        state.reportCoverEnd(end, 8f)
        state.progress.snapTo(0.1f)
        state.reportCoverRotation(80f)
        assertEquals(start, state.coverStartBounds)
        assertEquals(24f, state.coverStartCornerRadiusPx, 0f)
        assertEquals(80f, state.coverRotationDeg, 0f)
        assertEquals(
            computeCoverMorphFrame(0.1f, start, end, 24f, 8f, 80f)!!.imageRotationDeg,
            state.coverFrame()!!.imageRotationDeg,
            0.001f,
        )
    }

    @Test
    fun `predictive back previews only part of collapse before confirmation`() {
        assertEquals(1f, computePredictiveMorphProgress(1f, 0f), 0f)
        assertEquals(0.775f, computePredictiveMorphProgress(1f, 0.5f), 0.001f)
        assertEquals(0.55f, computePredictiveMorphProgress(1f, 1f), 0.001f)
        assertEquals(0.55f, computePredictiveMorphProgress(1f, 2f), 0.001f)
        assertEquals(1f, computePredictiveMorphProgress(1f, -1f), 0f)
    }

    @Test
    fun `predictive back starts from the current frame and reverses without a jump`() {
        val start = 0.7f
        assertEquals(start, computePredictiveMorphProgress(start, 0f), 0f)
        val halfway = computePredictiveMorphProgress(start, 0.5f)
        val further = computePredictiveMorphProgress(start, 0.8f)
        assertTrue(further < halfway)
        assertEquals(halfway, computePredictiveMorphProgress(start, 0.5f), 0f)
        assertEquals(start, computePredictiveMorphProgress(start, 0f), 0f)
    }

    @Test
    fun `panel returns material before the capsule endpoint`() {
        assertEquals(0f, computeMorphPanelAlpha(0f), 0f)
        assertEquals(0.5f, computeMorphPanelAlpha(0.075f), 0.001f)
        assertEquals(1f, computeMorphPanelAlpha(0.15f), 0f)
        assertEquals(1f, computeMorphPanelAlpha(1f), 0f)
    }

    @Test
    fun `capsule cover fades continuously during the final morph segment`() {
        assertEquals(1f, computeCapsuleCoverAlpha(0f), 0f)
        assertEquals(0.5f, computeCapsuleCoverAlpha(0.075f), 0.001f)
        assertEquals(0f, computeCapsuleCoverAlpha(0.15f), 0f)
    }

    @Test
    fun `veil transitions smoothly in mid to late phase`() {
        assertEquals(0f, computeMorphVeil(0.20f), 0.001f)
        assertEquals(0.5f, computeMorphVeil(0.425f), 0.001f)
        assertEquals(1f, computeMorphVeil(0.65f), 0.001f)
    }

    @Test
    fun `new source discards stale capsule anchors before opening`() {
        val state = ReadAloudMorphState(Animatable(0f), Density(1f))
        state.reportPanelStart(start, 32f)
        state.reportCoverStart(start, 20f)
        state.clearStartAnchors()
        assertEquals(Rect.Zero, state.panelStartBounds)
        assertEquals(Rect.Zero, state.coverStartBounds)
        state.reportPanelStart(end, 40f)
        assertEquals(end, state.panelStartBounds)
    }

    @Test
    fun `source changes cannot move an in flight anchor`() = runBlocking {
        val state = ReadAloudMorphState(Animatable(0f), Density(1f))
        state.reportPanelStart(start, 32f)
        state.progress.snapTo(0.5f)
        state.clearStartAnchors()
        state.reportPanelStart(end, 40f)
        assertEquals(start, state.panelStartBounds)
    }

    @Test
    fun `panel preserves independent screen corners and reverses continuously`() {
        val radii = PlayerPanelCornerRadii(80f, 72f, 60f, 64f)
        val collapsed = computePanelMorphFrame(0f, start, end, 68f, 0f, radii)!!
        val middle = computePanelMorphFrame(0.5f, start, end, 68f, 0f, radii)!!
        val expanded = computePanelMorphFrame(1f, start, end, 68f, 0f, radii)!!
        assertEquals(PlayerPanelCornerRadii.all(68f), collapsed.cornerRadii)
        assertEquals(PlayerPanelCornerRadii(74f, 70f, 64f, 66f), middle.cornerRadii)
        assertEquals(radii, expanded.cornerRadii)
        val nearEnd = computePanelMorphFrame(0.999f, start, end, 68f, 0f, radii)!!
        assertEquals(expanded.cornerRadii.topLeft, nearEnd.cornerRadii.topLeft, 0.02f)
    }

    @Test
    fun `flying cover lands at the page size without a scale jump`() {
        val coverEnd = Rect(200f, 320f, 760f, 1104f)
        val frame = computeCoverMorphFrame(1f, Rect(600f, 1800f, 640f, 1840f), coverEnd, 20f, 8f)!!
        assertEquals(coverEnd, frame.bounds)
        assertEquals(1f, frame.imageScale, 0f)
        assertEquals(8f, frame.cornerRadiusPx, 0f)
        assertEquals(0f, frame.imageRotationDeg, 0f)
    }

    @Test
    fun `panel frame matches capsule at zero progress`() {
        val frame = computePanelMorphFrame(0f, start, end, 32f, 0f)!!

        assertEquals(start, frame.bounds)
        assertEquals(32f, frame.cornerRadiusPx, 0.001f)
        assertEquals(1f, frame.shadowAlpha, 0.001f)
    }

    @Test
    fun `panel frame matches full screen at full progress`() {
        val frame = computePanelMorphFrame(1f, start, end, 32f, 0f)!!

        assertEquals(end.left, frame.bounds.left, 0.001f)
        assertEquals(end.top, frame.bounds.top, 0.001f)
        assertEquals(end.width, frame.bounds.width, 0.001f)
        assertEquals(end.height, frame.bounds.height, 0.001f)
        assertEquals(0f, frame.cornerRadiusPx, 0.001f)
        assertEquals(0f, frame.shadowAlpha, 0.001f)
    }

    @Test
    fun `panel geometry is linear`() {
        // 「面」不参与缓动：缓动全交给驱动它的进度动画，再叠一层就是双重缓动。
        val quarter = computePanelMorphFrame(0.25f, start, end, 32f, 0f)!!

        assertEquals(start.left + (end.left - start.left) * 0.25f, quarter.bounds.left, 0.01f)
        assertEquals(start.top + (end.top - start.top) * 0.25f, quarter.bounds.top, 0.01f)
        assertEquals(start.right + (end.right - start.right) * 0.25f, quarter.bounds.right, 0.01f)
        assertEquals(
            start.bottom + (end.bottom - start.bottom) * 0.25f,
            quarter.bounds.bottom,
            0.01f
        )
    }

    @Test
    fun `cover moves x and y on different curves`() {
        // 封面：X 走位置曲线（先快），Y 与尺寸一起走尺寸曲线（先慢）。
        // 这是「从胶囊里长出来」而不是「从胶囊里滑出来」的关键，不能用同一条曲线。
        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)
        val frame = computeCoverMorphFrame(0.5f, coverStart, coverEnd, 20f, 8f)!!

        val e1 = morphPositionEase(0.5f)
        val e2 = morphSizeEase(0.5f)
        assertTrue("位置曲线应比尺寸曲线快", e1 > e2)
        assertEquals(
            coverStart.center.x + (coverEnd.center.x - coverStart.center.x) * e1,
            frame.bounds.center.x,
            0.01f,
        )
        assertEquals(
            coverStart.center.y + (coverEnd.center.y - coverStart.center.y) * e2,
            frame.bounds.center.y,
            0.01f,
        )
        assertEquals(
            coverStart.height + (coverEnd.height - coverStart.height) * e2,
            frame.bounds.height,
            0.01f,
        )
    }

    @Test
    fun `cover keeps circular radius clamp during morph`() {
        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)
        val frame = computeCoverMorphFrame(0.5f, coverStart, coverEnd, 20f, 8f)!!

        assertTrue(frame.cornerRadiusPx <= minOf(frame.bounds.width, frame.bounds.height) / 2f)
        assertTrue(frame.imageScale in 1f..1.01f)
    }

    @Test
    fun `cover rotation falls back to zero while growing`() {
        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)

        assertEquals(
            180f,
            computeCoverMorphFrame(0f, coverStart, coverEnd, 20f, 8f, 180f)!!.imageRotationDeg,
            0.001f,
        )
        assertEquals(
            0f,
            computeCoverMorphFrame(1f, coverStart, coverEnd, 20f, 8f, 180f)!!.imageRotationDeg,
            0.001f,
        )
    }

    @Test
    fun `accumulated cover rotation is normalized to the shortest path`() {
        // 胶囊封面每 12 秒 +360，跑一小时就攒到几万度；
        // 不归一化的话收起时飞行封面会顺着插值把攒下的每一圈都转一遍。
        assertEquals(120f, normalizeMorphRotationDeg(4_440f), 0.001f)
        assertEquals(-170f, normalizeMorphRotationDeg(190f), 0.001f)
        assertEquals(0f, normalizeMorphRotationDeg(1_080f), 0.001f)
        assertEquals(0f, normalizeMorphRotationDeg(Float.NaN), 0.001f)

        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)
        val frame = computeCoverMorphFrame(0f, coverStart, coverEnd, 20f, 8f, 4_440f)!!
        assertEquals(120f, frame.imageRotationDeg, 0.001f)
    }

    @Test
    fun `cover rotation never exceeds half a turn while morphing`() {
        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)

        for (turns in 0..40) {
            for (step in 0..10) {
                val frame = computeCoverMorphFrame(
                    progress = step / 10f,
                    start = coverStart,
                    end = coverEnd,
                    startCornerRadiusPx = 20f,
                    endCornerRadiusPx = 8f,
                    imageRotationDeg = turns * 360f + step * 36f,
                )!!
                assertTrue(
                    "旋转角必须落在半圈以内：${frame.imageRotationDeg}",
                    frame.imageRotationDeg in -180f..180f,
                )
            }
        }
    }

    @Test
    fun `cover rotation settles before the content starts fading in`() {
        val coverStart = Rect(0f, 0f, 40f, 40f)
        val coverEnd = Rect(0f, 0f, 560f, 784f)

        // 飞行封面与播放页真实封面在 MORPH_VEIL_START 之后交叉淡入，
        // 那时旋转必须已经停住，否则交接处会「转着落地」。
        val frame = computeCoverMorphFrame(
            progress = MORPH_VEIL_START,
            start = coverStart,
            end = coverEnd,
            startCornerRadiusPx = 20f,
            endCornerRadiusPx = 8f,
            imageRotationDeg = 170f,
        )!!
        assertEquals(0f, frame.imageRotationDeg, 0.001f)
    }

    @Test
    fun `collapse keeps player background until the capsule end of the path`() {
        assertEquals(1f, computeMorphFaceBlend(1f), 0f)
        assertEquals(1f, computeMorphFaceBlend(0.85f), 0f)
        assertEquals(1f, computeMorphFaceBlend(0.5f), 0f)
        assertEquals(1f, computeMorphFaceBlend(0.15f), 0f)
        assertEquals(0.5f, computeMorphFaceBlend(0.075f), 0.001f)
        assertEquals(0f, computeMorphFaceBlend(0f), 0f)
    }

    @Test
    fun `settle velocity approaches zero near capsule to prevent endpoint crossing`() {
        assertEquals(-3f, computeMorphSettleVelocity(1f, 0f, -20f), 0f)
        val nearEnd = computeMorphSettleVelocity(0.01f, 0f, -3f)
        assertTrue(nearEnd > -0.17f && nearEnd < 0f)
        assertEquals(0f, computeMorphSettleVelocity(0f, 0f, -3f), 0f)
    }

    @Test
    fun `precise spring residual leaves less than a pixel of cover displacement`() {
        val nearEnd = computeCoverMorphFrame(0.0001f, start, end, 32f, 8f)!!
        val atEnd = computeCoverMorphFrame(0f, start, end, 32f, 8f)!!
        assertEquals(atEnd.bounds.left, nearEnd.bounds.left, 0.5f)
        assertEquals(atEnd.bounds.top, nearEnd.bounds.top, 0.5f)
    }

    @Test
    fun `frames are null when either anchor is missing`() {
        assertNull(computePanelMorphFrame(0.5f, Rect.Zero, end, 32f, 0f))
        assertNull(computePanelMorphFrame(0.5f, start, Rect.Zero, 32f, 0f))
        assertNull(computeCoverMorphFrame(0.5f, Rect.Zero, end, 20f, 8f))
    }

    @Test
    fun `veil stays hidden until the mid phase`() {
        assertEquals(0f, computeMorphVeil(0f), 0.001f)
        assertEquals(0f, computeMorphVeil(MORPH_VEIL_START), 0.001f)
        assertTrue(computeMorphVeil(0.5f) in 0.6f..0.8f)
        assertEquals(1f, computeMorphVeil(1f), 0.001f)
        assertEquals(1f, computeMorphVeil(MORPH_VEIL_END), 0.001f)
    }
}
