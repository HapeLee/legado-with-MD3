package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.selection.ReaderSelection
import io.legado.app.feature.reader.core.selection.ReaderSelectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDoublePageSelectionTest {
    private val style = ReaderTextStyle(0, 10f)
    private val config = ReaderPaginationConfig(
        chapterIndex = 2, chapterTitle = "", viewportWidthPx = 100, viewportHeightPx = 40,
        paddingLeftPx = 5f, paddingRightPx = 5f, paddingTopPx = 0f, paddingBottomPx = 0f,
        lineHeightPx = 20f, baselineOffsetPx = 15f, columnCount = 2,
    )
    private val text = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉"

    @Test
    fun `inline paragraph flows across columns and selection follows reading order`() {
        val blocks = listOf(ReaderMeasuredBlock.InlineParagraph(
            items = text.mapIndexed { index, char ->
                ReaderMeasuredInlineItem.Text(char.toString(), 10f, style, index)
            },
            indentCharacters = 0,
            alignment = ReaderTextAlignment.START,
            lineHeightPx = 20f,
            baselineOffsetPx = 15f,
            baseTextSizePx = 10f,
        ))
        val page = ReaderPaginator.paginateBlocks(blocks, config).first()

        assertEquals(8, ReaderSelectionPolicy.start(page, 56f, 1f)?.anchor)
        assertEquals(text.substring(6, 11), ReaderSelection(2, 6, 10).selectedText(page))
        assertEquals(
            2,
            page.elements.filterIsInstance<ReaderElement.Text>()
                .map { it.bounds.left >= 50f }
                .distinct()
                .size,
        )
    }
}
