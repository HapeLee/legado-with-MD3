package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class TTSReadAloudProgressTest {

    @Test
    fun nextParagraphAdvancesFromCurrentRangePosition() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 180,
                paragraphLength = 100,
                paragraphStartPosition = 80,
            )
        )
    }

    @Test
    fun nextParagraphPreservesInitialSelectionOffset() {
        assertEquals(
            201,
            nextParagraphPosition(
                currentPosition = 120,
                paragraphLength = 100,
                paragraphStartPosition = 20,
            )
        )
    }

    @Test
    fun rangeProgressUsesTheUtteranceStartSnapshot() {
        assertEquals(
            340,
            currentRangePosition(
                utteranceStartPosition = 200,
                rangeStart = 140,
            )
        )
    }

    @Test
    fun longParagraphRangeCanCrossSeveralPages() {
        val pageStarts = listOf(0, 100, 200, 300, 400)

        assertEquals(
            3,
            findReadAloudPageIndex(
                currentPageIndex = 0,
                chapterPosition = 350,
                pageCount = pageStarts.size,
                pageStart = pageStarts::get,
            )
        )
    }

    @Test
    fun pageBoundaryChangesAreUsedAfterRelayout() {
        var pageStarts = listOf(0, 100, 200, 300)
        val pageStart = { index: Int -> pageStarts[index] }

        assertEquals(
            2,
            findReadAloudPageIndex(0, 250, pageStarts.size, pageStart)
        )

        pageStarts = listOf(0, 180, 360)

        assertEquals(
            1,
            findReadAloudPageIndex(0, 250, pageStarts.size, pageStart)
        )
    }

    @Test
    fun exactPageBoundaryWaitsForPlaybackToEnterThePage() {
        val pageStarts = listOf(0, 100, 200)

        assertEquals(
            0,
            findReadAloudPageIndex(0, 100, pageStarts.size, pageStarts::get)
        )
        assertEquals(
            1,
            findReadAloudPageIndex(0, 101, pageStarts.size, pageStarts::get)
        )
    }

    @Test
    fun mediaProgressEstimatesTimeFromCharactersAndSpeechRate() {
        assertEquals(1_000L, estimatedReadAloudTimeMs(4, 4f))
        assertEquals(2_000L, estimatedReadAloudTimeMs(8, 4f))
        assertEquals(2_000L, estimatedReadAloudTimeMs(4, 2f))
        assertEquals(0L, estimatedReadAloudTimeMs(0, 4f))
        assertEquals(0L, estimatedReadAloudTimeMs(10, 0f))
        assertEquals(0L, estimatedReadAloudTimeMs(-5, 4f))
    }

    @Test
    fun mediaProgressStaysMonotonicWhilePlayingAndFreezesOnPause() {
        // PlaybackStateCompat: STATE_PLAYING = 3, STATE_PAUSED = 2, STATE_STOPPED = 1
        val playing = 3
        val paused = 2
        val stopped = 1
        // 估算值落后时按墙钟推进, 不回退
        assertEquals(
            11_000L,
            nextMediaSessionPositionMs(playing, playing, 9_000, 10_000, 61_000, 60_000)
        )
        // 估算值领先时跟随估算值
        assertEquals(
            12_000L,
            nextMediaSessionPositionMs(playing, playing, 12_000, 10_000, 61_000, 60_000)
        )
        // 播放转暂停: 冻结在系统插值的显示位置
        assertEquals(
            11_500L,
            nextMediaSessionPositionMs(paused, playing, 9_000, 10_000, 61_500, 60_000)
        )
        // 已暂停保持不变
        assertEquals(
            11_500L,
            nextMediaSessionPositionMs(paused, paused, 9_000, 11_500, 70_000, 61_500)
        )
        // 暂停转播放: 从冻结位置继续, 不包含暂停时长
        assertEquals(
            11_500L,
            nextMediaSessionPositionMs(playing, paused, 9_000, 11_500, 70_000, 61_500)
        )
        // 停止等其他状态用估算值
        assertEquals(
            9_000L,
            nextMediaSessionPositionMs(stopped, playing, 9_000, 10_000, 61_000, 60_000)
        )
        // 进度回退后锚点失效, 直接用估算值
        assertEquals(
            2_000L,
            nextMediaSessionPositionMs(playing, playing, 2_000, -1, 61_000, 60_000)
        )
    }

}
