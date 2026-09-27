package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderImageOptionsTest {
    private val shaper = ReaderTextShaper { text ->
        GlyphClusters(text.map(Char::toString), List(text.length) { 10f })
    }
    private val baseStyle = ReaderChapterMeasureStyle(
        ReaderTextStyle(0, 10f), ReaderTextStyle(0, 10f), 0,
        ReaderTextAlignment.START, ReaderTextAlignment.START,
        imageAvailableWidthPx = 100f,
    )
    private val config = ReaderPaginationConfig(0, "", 100, 200, 0f, 0f, 0f, 0f, 10f, 8f)

    private suspend fun measure(
        options: ReaderImageOptions,
        style: ReaderChapterMeasureStyle = baseStyle,
        dimensions: ReaderImageDimensions = ReaderImageDimensions(100f, 50f),
    ): ReaderChapterMeasureResult.Success {
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<img src=\"image\">"), false, false,
        )
        return ReaderChapterBlockMeasurer(
            shaper, shaper, { dimensions }, imageOptionsResolver = { _, _ -> options },
        ).measure(source, style) as ReaderChapterMeasureResult.Success
    }

    @Test fun globalTextModeKeepsLargeImagesInline() = runBlocking {
        val result = measure(ReaderImageOptions(), baseStyle.copy(imageLayoutMode = ReaderImageLayoutMode.INLINE))
        assertTrue(result.blocks.single() is ReaderMeasuredBlock.InlineParagraph)
    }

    @Test
    fun inlineImageUsesOneCharacterCellWidth() = runBlocking {
        val result = measure(
            ReaderImageOptions(ReaderImageLayoutMode.INLINE, action = "run()"),
            baseStyle.copy(
                imageLayoutMode = ReaderImageLayoutMode.INLINE,
                imageAvailableWidthPx = 100f,
            ),
            ReaderImageDimensions(100f, 50f),
        )
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        val image = paragraph.items.single() as ReaderMeasuredInlineItem.Image
        // 对照旧 `ImageColumn`：占位宽 = 占位字（袮）在本段 paint 下的 advance——本测试的 shaper
        // 给所有字 10f；高按原图比例换算（100×50 → 5f）。
        assertEquals(10f, image.widthPx, 0f)
        assertEquals(5f, image.heightPx, 0f)
        // 书级文字嵌入分支旧版不解析 JSON，连行内图的 click 也没有。
        assertNull(image.action)

        val element = ReaderPaginator.paginateBlocks(result.blocks, config)
            .single().elements.single() as ReaderElement.Image
        // 行内图必须带标记，绘制期才会按位图长宽比重算几何（旧 ImageColumn 语义）。
        assertTrue(element.inline)
        assertEquals(10f, element.bounds.width, 0f)
        assertEquals(5f, element.bounds.height, 0f)
    }

    @Test
    fun requestedWidthFractionIsIgnoredForInlineImages() = runBlocking {
        val result = measure(ReaderImageOptions(requestedWidthFraction = .5f))
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        val image = paragraph.items.single() as ReaderMeasuredInlineItem.Image
        // 旧版行内图不使用 width 参数（占位宽只看占位字的 advance）。
        assertEquals(10f, image.widthPx, 0f)
        assertEquals(5f, image.heightPx, 0f)
    }

    /** 占位字就是旧 `ChapterProvider` 的两个替换字，朗读剔除与书签标记都依赖它们。 */
    @Test
    fun placeholderGlyphsMatchTheLegacyReplacementCharacters() {
        assertEquals("袮", ReaderInlineImagePlaceholder.SRC_REPLACE.placeholderChar)
        assertEquals("꧁", ReaderInlineImagePlaceholder.REVIEW.placeholderChar)
    }

    /**
     * 行内图的宽 = 旧 View 插入正文的占位字（`袮` / `꧁`）在本段 paint 下的 advance，不是字号；
     * 高按原图比例从该宽度换算（100×50 → 一半）。
     */
    @Test
    fun inlineImageWidthFollowsTheLegacyPlaceholderGlyphAdvance() = runBlocking {
        val reviewChar = ReaderInlineImagePlaceholder.REVIEW.placeholderChar
        val placeholderShaper = ReaderTextShaper { text ->
            val advance = if (text == reviewChar) 5f else 14f
            GlyphClusters(text.map(Char::toString), List(text.length) { advance })
        }
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<img src=\"image\">"), false, false,
        )

        suspend fun inlineImage(
            placeholder: ReaderInlineImagePlaceholder,
        ): ReaderMeasuredInlineItem.Image {
            val result = ReaderChapterBlockMeasurer(
                placeholderShaper, placeholderShaper, { ReaderImageDimensions(100f, 50f) },
                imageOptionsResolver = { _, _ ->
                    ReaderImageOptions(
                        ReaderImageLayoutMode.INLINE,
                        inlinePlaceholder = placeholder,
                    )
                },
            ).measure(source, baseStyle) as ReaderChapterMeasureResult.Success
            return (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph)
                .items.single() as ReaderMeasuredInlineItem.Image
        }

        val srcReplace = inlineImage(ReaderInlineImagePlaceholder.SRC_REPLACE)
        assertEquals(14f, srcReplace.widthPx, 0f)
        assertEquals(7f, srcReplace.heightPx, 0f)

        val review = inlineImage(ReaderInlineImagePlaceholder.REVIEW)
        assertEquals(5f, review.widthPx, 0f)
        assertEquals(2.5f, review.heightPx, 0f)
    }

    /**
     * 旧 `TextChapterLayout` 的书级文字嵌入分支**完全不解析单图 JSON**：单图 FULL/对齐/宽度都不
     * 覆盖书级设置，占位字也固定是 `袮`（`"style":"TEXT"` 的 `꧁` 不生效）。
     */
    @Test
    fun bookLevelTextModeShortCircuitsPerImageOverrides() = runBlocking {
        val reviewChar = ReaderInlineImagePlaceholder.REVIEW.placeholderChar
        val placeholderShaper = ReaderTextShaper { text ->
            val advance = if (text == reviewChar) 5f else 14f
            GlyphClusters(text.map(Char::toString), List(text.length) { advance })
        }
        val source = ReaderChapterSourceParser.parse(
            0, "", listOf("<img src=\"image\">"), false, false,
        )
        val result = ReaderChapterBlockMeasurer(
            placeholderShaper, placeholderShaper, { ReaderImageDimensions(100f, 50f) },
            imageOptionsResolver = { _, _ ->
                ReaderImageOptions(
                    ReaderImageLayoutMode.FULL_WIDTH,
                    horizontalAlignment = ReaderTextAlignment.END,
                    requestedWidthFraction = .5f,
                    action = "run()",
                    inlinePlaceholder = ReaderInlineImagePlaceholder.REVIEW,
                )
            },
        ).measure(source, baseStyle.copy(imageLayoutMode = ReaderImageLayoutMode.INLINE))
                as ReaderChapterMeasureResult.Success

        // FULL 不能升级成整图，占位字仍是袮（advance 14f）而不是单图 TEXT 的 ꧁（5f）。
        val image = (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph)
            .items.single() as ReaderMeasuredInlineItem.Image
        assertEquals(14f, image.widthPx, 0f)
        assertEquals(7f, image.heightPx, 0f)
        // 旧分支连 click 也没解析：书级文字嵌入下段评气泡不可点。
        assertNull(image.action)
    }

    /**
     * 书级不是文字嵌入时，单图 style 才能覆盖书级设置：20×10 的小图本会因低于
     * `standaloneImageThresholdPx` 被自动行内，单图 FULL 让它按铺满排版。
     */
    @Test
    fun perImageFullStyleOverridesTheGlobalDefaultMode() = runBlocking {
        val result = measure(
            ReaderImageOptions(ReaderImageLayoutMode.FULL_WIDTH, action = "run()"),
            baseStyle,
            ReaderImageDimensions(20f, 10f),
        )
        val block = result.blocks.single() as ReaderMeasuredBlock.Image
        assertEquals(ReaderImageScaleMode.FIT_WIDTH, block.scaleMode)
        // 整图在旧 `setTypeImage` 里根本没传 click。
        assertNull(block.action)
        val image = ReaderPaginator.paginateBlocks(result.blocks, config)
            .single().elements.single() as ReaderElement.Image
        assertEquals(100f, image.bounds.width, 0f)
        assertEquals(50f, image.bounds.height, 0f)
        assertNull(image.action)
        assertFalse(image.inline)
    }

    @Test fun rightAlignmentAndSinglePageOverridesReachPaginator() = runBlocking {
        val right = measure(ReaderImageOptions(
            layoutMode = ReaderImageLayoutMode.STANDALONE,
            horizontalAlignment = ReaderTextAlignment.END,
        )).blocks.single() as ReaderMeasuredBlock.Image
        val rightImage = ReaderPaginator.paginateBlocks(listOf(right), config.copy(viewportWidthPx = 140))
            .single().elements.single() as ReaderElement.Image
        assertEquals(40f, rightImage.bounds.left, 0f)

        val single = measure(
            ReaderImageOptions(ReaderImageLayoutMode.SINGLE_PAGE),
            baseStyle,
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertTrue(single.pageBreakBefore)
        assertTrue(single.pageBreakAfter)
        assertEquals(ReaderImageScaleMode.FIT_PAGE, single.scaleMode)
    }

    /**
     * 旧 `setTypeImage` 的整图对齐取自"有效样式"：单图 style 缺失才回落到书级（`LEFT`/`RIGHT`）；
     * 单图 style 存在但不是 LEFT/RIGHT 时只居中，不吃书级对齐。
     */
    @Test
    fun bookLevelAlignmentAppliesOnlyWhenTheImageStyleIsAbsent() = runBlocking {
        val fromBook = measure(
            ReaderImageOptions(),
            baseStyle.copy(imageAlignment = ReaderTextAlignment.END),
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertEquals(ReaderTextAlignment.END, fromBook.horizontalAlignment)

        val fromOptions = measure(
            ReaderImageOptions(
                ReaderImageLayoutMode.FULL_WIDTH,
                horizontalAlignment = ReaderTextAlignment.CENTER,
            ),
            baseStyle.copy(imageAlignment = ReaderTextAlignment.END),
        ).blocks.single() as ReaderMeasuredBlock.Image
        assertEquals(ReaderTextAlignment.CENTER, fromOptions.horizontalAlignment)
    }
}
