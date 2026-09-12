package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderSingleImageTest {
    private val shaper = ReaderTextShaper { text ->
        GlyphClusters(text.map(Char::toString), List(text.length) { 10f })
    }
    private val style = ReaderChapterMeasureStyle(
        ReaderTextStyle(0, 10f), ReaderTextStyle(0, 10f), 2,
        ReaderTextAlignment.START, ReaderTextAlignment.START,
        imagePageBreakBefore = true, imagePageBreakAfter = true,
    )
    private val config = ReaderPaginationConfig(0, "", 120, 240, 10f, 20f, 10f, 20f, 10f, 8f)

    private suspend fun measure(vararg paragraphs: String) = ReaderChapterBlockMeasurer(
        shaper, shaper, { ReaderImageDimensions(100f, 50f) },
    ).measure(ReaderChapterSourceParser.parse(0, "", paragraphs.toList(), false, false), style)
        as ReaderChapterMeasureResult.Success

    @Test fun indentedImagesDoNotProduceBlankPagesOrChangeImageAnchors() = runBlocking {
        val measured = measure("　　<img src=\"a\">", "　　<img src=\"b\">   ")
        val pages = ReaderPaginator.paginateBlocks(measured.blocks, config)
        assertEquals(2, pages.size)
        assertEquals(listOf(2, 6), pages.flatMap { it.elements }
            .filterIsInstance<ReaderElement.Image>().map { it.chapterPosition })
        assertEquals(2, measured.blocks.size)
    }

    @Test fun firstTextAfterLeadingImageRetainsIndentButNotAWhitespacePage() = runBlocking {
        val measured = measure("　　<img src=\"a\">甲")
        assertEquals(2, measured.blocks.size)
        val text = measured.blocks.last() as ReaderMeasuredBlock.InlineParagraph
        assertEquals(20f, text.indentWidthPx!!, 0f)
        assertEquals(3, text.items.first().chapterPosition)
    }

    @Test fun meaningfulTextBeforeImageIsPreserved() = runBlocking {
        val measured = measure("　　甲<img src=\"a\">乙")
        val texts = measured.blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
        assertEquals(2, texts.size)
        assertEquals(0f, texts.last().indentWidthPx!!, 0f)
        assertEquals("　　甲", texts.first().items.filterIsInstance<ReaderMeasuredInlineItem.Text>()
            .joinToString("") { it.value })
    }

    @Test fun singleImageFillsAvailableWidthAndCentersWithinPaddedPage() {
        val pages = ReaderPaginator.paginateBlocks(listOf(
            ReaderMeasuredBlock.Image("a", 50f, 25f, 0, pageBreakBefore = true, pageBreakAfter = true),
        ), config)
        val image = pages.single().elements.single() as ReaderElement.Image
        assertEquals(10f, image.bounds.left, 0f)
        assertEquals(95f, image.bounds.top, 0f)
        assertEquals(110f, image.bounds.right, 0f)
        assertEquals(145f, image.bounds.bottom, 0f)
    }

    @Test fun tallSingleImageFitsHeightAndCentersHorizontally() {
        val pages = ReaderPaginator.paginateBlocks(listOf(
            ReaderMeasuredBlock.Image("a", 25f, 100f, 0, pageBreakBefore = true, pageBreakAfter = true),
        ), config)
        val image = pages.single().elements.single() as ReaderElement.Image
        assertEquals(35f, image.bounds.left, 0f)
        assertEquals(20f, image.bounds.top, 0f)
        assertEquals(85f, image.bounds.right, 0f)
        assertEquals(220f, image.bounds.bottom, 0f)
    }

    @Test fun ordinaryStandaloneImageDoesNotUpscaleOrCenterVertically() {
        val page = ReaderPaginator.paginateBlocks(listOf(
            ReaderMeasuredBlock.Image("a", 50f, 25f, 0),
        ), config).single()
        val image = page.elements.single() as ReaderElement.Image
        assertEquals(35f, image.bounds.left, 0f)
        assertEquals(20f, image.bounds.top, 0f)
        assertEquals(85f, image.bounds.right, 0f)
        assertEquals(45f, image.bounds.bottom, 0f)
    }

    @Test fun singleImagesUseIndividualColumnGeometry() {
        val pages = ReaderPaginator.paginateBlocks(listOf(
            ReaderMeasuredBlock.Image("a", 20f, 10f, 0, pageBreakBefore = true, pageBreakAfter = true),
            ReaderMeasuredBlock.Image("b", 20f, 10f, 1, pageBreakBefore = true, pageBreakAfter = true),
        ), config.copy(viewportWidthPx = 240, columnCount = 2))
        assertEquals(1, pages.size)
        assertEquals(listOf(10f, 130f), pages.single().elements.map { it.bounds.left })
        assertEquals(listOf(95f, 95f), pages.single().elements.map { it.bounds.top })
    }

    /**
     * 对照旧 View `TextChapterLayout`：`imgStyleSingle` 下标题段排版完会无条件结束当前页
     * （`prepareNextPageIfNeed()`），标题因此独占一页且水平居中。
     */
    @Test
    fun singleImageHeadingGetsItsOwnCenteredPage() = runBlocking {
        val source = ReaderChapterSource(
            1, "章标题", listOf(
                ReaderChapterSourceBlock.Text("章标题", 0, true),
                ReaderChapterSourceBlock.Text("正文正文", 3),
            ), 7
        )
        val measurer = ReaderChapterBlockMeasurer(shaper, shaper, { null })

        val plain = measurer.measure(source, style) as ReaderChapterMeasureResult.Success
        assertEquals(2, plain.blocks.size)

        val measured = measurer.measure(
            source,
            style.copy(
                titlePageBreakAfter = true,
                titleAlignment = ReaderTextAlignment.CENTER,
            ),
        ) as ReaderChapterMeasureResult.Success
        assertTrue(measured.blocks[1] is ReaderMeasuredBlock.PageBreak)

        val pages = ReaderPaginator.paginateBlocks(measured.blocks, config)
        assertEquals(2, pages.size)
        val heading = pages[0].elements.filterIsInstance<ReaderElement.Text>()
        assertEquals("章标题", heading.joinToString("") { it.value })
        assertEquals(45f, heading.first().bounds.left, 0f)
        assertEquals(
            "正文正文",
            pages[1].elements.filterIsInstance<ReaderElement.Text>().joinToString("") { it.value },
        )
    }

    @Test fun inlineIconKeepsItsIndentPrefix() = runBlocking {
        val source = ReaderChapterSourceParser.parse(0, "", listOf("　　<img src=\"icon\">甲"), false, false)
        val measured = ReaderChapterBlockMeasurer(shaper, shaper, { ReaderImageDimensions(10f, 10f) })
            .measure(source, style.copy(imagePageBreakBefore = false, imagePageBreakAfter = false))
            as ReaderChapterMeasureResult.Success
        val paragraph = measured.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        assertEquals(2, paragraph.leadingIndentItems)
        assertEquals(4, paragraph.items.size)
        assertEquals(0f, paragraph.indentWidthPx!!, 0f)
        assertEquals("　　\uFFFC甲\n", source.semanticContent)
    }
}
