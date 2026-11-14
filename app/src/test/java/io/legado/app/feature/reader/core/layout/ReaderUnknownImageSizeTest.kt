package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderUnknownImageSizeTest {
    private val config = ReaderPaginationConfig(
        chapterIndex = 0,
        chapterTitle = "",
        viewportWidthPx = 1000,
        viewportHeightPx = 2000,
        paddingLeftPx = 0f,
        paddingTopPx = 0f,
        paddingRightPx = 0f,
        paddingBottomPx = 0f,
        lineHeightPx = 40f,
        baselineOffsetPx = 30f,
    )
    private val shaper = ReaderTextShaper { text ->
        GlyphClusters(text.map(Char::toString), FloatArray(text.length) { 10f })
    }
    private val measureStyle = ReaderChapterMeasureStyle(
        ReaderTextStyle(0, 10f), ReaderTextStyle(0, 10f), 0,
        ReaderTextAlignment.START, ReaderTextAlignment.START,
        bodyLineHeightPx = 40f,
        imageAvailableWidthPx = 1000f,
    )
    private val source = ReaderChapterSourceParser.parse(
        0, "", listOf("<img src=\"card\">"), false, false,
    )

    private fun measure(
        options: ReaderImageOptions,
        dimensions: ReaderImageDimensions?,
    ) = runBlocking {
        ReaderChapterBlockMeasurer(
            shaper, shaper, { dimensions },
            imageOptionsResolver = { _, _ -> options },
        ).measure(source, measureStyle) as ReaderChapterMeasureResult.Success
    }

    private fun paginatedImage(block: ReaderMeasuredBlock.Image): ReaderElement.Image =
        ReaderPaginator.paginateBlocks(listOf(block), config)
            .single().elements.single() as ReaderElement.Image

    /**
     * #2522 的稳定复现样本（人为把 SVG 改成 1000×1000）：书源真的给出方形内在尺寸时
     * 仍按方形排版，不能把「探测失败」和「内容本身就是方形」混为一谈。
     */
    @Test
    fun squareIntrinsicSizeIsStillLaidOutAsSquare() {
        val block = measure(
            ReaderImageOptions(ReaderImageLayoutMode.FULL_WIDTH),
            ReaderImageDimensions(512f, 512f),
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertTrue(block.dimensionsKnown)
        val image = paginatedImage(block)
        assertEquals(1000f, image.bounds.width, 0f)
        assertEquals(1000f, image.bounds.height, 0f)
    }

    /** #2607：尺寸未知时不再用 1:1 兜底比例撑出整宽方形空白。 */
    @Test
    fun unknownIntrinsicSizeReservesOnlyOneLine() {
        val block = measure(
            ReaderImageOptions(ReaderImageLayoutMode.FULL_WIDTH),
            null,
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertFalse(block.dimensionsKnown)
        val image = paginatedImage(block)
        assertEquals(1000f, image.bounds.width, 0f)
        assertEquals(40f, image.bounds.height, 0f)
    }

    @Test
    fun knownWideSvgKeepsItsAspectRatio() {
        val block = measure(
            ReaderImageOptions(ReaderImageLayoutMode.FULL_WIDTH),
            ReaderImageDimensions(1000f, 120f),
        ).blocks.single() as ReaderMeasuredBlock.Image
        val image = paginatedImage(block)
        assertEquals(1000f, image.bounds.width, 0f)
        assertEquals(120f, image.bounds.height, 0f)
    }

    /** 行内图仍按字符格排版，未知尺寸不改变文字嵌入的几何。 */
    @Test
    fun unknownSizeKeepsInlineImageInItsCharacterCell() {
        val result = measure(ReaderImageOptions(ReaderImageLayoutMode.INLINE), null)
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        val image = paragraph.items.single() as ReaderMeasuredInlineItem.Image
        assertEquals(10f, image.widthPx, 0f)
    }
}
