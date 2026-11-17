package io.legado.app.domain.model.readaloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterTitleSpeechTest {

    @Test
    fun `reads chapter title at chapter start when enabled`() {
        assertTrue(shouldSpeakChapterTitle(true, "第 1 章", true, true))
    }

    @Test
    fun `skips chapter title at chapter start when disabled`() {
        assertFalse(shouldSpeakChapterTitle(false, "第 1 章", true, true))
    }

    @Test
    fun `volume only chapter reads its title under any setting`() {
        assertTrue(shouldSpeakChapterTitle(true, "第一卷", false, true))
        assertTrue(shouldSpeakChapterTitle(false, "第一卷", false, true))
    }

    @Test
    fun `resuming in the middle never inserts a title cue`() {
        assertFalse(shouldSpeakChapterTitle(true, "第 1 章", true, false))
    }

    @Test
    fun `jumping to the last paragraph skips the title`() {
        assertFalse(shouldSpeakChapterTitle(true, "第 1 章", true, true, moveToLast = true))
    }

    @Test
    fun `blank title is never spoken`() {
        assertFalse(shouldSpeakChapterTitle(true, "   ", true, true))
        assertFalse(shouldSpeakChapterTitle(true, "", false, true))
    }
}
