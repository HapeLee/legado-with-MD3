package io.legado.app.help.readaloud.segment

import io.legado.app.domain.model.readaloud.CanonicalSpeechParagraph
import io.legado.app.domain.model.readaloud.SpeechRoleType
import io.legado.app.domain.model.readaloud.SpeechEmotion
import io.legado.app.domain.model.readaloud.SpeechSegmentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedSpeechSegmenterTest {

    @Test
    fun `splits narration and dialogue without losing text`() {
        val paragraph = paragraph(0, "张三停下脚步。“你终于来了！”他看向门口。", 100)

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(
            listOf(SpeechRoleType.Narrator, SpeechRoleType.Character, SpeechRoleType.Narrator),
            result.map { it.roleType },
        )
        assertEquals("“你终于来了！”", result[1].text)
        assertEquals(107, result[1].chapterPosition)
        assertCoverage(listOf(paragraph), result)
    }

    @Test
    fun `keeps short quoted terms in narration`() {
        val paragraph = paragraph(0, "此物名为“青锋”，并非人名。")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(listOf(SpeechRoleType.Narrator), result.map { it.roleType })
        assertCoverage(listOf(paragraph), result)
    }

    @Test
    fun `recognizes quoted thought from local cue`() {
        val paragraph = paragraph(0, "她心想：“这件事绝不能让他知道。”")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(SpeechRoleType.Thought, result.last().roleType)
        assertCoverage(listOf(paragraph), result)
    }

    @Test
    fun `keeps cross paragraph quotes as character speech`() {
        val paragraphs = listOf(
            paragraph(0, "他说：“第一句话没有结束。"),
            paragraph(1, "第二句话现在结束。”随后众人散去。", 20),
        )

        val result = RuleBasedSpeechSegmenter.segment(paragraphs)

        assertEquals(
            SpeechRoleType.Character,
            result.first { it.paragraphIndex == 0 && it.text.startsWith("“") }.roleType,
        )
        assertEquals(
            SpeechRoleType.Character,
            result.first { it.paragraphIndex == 1 }.roleType,
        )
        assertEquals(SpeechRoleType.Narrator, result.last().roleType)
        assertCoverage(paragraphs, result)
    }

    @Test
    fun `splits colon speech cue from spoken text`() {
        val paragraph = paragraph(0, "张三低声道：不要回头。")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(listOf("张三低声道：", "不要回头。"), result.map { it.text })
        assertEquals(
            listOf(SpeechRoleType.Narrator, SpeechRoleType.Character),
            result.map { it.roleType },
        )
        assertCoverage(listOf(paragraph), result)
    }

    @Test
    fun `detects explicit emotion cue for dialogue`() {
        val paragraph = paragraph(0, "张三怒吼：“你立刻给我滚出去！”")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(
            SpeechEmotion.Angry.storageValue,
            result.first { it.roleType == SpeechRoleType.Character }.emotion,
        )
    }

    @Test
    fun `keeps dialogue neutral without explicit evidence`() {
        val paragraph = paragraph(0, "张三说道：“今天我们去城里看看。”")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(
            SpeechEmotion.Neutral.storageValue,
            result.first { it.roleType == SpeechRoleType.Character }.emotion,
        )
    }

    @Test
    fun `returns no segment for empty paragraphs`() {
        val paragraphs = listOf(paragraph(0, ""), paragraph(1, "旁白", 1))

        val result = RuleBasedSpeechSegmenter.segment(paragraphs)

        assertEquals(1, result.size)
        assertEquals(1, result.single().paragraphIndex)
        assertCoverage(paragraphs, result)
    }

    @Test
    fun `does not treat apostrophe inside word as quote`() {
        val paragraph = paragraph(0, "He doesn't know the answer.")

        val result = RuleBasedSpeechSegmenter.segment(listOf(paragraph))

        assertEquals(listOf(SpeechRoleType.Narrator), result.map { it.roleType })
        assertCoverage(listOf(paragraph), result)
    }

    private fun paragraph(index: Int, text: String, chapterPosition: Int = 0) =
        CanonicalSpeechParagraph(index, text, chapterPosition)

    private fun assertCoverage(
        paragraphs: List<CanonicalSpeechParagraph>,
        segments: List<SpeechSegmentDraft>,
    ) {
        paragraphs.forEach { paragraph ->
            val paragraphSegments = segments.filter { it.paragraphIndex == paragraph.index }
            assertEquals(paragraph.text, paragraphSegments.joinToString("") { it.text })
            paragraphSegments.zipWithNext().forEach { (left, right) ->
                assertEquals(left.end, right.start)
            }
            paragraphSegments.forEach { segment ->
                assertTrue(segment.start in 0..paragraph.text.length)
                assertTrue(segment.end in segment.start..paragraph.text.length)
                assertEquals(paragraph.text.substring(segment.start, segment.end), segment.text)
            }
        }
    }
}
