package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderPageColorTransformTest {
    @Test
    fun `theme recolor updates matching reader colors without changing layout or custom colors`() {
        val oldColor = 0xFF111111.toInt()
        val newColor = 0xFFEEEEEE.toInt()
        val customColor = 0xFFCC5500.toInt()
        val page = ReaderPage(
            id = ReaderPageId(1, 2),
            chapterTitle = "chapter",
            text = "body custom",
            widthPx = 100,
            heightPx = 200,
            contentTopPx = 10f,
            contentBottomPx = 190f,
            elements = listOf(
                text("body", oldColor, 0f),
                text("custom", customColor, 50f),
            ),
            revision = 7L,
        )

        val result = page.remapThemeColors(
            ReaderThemeColorChange(oldColor, newColor, oldColor, newColor),
            revisionSalt = 3L,
        )

        assertEquals(newColor, (result.elements[0] as ReaderElement.Text).style.colorArgb)
        assertEquals(customColor, (result.elements[1] as ReaderElement.Text).style.colorArgb)
        assertEquals(page.elements.map { it.bounds }, result.elements.map { it.bounds })
        assertEquals(7L xor 3L, result.revision)
    }

    @Test
    fun `body and title split correctly when their old color was identical`() {
        val oldColor = 0xFF222222.toInt()
        val newBody = 0xFFDDDDDD.toInt()
        val newTitle = 0xFFFFFFFF.toInt()
        val body = text("body", oldColor, 30f)
        val title = text("title", oldColor, 0f, emphasized = true)
        val page = ReaderPage(
            id = ReaderPageId(0, 0), chapterTitle = "title", text = "body",
            widthPx = 100, heightPx = 200, contentTopPx = 0f, contentBottomPx = 200f,
            elements = listOf(title, body), revision = 1L,
        )

        val result = page.remapThemeColors(
            ReaderThemeColorChange(oldColor, newBody, oldColor, newTitle),
            revisionSalt = 5L,
        )

        assertEquals(newTitle, (result.elements[0] as ReaderElement.Text).style.colorArgb)
        assertEquals(newBody, (result.elements[1] as ReaderElement.Text).style.colorArgb)
    }

    @Test
    fun `theme recolor updates shadow page underline and inherited paragraph marker immediately`() {
        val oldBody = 0xFF202020.toInt()
        val newBody = 0xFFE0E0E0.toInt()
        val oldShadow = 0x66000000
        val newShadow = 0x66FFFFFF
        val oldUnderline = 0xFF303030.toInt()
        val newUnderline = 0xFFD0D0D0.toInt()
        val body = text("body", oldBody, 0f).let { element ->
            element.copy(style = element.style.copy(
                shadow = ReaderTextShadow(oldShadow, 2f, 1f, 1f),
            ))
        }
        val rule = ReaderElement.Rule(
            bounds = ReaderRect(0f, 20f, 50f, 20f),
            colorArgb = oldUnderline,
            widthPx = 1f,
            dashed = false,
        )
        val marker = ReaderElement.ParagraphMarker(
            bounds = ReaderRect(0f, 25f, 0f, 45f),
            colorArgb = oldBody,
            strokeWidthPx = 2f,
            circular = false,
        )
        val page = ReaderPage(
            id = ReaderPageId(0, 0), chapterTitle = "", text = "body",
            widthPx = 100, heightPx = 100, contentTopPx = 0f, contentBottomPx = 100f,
            elements = listOf(body, rule, marker), revision = 1L,
        )

        val result = page.remapThemeColors(
            ReaderThemeColorChange(
                oldBody, newBody, oldBody, newBody,
                oldShadow, newShadow, oldUnderline, newUnderline,
            ),
            revisionSalt = 2L,
        )

        assertEquals(newShadow, (result.elements[0] as ReaderElement.Text).style.shadow?.colorArgb)
        assertEquals(newUnderline, (result.elements[1] as ReaderElement.Rule).colorArgb)
        assertEquals(newBody, (result.elements[2] as ReaderElement.ParagraphMarker).colorArgb)
    }

    private fun text(value: String, color: Int, top: Float, emphasized: Boolean = false) = ReaderElement.Text(
        bounds = ReaderRect(0f, top, 50f, top + 20f),
        baselinePx = top + 15f,
        value = value,
        style = ReaderTextStyle(colorArgb = color, fontSizePx = 16f),
        selected = false,
        emphasized = emphasized,
        chapterPosition = top.toInt(),
    )
}
