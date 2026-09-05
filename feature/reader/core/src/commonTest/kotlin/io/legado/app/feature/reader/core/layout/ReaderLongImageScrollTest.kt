package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.transition.ReaderScrollCrossing
import io.legado.app.feature.reader.core.transition.ReaderScrollPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLongImageScrollTest {
    private val paged = ReaderPaginationConfig(
        0, "", 100, 100, 0f, 0f, 0f, 0f, 10f, 8f,
    )
    private val fullImage = ReaderMeasuredBlock.Image(
        "long", 50f, 200f, 0, scaleMode = ReaderImageScaleMode.FIT_WIDTH,
    )

    @Test fun continuousFullImageKeepsItsWidthScaledHeightAndHitArea() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(fullImage), paged.copy(continuousScroll = true),
        ).single()
        val image = page.elements.single() as ReaderElement.Image

        assertEquals(100f, image.bounds.width, 0f)
        assertEquals(400f, image.bounds.height, 0f)
        assertEquals(400f, page.scrollExtentPx, 0f)
        assertEquals(100, page.heightPx)
        assertEquals(image, page.elementAt(50f, 300f))
    }

    @Test fun pagedFullImageStillFitsInsideOneViewport() {
        val page = ReaderPaginator.paginateBlocks(listOf(fullImage), paged).single()
        val image = page.elements.single() as ReaderElement.Image

        assertEquals(25f, image.bounds.width, 0f)
        assertEquals(100f, image.bounds.height, 0f)
        assertEquals(100f, page.scrollExtentPx, 0f)
    }

    @Test fun contentFollowingAFullLongImageStartsANewScrollPage() {
        val paragraph = ReaderMeasuredParagraph(
            "甲", listOf("甲"), listOf(10f), ReaderTextStyle(0, 10f), 1,
        )
        val pages = ReaderPaginator.paginateBlocks(
            listOf(fullImage, ReaderMeasuredBlock.Paragraph(paragraph)),
            paged.copy(continuousScroll = true),
        )

        assertEquals(2, pages.size)
        assertEquals(400f, pages.first().scrollExtentPx, 0f)
        assertEquals(100f, pages.last().scrollExtentPx, 0f)
        assertTrue(pages.last().elements.single() is ReaderElement.Text)
    }

    @Test fun continuousPagesExcludeFixedViewportChromeFromTheirStackingExtent() {
        val paragraph = ReaderMeasuredParagraph(
            "甲", listOf("甲"), listOf(10f), ReaderTextStyle(0, 10f), 1,
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.Paragraph(paragraph)),
            paged.copy(
                paddingTopPx = 10f,
                paddingBottomPx = 20f,
                continuousScroll = true,
            ),
        ).single()

        assertEquals(70f, page.scrollExtentPx, 0f)
        assertEquals(10f, page.contentTopPx, 0f)
        assertEquals(80f, page.contentBottomPx, 0f)
    }

    @Test fun scrollReducerTraversesTheLongExtentBeforeCrossing() {
        val within = ReaderScrollPolicy.apply(0f, -250f, 100f, 400f, 100f, false, true)
        assertNull(within.crossing)
        assertEquals(-250f, within.offsetPx, 0f)

        val crossing = ReaderScrollPolicy.apply(-350f, -100f, 100f, 400f, 100f, false, true)
        assertEquals(ReaderScrollCrossing.NEXT, crossing.crossing)
        assertEquals(-50f, crossing.offsetPx, 0f)
    }

    @Test fun finalLongImageClampsWithItsBottomAtTheViewportBottom() {
        val result = ReaderScrollPolicy.apply(-250f, -100f, 100f, 400f, 100f, false, false)
        assertTrue(result.hitBoundary)
        assertEquals(-300f, result.offsetPx, 0f)
    }
}
