package io.legado.app.feature.reader.core.readaloud

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

        assertEquals(listOf("甲乙丙丁", "戊己"), chapter.paragraphs.map { it.text })
        assertEquals(listOf("甲乙", "丙丁", "戊己"), chapter.pageParagraphs.map { it.text })
        assertEquals(listOf(false, true, true), chapter.pageParagraphs.map { it.isParagraphEnd })
        assertEquals(listOf(0, 2, 5), chapter.pageParagraphs.map { it.chapterPosition })
        assertEquals(7, chapter.chapterLength)
        assertEquals(1, chapter.pageIndexAt(4))
        assertEquals(0, chapter.paragraphIndexAtOrAfter(4, splitByPage = false))
    }

    @Test
    fun speechParagraphsUseDisplayedTextPositions() {
        val chapter = ReaderReadAloudChapter.create(0, "", "袮甲\n乙\n", listOf(0))
        assertEquals(" 甲", chapter.speechParagraphs()[0].text)
        assertEquals(3, chapter.speechParagraphs()[1].chapterPosition)
    }
}
