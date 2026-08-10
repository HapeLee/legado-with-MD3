package io.legado.app.ui.book.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
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
}
