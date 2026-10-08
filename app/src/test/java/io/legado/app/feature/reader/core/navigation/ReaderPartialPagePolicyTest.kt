package io.legado.app.feature.reader.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPartialPagePolicyTest {

    @Test
    fun streamedChapterPreviewsOnlyItsOwnPages() {
        assertTrue(ReaderPartialPagePolicy.canPreviewPage(4, 4, true))
        assertTrue(!ReaderPartialPagePolicy.canPreviewPage(4, 5, true))
        assertTrue(!ReaderPartialPagePolicy.canPreviewPage(4, 3, true))
        assertTrue(ReaderPartialPagePolicy.canPreviewPage(4, 5, false))
    }

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
        // View 的页表可原地读取新页；Canvas 必须把刚成型的相邻页发布进窗口。
        assertTrue(ReaderPartialPagePolicy.shouldPublishPage(0, 6, 5, false, false))
        assertTrue(!ReaderPartialPagePolicy.shouldPublishPage(0, 7, 5, false, false))
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
    fun loadingPlaceholderIsPublishedForAnotherChapterEvenWithUnchangedText() {
        // 目标章正文没到位、窗口还停在别的章：照旧发占位页。
        assertTrue(
            ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = false,
                visibleChapterIndex = 3,
                visibleIsPlaceholder = false,
                visibleText = "上一章正文",
                messageText = null,
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun loadingPlaceholderIsNotRepublishedWhenTheVisibleOneIsUpToDate() {
        assertTrue(
            !ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = false,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = true,
                visibleText = "加载数据中…",
                messageText = null,
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun changedFailureMessageIsRepublished() {
        // ReadBook.msg 变成失败原因后必须重发，否则页面一直停在“加载中”，看不到原因。
        assertTrue(
            ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = false,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = true,
                visibleText = "加载数据中…",
                messageText = "加载失败\nFileNotFoundException: book.txt",
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun messagePreemptsLoadedChapterLikeTheViewReader() {
        // 旧 View `curPage` 先看 msg：正文已排好也要整页换成消息页（“目录更新中”“换源中”）。
        assertTrue(
            ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = true,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = false,
                visibleText = "正文",
                messageText = "目录更新中…",
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun sameMessagePageIsNotRepublished() {
        assertTrue(
            !ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = true,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = true,
                visibleText = "目录更新中…",
                messageText = "目录更新中…",
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun loadedChapterKeepsItsRealPageWhenNoMessageIsSet() {
        assertTrue(
            !ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = true,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = false,
                visibleText = "正文",
                messageText = null,
                placeholderText = "加载数据中…",
            )
        )
    }

    @Test
    fun realPageOfTheTargetChapterIsNotReplacedWithoutAMessage() {
        // 没有消息时不把真实页换成占位页（Canvas 按批成型，避免误导性加载屏）。
        assertTrue(
            !ReaderPartialPagePolicy.shouldPublishLoadingPlaceholder(
                targetChapterIndex = 4,
                currentInputReady = false,
                visibleChapterIndex = 4,
                visibleIsPlaceholder = false,
                visibleText = "正文",
                messageText = null,
                placeholderText = "加载数据中…",
            )
        )
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

    @Test
    fun chapterTurnKeepsTheNewCurrentChaptersStreamedPages() {
        // 旧 `TextChapter.isLayoutRunning`："还在排"时已排出的页继续可用，换章不摘掉它们。
        assertEquals(
            5,
            ReaderPartialPagePolicy.retainedStreamedChapter(
                chapterIndex = 5,
                environmentChanged = false,
                streamedChapters = setOf(4, 5, 6),
            )
        )
    }

    @Test
    fun chapterWithoutStreamedPagesKeepsNothing() {
        // 新当前章还没有部分页：没有可承接的页，不改变原有行为。
        assertNull(
            ReaderPartialPagePolicy.retainedStreamedChapter(
                chapterIndex = 5,
                environmentChanged = false,
                streamedChapters = setOf(4, 6),
            )
        )
    }

    @Test
    fun reflowDropsStreamedPagesOfEveryChapter() {
        // 排版环境变化（旧 `TextChapter.isLayoutSizeMatch()` 失败）：旧几何失效，整批重排。
        assertNull(
            ReaderPartialPagePolicy.retainedStreamedChapter(
                chapterIndex = 5,
                environmentChanged = true,
                streamedChapters = setOf(5),
            )
        )
    }
}
