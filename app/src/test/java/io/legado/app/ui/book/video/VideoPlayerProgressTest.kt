package io.legado.app.ui.book.video

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerProgressTest {

    @Test
    fun clampsSeekPositionToDuration() {
        assertEquals(9_000L, clampSeekPosition(12_000L, 9_000L))
    }

    @Test
    fun keepsValidSeekPosition() {
        assertEquals(3_000L, clampSeekPosition(3_000L, 9_000L))
    }

    @Test
    fun handlesUnknownDuration() {
        assertEquals(3_000L, clampSeekPosition(3_000L, 0L))
    }

    @Test
    fun previousChapterAtIndexZeroReturnsSameIndex() {
        assertEquals(0, previousChapterIndex(0))
    }

    @Test
    fun nextChapterStopsAtLastIndex() {
        assertEquals(9, nextChapterIndex(9, 10))
    }

    @Test
    fun doesNotSaveBeforeMediaItemIsLoaded() {
        assertEquals(false, shouldSaveProgress(0))
    }

    @Test
    fun savesAfterMediaItemIsLoaded() {
        assertEquals(true, shouldSaveProgress(1))
    }
}
