package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderEmphasisUnderlineRunTest {
    private val emphasis = ReaderEmphasisUnderline(
        colorArgb = 0xff123456.toInt(),
        widthPx = 2f,
        bottomOffsetPx = 1f,
    )

    @Test fun `one emphasized glyph underlines its entire visual line`() {
        val page = page(
            text(10f, 20f, emphasis),
            text(20f, 30f),
            text(30f, 45f),
        )

        assertEquals(
            listOf(ReaderEmphasisUnderlineRun(10f, 45f, 39f, emphasis)),
            page.emphasisUnderlineRuns(),
        )
    }

    @Test fun `separate visual lines produce separate emphasis rules`() {
        val page = page(
            text(0f, 10f, emphasis, top = 0f, bottom = 20f),
            text(10f, 20f, top = 0f, bottom = 20f),
            text(5f, 15f, emphasis, top = 20f, bottom = 40f),
        )

        assertEquals(listOf(19f, 39f), page.emphasisUnderlineRuns().map { it.yPx })
    }

    private fun text(
        left: Float,
        right: Float,
        underline: ReaderEmphasisUnderline? = null,
        top: Float = 20f,
        bottom: Float = 40f,
    ) = ReaderElement.Text(
        bounds = ReaderRect(left, top, right, bottom),
        baselinePx = bottom - 4f,
        value = "字",
        style = ReaderTextStyle(0xff000000.toInt(), 20f),
        selected = false,
        emphasized = false,
        emphasisUnderline = underline,
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
