package io.legado.app.feature.reader.core.readaloud

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderReadAloudSpeechAdapterTest {
    @Test
    fun `Android adapter preserves shared speech paragraph fields`() {
        val chapter = ReaderReadAloudChapter.create(0, "", "袮甲\n乙\n", listOf(0))

        val paragraphs = chapter.canonicalSpeechParagraphs()

        assertEquals(" 甲", paragraphs[0].text)
        assertEquals(3, paragraphs[1].chapterPosition)
    }
}
