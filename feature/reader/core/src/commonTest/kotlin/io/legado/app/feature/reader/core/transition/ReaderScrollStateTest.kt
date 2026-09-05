package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderScrollStateTest {
    private val style = ReaderTextStyle(0, 16f)

    @Test fun crossingNextCarriesRemainder() {
        val result = ReaderScrollPolicy.apply(-900f, -250f, 1000f, 1000f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.NEXT, result.crossing)
        assertEquals(-150f, result.offsetPx)
    }

    @Test fun crossingPreviousUsesPreviousExtent() {
        val result = ReaderScrollPolicy.apply(-20f, 80f, 900f, 1000f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.PREVIOUS, result.crossing)
        assertEquals(-840f, result.offsetPx)
    }

    @Test fun finalPageClampsBottomToViewport() {
        val result = ReaderScrollPolicy.apply(-300f, -900f, 1000f, 1200f, 800f, true, false)
        assertTrue(result.hitBoundary)
        assertEquals(-400f, result.offsetPx)
    }

    @Test fun crossingRemainderStaysWithinTheReplacementPageExtent() {
        // 跨页后携带的余量必须落在新当前页的滚动范围内：同步换窗后渲染层直接以
        // 该余量继续滚动，不允许出现越界空档或增量丢弃。单帧位移以一个视口为上限。
        repeat(50) { seed ->
            val currentExtent = 800f + seed * 7f
            val nextExtent = 700f + seed * 3f
            val overshoot = 1f + (seed % 5) * 150f
            val delta = -(currentExtent - 300f + overshoot)
            val result = ReaderScrollPolicy.apply(
                -300f, delta, 900f, currentExtent, 800f, true, true,
            )
            assertEquals(ReaderScrollCrossing.NEXT, result.crossing)
            assertTrue(result.offsetPx < 0f)
            assertTrue(result.offsetPx > -nextExtent)
        }
    }

    @Test fun oversizedDeltaCrossesAgainOnTheNextFrame() {
        // 超过两页的极端步长（仅程序化滚动可能触达）：余量超出下一页范围时，
        // 下一帧按同一策略继续折算跨页，输入不丢失也不冻结。
        val first = ReaderScrollPolicy.apply(-300f, -2500f, 900f, 1000f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.NEXT, first.crossing)
        val second = ReaderScrollPolicy.apply(first.offsetPx, 0f, 800f, 900f, 800f, true, true)
        assertEquals(ReaderScrollCrossing.NEXT, second.crossing)
    }

    @Test fun textPageStepsKeepOneVisibleRowInBothDirections() {
        val page = scrollPage(
            listOf(
                text(0f, 20f, 0),
                text(20f, 40f, 1),
                text(40f, 60f, 2),
                text(60f, 80f, 3),
            ),
        )

        assertEquals(-60f, ReaderScrollPolicy.pageStep(page, 0f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(60f, ReaderScrollPolicy.pageStep(page, 0f, ReaderTurnDirection.PREVIOUS), 0f)
    }

    @Test fun visibleRowsAreCalculatedAfterTheCurrentScrollOffset() {
        val page = scrollPage(
            listOf(
                text(0f, 20f, 0),
                text(20f, 40f, 1),
                text(80f, 100f, 2),
                text(140f, 160f, 3),
            ),
        )

        assertEquals(-40f, ReaderScrollPolicy.pageStep(page, -40f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(20f, ReaderScrollPolicy.pageStep(page, -40f, ReaderTurnDirection.PREVIOUS), 0f)
    }

    @Test fun nonInlineImageAndEmptyPagesUseAFullViewportStep() {
        val image = ReaderElement.Image(ReaderRect(0f, 10f, 80f, 70f), "image", null)
        val imagePage = scrollPage(listOf(image), inlineImages = false)
        val emptyPage = scrollPage(emptyList())

        assertEquals(-80f, ReaderScrollPolicy.pageStep(imagePage, 0f, ReaderTurnDirection.NEXT), 0f)
        assertEquals(80f, ReaderScrollPolicy.pageStep(imagePage, 0f, ReaderTurnDirection.PREVIOUS), 0f)
        assertEquals(-80f, ReaderScrollPolicy.pageStep(emptyPage, 0f, ReaderTurnDirection.NEXT), 0f)
    }

    @Test fun tapStepAtPageBottomContinuesIntoTheNextPage() {
        // 滚到页尾（只剩当前页末行可见、下一页顶部已露出）时，合成可视页的末行
        // 在下一页里，点按下一页应跨过页边界继续滚，而不是停在页尾只挪几像素。
        val rows = listOf(
            text(0f, 20f, 0),
            text(20f, 40f, 1),
            text(40f, 60f, 2),
            text(60f, 80f, 3),
        )
        val current = scrollPage(rows, extent = 80f)
        val next = scrollPage(rows, extent = 80f)

        val withNext = ReaderScrollPolicy.pageStep(current, -70f, ReaderTurnDirection.NEXT, next = next)
        assertEquals(-70f, withNext, 0f)

        // 对照：不提供邻页时保持旧行为（只在当前页内折算，页尾步距退化为整视口钳制）。
        val withoutNext = ReaderScrollPolicy.pageStep(current, -70f, ReaderTurnDirection.NEXT)
        assertEquals(-80f, withoutNext, 0f)
    }

    @Test fun tapStepAtPageTopContinuesIntoThePreviousPage() {
        // 跨页余量为正（页顶露出上一页末行）时，合成可视页的首行在上一页里，
        // 点按上一页的步距把该行对齐到视口底。
        val rows = listOf(
            text(0f, 20f, 0),
            text(20f, 40f, 1),
            text(40f, 60f, 2),
            text(60f, 80f, 3),
        )
        val current = scrollPage(rows, extent = 80f)
        val previous = scrollPage(rows, extent = 80f)

        val withPrevious = ReaderScrollPolicy.pageStep(
            current, 10f, ReaderTurnDirection.PREVIOUS, previous = previous,
        )
        assertEquals(70f, withPrevious, 0f)

        val withoutPrevious = ReaderScrollPolicy.pageStep(current, 10f, ReaderTurnDirection.PREVIOUS)
        assertEquals(50f, withoutPrevious, 0f)
    }

    private fun scrollPage(
        elements: List<ReaderElement>,
        inlineImages: Boolean = true,
        extent: Float = 200f,
    ) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "",
        text = "",
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 10f,
        contentBottomPx = 90f,
        elements = elements,
        revision = 1,
        scrollExtentPx = extent,
        inlineImagesPreserveScrollLine = inlineImages,
    )

    private fun text(top: Float, bottom: Float, position: Int) = ReaderElement.Text(
        bounds = ReaderRect(0f, top + 10f, 10f, bottom + 10f),
        baselinePx = bottom + 5f,
        value = position.toString(),
        style = style,
        selected = false,
        emphasized = false,
        chapterPosition = position,
    )
}
