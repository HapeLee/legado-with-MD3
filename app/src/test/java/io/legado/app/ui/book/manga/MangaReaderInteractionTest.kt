package io.legado.app.ui.book.manga

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaReaderInteractionTest {

    @Test
    fun `nine grid maps every cell to its configured index`() {
        val expected = (0..8).toList()
        val actual = buildList {
            repeat(3) { row ->
                repeat(3) { column ->
                    add(
                        mangaClickRegionIndex(
                            x = column * 300f + 150f,
                            y = row * 600f + 300f,
                            width = 900,
                            height = 1800,
                        )
                    )
                }
            }
        }

        assertEquals(expected, actual)
    }

    @Test
    fun `nine grid clamps touches on viewport edges`() {
        assertEquals(0, mangaClickRegionIndex(-20f, -20f, 900, 1800))
        assertEquals(8, mangaClickRegionIndex(920f, 1820f, 900, 1800))
    }

    @Test
    fun `click action cycles through chapter menu and page actions`() {
        assertEquals(0, nextMangaClickAction(-1))
        assertEquals(1, nextMangaClickAction(0))
        assertEquals(2, nextMangaClickAction(1))
        assertEquals(-1, nextMangaClickAction(2))
    }

    @Test
    fun `page step returns item target inside chapter`() {
        assertEquals(4, mangaPageStepTarget(currentIndex = 3, itemCount = 8, direction = 1))
        assertEquals(2, mangaPageStepTarget(currentIndex = 3, itemCount = 8, direction = -1))
    }

    @Test
    fun `page step delegates to chapter navigation at list boundaries`() {
        assertNull(mangaPageStepTarget(currentIndex = 0, itemCount = 8, direction = -1))
        assertNull(mangaPageStepTarget(currentIndex = 7, itemCount = 8, direction = 1))
        assertNull(mangaPageStepTarget(currentIndex = 0, itemCount = 0, direction = 1))
    }

    @Test
    fun `adjacent chapter callbacks stay hidden until target chapter finishes`() {
        assertFalse(shouldExposeMangaPages(currentChapterFinished = false))
        assertTrue(shouldExposeMangaPages(currentChapterFinished = true))
    }

    @Test
    fun `chapter switch stays put while current chapter is still visible`() {
        assertEquals(
            MangaChapterSwitch.NONE,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 6,
                currentChapterVisible = true,
            ),
        )
        assertEquals(
            MangaChapterSwitch.NONE,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 4,
                currentChapterVisible = true,
            ),
        )
    }

    @Test
    fun `chapter switch fires only when current chapter fully off-screen`() {
        assertEquals(
            MangaChapterSwitch.NEXT,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 6,
                currentChapterVisible = false,
            ),
        )
        assertEquals(
            MangaChapterSwitch.PREVIOUS,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 4,
                currentChapterVisible = false,
            ),
        )
    }

    @Test
    fun `same chapter never switches regardless of visibility`() {
        assertEquals(
            MangaChapterSwitch.NONE,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 5,
                currentChapterVisible = true,
            ),
        )
        assertEquals(
            MangaChapterSwitch.NONE,
            mangaChapterSwitchDecision(
                currentChapterIndex = 5,
                visibleChapterIndex = 5,
                currentChapterVisible = false,
            ),
        )
    }

    @Test
    fun `zoom pan clamps within zoomed content bounds`() {
        val viewport = IntSize(500, 800)
        // zoom 2、item 宽 500：maxX = (500*2-500)/2 = 250；内容高 2000：maxY = 2000*2-800 = 3200
        assertEquals(
            Offset(250f, -3200f),
            clampZoomPan(
                Offset(9999f, -9999f),
                zoom = 2f,
                itemWidth = 500f,
                contentHeight = 2000f,
                viewport = viewport
            ),
        )
        assertEquals(
            Offset(-250f, 0f),
            clampZoomPan(
                Offset(-9999f, 9999f),
                zoom = 2f,
                itemWidth = 500f,
                contentHeight = 2000f,
                viewport = viewport
            ),
        )
        assertEquals(
            Offset(100f, -500f),
            clampZoomPan(
                Offset(100f, -500f),
                zoom = 2f,
                itemWidth = 500f,
                contentHeight = 2000f,
                viewport = viewport
            ),
        )
    }

    @Test
    fun `zoom pan keeps content when item narrower than viewport`() {
        val viewport = IntSize(500, 800)
        // item 200 宽，放大 2 倍仍只有 400 < 500：不允许横向平移
        assertEquals(
            Offset(0f, -500f),
            clampZoomPan(
                Offset(9999f, -500f),
                zoom = 2f,
                itemWidth = 200f,
                contentHeight = 2000f,
                viewport = viewport
            ),
        )
        // 内容高度未知时不允许平移
        assertEquals(
            Offset.Zero,
            clampZoomPan(
                Offset(100f, -100f),
                zoom = 2f,
                itemWidth = 500f,
                contentHeight = 0f,
                viewport = viewport
            ),
        )
    }
}
