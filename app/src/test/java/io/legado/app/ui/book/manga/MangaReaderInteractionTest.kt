package io.legado.app.ui.book.manga

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.legado.app.ui.book.manga.config.MangaDoublePageMode
import io.legado.app.ui.book.manga.config.MangaZoomStartPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaReaderInteractionTest {

    @Test
    fun `explicit chapter placeholder stays at target and exposes retry after failure`() {
        val loading = mangaChapterLoadingItem(12, "loading", failed = false)
        val failed = mangaChapterLoadingItem(12, "failed", failed = true)

        assertTrue(loading.loading)
        assertEquals(null, loading.retryChapterIndex)
        assertFalse(failed.loading)
        assertEquals(12, failed.retryChapterIndex)
        assertTrue(failed.key.contains("12"))
    }

    private fun page(index: Int, chapter: Int = 0) = MangaReaderItemUi.Page(
        key = "p$index",
        imageUrl = "url$index",
        bookUrl = "book",
        chapterIndex = chapter,
        chapterCount = 2,
        pageIndex = index,
        pageCount = 10,
        chapterName = "chapter",
    )

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
    fun `nine grid resolves the configured action using viewport coordinates`() {
        val actions = listOf(-1, 0, 3, 2, 0, 1, 4, 1, 2)

        assertEquals(3, mangaClickActionAt(actions, 750f, 300f, 900, 1800))
        assertEquals(2, mangaClickActionAt(actions, 150f, 900f, 900, 1800))
        assertEquals(4, mangaClickActionAt(actions, 150f, 1500f, 900, 1800))
    }

    @Test
    fun `click action cycles through chapter menu and page actions`() {
        assertEquals(0, nextMangaClickAction(-1))
        assertEquals(1, nextMangaClickAction(0))
        assertEquals(2, nextMangaClickAction(1))
        assertEquals(3, nextMangaClickAction(2))
        assertEquals(4, nextMangaClickAction(3))
        assertEquals(-1, nextMangaClickAction(4))
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

    @Test
    fun `explicit chapter navigation overrides the retained previous page anchor`() {
        assertTrue(
            shouldForceMangaChapterPosition(
                hasPages = true,
                isLoading = false,
                currentBookUrl = "book",
                targetBookUrl = "book",
                pendingExplicitChapterIndex = 15,
                targetChapterIndex = 15,
            )
        )
        assertFalse(
            shouldForceMangaChapterPosition(
                hasPages = true,
                isLoading = false,
                currentBookUrl = "book",
                targetBookUrl = "book",
                pendingExplicitChapterIndex = null,
                targetChapterIndex = 15,
            )
        )
    }

    @Test
    fun `double page spreads never pair across chapter boundaries`() {
        val items = listOf(page(0), page(1), page(2), page(0, 1), page(1, 1))
        assertEquals(
            listOf(listOf(0, 1), listOf(2), listOf(3, 4)),
            buildMangaSpreads(items, doublePage = true).map { it.itemIndices },
        )
    }

    @Test
    fun `chapter edge stays on its own spread`() {
        val items = listOf(
            page(0),
            MangaReaderItemUi.ChapterEdge("edge", "next"),
            page(1),
        )
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2)),
            buildMangaSpreads(items, true).map { it.itemIndices },
        )
    }

    @Test
    fun `wide pages stay on their own double page spread`() {
        val items = listOf(page(0), page(1), page(2), page(3))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3)),
            buildMangaSpreads(
                items = items,
                doublePage = true,
                aspectRatios = mapOf("p0" to 1.5f, "p3" to 2f),
            ).map { it.itemIndices },
        )
    }

    @Test
    fun `spread identity follows its page composition`() {
        val items = listOf(page(0), page(1))
        val paired = buildMangaSpreads(items, doublePage = true).single()
        val separated = buildMangaSpreads(
            items,
            doublePage = true,
            aspectRatios = mapOf("p0" to 2f),
        )
        assertEquals(listOf(0, 1), paired.itemIndices)
        assertEquals(listOf(listOf(0), listOf(1)), separated.map { it.itemIndices })
        assertTrue(paired.key.contains("p0"))
        assertTrue(paired.key.contains("p1"))
    }

    @Test
    fun `chapter cover can stay on a single spread`() {
        val items = listOf(page(0), page(1), page(2), page(3))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3)),
            buildMangaSpreads(items, doublePage = true, coverSingle = true)
                .map { it.itemIndices },
        )
    }

    @Test
    fun `shift pairing leaves first page of every chapter single`() {
        val items = listOf(page(0), page(1), page(2), page(0, 1), page(1, 1))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3), listOf(4)),
            buildMangaSpreads(items, doublePage = true, shiftPairing = true)
                .map { it.itemIndices },
        )
    }

    @Test
    fun `wide page split follows reading direction`() {
        val items = listOf(page(0))
        val ratios = mapOf("p0" to 2f)
        val leftToRight = buildMangaSpreads(
            items,
            doublePage = true,
            aspectRatios = ratios,
            splitWidePages = true,
        )
        val rightToLeft = buildMangaSpreads(
            items,
            doublePage = true,
            aspectRatios = ratios,
            splitWidePages = true,
            splitRightToLeft = true,
        )
        assertEquals(
            listOf(MangaPageSlice.LEFT, MangaPageSlice.RIGHT),
            leftToRight.map { it.slots.single().slice })
        assertEquals(
            listOf(MangaPageSlice.RIGHT, MangaPageSlice.LEFT),
            rightToLeft.map { it.slots.single().slice })
    }

    @Test
    fun `automatic zoom starts on reading direction side`() {
        val size = IntSize(100, 200)
        assertEquals(Offset(50f, 0f), zoomStartOffset(MangaZoomStartPosition.AUTOMATIC, size, 2f, false))
        assertEquals(Offset(-50f, 0f), zoomStartOffset(MangaZoomStartPosition.AUTOMATIC, size, 2f, true))
    }

    @Test
    fun `landscape double page only activates for wide viewport`() {
        assertTrue(isDoublePageActive(MangaDoublePageMode.LANDSCAPE, IntSize(1000, 600)))
        assertFalse(isDoublePageActive(MangaDoublePageMode.LANDSCAPE, IntSize(600, 1000)))
    }
}
