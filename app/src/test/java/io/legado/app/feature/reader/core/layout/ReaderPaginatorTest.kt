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

    @Test
    fun nineSliceVerticalFrameScalesIntoHalfTheAvailableLineGap() {
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
                lineSpacingMultiplier = 1.5f,
            )),
            config.copy(viewportHeightPx = 100),
        ).single()

        val glyph = page.elements.single() as ReaderElement.Text
        assertEquals(10f / 3f, glyph.backgroundFrameTopPx, 0.001f)
        assertEquals(5f, glyph.backgroundFrameBottomPx, 0.001f)
        val run = page.textBackgroundRuns().single()
        assertEquals(glyph.bounds.top, run.contentBounds.top, 0f)
        assertEquals(glyph.bounds.bottom, run.contentBounds.bottom, 0f)
        assertEquals(glyph.bounds.top - 10f / 3f, run.bounds.top, 0.001f)
        assertEquals(glyph.bounds.bottom + 5f, run.bounds.bottom, 0.001f)
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
}
