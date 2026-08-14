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
            finishChapterSettingEnabled = true,
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
            finishChapterSettingEnabled = true,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(false, decision.clearTimer)
    }

    @Test
    fun timerArmedForDifferentChapterClearsAndContinues() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 3,
            finishChapterSettingEnabled = true,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun timerDisabledAfterArmingClearsAndContinues() {
        val decision = decideChapterCompletion(
            durChapterIndex = 5,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 5,
            finishChapterSettingEnabled = false,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun chapterAlreadyAdvancedSkipsWithMatchingArmCleared() {
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 5,
            finishChapterSettingEnabled = true,
        )

        assertEquals(ChapterCompletionAction.SKIP, decision.action)
        assertEquals(true, decision.clearTimer)
    }

    @Test
    fun chapterAlreadyAdvancedSkipsWithoutTouchingOtherArms() {
        val decision = decideChapterCompletion(
            durChapterIndex = 6,
            finishedChapterIndex = 5,
            finishChapterAtIndex = 3,
            finishChapterSettingEnabled = true,
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
            finishChapterSettingEnabled = true,
        )

        assertEquals(ChapterCompletionAction.ADVANCE, decision.action)
        assertEquals(false, decision.clearTimer)
    }

}
