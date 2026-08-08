package io.legado.app.ui.book.read.page.entities

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TextChapterReadAloudPositionTest {

    private lateinit var paragraphs: List<TextParagraph>

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
        paragraphs = listOf(
            paragraph(num = 1, position = 10, text = "第一段"),
            paragraph(num = 2, position = 20, text = "第二段"),
        )
    }

    @Test
    fun `resolves a position within a paragraph`() {
        assertEquals(1, findReadAloudParagraphNumAtOrAfter(paragraphs, 12))
    }

    @Test
    fun `resolves a gap to the next paragraph`() {
        assertEquals(2, findReadAloudParagraphNumAtOrAfter(paragraphs, 18))
    }

    @Test
    fun `does not resolve a position after the final paragraph`() {
        assertNull(findReadAloudParagraphNumAtOrAfter(paragraphs, 30))
    }

    private fun paragraph(num: Int, position: Int, text: String) = TextParagraph(
        num = num,
        textLines = arrayListOf(TextLine(text = text, chapterPosition = position)),
    )
}
