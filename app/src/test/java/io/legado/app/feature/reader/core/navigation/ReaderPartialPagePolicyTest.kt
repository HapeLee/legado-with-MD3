package io.legado.app.feature.reader.core.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPartialPagePolicyTest {

    @Test
    fun nextChapterPublishesOnlyItsFirstTwoPagesLikeTheViewReader() {
        // offset = 1：旧 loadContent 在 `page.index > 1` 时停止提前重绘。
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(1, 0, 0, false, false))
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(1, 1, 0, false, false))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(1, 2, 0, false, false))
    }

    @Test
    fun previousChapterIsNotPublishedEarly() {
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(-1, 0, 0, true, true))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(-1, 3, 3, false, true))
    }

    @Test
    fun currentChapterPublishesThePageThatHoldsTheReadingPosition() {
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(0, 7, 0, true, false))
        // 分页模式没有别的触发条件：不是当前阅读页就不重绘。
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 1, 5, false, false))
    }

    @Test
    fun scrollModeKeepsThreePagesOfLookahead() {
        // 旧 `max(index - 3, 0) < durPageIndex`：当前第 5 页时，成型到第 7 页仍余 3 页。
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(0, 7, 5, false, true))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 8, 5, false, true))
        // 当前首页时余量从 0 算起：`max(index-3,0) < 0` 恒为假，前几页成型都不触发，
        // 旧 View 这时只靠"含 durChapterPos 的页成型"这条。
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 0, 0, false, true))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 3, 0, false, true))
        // 当前第 1 页时，第 3 页成型仍在余量内（`max(0,0) < 1`）。
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(0, 3, 1, false, true))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 4, 1, false, true))
    }

    @Test
    fun streamingChapterRefusesToSkipItsRemainingPages() {
        // 本章还在排：向前翻会跳过没成型的页。
        assertTrue(
            !ReaderPartialPagePolicy.allowsChapterMove(
                fromChapterIndex = 4,
                targetChapterIndex = 5,
                fromChapterStreaming = true,
                targetChapterStreaming = false,
            )
        )
        assertTrue(
            ReaderPartialPagePolicy.allowsChapterMove(
                fromChapterIndex = 4,
                targetChapterIndex = 5,
                fromChapterStreaming = false,
                targetChapterStreaming = false,
            )
        )
    }

    @Test
    fun streamingPreviousChapterRefusesBackwardEntry() {
        // 旧 `moveToPrev`：`prevChapter.isCompleted == false` 时拒绝后退。
        assertTrue(
            !ReaderPartialPagePolicy.allowsChapterMove(
                fromChapterIndex = 4,
                targetChapterIndex = 3,
                fromChapterStreaming = false,
                targetChapterStreaming = true,
            )
        )
        assertTrue(
            ReaderPartialPagePolicy.allowsChapterMove(
                fromChapterIndex = 4,
                targetChapterIndex = 3,
                fromChapterStreaming = false,
                targetChapterStreaming = false,
            )
        )
    }

    @Test
    fun withinChapterTurnsAreAlwaysAllowed() {
        assertTrue(
            ReaderPartialPagePolicy.allowsChapterMove(
                fromChapterIndex = 4,
                targetChapterIndex = 4,
                fromChapterStreaming = true,
                targetChapterStreaming = true,
            )
        )
    }
}
