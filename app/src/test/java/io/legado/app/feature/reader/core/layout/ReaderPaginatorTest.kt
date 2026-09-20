package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.model.textBackgroundRuns
import org.junit.Assert.assertEquals
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

    @Test
    fun nineSliceSidePiecesReserveSpaceAndReflowEachVisualLine() {
        val frame = ReaderTextBackgroundImage(
            source = "frame.png",
            fit = 3,
            scale = 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
        )
        val framedStyle = style.copy(backgroundImage = frame)
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
            )),
            config.copy(viewportWidthPx = 25, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(3f, 3f, 3f, 3f), glyphs.map { it.bounds.left })
        assertEquals(listOf(0f, 20f, 40f, 60f), glyphs.map { it.bounds.top })
        assertTrue(page.textBackgroundRuns().all { it.bounds.left == 0f && it.bounds.right == 17f })
    }

    @Test
    fun nineSliceSidePiecesDoNotOverlapAdjacentPlainText() {
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
        val frameBounds = page.textBackgroundRuns().single().bounds
        assertEquals(glyphs[0].bounds.right, frameBounds.left, 0f)
        assertEquals(frameBounds.right, glyphs[2].bounds.left, 0f)
    }

    @Test
    fun nineSliceReflowDoesNotStrandTheRemainderOfAnOriginalLine() {
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
                    items = (0 until 4).map { index ->
                        ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, index)
                    },
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                )
            ),
            config.copy(viewportWidthPx = 35, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        assertEquals(listOf(0f, 0f, 20f, 20f), glyphs.map { it.bounds.top })
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
     * 孤立的一行高亮：上下邻行都没有框，整段行距都空着，框可以按原图尺寸画——不再像
     * 旧 View 那样被钉死在一半行距上（`TextLine.drawNineSliceFrames` 的 overflowScale）。
     */
    @Test
    fun lonelyNineSliceLineMayUseTheWholeLineGap() {
        val framedStyle = style.copy(
            backgroundImage = ReaderTextBackgroundImage(
                "frame.png", 3, 1f,
                contentInsetLeftPx = 3f,
                contentInsetRightPx = 4f,
                contentInsetTopPx = 8f,
                contentInsetBottomPx = 8f,
            )
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, 0)),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                    lineSpacingMultiplier = 1.5f,
                )
            ),
            config.copy(viewportHeightPx = 100),
        ).single()

        val glyph = page.elements.single() as ReaderElement.Text
        // 行距 1.5 ⇒ 整段留白 10px，一半只有 5px。
        assertTrue(glyph.backgroundFrameTopPx > 5f)
        assertEquals(8f, glyph.backgroundFrameTopPx, 0.001f)
        assertEquals(8f, glyph.backgroundFrameBottomPx, 0.001f)
        val fittedImage = glyph.style.backgroundImage!!
        assertEquals(3f, fittedImage.contentInsetLeftPx, 0.001f)
        assertEquals(4f, fittedImage.contentInsetRightPx, 0.001f)
        val run = page.textBackgroundRuns().single()
        assertEquals(glyph.bounds.left - 3f, run.bounds.left, 0.001f)
        assertEquals(glyph.bounds.right + 4f, run.bounds.right, 0.001f)
        assertEquals(glyph.bounds.top - 8f, run.bounds.top, 0.001f)
        assertEquals(glyph.bounds.bottom + 8f, run.bounds.bottom, 0.001f)
    }

    /**
     * 行距不够时四边**等比**收紧：旧 View 只钳上下，左右保持原图厚度，于是「左右两条宽竖边
     * + 上下两条发丝横线、四角被纵向抹平」；这里要求四条边共用同一个因子。
     */
    @Test
    fun nineSliceShrinksAllFourEdgesByTheSameFactorWhenTheGapIsTight() {
        val framedStyle = style.copy(backgroundImage = ReaderTextBackgroundImage(
            "frame.png", 3, 1f,
            contentInsetLeftPx = 3f,
            contentInsetRightPx = 4f,
            contentInsetTopPx = 4f,
            contentInsetBottomPx = 6f,
        ))
        val page = ReaderPaginator.paginateBlocks(
            listOf(ReaderMeasuredBlock.InlineParagraph(
                items = listOf(ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, 0)),
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                lineSpacingMultiplier = 1.2f,
            )
            ),
            config.copy(viewportHeightPx = 100),
        ).single()

        val glyph = page.elements.single() as ReaderElement.Text
        val fitted = glyph.style.backgroundImage!!
        // 行距留白 4px：因子取 min(1, 4/4, 4/6) = 2/3。
        val factor = 2f / 3f
        assertEquals(3f * factor, fitted.contentInsetLeftPx, 0.001f)
        assertEquals(4f * factor, fitted.contentInsetRightPx, 0.001f)
        assertEquals(4f * factor, fitted.contentInsetTopPx, 0.001f)
        assertEquals(6f * factor, fitted.contentInsetBottomPx, 0.001f)
        assertEquals(fitted.contentInsetTopPx, glyph.backgroundFrameTopPx, 0.001f)
        assertEquals(fitted.contentInsetBottomPx, glyph.backgroundFrameBottomPx, 0.001f)
        // 左右边不再独立于上下边：缩放比例一致，四角不会被纵向抹平。
        assertEquals(
            fitted.contentInsetLeftPx / 3f,
            fitted.contentInsetTopPx / 4f,
            0.001f,
        )
    }

    /**
     * 连续多行都带框时，相邻两行各让一半行距：`上一行的下边 + 下一行的上边 ≤ 行距`，
     * 两个框正好相接不重叠，也不会出现两条平行描边。
     */
    @Test
    fun adjacentNineSliceLinesShareTheLineGapInsteadOfOverlapping() {
        val framedStyle = style.copy(
            backgroundImage = ReaderTextBackgroundImage(
                "frame.png", 3, 1f,
                contentInsetLeftPx = 3f,
                contentInsetRightPx = 4f,
                contentInsetTopPx = 8f,
                contentInsetBottomPx = 8f,
            )
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = (0 until 4).map { index ->
                        ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, index)
                    },
                indentCharacters = 0,
                alignment = ReaderTextAlignment.START,
                lineHeightPx = 20f,
                baselineOffsetPx = 15f,
                baseTextSizePx = 10f,
                lineSpacingMultiplier = 1.5f,
            )),
            config.copy(viewportWidthPx = 25, viewportHeightPx = 100),
        ).single()

        val glyphs = page.elements.filterIsInstance<ReaderElement.Text>()
        val lineGapPx = 10f
        val lines = glyphs.groupBy { it.bounds.top }.values.toList()
        assertEquals(2, lines.size)
        // 同一行共用一份 inset（同一个因子），左右与上下等比。
        lines.forEach { line ->
            val image = line.first().style.backgroundImage!!
            line.forEach { assertEquals(image, it.style.backgroundImage) }
            assertEquals(3f * 5f / 8f, image.contentInsetLeftPx, 0.001f)
            assertEquals(5f, image.contentInsetTopPx, 0.001f)
            assertEquals(5f, image.contentInsetBottomPx, 0.001f)
        }
        // 相邻两边各让半个行距：正好相接，不会叠出两条平行描边。
        val upper = lines[0].first().style.backgroundImage!!
        val lower = lines[1].first().style.backgroundImage!!
        assertTrue(upper.contentInsetBottomPx + lower.contentInsetTopPx <= lineGapPx + 0.001f)
        assertEquals(lineGapPx / 2f, upper.contentInsetBottomPx, 0.001f)
        assertEquals(lineGapPx / 2f, lower.contentInsetTopPx, 0.001f)
        assertEquals(upper.contentInsetBottomPx, lines[0].first().backgroundFrameBottomPx, 0.001f)
        assertEquals(lower.contentInsetTopPx, lines[1].first().backgroundFrameTopPx, 0.001f)
    }

    /**
     * 行距 1.0 ⇒ 上下边归零。旧 View 此时画「中心 + 左右两条边」：左右边仍是原图厚度，
     * 且落在文字框外侧；文字照常内缩，不再出现把九格塞进文字框、压住首末字的情形。
     */
    @Test
    fun nineSliceWithoutALineGapKeepsTheSideEdgesOutsideTheTextRect() {
        val framedStyle = style.copy(
            backgroundImage = ReaderTextBackgroundImage(
                "frame.png", 3, 1f,
                contentInsetLeftPx = 3f,
                contentInsetRightPx = 4f,
                contentInsetTopPx = 4f,
                contentInsetBottomPx = 6f,
            )
        )
        val page = ReaderPaginator.paginateBlocks(
            listOf(
                ReaderMeasuredBlock.InlineParagraph(
                    items = listOf(ReaderMeasuredInlineItem.Text("字", 10f, framedStyle, 0)),
                    indentCharacters = 0,
                    alignment = ReaderTextAlignment.START,
                    lineHeightPx = 20f,
                    baselineOffsetPx = 15f,
                    baseTextSizePx = 10f,
                    lineSpacingMultiplier = 1f,
                )
            ),
            config.copy(viewportHeightPx = 100),
        ).single()

        val glyph = page.elements.single() as ReaderElement.Text
        assertEquals(0f, glyph.backgroundFrameTopPx, 0f)
        assertEquals(0f, glyph.backgroundFrameBottomPx, 0f)
        assertEquals(3f, glyph.bounds.left, 0.001f)
        val run = page.textBackgroundRuns().single()
        assertEquals(glyph.bounds.top, run.bounds.top, 0f)
        assertEquals(glyph.bounds.bottom, run.bounds.bottom, 0f)
        assertEquals(glyph.bounds.left - 3f, run.bounds.left, 0.001f)
        assertEquals(glyph.bounds.right + 4f, run.bounds.right, 0.001f)
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
