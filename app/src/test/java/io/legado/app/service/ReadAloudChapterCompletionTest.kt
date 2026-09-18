package io.legado.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAloudChapterCompletionTest {

    @Test
    fun timerArmedForThisChapterStopsAtChapterEnd() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 5,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.STOP, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun noTimerArmedContinuesToNextChapter() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(false, decision.clearTimer)
        assertEquals(null, decision.remainingChapters)
    }

    @Test
    fun timerArmedForDifferentChapterClearsAndContinues() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 3,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun armedChapterFinishingWhileReaderBrowsedAheadStillStops() {
        // 脱离浏览：页面已翻到第 6 章，朗读仍在第 5 章；定时臂标锚定的是
        // 朗读中的第 5 章 —— 本章自然读完必须停止，不能被误判为"章节已推进"
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 5,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.STOP, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun chapterAlreadyAdvancedSkipsWithoutTouchingOtherArms() {
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 3,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.SKIP, decision.action)
        assertEquals(false, decision.clearTimer)
    }

    @Test
    fun lateDuplicateCompletionAfterAdvanceSkipsInsteadOfDoubleAdvancing() {
        // 双重触发竞态：首次完结已 ADVANCE 且臂标为空，迟到的同章完结
        // 不得再次推进（否则跳过下一章）
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.SKIP, decision.action)
        assertEquals(false, decision.clearTimer)
    }

    @Test
    fun chapterIndexZeroDoesNotCollideWithSentinel() {
        val decision = decideChapterCompletion(
            durChapterIndex = 0,
            finishedChapterIndex = 0,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = null,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(false, decision.clearTimer)
    }

    @Test
    fun chapterQuotaOneStopsAtFirstChapterBoundary() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = 1,
        )

        assertEquals(ChapterCompletionAction.STOP, decision.action)
        assertEquals(0, decision.remainingChapters)
    }

    @Test
    fun chapterQuotaDecrementsAndContinuesUntilExhausted() {
        val first = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = 3,
        )
        assertEquals(ChapterCompletionAction.ADVANCE, first.action)
        assertEquals(2, first.remainingChapters)

        val second = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 6,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = first.remainingChapters,
        )
        assertEquals(ChapterCompletionAction.ADVANCE, second.action)
        assertEquals(1, second.remainingChapters)

        val third = decideChapterCompletion(
            durChapterIndex = 7,
            finishedChapterIndex = 7,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = second.remainingChapters,
        )
        assertEquals(ChapterCompletionAction.STOP, third.action)
        assertEquals(0, third.remainingChapters)
    }

    @Test
    fun raceSkipDoesNotConsumeChapterQuota() {
        // 竞态下这一章的完结不属于本次朗读推进，配额不能被白扣
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = NO_FINISH_CHAPTER,
            chapterQuota = 3,
        )

        assertEquals(ChapterCompletionAction.SKIP, decision.action)
        assertEquals(null, decision.remainingChapters)
    }
}
