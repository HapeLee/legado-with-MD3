package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.source.ReaderChapterSourceParser
import io.legado.app.feature.reader.core.source.ReaderTitleSegmentation
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderStyleRange
import io.legado.app.feature.reader.core.style.ReaderStyleTarget
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

class ReaderChapterBlockMeasurerTest {
    @Test
    fun cachesShaperPerStyleAndRetainsInheritedFontProperties() = runBlocking {
        var creations = 0
        val body = style.bodyStyle.copy(
            fontWeight = 550, italic = true, fontPath = "content://fonts/body", fontFamily = "serif",
            shadow = io.legado.app.feature.reader.core.model.ReaderTextShadow(3, 2f, 1f, 1f),
        )
        val source = ReaderChapterSourceParser.parse(0, "", listOf("甲乙丙丁", "戊己"), false, false)
        val measured = ReaderChapterBlockMeasurer(
            bodyShaper = shaper, titleShaper = shaper, imageDimensionsResolver = { null },
            textShaperFactory = ReaderTextShaperFactory { creations++; shaper },
        ).measure(source, style.copy(bodyStyle = body)) as ReaderChapterMeasureResult.Success
        assertEquals(1, creations)
        val glyphs = measured.blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
            .flatMap { it.items }.filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertTrue(glyphs.all { it.style == body })
    }

