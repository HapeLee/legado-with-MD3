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

    private fun item(
        text: String,
        start: Int,
        paragraph: Int,
        role: SpeechRoleType = SpeechRoleType.Narrator,
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
        voice = null,
        fallbackVoices = emptyList(),
    )
}
