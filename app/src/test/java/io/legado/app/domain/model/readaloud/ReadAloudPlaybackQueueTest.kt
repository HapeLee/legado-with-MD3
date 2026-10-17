package io.legado.app.domain.model.readaloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudPlaybackQueueTest {

    private val queue = ReadAloudPlaybackQueue.from(
        listOf(
            item("旁白", 10, 0),
            item("“你好”", 12, 0, SpeechRoleType.Character),
            item("回答。", 30, 1),
        )
    )

    @Test
    fun `finds cue and offset by absolute chapter position`() {
        assertEquals(ReadAloudPlaybackCursor(0, 1), queue.cursorAt(11))
        assertEquals(ReadAloudPlaybackCursor(1, 2), queue.cursorAt(14))
    }

    @Test
    fun `moves gaps to next cue and clamps after chapter content`() {
        assertEquals(ReadAloudPlaybackCursor(2, 0), queue.cursorAt(20))
        assertEquals(ReadAloudPlaybackCursor(2, 3), queue.cursorAt(100))
    }

    @Test
    fun `navigates without paragraph assumptions`() {
        val middle = ReadAloudPlaybackCursor(1, 2)
        assertEquals(ReadAloudPlaybackCursor(0, 0), queue.previous(middle))
        assertEquals(ReadAloudPlaybackCursor(2, 0), queue.next(middle))
        assertNull(queue.previous(ReadAloudPlaybackCursor(0, 0)))
        assertNull(queue.next(ReadAloudPlaybackCursor(2, 0)))
    }

    @Test
    fun `chapter title is first playback cue but excluded from position lookup`() {
        val titledQueue = queue.withChapterTitle(" 第 1 章 ")

        assertEquals("第 1 章", titledQueue.cues.first().text)
        assertEquals(true, titledQueue.cues.first().isChapterTitle)
        assertEquals(1, titledQueue.leadingTitleCueCount)
        assertEquals(ReadAloudPlaybackCursor(1, 0), titledQueue.cursorAt(10))
        assertEquals(
            ReadAloudPlaybackCursor(1, 0),
            titledQueue.next(ReadAloudPlaybackCursor(0, 0)),
        )
    }

    @Test
    fun `empty playback queue does not become title-only queue`() {
        assertEquals(true, ReadAloudPlaybackQueue.Empty.withChapterTitle("标题").isEmpty)
    }

    /** 开多角色朗读时标题要跟旁白同一个人念；没给旁白音就还是引擎默认发音人。 */
    @Test
    fun `chapter title is voiced by the narrator voice taken from the plan`() {
        val narrator = ReadAloudVoice(
            id = "narrator",
            engineType = "http",
            engineId = "narrator",
            speakerId = "",
            displayName = "旁白",
        )
        val titled = ReadAloudPlaybackQueue
            .from(listOf(item("旁白", 10, 0, voice = narrator)))
            .withChapterTitle("第 1 章")

        assertEquals(narrator, titled.narratorVoice())
        assertEquals(narrator, titled.cues.first().voice)
        assertNull(queue.withChapterTitle("第 1 章").cues.first().voice)
    }

    private fun item(
        text: String,
        start: Int,
        paragraph: Int,
        role: SpeechRoleType = SpeechRoleType.Narrator,
        voice: ReadAloudVoice? = null,
    ): SpeechPlanItem = SpeechPlanItem(
        segment = ChapterSpeechSegment(
            id = "$start",
            analysisId = "analysis",
            bookUrl = "book",
            chapterIndex = 1,
            paragraphIndex = paragraph,
            start = 0,
            end = text.length,
            chapterPosition = start,
            text = text,
            roleType = role,
            source = SpeechResolutionSource.Rule,
        ),
        voice = voice,
        fallbackVoices = emptyList(),
    )
}
