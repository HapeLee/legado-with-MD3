package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test

class ReaderPageLayoutRevisionTest {
    @Test
    fun `visual refresh changes revision without pretending geometry changed`() {
        val page = page(revision = 10L)

        val refreshed = page.copy(revision = 11L)

        assertNotEquals(page.revision, refreshed.revision)
        assertEquals(page.layoutRevision, refreshed.layoutRevision)
    }

    @Test
    fun `new pagination can identify geometry change at the same page id`() {
        val oldPage = page(revision = 10L)
        val repaginated = page(revision = 20L)

        assertEquals(oldPage.id, repaginated.id)
        assertNotEquals(oldPage.layoutRevision, repaginated.layoutRevision)
    }

    @Test
    fun `theme-only page snapshot has the same geometry`() {
        val oldPage = page(revision = 10L)
        val themed = oldPage.copy(
            revision = 20L,
            elements = listOf(text(color = 0xFFFFFFFF.toInt())),
        )
        val originalWithText = oldPage.copy(elements = listOf(text(color = 0xFF111111.toInt())))

        assertEquals(true, themed.hasSameGeometryAs(originalWithText))
    }

    @Test
    fun `changed element bounds are a geometry change`() {
        val oldPage = page(revision = 10L).copy(elements = listOf(text(0xFF111111.toInt())))
        val reflowed = page(revision = 20L).copy(
            elements = listOf(text(0xFF111111.toInt()).copy(bounds = ReaderRect(0f, 20f, 80f, 40f))),
        )

        assertEquals(false, reflowed.hasSameGeometryAs(oldPage))
    }

    private fun text(color: Int) = ReaderElement.Text(
        bounds = ReaderRect(0f, 10f, 80f, 30f),
        baselinePx = 25f,
        value = "text",
        style = ReaderTextStyle(colorArgb = color, fontSizePx = 16f),
        selected = false,
        emphasized = false,
        chapterPosition = 0,
    )

    private fun page(revision: Long) = ReaderPage(
        id = ReaderPageId(1, 2),
        chapterTitle = "chapter",
        text = "text",
        widthPx = 100,
        heightPx = 200,
        contentTopPx = 10f,
        contentBottomPx = 190f,
        elements = emptyList(),
        revision = revision,
    )
}
