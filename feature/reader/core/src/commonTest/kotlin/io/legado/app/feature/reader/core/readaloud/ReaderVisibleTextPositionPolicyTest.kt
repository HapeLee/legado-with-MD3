package io.legado.app.feature.reader.core.readaloud

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderPageWindow
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderVisibleTextPositionPolicyTest {
    private val style = ReaderTextStyle(0xff000000.toInt(), 16f)

    @Test
    fun anchorsReadAloudAtTheFirstVisibleBodyGlyph() {
        val page = page(
            chapter = 2,
            index = 3,
            elements = listOf(
                text("标题", 0, 0f, emphasized = true),
                text("甲", 2, 20f),
                text("乙", 3, 50f),
            ),
        )

        assertEquals(
            ReaderVisibleTextPosition(chapterIndex = 2, chapterPosition = 3),
            ReaderVisibleTextPositionPolicy.firstVisibleBodyText(
                ReaderPageWindow(current = page),
                scrollOffsetPx = -45f,
            ),
        )
    }

    @Test
    fun resolvesTheVisiblePreviousPageBeforeTheCurrentPage() {
        val previous = page(1, 5, listOf(text("前", 40, 80f)))
        val current = page(2, 0, listOf(text("当", 0, 10f)))

        assertEquals(
            ReaderVisibleTextPosition(chapterIndex = 1, chapterPosition = 40),
            ReaderVisibleTextPositionPolicy.firstVisibleBodyText(
                ReaderPageWindow(previous = previous, current = current),
                scrollOffsetPx = 20f,
            ),
        )
    }

    @Test
    fun ignoresPlaceholderAndTitleOnlyPages() {
        val placeholder = page(1, 0, listOf(text("加载中", 0, 0f)), placeholder = true)
        val titleOnly = page(2, 0, listOf(text("标题", 0, 0f, emphasized = true)))

        assertNull(
            ReaderVisibleTextPositionPolicy.firstVisibleBodyText(
                ReaderPageWindow(previous = placeholder, current = titleOnly),
                scrollOffsetPx = 0f,
            ),
        )
    }

    private fun page(
        chapter: Int,
        index: Int,
        elements: List<ReaderElement.Text>,
        placeholder: Boolean = false,
    ) = ReaderPage(
        id = ReaderPageId(chapter, index),
        chapterTitle = "",
        text = "",
        widthPx = 100,
        heightPx = 100,
        contentTopPx = 0f,
        contentBottomPx = 100f,
        elements = elements,
        revision = 1L,
        isPlaceholder = placeholder,
    )

    private fun text(value: String, position: Int, top: Float, emphasized: Boolean = false) =
        ReaderElement.Text(
            bounds = ReaderRect(0f, top, 20f, top + 20f),
            baselinePx = top + 15f,
            value = value,
            style = style,
            selected = false,
            emphasized = emphasized,
            chapterPosition = position,
        )
}
