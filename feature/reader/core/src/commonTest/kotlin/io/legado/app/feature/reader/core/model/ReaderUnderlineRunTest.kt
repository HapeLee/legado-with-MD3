package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderUnderlineRunTest {
    private val underline = ReaderUnderline(5, 0xFF000000.toInt(), 1f, 2f, "M0 50 L100 50")
    private val style = ReaderTextStyle(0xFF000000.toInt(), 20f, underline = underline)

    @Test
    fun `adjacent characters with one style form one underline segment`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(10f, 0f, 25f, 20f, style),
        )

        assertEquals(listOf(ReaderRect(0f, 0f, 25f, 20f)), page.underlineRuns().map { it.bounds })
    }

    @Test
    fun `line and style changes start distinct segments`() {
        val other = style.copy(underline = underline.copy(mode = 3))
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(12f, 0f, 22f, 20f, style),
            text(0f, 20f, 10f, 40f, style),
            text(10f, 20f, 20f, 40f, other),
        )

        assertEquals(3, page.underlineRuns().size)
    }

    @Test
    fun `letter spacing remains one continuous underline run`() {
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(14f, 0f, 24f, 20f, style),
        )

        assertEquals(listOf(ReaderRect(0f, 0f, 24f, 20f)), page.underlineRuns().map { it.bounds })
    }

    @Test
    fun `unstyled text breaks an underline run`() {
        val plain = style.copy(underline = null)
        val page = page(
            text(0f, 0f, 10f, 20f, style),
            text(10f, 0f, 20f, 20f, plain),
            text(20f, 0f, 30f, 20f, style),
        )

        assertEquals(
            listOf(ReaderRect(0f, 0f, 10f, 20f), ReaderRect(20f, 0f, 30f, 20f)),
            page.underlineRuns().map { it.bounds },
        )
    }

    private fun text(left: Float, top: Float, right: Float, bottom: Float, textStyle: ReaderTextStyle) =
        ReaderElement.Text(
            bounds = ReaderRect(left, top, right, bottom),
            baselinePx = bottom - 4f,
            value = "字",
            style = textStyle,
            selected = false,
            emphasized = false,
            chapterPosition = left.toInt(),
        )

    private fun page(vararg elements: ReaderElement) = ReaderPage(
        id = ReaderPageId(0, 0),
        chapterTitle = "",
        text = "",
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 0f,
        contentBottomPx = 100f,
        elements = elements.toList(),
        revision = 1L,
    )
}
