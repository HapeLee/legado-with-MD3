package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderBottomJustifyTest {
    private val style = ReaderTextStyle(0, 10f)

    private fun paragraph(text: String) = ReaderMeasuredBlock.Paragraph(
        ReaderMeasuredParagraph(
            text = text,
            clusters = text.map(Char::toString),
            clusterWidthsPx = List(text.length) { 10f },
            style = style,
            chapterPosition = 0,
        ),
    )

    @Test fun enabledSettingDistributesNearBottomSurplusAcrossTextRows() {
        val base = ReaderPaginationConfig(
            0, "", 10, 45, 0f, 0f, 0f, 5f, 10f, 8f,
            textBottomJustify = false,
        )
        val ordinary = ReaderPaginator.paginateBlocks(listOf(paragraph("甲乙丙")), base).single()
            .elements.filterIsInstance<ReaderElement.Text>()
        val justified = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙")),
            base.copy(textBottomJustify = true),
        ).single().elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(listOf(0f, 10f, 20f), ordinary.map { it.bounds.top })
        assertEquals(listOf(0f, 15f, 30f), justified.map { it.bounds.top })
        assertEquals(40f, justified.last().bounds.bottom)
    }

    @Test fun doublePageJustifiesEachColumnIndependently() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("甲乙丙丁戊己")),
            ReaderPaginationConfig(
                0, "", 20, 40, 0f, 0f, 0f, 5f, 10f, 8f,
                columnCount = 2,
                textBottomJustify = true,
            ),
        ).single()
        val columns = page.elements.filterIsInstance<ReaderElement.Text>().groupBy { it.bounds.left }

        assertEquals(listOf(0f, 12.5f, 25f), columns.getValue(0f).map { it.bounds.top })
        assertEquals(listOf(0f, 12.5f, 25f), columns.getValue(10f).map { it.bounds.top })
        assertEquals(35f, columns.getValue(0f).last().bounds.bottom)
        assertEquals(35f, columns.getValue(10f).last().bounds.bottom)
    }

    @Test fun standaloneImageAtColumnBottomPreventsTextRowStretching() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                paragraph("甲乙"),
                ReaderMeasuredBlock.Image("image", 10f, 10f, 2),
            ),
            ReaderPaginationConfig(
                0, "", 10, 45, 0f, 0f, 0f, 5f, 10f, 8f,
                textBottomJustify = true,
            ),
        ).single()
        val text = page.elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(listOf(0f, 10f), text.map { it.bounds.top })
        assertEquals(20f, page.elements.filterIsInstance<ReaderElement.Image>().single().bounds.top)
    }
}