    @Test
    fun subtitleScaleChangesShapingLineMetricsAndSpacingButNotBodyAnchors() = runBlocking {
        val source = ReaderChapterSourceParser.parse(
            1, "甲乙丙丁", listOf("正文"), true, false,
        ).withTitleVisibility(true, ReaderTitleSegmentation(
            type = 1, distance = 2, subtitleScale = 0.5f,
        ))
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            textShaperFactory = ReaderTextShaperFactory { textStyle ->
                ReaderTextShaper { text -> GlyphClusters(text.map(Char::toString), List(text.length) { textStyle.fontSizePx }) }
            },
        ).measure(source, style.copy(
            titleLineHeightPx = 24f,
            titleBaselineOffsetPx = 18f,
            bodyLineSpacingMultiplier = 1.5f,
            titleLineSpacingMultiplier = 2f,
            letterSpacingEm = 0.2f,
            styleRanges = listOf(ReaderStyleRange(3, 5, ReaderStyleTarget.TITLE, ReaderCharacterStyle(colorArgb = 9), 1)),
        )) as ReaderChapterMeasureResult.Success
        val paragraphs = result.blocks.filterIsInstance<ReaderMeasuredBlock.InlineParagraph>()
        val subtitle = paragraphs[1]
        assertEquals(12f, subtitle.lineHeightPx, 0f)
        assertEquals(9f, subtitle.baselineOffsetPx, 0f)
        assertEquals(0.5f, subtitle.titleSpacingScale, 0f)
        assertEquals(2f, subtitle.lineSpacingMultiplier, 0f)
        assertEquals(1.5f, paragraphs.last().lineSpacingMultiplier, 0f)
        assertEquals(4f, paragraphs.first().letterSpacingPx!!, 0f)
        assertEquals(2f, subtitle.letterSpacingPx!!, 0f)
        assertEquals(2f, paragraphs.last().letterSpacingPx!!, 0f)
        val glyphs = subtitle.items.filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals(listOf(10f, 10f), glyphs.map { it.style.fontSizePx })
        assertEquals(listOf(10f, 10f), glyphs.map { it.widthPx })
        assertEquals(listOf(9, 9), glyphs.map { it.style.colorArgb })
        assertEquals(listOf(0, 1), paragraphs.last().items.map { it.chapterPosition })
    }

    private val shaper = ReaderTextShaper { text -> GlyphClusters(text.map(Char::toString), List(text.length) { 10f }) }
    private val style = ReaderChapterMeasureStyle(
        bodyStyle = ReaderTextStyle(1, 10f),
        titleStyle = ReaderTextStyle(2, 20f),
        bodyIndentCharacters = 2,
        bodyAlignment = ReaderTextAlignment.JUSTIFY,
        titleAlignment = ReaderTextAlignment.CENTER,
    )

    @Test
    fun measuresTextAndImagesWithoutLegacyLayoutModels() = runBlocking {
        val source = ReaderChapterSource(1, "章", listOf(
            ReaderChapterSourceBlock.Text("章", 0, true),
            ReaderChapterSourceBlock.Text("正文", 0),
            ReaderChapterSourceBlock.Image("a", 2),
        ), 3)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { ReaderImageDimensions(30f, 40f) },
        )
            .measure(source, style) as ReaderChapterMeasureResult.Success
        val title = result.blocks[0] as ReaderMeasuredBlock.InlineParagraph
        val body = result.blocks[1] as ReaderMeasuredBlock.InlineParagraph
        val image = result.blocks[2] as ReaderMeasuredBlock.Image
        assertTrue(title.emphasized)
        assertEquals(ReaderTextAlignment.CENTER, title.alignment)
        assertEquals(2, body.indentCharacters)
        assertEquals(40f, image.intrinsicHeightPx, 0f)
    }

    @Test
    fun fallsBackToSemanticHtmlTextWithoutLosingPositions() = runBlocking {
        val source = ReaderChapterSource(
            chapterIndex = 1,
            title = "",
            blocks = listOf(ReaderChapterSourceBlock.Html("<b>x</b>", 0, semanticLength = 4)),
            characterCount = 5,
            semanticContent = "甲\n\n乙\n",
        )
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
        ).measure(source, style)
            as ReaderChapterMeasureResult.Success

        val paragraphs = result.blocks
        val first = paragraphs[0] as ReaderMeasuredBlock.InlineParagraph
        val blank = paragraphs[1] as ReaderMeasuredBlock.BlankLine
        val last = paragraphs[2] as ReaderMeasuredBlock.InlineParagraph
        assertEquals("甲", (first.items.single() as ReaderMeasuredInlineItem.Text).value)
        assertEquals(0, first.items.single().chapterPosition)
        assertEquals(2, blank.chapterPosition)
        assertEquals("乙", (last.items.single() as ReaderMeasuredInlineItem.Text).value)
        assertEquals(3, last.items.single().chapterPosition)
    }

    @Test
    fun `html resolver exception also falls back to semantic text`() = runBlocking {
        val source = ReaderChapterSource(
            chapterIndex = 1,
            title = "",
            blocks = listOf(ReaderChapterSourceBlock.Html("broken", 0, semanticLength = 2)),
            characterCount = 3,
            semanticContent = "正文\n",
        )
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, _ -> error("bad span") },
        ).measure(source, style) as ReaderChapterMeasureResult.Success

        val items = (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph)
            .items.filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals("正文", items.joinToString("") { it.value })
        assertEquals(listOf(0, 1), items.map { it.chapterPosition })
    }

    @Test
    fun keepsSmallImageInsideItsTextParagraph() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(
            ReaderChapterSourceBlock.Paragraph(listOf(
                ReaderChapterInlineSource.Text("甲", 0),
                ReaderChapterInlineSource.Image("icon", 1),
                ReaderChapterInlineSource.Text("乙", 2),
            ), 0),
        ), 4)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { ReaderImageDimensions(12f, 12f) },
        )
            .measure(source, style.copy(bodyLineHeightPx = 20f)) as ReaderChapterMeasureResult.Success
        val paragraph = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        assertEquals(3, paragraph.items.size)
        assertTrue(paragraph.items[1] is ReaderMeasuredInlineItem.Image)
        assertEquals(listOf(0, 1, 2), paragraph.items.map { it.chapterPosition })
    }

    @Test
    fun `missing inline image keeps chapter text and uses line sized placeholder`() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(
            ReaderChapterSourceBlock.Paragraph(listOf(
                ReaderChapterInlineSource.Text("甲", 0),
                ReaderChapterInlineSource.Image("broken", 1),
                ReaderChapterInlineSource.Text("乙", 2),
            ), 0),
        ), 3)

        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
        ).measure(source, style.copy(bodyLineHeightPx = 24f)) as ReaderChapterMeasureResult.Success

        val items = (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph).items
        val image = items[1] as ReaderMeasuredInlineItem.Image
        assertEquals(listOf(0, 1, 2), items.map { it.chapterPosition })
        assertEquals(24f, image.widthPx, 0f)
        assertEquals(24f, image.heightPx, 0f)
    }

    @Test
    fun `missing standalone image reserves placeholder without blanking chapter`() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(
            ReaderChapterSourceBlock.Text("正文", 0),
            ReaderChapterSourceBlock.Image("broken", 2),
        ), 3)

        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
        ).measure(source, style.copy(bodyLineHeightPx = 28f)) as ReaderChapterMeasureResult.Success

        val image = result.blocks.last() as ReaderMeasuredBlock.Image
        assertEquals(28f, image.intrinsicWidthPx, 0f)
        assertEquals(28f, image.intrinsicHeightPx, 0f)
    }

    @Test
    fun convertsResolvedHtmlStylesIntoCanvasTextStyles() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(ReaderChapterSourceBlock.Html("<b>x</b>", 5)), 7)
        val measurer = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            textShaperFactory = ReaderTextShaperFactory { textStyle ->
                ReaderTextShaper { text -> GlyphClusters(listOf(text), listOf(textStyle.fontSizePx)) }
            },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, position -> listOf(ReaderHtmlParagraph(listOf(
                ReaderChapterInlineSource.Text(
                    "x", position,
                    ReaderInlineSourceStyle(
                        colorArgb = 9,
                        fontWeight = 700,
                        underline = true,
                        strikeThrough = true,
                        link = "https://x",
                        fontFamily = "monospace",
                    ),
                ),
            ))) },
        )
        val result = measurer.measure(
            source,
            style.copy(styleRanges = listOf(
                ReaderStyleRange(5, 6, ReaderStyleTarget.BODY, ReaderCharacterStyle(markingId = "mark-1"), 10_000),
            )),
        ) as ReaderChapterMeasureResult.Success
        val text = ((result.blocks.single() as ReaderMeasuredBlock.InlineParagraph).items.single() as ReaderMeasuredInlineItem.Text)
        assertEquals(9, text.style.colorArgb)
        assertEquals(700, text.style.fontWeight)
        assertEquals("https://x", text.link)
        assertTrue(text.style.nativeUnderline)
        assertTrue(text.style.underline == null)
        assertTrue(text.style.strikeThrough)
        assertEquals("monospace", text.style.fontFamily)
        assertEquals("mark-1", text.markingId)
    }

    @Test fun convertsHtmlNewlineOnlyParagraphIntoAVisualBlankLineWithoutSyntheticText() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(ReaderChapterSourceBlock.Html("x", 5)), 4)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, _ -> listOf(
                ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.Text("甲", 5))),
                ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.BlankLine(7))),
                ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.Text("乙", 8))),
            ) },
        ).measure(
            source,
            style.copy(bodyLineHeightPx = 20f, bodyLineSpacingMultiplier = 1.5f),
        ) as ReaderChapterMeasureResult.Success

        val blank = result.blocks[1] as ReaderMeasuredBlock.BlankLine
        assertEquals(7, blank.chapterPosition)
        assertEquals(20f, blank.lineHeightPx, 0f)
        assertEquals(1.5f, blank.lineSpacingMultiplier, 0f)
    }

    @Test fun htmlParagraphDoesNotInheritPlainBodyFirstLineIndent() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(ReaderChapterSourceBlock.Html("x", 0)), 1)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, position -> listOf(
                ReaderHtmlParagraph(listOf(ReaderChapterInlineSource.Text("正文", position))),
            ) },
        ).measure(source, style.copy(bodyIndentCharacters = 2)) as ReaderChapterMeasureResult.Success

        val html = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        assertEquals(0, html.indentCharacters)
        assertEquals(0f, html.indentWidthPx!!, 0f)
    }

    @Test fun htmlParagraphCarriesBlockMarginsAndAlignmentIntoCanvasLayout() = runBlocking {
        val source = ReaderChapterSource(1, "", listOf(ReaderChapterSourceBlock.Html("x", 0)), 1)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = shaper,
            titleShaper = shaper,
            imageDimensionsResolver = { null },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, position -> listOf(
                ReaderHtmlParagraph(
                    items = listOf(ReaderChapterInlineSource.Text("正文", position)),
                    firstLineMarginPx = 12f,
                    restLineMarginPx = 8f,
                    alignment = ReaderTextAlignment.CENTER,
                ),
            ) },
        ).measure(source, style) as ReaderChapterMeasureResult.Success

        val html = result.blocks.single() as ReaderMeasuredBlock.InlineParagraph
        assertEquals(12f, html.indentWidthPx!!, 0f)
        assertEquals(8f, html.restLineIndentWidthPx, 0f)
        assertEquals(ReaderTextAlignment.CENTER, html.alignment)
        assertTrue(html.justifyAtWordBoundaries)
    }

    @Test
    fun carriesPerFontLineMetricsIntoInlineGlyphs() = runBlocking {
        val metricShaper = object : ReaderTextShaper {
            override fun shape(text: String) = GlyphClusters(listOf(text), listOf(10f))
            override val fontLineMetrics = ReaderFontLineMetrics(26f, 17f)
        }
        val source = ReaderChapterSource(
            1, "", listOf(ReaderChapterSourceBlock.Text("甲", 0)), 1,
        )

        val result = ReaderChapterBlockMeasurer(
            bodyShaper = metricShaper,
            titleShaper = metricShaper,
            imageDimensionsResolver = { null },
            textShaperFactory = ReaderTextShaperFactory { metricShaper },
        ).measure(
            source,
            style.copy(styleRanges = listOf(
                ReaderStyleRange(0, 1, ReaderStyleTarget.BODY, ReaderCharacterStyle(fontSizeOffsetPx = 2f), 1),
            )),
        ) as ReaderChapterMeasureResult.Success

        val item = (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph)
            .items.single() as ReaderMeasuredInlineItem.Text
        assertEquals(26f, item.lineHeightPx!!, 0f)
        assertEquals(17f, item.baselineOffsetPx!!, 0f)
    }

    @Test
    fun convertsHtmlBaselineSpansUsingActualFontAscentAndDescent() = runBlocking {
        val metricShaper = object : ReaderTextShaper {
            override fun shape(text: String) = GlyphClusters(listOf(text), listOf(10f))
            override val fontLineMetrics = ReaderFontLineMetrics(
                heightPx = 20f,
                baselineOffsetPx = 16f,
                ascentPx = 14f,
                descentPx = 4f,
            )
        }
        val source = ReaderChapterSource(1, "", listOf(ReaderChapterSourceBlock.Html("x", 0)), 3)
        val result = ReaderChapterBlockMeasurer(
            bodyShaper = metricShaper,
            titleShaper = metricShaper,
            imageDimensionsResolver = { null },
            textShaperFactory = ReaderTextShaperFactory { metricShaper },
            htmlSourceResolver = ReaderHtmlSourceResolver { _, position -> listOf(ReaderHtmlParagraph(listOf(
                ReaderChapterInlineSource.Text("上", position, ReaderInlineSourceStyle(superscript = true)),
                ReaderChapterInlineSource.Text("下", position + 1, ReaderInlineSourceStyle(subscript = true)),
            ))) },
        ).measure(source, style.copy(bodyLineHeightPx = 20f, bodyBaselineOffsetPx = 16f))
            as ReaderChapterMeasureResult.Success

        val items = (result.blocks.single() as ReaderMeasuredBlock.InlineParagraph)
            .items.filterIsInstance<ReaderMeasuredInlineItem.Text>()
        assertEquals(-7f, items[0].baselineShiftPx, 0f)
        assertEquals(2f, items[1].baselineShiftPx, 0f)
        assertEquals(10f, items[0].style.fontSizePx, 0f)
        assertEquals(10f, items[1].style.fontSizePx, 0f)
    }
}
