package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.textBackgroundRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPaginatorTest {
    private val style = ReaderTextStyle(colorArgb = 0xff111111.toInt(), fontSizePx = 10f)
    private val config = ReaderPaginationConfig(
        chapterIndex = 3,
        chapterTitle = "标题",
        viewportWidthPx = 40,
        viewportHeightPx = 45,
        paddingLeftPx = 0f,
        paddingTopPx = 0f,
        paddingRightPx = 0f,
        paddingBottomPx = 5f,
        lineHeightPx = 20f,
        baselineOffsetPx = 15f,
    )

    /** 单行段落：内容区宽 40f、每字 20f，必定排成一行、占 20f 行高。 */
    private fun paragraph(text: String, position: Int = 0) = ReaderMeasuredParagraph(
        text, text.map(Char::toString), List(text.length) { 20f }, style, position,
    )

    @Test fun emphasisUnderlineStyleIsCarriedByEveryPublishedPage() {
        val emphasis = ReaderEmphasisUnderline(0xff123456.toInt(), 2f, 1f)
        val pages = ReaderPaginator.paginate(
            listOf(ReaderMeasuredParagraph("字".repeat(6), List(6) { "字" }, List(6) { 20f }, style, 0)),
            config.copy(emphasisUnderlineStyle = emphasis),
        )

        assertTrue(pages.size > 1)
        assertTrue(pages.all { it.emphasisUnderlineStyle == emphasis })
    }

    @Test fun htmlBlankLineOccupiesLayoutHeightAndOnlyUsesTheExistingNewlinePosition() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(ReaderMeasuredInlineItem.Text("甲", 10f, style, 0)),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                ),
                ReaderMeasuredBlock.BlankLine(2, 20f, 1.5f),
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(ReaderMeasuredInlineItem.Text("乙", 10f, style, 3)),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                ),
            ),
            config.copy(viewportHeightPx = 200, paragraphSpacingPx = 4f),
        ).single()

        val spacer = page.elements.filterIsInstance<ReaderElement.Spacer>().single()
        val following = page.elements.filterIsInstance<ReaderElement.Text>().last()
        assertEquals(24f, spacer.bounds.top, 0f)
        assertEquals(58f, following.bounds.top, 0f)
        assertEquals(2, spacer.chapterPosition)
        assertEquals("甲\n\n乙", page.text)
    }

    @Test fun htmlFirstAndContinuationMarginsBothConstrainWrappingAndPlacement() {
        val items = List(7) { index ->
            ReaderMeasuredInlineItem.Text("字", 10f, style, index)
        }
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = items,
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                indentWidthPx = 10f,
                restLineIndentWidthPx = 20f,
            )),
            config.copy(viewportHeightPx = 200),
        ).single()
        val lines = page.elements.filterIsInstance<ReaderElement.Text>().groupBy { it.bounds.top }.values

        assertEquals(listOf(10f, 20f, 20f), lines.map { it.first().bounds.left })
        assertTrue(lines.flatten().all { it.bounds.right <= 40f })
    }

    @Test fun htmlQuoteContinuesAcrossVisualLinesWhileBulletOnlyMarksTheFirstLine() {
        val items = List(7) { index ->
            ReaderMeasuredInlineItem.Text("字", 10f, style, index)
        }
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = items,
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                indentWidthPx = 10f,
                restLineIndentWidthPx = 10f,
                decorations = listOf(
                    ReaderParagraphDecoration(ReaderParagraphDecorationKind.QUOTE, 0xff123456.toInt(), 2f),
                    ReaderParagraphDecoration(
                        ReaderParagraphDecorationKind.BULLET,
                        null,
                        3f,
                        leadingOffsetPx = 6f,
                    ),
                ),
            )),
            config.copy(viewportHeightPx = 200),
        ).single()

        val markers = page.elements.filterIsInstance<ReaderElement.ParagraphMarker>()
        val quotes = markers.filterNot { it.circular }
        val bullets = markers.filter { it.circular }
        assertEquals(3, quotes.size)
        assertEquals(1, bullets.size)
        assertEquals(0xff123456.toInt(), quotes.first().colorArgb)
        assertEquals(style.colorArgb, bullets.single().colorArgb)
        assertEquals(9f, bullets.single().bounds.left, 0f)
        assertEquals("字".repeat(7), page.text)
    }

    @Test
    fun subtitleSpacingScalesWithFontButTitleBottomPaddingDoesNot() {
        fun title(value: String, scale: Float) = ReaderMeasuredBlock.InlineParagraph(
            items = listOf(ReaderMeasuredInlineItem.Text(value, 10f * scale, style.copy(fontSizePx = 10f * scale), 0)),
            indentCharacters = 0,
            alignment = ReaderTextAlignment.START,
            lineHeightPx = 20f * scale,
            baselineOffsetPx = 15f * scale,
            baseTextSizePx = 10f * scale,
            emphasized = true,
            titleSpacingScale = scale,
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(title("主", 1f), title("副", 0.5f), title("末", 0.5f),
                ReaderMeasuredBlock.Paragraph(ReaderMeasuredParagraph("文", listOf("文"), listOf(10f), style, 0))),
            config.copy(
                viewportHeightPx = 200,
                titleParagraphSpacingPx = 4f,
                titleSegmentSpacingPx = 6f,
                titleBottomSpacingPx = 11f,
            ),
        ).single()
        assertEquals(listOf(0f, 30f, 45f, 68f), page.elements.map { it.bounds.top })
    }

    @Test
    fun titleSpacingIsAppliedOnceAndUsesTitleParagraphMetrics() {
        fun paragraph(value: String, title: Boolean) = ReaderMeasuredBlock.InlineParagraph(
            items = listOf(ReaderMeasuredInlineItem.Text(value, 10f, style, 0)),
            indentCharacters = 0,
            alignment = ReaderTextAlignment.START,
            lineHeightPx = 20f,
            baselineOffsetPx = 15f,
            baseTextSizePx = 10f,
            emphasized = title,
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(paragraph("主", true), paragraph("副", true), paragraph("文", false)),
            config.copy(
                viewportHeightPx = 200,
                paddingTopPx = 5f,
                paragraphSpacingPx = 2f,
                titleTopSpacingPx = 7f,
                titleBottomSpacingPx = 11f,
                titleParagraphSpacingPx = 4f,
                titleSegmentSpacingPx = 6f,
            ),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(12f, 42f, 77f), glyphs.map { it.bounds.top })
        assertEquals("主\n副\n文", page.text)
    }

    @Test
    fun titleBottomSpacingCanMoveBodyToNextPageWithoutRepeatingTopSpacing() {
        val pages = ReaderPaginator.paginate(
            listOf(
                ReaderMeasuredParagraph("题", listOf("题"), listOf(10f), style, 0, isTitle = true),
                ReaderMeasuredParagraph("文", listOf("文"), listOf(10f), style, 0),
            ),
            config.copy(titleTopSpacingPx = 5f, titleBottomSpacingPx = 10f),
        )
        assertEquals(2, pages.size)
        assertEquals(5f, pages.first().elements.first().bounds.top, 0f)
        assertEquals(0f, pages.last().elements.first().bounds.top, 0f)
    }

    @Test
    fun hiddenTitleDoesNotLeaveTitleSpacingBehind() {
        val page = ReaderPaginator.paginate(
            listOf(ReaderMeasuredParagraph("文", listOf("文"), listOf(10f), style, 0)),
            config.copy(titleTopSpacingPx = 50f, titleBottomSpacingPx = 50f),
        ).single()
        assertEquals(0f, page.elements.single().bounds.top, 0f)
    }

    @Test
    fun paginatesWithoutLosingChapterPositions() {
        val text = "甲乙丙丁戊己庚辛壬癸"
        val pages = ReaderPaginator.paginate(
            listOf(ReaderMeasuredParagraph(text, text.map(Char::toString), List(text.length) { 10f }, style, 100)),
            config,
        )
        val glyphs = pages.flatMap { it.elements }.filterIsInstance<ReaderElement.Text>()
        assertEquals(2, pages.size)
        assertEquals(text, glyphs.joinToString("") { it.value })
        assertEquals((100 until 110).toList(), glyphs.map { it.chapterPosition })
        assertTrue(pages.all { page -> page.elements.all { it.bounds.bottom <= 40f } })
    }

    @Test
    fun keepsParagraphIdentityAcrossPageBoundaries() {
        val first = "甲乙丙丁戊己庚辛壬癸"
        val second = "子丑寅卯"
        val pages = ReaderPaginator.paginate(
            listOf(
                ReaderMeasuredParagraph(first, first.map(Char::toString), List(first.length) { 10f }, style, 0),
                ReaderMeasuredParagraph(second, second.map(Char::toString), List(second.length) { 10f }, style, first.length + 1),
            ),
            config,
        )
        val glyphs = pages.flatMap { it.elements }.filterIsInstance<ReaderElement.Text>()

        assertEquals(setOf(0), glyphs.filter { it.chapterPosition < first.length }.map { it.paragraphIndex }.toSet())
        assertEquals(setOf(1), glyphs.filter { it.chapterPosition > first.length }.map { it.paragraphIndex }.toSet())
    }

    @Test
    fun appliesIndentAndJustifiesNonFinalLine() {
        val text = "甲乙丙丁戊己"
        val page = ReaderPaginator.paginate(
            listOf(ReaderMeasuredParagraph(text, text.map(Char::toString), List(text.length) { 10f }, style, 0, indentCharacters = 1, alignment = ReaderTextAlignment.JUSTIFY)),
            config.copy(viewportWidthPx = 45, viewportHeightPx = 100),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(10f, glyphs.first().bounds.left)
        assertTrue(glyphs[1].bounds.left > 20f)
    }

    @Test
    fun keepsClosingPunctuationOffNextLineWhenPossible() {
        val text = "甲乙丙，丁"
        val page = ReaderPaginator.paginate(
            listOf(ReaderMeasuredParagraph(text, text.map(Char::toString), List(text.length) { 10f }, style, 0)),
            config.copy(viewportWidthPx = 30, viewportHeightPx = 100),
        ).single()
        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        val commaIndex = glyphs.indexOfFirst { it.value == "，" }
        assertTrue(commaIndex > 0)
        assertEquals(glyphs[commaIndex - 1].bounds.top, glyphs[commaIndex].bounds.top, 0.01f)
    }

    @Test
    fun laysOutImagesAndHonorsForcedPageBreaks() {
        val pages = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.Image("cover", 80f, 80f, chapterPosition = 0, pageBreakAfter = true),
                ReaderMeasuredBlock.Paragraph(
                    ReaderMeasuredParagraph("正文", listOf("正", "文"), listOf(10f, 10f), style, 1),
                ),
            ),
            config.copy(viewportWidthPx = 40, viewportHeightPx = 45),
        )
        val image = pages.first().elements.single() as ReaderElement.Image
        assertEquals(2, pages.size)
        assertEquals(40f, image.bounds.width, 0.01f)
        assertEquals(0, image.chapterPosition)
        assertEquals("正文", pages.last().text)
    }

    @Test
    fun ruleMovesToNextPageWhenItDoesNotFit() {
        val pages = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.Paragraph(
                    ReaderMeasuredParagraph("甲乙丙丁", listOf("甲", "乙", "丙", "丁"), List(4) { 10f }, style, 0),
                ),
                ReaderMeasuredBlock.Rule(0xff000000.toInt(), widthPx = 2f, verticalPaddingPx = 10f),
            ),
            config,
        )
        assertEquals(2, pages.size)
        assertTrue(pages.last().elements.single() is ReaderElement.Rule)
    }

    @Test
    fun inlineImageParticipatesInLineBreakingWithoutSplittingParagraph() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, style, 0),
                    ReaderMeasuredInlineItem.Image("icon", 10f, 10f, 1),
                    ReaderMeasuredInlineItem.Text("乙", 10f, style, 2),
                    ReaderMeasuredInlineItem.Text("丙", 10f, style, 3),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 30, viewportHeightPx = 100),
        ).single()
        val image = page.elements.filterIsInstance<ReaderElement.Image>().single()
        val text = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(text.first().bounds.top + text.first().bounds.height / 2f, image.bounds.top + image.bounds.height / 2f, 0.01f)
        assertEquals(20f, text.last().bounds.top, 0.01f)
        assertEquals("甲\uFFFC乙丙", page.text)
    }

    /**
     * 旧 `setTypeHtml` 的行末特例：图片是本行最后一项时，绘制宽改用 `measureText("\uFFFC")`
     * （[ReaderMeasuredInlineItem.Image.lineFinalWidthPx]），断行推进仍按 span advance——行末会像
     * 旧版一样留出空档；行中的图则用 span advance。
     */
    @Test
    fun htmlInlineImageUsesTheObjectReplacementWidthOnlyAtTheLineEnd() {
        fun drawnWidth(items: List<ReaderMeasuredInlineItem>): Float =
            ReaderPaginator.paginateBlocks(
                listOf(
                    ReaderMeasuredBlock.InlineParagraph(
                        items = items,
                        indentCharacters = 0,
                        alignment = ReaderTextAlignment.START,
                        lineHeightPx = 20f,
                        baselineOffsetPx = 15f,
                        baseTextSizePx = 10f,
                    )
                ),
                config.copy(viewportWidthPx = 100, viewportHeightPx = 100),
            ).single().elements.filterIsInstance<ReaderElement.Image>().single().bounds.width

        val htmlImage = ReaderMeasuredInlineItem.Image("icon", 40f, 24f, 0, lineFinalWidthPx = 10f)
        assertEquals(
            10f,
            drawnWidth(listOf(ReaderMeasuredInlineItem.Text("甲", 10f, style, 0), htmlImage)),
            0.01f,
        )
        assertEquals(
            40f,
            drawnWidth(listOf(htmlImage, ReaderMeasuredInlineItem.Text("甲", 10f, style, 1))),
            0.01f,
        )
    }

    @Test
    fun largerInlineFontExpandsLineAndBaseline() {
        val large = style.copy(fontSizePx = 20f)
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("小", 10f, style, 0),
                    ReaderMeasuredInlineItem.Text("大", 20f, large, 1),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportHeightPx = 100),
        ).single()
        val text = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(40f, text.first().bounds.height, 0.01f)
        assertEquals(30f, text.first().baselinePx, 0.01f)
        assertEquals(text.first().baselinePx, text.last().baselinePx, 0.01f)
    }

    @Test
    fun mixedFontsUseActualAscentAndDescentForTheSharedBaseline() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text(
                        "高", 10f, style, 0,
                        lineHeightPx = 24f, baselineOffsetPx = 20f,
                    ),
                    ReaderMeasuredInlineItem.Text(
                        "深", 10f, style, 1,
                        lineHeightPx = 18f, baselineOffsetPx = 10f,
                    ),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportHeightPx = 100),
        ).single()

        val text = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(28f, text.first().bounds.height, 0.01f)
        assertEquals(20f, text.first().baselinePx, 0.01f)
        assertEquals(text.first().baselinePx, text.last().baselinePx, 0.01f)
    }

    @Test
    fun baselineShiftExpandsBothSidesOfTheLineAndMovesOnlyTheShiftedGlyphs() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text(
                        "基", 10f, style, 0,
                        lineHeightPx = 20f, baselineOffsetPx = 15f,
                    ),
                    ReaderMeasuredInlineItem.Text(
                        "上", 10f, style, 1,
                        lineHeightPx = 20f, baselineOffsetPx = 15f, baselineShiftPx = -7f,
                    ),
                    ReaderMeasuredInlineItem.Text(
                        "下", 10f, style, 2,
                        lineHeightPx = 20f, baselineOffsetPx = 15f, baselineShiftPx = 2f,
                    ),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 100, viewportHeightPx = 100),
        ).single()

        val text = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(29f, text.first().bounds.height, 0.01f)
        assertEquals(listOf(22f, 15f, 24f), text.map { it.baselinePx })
    }

    @Test
    fun htmlJustificationPrefersSeveralWordSpacesOverCharacterGaps() {
        val value = "a b c d e"
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = value.mapIndexed { index, char ->
                    ReaderMeasuredInlineItem.Text(char.toString(), 5f, style, index)
                },
                indentCharacters = 0,
                alignment = ReaderTextAlignment.JUSTIFY,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                justifyAtWordBoundaries = true,
            )),
            config.copy(viewportWidthPx = 32, viewportHeightPx = 100),
        ).single()

        val firstLine = page.elements.filterIsInstance<ReaderElement.Text>()
            .filter { it.bounds.top == 0f }
        val spaces = firstLine.filter { it.value == " " }
        val letters = firstLine.filter { it.value != " " }
        assertTrue(spaces.size > 1)
        assertTrue(spaces.all { it.bounds.width > 5f })
        assertTrue(letters.all { it.bounds.width == 5f })
        firstLine.zipWithNext().forEach { (left, right) ->
            assertEquals(left.bounds.right, right.bounds.left, 0.001f)
        }
    }

    /**
     * 背景图（含九宫格）只是压在字后面的装饰：同一批字加上背景图，落位与断行必须和没加时
     * 逐字一致——分页不许给九宫格在左右留出额外宽度（左边条 + 半个长度偏移），
     * 否则字被推歪、行被拆短，「只是加个背景」就变成了「改排版」。
     */
    @Test
    fun backgroundImagesNeverMoveAGlyphOrChangeALineBreak() {
        fun glyphLayout(itemStyle: ReaderTextStyle) = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = (0 until 6).map { index ->
                    ReaderMeasuredInlineItem.Text("字", 10f, itemStyle, index)
                },
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 35, viewportHeightPx = 100),
        ).single().elements.filterIsInstance<ReaderElement.Text>()
            .map { listOf(it.bounds.left, it.bounds.top, it.bounds.right) }

        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            lengthOffsetLeftPx = 10f,
            lengthOffsetRightPx = 6f,
        ))

        assertEquals(glyphLayout(style), glyphLayout(framedStyle))
        // 每行照样放得下 3 个字（10×3 = 30 ≤ 35），背景图不许把它挤成 2 个。
        assertEquals(listOf(0f, 10f, 20f, 0f, 10f, 20f), glyphLayout(framedStyle).map { it[0] })
    }

    /**
     * 气泡超出边距、压到相邻的字，是「只做装饰」这一口径下的正常结果：超出的只应该是图本身。
     * 相邻正文的位置由 [backgroundImagesNeverMoveAGlyphOrChangeALineBreak] 保证不受影响。
     */
    @Test
    fun nineSliceOverlapsItsNeighboursInsteadOfPushingThemApart() {
        val frame = ReaderTextBackgroundImage(
            source = "frame.png",
            fit = 3,
            scale = 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        )
        val framedStyle = style.copy(backgroundImage = frame)
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(
                        ReaderMeasuredInlineItem.Text("前", 10f, style, 0),
                        ReaderMeasuredInlineItem.Text("中", 10f, framedStyle, 1),
                        ReaderMeasuredInlineItem.Text("后", 10f, style, 2),
                    ),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                )
            ),
            config.copy(viewportWidthPx = 60, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(0f, 10f, 20f), glyphs.map { it.bounds.left })
        val frameBounds = page.textBackgroundRuns().single().bounds
        // 左右两条边原样厚，压在相邻的字上而不是把它们隔开。
        assertEquals(7f, frameBounds.left, 0.001f)
        assertEquals(24f, frameBounds.right, 0.001f)
    }

    /** 放行标记按「绘制用实例」比较：同图连续才续接，换一张图就要断开。 */
    @Test
    fun backgroundRunContinuesOnlyAcrossEqualDrawnImages() {
        val frame = ReaderTextBackgroundImage(
            source = "frame.png",
            fit = 3,
            scale = 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        )
        val framed = style.copy(backgroundImage = frame)
        val reframed = style.copy(backgroundImage = frame.copy(source = "other.png"))
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(
                        ReaderMeasuredInlineItem.Text("甲", 10f, style, 0),
                        ReaderMeasuredInlineItem.Text("乙", 10f, framed, 1),
                        ReaderMeasuredInlineItem.Text("丙", 10f, framed, 2),
                        ReaderMeasuredInlineItem.Text("丁", 10f, reframed, 3),
                        ReaderMeasuredInlineItem.Text("戊", 10f, reframed, 4),
                    ),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                )
            ),
            config.copy(viewportWidthPx = 200, viewportHeightPx = 100),
        ).single()

        assertEquals(
            listOf(false, false, true, false, true),
            page.elements.filterIsInstance<ReaderElement.Text>().map { it.continuesBackgroundRun },
        )
    }

    /**
     * 角色/配乐胶囊是我们自己插进行里的按钮，原文里没有它：它对背景 run 必须完全透明。
     * 分页期不把它的空背景覆盖到前一项上，胶囊之后的字才拿得到放行标记，绘制期一句对白
     * 才会并成一个气泡、把胶囊整个包住，而不是被切成两个半截框。
     */
    @Test
    fun capsulesAreTransparentToTheBackgroundRun() {
        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        ))
        val capsule = ReaderMeasuredInlineItem.RoleCast(
            widthPx = 20f,
            heightPx = 12f,
            chapterPosition = 1,
            raw = "<<>>",
            name = "miku",
            voicePoolLabel = "池",
            quoteOrdinal = 0,
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, framedStyle, 0),
                    capsule,
                    ReaderMeasuredInlineItem.Text("乙", 10f, framedStyle, 2),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 200, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(2, glyphs.size)
        // 胶囊把两个字隔开了（10 → 30），几何上已经不相邻，只能靠放行标记跨过去。
        assertEquals(30f, glyphs[1].bounds.left, 0.001f)
        assertTrue(glyphs[1].continuesBackgroundRun)

        val run = page.textBackgroundRuns().single()
        assertEquals(ReaderRect(-3f, run.bounds.top, 44f, run.bounds.bottom), run.bounds)
        // 透明不等于消失：胶囊照样上屏，只是不参与背景切分。
        assertEquals(10f, page.elements.filterIsInstance<ReaderElement.RoleCast>()
            .single().bounds.left, 0.001f)
    }

    /** 背景音乐胶囊同理：段首那颗按钮也不能把这一段的背景劈成两半。 */
    @Test
    fun bgmCapsuleIsTransparentToTheBackgroundRun() {
        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        ))
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, framedStyle, 0),
                    ReaderMeasuredInlineItem.BgmScene(
                        widthPx = 15f,
                        heightPx = 12f,
                        chapterPosition = 0,
                        paragraphIndex = 0,
                        poolName = "池",
                        trackName = "曲",
                    ),
                    ReaderMeasuredInlineItem.Text("乙", 10f, framedStyle, 1),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 200, viewportHeightPx = 100),
        ).single()

        assertTrue(page.elements.filterIsInstance<ReaderElement.Text>()
            .last().continuesBackgroundRun)
        assertEquals(1, page.textBackgroundRuns().size)
    }

    @Test
    fun nineSliceDoesNotOrphanClosingPunctuation() {
        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f, contentInsetLeftPx = 3f, contentInsetRightPx = 4f,
        ))
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = "甲，乙".mapIndexed { index, value ->
                    ReaderMeasuredInlineItem.Text(value.toString(), 10f, framedStyle, index)
                },
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 25, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(glyphs[0].bounds.top, glyphs[1].bounds.top, 0f)
    }

    /**
     * 纵向等比缩放：中间带对上行盒高，上下两条边按同一倍率换算。行距倍数只把下一行推远，
     * 不改这一行的行盒，因此图高也不变——图高归行盒与倍率管，分页不许改写导入的厚度。
     */
    @Test
    fun nineSliceFrameHeightFollowsTheLineBoxNotTheLineSpacing() {
        val framed = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            contentInsetTopPx = 4f,
            contentInsetBottomPx = 8f,
            contentBandHeightPx = 20f,
        )
        val framedStyle = style.copy(backgroundImage = framed)

        listOf(1f, 1.5f, 2.5f).forEach { multiplier ->
            val page = ReaderPaginator.paginateBlocks(
                listOf(ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, 0)),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                    lineSpacingMultiplier = multiplier,
                )),
                config.copy(viewportHeightPx = 200),
            ).single()

            val glyph = page.elements.single() as ReaderElement.Text
            val run = page.textBackgroundRuns().single()
            // 上下各让出图自己的边条 4 与 8，字就落在两条切线之间。
            assertEquals(4f, glyph.backgroundFrameTopPx, 0.001f)
            assertEquals(8f, glyph.backgroundFrameBottomPx, 0.001f)
            // 左右同理：倍率正好是 1，所以边条就是它们自己的厚度。
            assertEquals(3f, glyph.backgroundFrameLeftPx, 0.001f)
            assertEquals(4f, glyph.backgroundFrameRightPx, 0.001f)
            // 图总高 = 4 + 行盒 20 + 8 = 32，与行距倍数无关。
            assertEquals(32f, run.bounds.height, 0.01f)
            assertEquals(17f, run.bounds.width, 0.01f)
            assertEquals(framed, glyph.style.backgroundImage)
        }
    }

    /**
     * 四条边共用一个倍率：行盒只有中间带的一半时，左右边条也必须跟着缩一半。
     * 只缩纵向就等于把图横向拉一倍——右边那块图案会被压扁。
     */
    @Test
    fun nineSliceFrameScalesAllFourEdgesByOneFactor() {
        val framed = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 20f,
            contentInsetRightPx = 20f,
            contentInsetTopPx = 10f,
            contentInsetBottomPx = 10f,
            contentBandHeightPx = 40f,
        )
        val framedStyle = style.copy(backgroundImage = framed)

        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, 0)),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                lineSpacingMultiplier = 1f,
            )),
            config.copy(viewportHeightPx = 200),
        ).single()

        val glyph = page.elements.single() as ReaderElement.Text
        val run = page.textBackgroundRuns().single()
        // 倍率 = 行盒 20 / 中间带 40 = 0.5：四边各 10×0.5、20×0.5。
        assertEquals(5f, glyph.backgroundFrameTopPx, 0.001f)
        assertEquals(5f, glyph.backgroundFrameBottomPx, 0.001f)
        assertEquals(10f, glyph.backgroundFrameLeftPx, 0.001f)
        assertEquals(10f, glyph.backgroundFrameRightPx, 0.001f)
        // 整张图高 = 5 + 行盒 20 + 5 = 30，正是自然高 60 的一半。
        assertEquals(30f, run.bounds.height, 0.01f)
    }

    /**
     * 左/右偏移各自把气泡的那一端推出去，字一个都不动（偏移只改图，不改排版）。
     * 负值收到头也只能把中间那一格挤成零宽，两侧各让一半，不会交叉。
     */
    @Test
    fun lengthOffsetMovesOnlyTheTwoEndsOfTheBubble() {
        fun runBounds(left: Float, right: Float): ReaderRect {
            val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
                "frame.png", 3, 1f,
                contentInsetLeftPx = 3f,
                contentInsetRightPx = 4f,
                lengthOffsetLeftPx = left,
                lengthOffsetRightPx = right,
            ))
            val page = ReaderPaginator.paginateBlocks(
                listOf(ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(
                        ReaderMeasuredInlineItem.Text("甲", 10f, framedStyle, 0),
                        ReaderMeasuredInlineItem.Text("乙", 10f, framedStyle, 1),
                    ),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                )),
                config.copy(viewportWidthPx = 60, viewportHeightPx = 100),
            ).single()
            val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
            // 字的位置与不开背景图时逐字节一致：段首不从 8f 起、段末不推到 28f。
            assertEquals(listOf(0f, 10f), glyphs.map { it.bounds.left })
            return page.textBackgroundRuns().single().bounds
        }

        // 文字段 0..20：左沿 = 0 − 左边条 3 − 左偏移 10，右沿 = 20 + 右边条 4 + 右偏移 6。
        val widened = runBounds(10f, 6f)
        assertEquals(-13f, widened.left, 0.001f)
        assertEquals(30f, widened.right, 0.001f)

        // 两侧各 -20：字宽 20，每边最多只能让出一半（10），中间那一格挤到零宽就停住，
        // 两条切分线正好撞上而不会交叉，外框整体收进文字段里面。
        val shrunk = runBounds(-20f, -20f)
        assertEquals(7f, shrunk.left, 0.001f)
        assertEquals(14f, shrunk.right, 0.001f)
    }

    /**
     * 连续两行都带框时各自量自己的行盒：两行的图一样高，都是「行盒 + 上下两条等比换算过的边条」，
     * 谁也不被行距挤扁，也不去盖住另一行。
     */
    @Test
    fun consecutiveFramedLinesBothSizeToTheirOwnLineBox() {
        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            contentInsetTopPx = 4f,
            contentInsetBottomPx = 8f,
            contentBandHeightPx = 20f,
        ))
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = (0 until 4).map { index ->
                    ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, index)
                },
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                lineSpacingMultiplier = 1f,
            )),
            config.copy(viewportWidthPx = 30, viewportHeightPx = 200),
        ).single()

        val runs = page.textBackgroundRuns()

        assertEquals(2, runs.size)
        runs.forEach { assertEquals(32f, it.bounds.height, 0.01f) }
        // 两行的图一样高，只是各自往下挪了一行的距离——没有被行距挤扁。
        assertEquals(20f, runs[1].bounds.top - runs[0].bounds.top, 0.01f)
    }

    /**
     * 命中字距只在命中段的两端留白：段首让出 before、段末让出 after，段内一个字都不加
     * （段内也加的话，调的就不是间距而是字号了）。
     */
    @Test
    fun matchSpacingOnlyWidensTheTwoEndsOfAHit() {
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text(
                        "甲", 10f, style.copy(matchSpacingBeforePx = 6f), 0
                    ),
                    ReaderMeasuredInlineItem.Text("乙", 10f, style, 1),
                    ReaderMeasuredInlineItem.Text(
                        "丙", 10f, style.copy(matchSpacingAfterPx = 6f), 2
                    ),
                    ReaderMeasuredInlineItem.Text("丁", 10f, style, 3),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 60, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()

        assertEquals(listOf(6f, 16f, 26f, 42f), glyphs.map { it.bounds.left })
    }

    /**
     * 命中字距留出的那截空隙在命中段**外面**：内容没变，图就不许被拉长。
     * 两个共用同一张背景图的命中段之间隔着这截空隙时，run 必须在空隙处断成两个气泡，
     * 而不是把空隙吞进中间那一格。
     */
    @Test
    fun matchSpacingGapSplitsTheBubbleInsteadOfStretchingIt() {
        val framed = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        )
        val framedStyle = style.copy(backgroundImage = framed)
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, framedStyle, 0),
                    ReaderMeasuredInlineItem.Text("乙", 10f, framedStyle, 1),
                    ReaderMeasuredInlineItem.Text(
                        "丙", 10f, framedStyle.copy(matchSpacingBeforePx = 6f), 2
                    ),
                    ReaderMeasuredInlineItem.Text("丁", 10f, framedStyle, 3),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 200, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        // 空隙照常把后两个字推走：0 / 10 / 26 / 36。
        assertEquals(listOf(0f, 10f, 26f, 36f), glyphs.map { it.bounds.left })
        assertFalse(glyphs[2].continuesBackgroundRun)

        val runs = page.textBackgroundRuns()
        assertEquals(2, runs.size)
        assertEquals(0f, runs[0].contentBounds.left, 0.01f)
        assertEquals(20f, runs[0].contentBounds.right, 0.01f)
        assertEquals(26f, runs[1].contentBounds.left, 0.01f)
        assertEquals(46f, runs[1].contentBounds.right, 0.01f)
        // 每个气泡只按自己那一段的宽度画，左右各留一条原厚边：20 宽 + 3 + 4。
        assertEquals(27f, runs[0].bounds.width, 0.01f)
        assertEquals(27f, runs[1].bounds.width, 0.01f)
    }

    /** 同理，空隙是上一段「命中字距（后）」让出来的，也要断在空隙之前。 */
    @Test
    fun matchSpacingAfterAlsoSplitsTheBubble() {
        val framed = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        )
        val framedStyle = style.copy(backgroundImage = framed)
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, framedStyle, 0),
                    ReaderMeasuredInlineItem.Text(
                        "乙", 10f, framedStyle.copy(matchSpacingAfterPx = 6f), 1
                    ),
                    ReaderMeasuredInlineItem.Text("丙", 10f, framedStyle, 2),
                    ReaderMeasuredInlineItem.Text("丁", 10f, framedStyle, 3),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
            )),
            config.copy(viewportWidthPx = 200, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(0f, 10f, 26f, 36f), glyphs.map { it.bounds.left })
        assertFalse(glyphs[2].continuesBackgroundRun)
        assertTrue(glyphs[3].continuesBackgroundRun)
        val runs = page.textBackgroundRuns()
        assertEquals(2, runs.size)
        assertEquals(20f, runs[0].contentBounds.right, 0.01f)
        assertEquals(26f, runs[1].contentBounds.left, 0.01f)
    }

    /**
     * 命中行行距：包含命中的那一行整行往下抬 before 那一截，之后多留 after 那一截；
     * 行盒本身高度不变，不然字会被挤扁。
     */
    @Test
    fun hitLineSpacingMovesTheWholeRowDownWithoutChangingTheLineBox() {
        val padded = style.copy(linePadTopPx = 6f, linePadBottomPx = 4f)
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(
                    ReaderMeasuredInlineItem.Text("甲", 10f, padded, 0),
                    ReaderMeasuredInlineItem.Text("乙", 10f, style, 1),
                    ReaderMeasuredInlineItem.Text("丙", 10f, style, 2),
                    ReaderMeasuredInlineItem.Text("丁", 10f, style, 3),
                ),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                lineSpacingMultiplier = 1f,
            )),
            config.copy(viewportWidthPx = 20, viewportHeightPx = 200),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()

        // 第一行被抬起 6px，行盒仍高 20；第二行落在 20 + 6 + 4 = 30。
        assertEquals(
            listOf(6f, 30f),
            glyphs.map { it.bounds.top }.distinct().sorted(),
        )
        glyphs.forEach { assertEquals(20f, it.bounds.height, 0.001f) }
    }

    /**
     * 章末页的堆叠高度额外加 [ReaderPaginationConfig.chapterEndPaddingPx]：旧
     * `TextChapterLayout.setTypeText` 收尾时 `height = max(height, durY + 20dp)`，让下一章
     * 正文与本章末尾之间留一段空档。中间页不受影响。
     */
    @Test
    fun scrollModeAddsTheLegacyChapterEndPaddingOnlyToTheLastPage() {
        val scrollConfig = config.copy(continuousScroll = true, chapterEndPaddingPx = 20f)
        val lines = (0..2).map { index ->
            ReaderMeasuredParagraph(
                index.toString(),
                listOf(index.toString()),
                listOf(20f),
                style,
                index
            )
        }
        val pages = ReaderPaginator.paginate(lines, scrollConfig)
        assertEquals(2, pages.size)
        // 内容区高度 40f（45 − 5）：中间页就是排版游标；章末页同样是游标（20f）加 20f 留白，
        // 不向内容区高度收口（对照旧 TextChapterLayout 的 `height = durY + 20dp`）。
        assertEquals(40f, pages[0].scrollExtentPx, 0.01f)
        assertEquals(40f, pages[1].scrollExtentPx, 0.01f)
    }

    /**
     * 滚动模式章末残页只占自身内容高度 + [ReaderPaginationConfig.chapterEndPaddingPx]：下一章
     * 正文紧接本章末尾出现，中间不会先顶满一屏空白。
     *
     * 旧 `TextChapterLayout.setTypeText` 收尾时 `textPage.height = durY + 20dp`（`durY` 是排版
     * 游标），`ContentTextView.drawPage` 把下一页画在 `相对偏移 + textPage.height` 处；把章末页
     * 收口到「内容区高度」会让残页后的空白撑满一屏，必须滚过整屏才接上下一章。
     */
    @Test
    fun scrollModeChapterEndIsFollowedImmediatelyByTheNextChapterContent() {
        val scrollConfig = config.copy(continuousScroll = true, chapterEndPaddingPx = 20f)
        val chapterEnd = ReaderPaginator.paginate(listOf(paragraph("甲")), scrollConfig).single()
        val nextChapter = ReaderPaginator.paginate(
            listOf(paragraph("乙")),
            scrollConfig.copy(chapterIndex = config.chapterIndex + 1),
        ).single()

        // 内容区高 40f（45 − 5），本章只有一行 20f：章末页页高 = 20f 内容 + 20f 留白。
        assertEquals(40f, chapterEnd.scrollExtentPx, 0.01f)
        val stackedGap = chapterEnd.scrollExtentPx +
                nextChapter.elements.minOf { it.bounds.top } -
                chapterEnd.elements.maxOf { it.bounds.bottom }
        assertEquals(
            "下一章首行与本章末行之间只应留 chapterEndPaddingPx",
            scrollConfig.chapterEndPaddingPx,
            stackedGap,
            0.01f,
        )
    }

    @Test
    fun letterSpacedBackgroundRowStaysOneRunInsteadOfPerGlyph() {
        val bgStyle = style.copy(backgroundImage = ReaderTextBackgroundImage("bg.png", 1, 1f))
        val paragraph = ReaderMeasuredParagraph(
            "甲乙丙", listOf("甲", "乙", "丙"), List(3) { 10f }, bgStyle, 0,
            letterSpacingPx = 2f,
        )
        val page = ReaderPaginator.paginate(listOf(paragraph), config).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        // 字间距在字形间留下 2f 间隙：旧实现按字拆 run，现在整行合并为一段
        assertEquals(listOf(0f, 12f, 24f), glyphs.map { it.bounds.left })
        val runs = page.textBackgroundRuns()
        assertEquals(1, runs.size)
        assertEquals(34f, runs.single().contentBounds.right, 0f)
    }

    /**
     * 高亮规则命中的段落走富文本路径（逐 item 样式），背景图 fit=1（拉伸）/0（平铺）/2（裁剪）
     * 不参与九宫格预算，但放行标记必须照发：默认字间距 0.1em 远大于 1px 的几何相邻阈值，
     * 少发标记就会把一条连续气泡切成逐字绘制（issue #2286）。
     */
    @Test
    fun stretchedBackgroundMergesAcrossLetterSpacingOnRichTextRow() {
        val stretched = ReaderTextBackgroundImage("bubble.png", fit = 1, scale = 1f)
        val stretchedStyle = style.copy(backgroundImage = stretched)
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = (0 until 3).map { index ->
                        ReaderMeasuredInlineItem.Text("字", 10f, stretchedStyle, index)
                    },
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                )
            ),
            config.copy(viewportWidthPx = 100, viewportHeightPx = 100, letterSpacingPx = 5f),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        // 字间距在每个字之间留下 5f 间隙，几何相邻判定必然失败。
        assertEquals(listOf(0f, 15f, 30f), glyphs.map { it.bounds.left })
        val run = page.textBackgroundRuns().single()
        assertEquals(0f, run.contentBounds.left, 0f)
        assertEquals(40f, run.contentBounds.right, 0f)
    }

    /**
     * 流式会话（对照旧 View `TextChapterLayout.onPageCompleted()` 的 `channel.trySend`）：
     * 逐 block 推送得到的页必须与整章批次入口完全一致，流出顺序也与最终列表一致。
     */
    @Test
    fun streamingSessionEmitsExactlyTheBatchPages() {
        val scrollConfig = config.copy(continuousScroll = true, chapterEndPaddingPx = 7f)
        val blocks = (0..4).map { index ->
            ReaderMeasuredBlock.Paragraph(paragraph(index.toString(), index))
        }
        val batch = ReaderPaginator.paginateBlocks(blocks, scrollConfig)

        val session = ReaderPaginationSession(scrollConfig)
        val streamed = mutableListOf<io.legado.app.feature.reader.core.model.ReaderPage>()
        session.onPage = { streamed += it }
        blocks.forEach(session::accept)
        val finished = session.finish()

        assertEquals(batch.size, finished.size)
        assertEquals(batch.map { it.id }, finished.map { it.id })
        assertEquals(batch.map { it.text }, finished.map { it.text })
        assertEquals(batch.map { it.scrollExtentPx }, finished.map { it.scrollExtentPx })
        // 5 个单行段落排成 3 页（2/2/1）：章末留白只加在最后一页（20f 内容 + 7f 留白）。
        assertEquals(listOf(40f, 40f, 27f), finished.map { it.scrollExtentPx })
        assertEquals(finished.map { it.id }, streamed.map { it.id })
        assertEquals(finished.map { it.scrollExtentPx }, streamed.map { it.scrollExtentPx })
    }

    /**
     * 章末页延迟到收尾才流出：刚收尾的页只有在下一次收尾（或章末）才知道自己是不是最后一页，
     * 而最后一页的堆叠高度要加 [ReaderPaginationConfig.chapterEndPaddingPx]。
     */
    @Test
    fun streamingSessionHoldsTheChapterEndPageUntilFinish() {
        val scrollConfig = config.copy(continuousScroll = true, chapterEndPaddingPx = 7f)
        // 内容区高 40f、每行 20f：6 个单行段落排成 3 页（2/2/2）。
        val blocks = (0..5).map { index ->
            ReaderMeasuredBlock.Paragraph(paragraph(index.toString(), index))
        }
        val session = ReaderPaginationSession(scrollConfig)
        val emitted = mutableListOf<Int>()
        session.onPage = { emitted += it.id.pageIndex }
        blocks.forEach(session::accept)

        // 第 2 页成型时第 1 页流出；第 3 页（章末页）要等 finish。
        assertEquals(listOf(0), emitted)
        val pages = session.finish()
        assertEquals(listOf(0, 1, 2), pages.map { it.id.pageIndex })
        assertEquals(listOf(0, 1, 2), emitted)
    }

    /** 空章（没有任何 block）不产出页，也不能让流式会话收尾时越界。 */
    @Test
    fun streamingSessionWithNoBlocksProducesNoPages() {
        val session = ReaderPaginationSession(config)
        val emitted = mutableListOf<Int>()
        session.onPage = { emitted += it.id.pageIndex }
        assertEquals(emptyList<Any>(), session.finish())
        assertEquals(emptyList<Int>(), emitted)
    }
}
