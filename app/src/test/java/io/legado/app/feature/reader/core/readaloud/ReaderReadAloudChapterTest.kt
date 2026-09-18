package io.legado.app.feature.reader.core.readaloud

import io.legado.app.domain.model.readaloud.ContentSplitPolicies
import io.legado.app.domain.model.settings.ReadAloudContentSplitMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderReadAloudChapterTest {
    @Test
    fun buildsParagraphAndPageSplitViewsInCanvasPositionSpace() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 3,
            title = "第三章",
            semanticContent = "甲乙丙丁\n戊己\n",
            pageStarts = listOf(0, 2, 5),
        )

        assertEquals(
            listOf("甲乙丙丁", "戊己"),
            chapter.paragraphs(splitByPage = false).map { it.text })
        assertEquals(
            listOf("甲乙", "丙丁", "戊己"),
            chapter.paragraphs(splitByPage = true).map { it.text })
        assertEquals(
            listOf(false, true, true),
            chapter.paragraphs(splitByPage = true).map { it.isParagraphEnd },
        )
        assertEquals(
            listOf(0, 2, 5),
            chapter.paragraphs(splitByPage = true).map { it.chapterPosition })
        assertEquals(7, chapter.chapterLength)
        assertEquals(1, chapter.pageIndexAt(4))
        assertEquals(0, chapter.paragraphIndexAtOrAfter(4, splitByPage = false))
        assertEquals(ReadAloudContentSplitMode.Paragraph, chapter.contentSplitMode)
    }

    @Test
    fun canonicalSpeechParagraphsUseDisplayedTextPositions() {
        val chapter = ReaderReadAloudChapter.create(0, "", "袮甲\n乙\uFFFC\n", listOf(0))
        val paragraphs = chapter.canonicalSpeechParagraphs(splitByPage = false)
        assertEquals(" 甲", paragraphs[0].text)
        assertEquals("乙 ", paragraphs[1].text)
        assertEquals(3, paragraphs[1].chapterPosition)
    }

    @Test
    fun defaultSplitModeCreatesOneUnitPerSentence() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "他来了。她走了！\n下一段。\n",
            pageStarts = listOf(0),
            contentSplitMode = ReadAloudContentSplitMode.Default,
        )

        assertEquals(
            listOf("他来了。", "她走了！", "下一段。"),
            chapter.paragraphs(splitByPage = false).map { it.text },
        )
        assertEquals(
            listOf(0, 4, 9),
            chapter.paragraphs(splitByPage = false).map { it.chapterPosition },
        )
        assertEquals(13, chapter.chapterLength)
        assertEquals(0, chapter.paragraphIndexAtOrAfter(1, splitByPage = false))
        assertEquals(1, chapter.paragraphIndexAtOrAfter(5, splitByPage = false))
    }

    @Test
    fun explicitSymbolPolicyResplitsUnitsAtAbsolutePositions() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "甲乙，丙丁\n戊己\n",
            pageStarts = listOf(0),
        )
        val policy = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，"),
        )

        val units = chapter.paragraphs(splitByPage = false, policy = policy)

        assertEquals(listOf("甲乙，", "丙丁", "戊己"), units.map { it.text })
        assertEquals(listOf(0, 3, 6), units.map { it.chapterPosition })
        // 段内切出的子单元不是段末，只有每个原始段的最后一个单元是
        assertEquals(listOf(false, true, true), units.map { it.isParagraphEnd })
    }

    @Test
    fun symbolPolicyResplitsPageUnitsToo() {
        val chapter = ReaderReadAloudChapter.create(
            chapterIndex = 0,
            title = "",
            semanticContent = "甲乙，丙丁\n戊己\n",
            pageStarts = listOf(0, 3, 6),
        )
        val policy = ContentSplitPolicies.forMode(
            ReadAloudContentSplitMode.Symbols,
            listOf("，"),
        )

        val units = chapter.paragraphs(splitByPage = true, policy = policy)

        assertEquals(listOf("甲乙，", "丙丁", "戊己"), units.map { it.text })
        assertEquals(listOf(0, 3, 6), units.map { it.chapterPosition })
    }
}
