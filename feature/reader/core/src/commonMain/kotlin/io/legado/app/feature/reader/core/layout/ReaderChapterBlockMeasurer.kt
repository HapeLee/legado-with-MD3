package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderCharacterStyleResolver
import io.legado.app.feature.reader.core.style.ReaderStyleRange
import kotlin.coroutines.cancellation.CancellationException

fun interface ReaderTextShaperFactory {
    fun create(style: ReaderTextStyle): ReaderTextShaper
}

fun interface ReaderHtmlSourceResolver {
    fun resolve(html: String, chapterPosition: Int): List<ReaderHtmlParagraph>?
}

data class ReaderHtmlParagraph(
    val items: List<ReaderChapterInlineSource>,
    val firstLineMarginPx: Float = 0f,
    val restLineMarginPx: Float = 0f,
    val alignment: ReaderTextAlignment? = null,
    val decorations: List<ReaderParagraphDecoration> = emptyList(),
)

enum class ReaderParagraphDecorationKind { QUOTE, BULLET }

data class ReaderParagraphDecoration(
    val kind: ReaderParagraphDecorationKind,
    val colorArgb: Int?,
    val sizePx: Float,
    val leadingOffsetPx: Float = 0f,
)

data class ReaderImageDimensions(val widthPx: Float, val heightPx: Float)

enum class ReaderImageLayoutMode { AUTO, INLINE, STANDALONE, FULL_WIDTH, SINGLE_PAGE }

data class ReaderImageOptions(
    val layoutMode: ReaderImageLayoutMode? = null,
    val requestedWidthPx: Float? = null,
    val requestedWidthFraction: Float? = null,
    val horizontalAlignment: ReaderTextAlignment? = null,
    val action: String? = null,
)

fun interface ReaderImageDimensionsResolver {
    suspend fun resolve(source: String): ReaderImageDimensions?
}

fun interface ReaderImageOptionsResolver {
    fun resolve(source: String): ReaderImageOptions?
}

data class ReaderChapterMeasureStyle(
    val bodyStyle: ReaderTextStyle,
    val titleStyle: ReaderTextStyle,
    val bodyIndentCharacters: Int,
    val bodyAlignment: ReaderTextAlignment,
    val titleAlignment: ReaderTextAlignment,
    val imagePageBreakBefore: Boolean = false,
    val imagePageBreakAfter: Boolean = false,
    val bodyLineHeightPx: Float? = null,
    val bodyBaselineOffsetPx: Float? = null,
    val titleLineHeightPx: Float? = null,
    val titleBaselineOffsetPx: Float? = null,
    val standaloneImageThresholdPx: Float = 80f,
    val styleRanges: List<ReaderStyleRange> = emptyList(),
    val bodyLineSpacingMultiplier: Float = 1f,
    val titleLineSpacingMultiplier: Float = 1f,
    val letterSpacingEm: Float? = null,
    val bodyIndentText: String? = null,
    val imageLayoutMode: ReaderImageLayoutMode = ReaderImageLayoutMode.AUTO,
    val imageAvailableWidthPx: Float? = null,
)

sealed interface ReaderChapterMeasureResult {
    data class Success(val blocks: List<ReaderMeasuredBlock>) : ReaderChapterMeasureResult
    data class Unsupported(val reason: String) : ReaderChapterMeasureResult
}

class ReaderChapterBlockMeasurer(
    bodyShaper: ReaderTextShaper,
    titleShaper: ReaderTextShaper,
    private val imageDimensionsResolver: ReaderImageDimensionsResolver,
    private val textShaperFactory: ReaderTextShaperFactory = ReaderTextShaperFactory { bodyShaper },
    private val htmlSourceResolver: ReaderHtmlSourceResolver = ReaderHtmlSourceResolver { _, _ -> null },
    private val imageOptionsResolver: ReaderImageOptionsResolver = ReaderImageOptionsResolver { null },
) {
    suspend fun measure(
        source: ReaderChapterSource,
        style: ReaderChapterMeasureStyle,
    ): ReaderChapterMeasureResult {
        val blocks = ArrayList<ReaderMeasuredBlock>(source.blocks.size)
        val shapers = mutableMapOf<ReaderTextStyle, ReaderTextShaper>()
        fun shaper(textStyle: ReaderTextStyle) = shapers.getOrPut(textStyle) {
            textShaperFactory.create(textStyle)
        }
        val bodyIndentText = style.bodyIndentText ?: "　".repeat(style.bodyIndentCharacters.coerceAtLeast(0))
        val bodyIndentWidth by lazy {
            val shaped = shaper(style.bodyStyle).shape(bodyIndentText)
            shaped.widthsPx.sum() + (style.letterSpacingEm ?: 0f) * style.bodyStyle.fontSizePx * shaped.text.size
        }
        suspend fun addStyledParagraph(
            items: List<ReaderChapterInlineSource>,
            isTitle: Boolean,
            titleScale: Float = 1f,
            isSubtitle: Boolean = false,
            applyBodyIndent: Boolean = true,
            firstLineMarginPx: Float = 0f,
            restLineMarginPx: Float = 0f,
            alignmentOverride: ReaderTextAlignment? = null,
            decorations: List<ReaderParagraphDecoration> = emptyList(),
            justifyAtWordBoundaries: Boolean = false,
        ): ReaderChapterMeasureResult.Unsupported? {
            val baseStyle = if (isTitle) style.titleStyle.copy(
                fontSizePx = style.titleStyle.fontSizePx * titleScale,
            ) else style.bodyStyle
            val subtitleBounds = if (isTitle && isSubtitle) shaper(baseStyle).fontBounds else null
            val lineHeight = subtitleBounds?.heightPx
                ?: if (isTitle) style.titleLineHeightPx?.times(titleScale) else style.bodyLineHeightPx
            val baselineOffset = subtitleBounds?.baselineOffsetPx
                ?: if (isTitle) style.titleBaselineOffsetPx?.times(titleScale) else style.bodyBaselineOffsetPx
            val titleSpacingScale = if (subtitleBounds != null) {
                subtitleBounds.heightPx / (style.titleLineHeightPx ?: style.titleStyle.fontSizePx).coerceAtLeast(1f)
            } else titleScale
            val blankLine = items.singleOrNull() as? ReaderChapterInlineSource.BlankLine
            if (blankLine != null) {
                blocks += ReaderMeasuredBlock.BlankLine(
                    chapterPosition = blankLine.chapterPosition,
                    lineHeightPx = lineHeight ?: baseStyle.fontSizePx,
                    lineSpacingMultiplier = if (isTitle) {
                        style.titleLineSpacingMultiplier
                    } else style.bodyLineSpacingMultiplier,
                )
                return null
            }
            val firstText = items.firstOrNull() as? ReaderChapterInlineSource.Text
            val prefixEnd = firstText?.takeIf {
                applyBodyIndent && !isTitle && bodyIndentText.isNotEmpty() &&
                    it.value.startsWith(bodyIndentText)
            }?.let { it.chapterPosition + bodyIndentText.length }
            var emittedContent = false
            var hasStandaloneImage = false
            val inline = mutableListOf<ReaderMeasuredInlineItem>()
            fun flushInline(skipBlank: Boolean = false) {
                if (inline.isEmpty()) return
                // Processed paragraphs include indentation even when they contain only an image.
                // Hide that geometry, but retain the source's character-position space.
                if (skipBlank && inline.all { it is ReaderMeasuredInlineItem.Text && it.value.isBlank() }) {
                    inline.clear()
                    return
                }
                val leadingIndentItems = if (prefixEnd == null) 0 else inline.takeWhile {
                    it is ReaderMeasuredInlineItem.Text && it.chapterPosition < prefixEnd
                }.size
                val needsIndent = applyBodyIndent && !isTitle &&
                    leadingIndentItems == 0 && !emittedContent
                val htmlFirstLineMargin = if (emittedContent) restLineMarginPx else firstLineMarginPx
                blocks += ReaderMeasuredBlock.InlineParagraph(
                    items = inline.toList(),
                    indentCharacters = if (needsIndent) style.bodyIndentCharacters else 0,
                    indentWidthPx = if (needsIndent) bodyIndentWidth else htmlFirstLineMargin,
                    restLineIndentWidthPx = restLineMarginPx,
                    leadingIndentItems = leadingIndentItems,
                    decorations = decorations,
                    justifyAtWordBoundaries = justifyAtWordBoundaries,
                    alignment = alignmentOverride ?: if (isTitle) style.titleAlignment else style.bodyAlignment,
                    lineHeightPx = lineHeight ?: baseStyle.fontSizePx,
                    baselineOffsetPx = baselineOffset ?: baseStyle.fontSizePx,
                    baseTextSizePx = baseStyle.fontSizePx,
                    emphasized = isTitle,
                    titleSpacingScale = if (isTitle) titleSpacingScale else 1f,
                    lineSpacingMultiplier = if (isTitle) style.titleLineSpacingMultiplier else style.bodyLineSpacingMultiplier,
                    letterSpacingPx = style.letterSpacingEm?.times(baseStyle.fontSizePx),
                )
                inline.clear()
                emittedContent = true
            }
            items.forEach { item ->
                when (item) {
                    is ReaderChapterInlineSource.Text -> {
                        val htmlStyle = baseStyle.merge(item.style)
                        val initiallyShaped = ReaderParagraphFactory(shaper(htmlStyle))
                            .create(item.value, htmlStyle, item.chapterPosition)
                        var offset = 0
                        initiallyShaped.clusters.forEach { cluster ->
                            val position = item.chapterPosition + offset
                            val rangeStyle = ReaderCharacterStyleResolver.resolve(style.styleRanges, position, isTitle)
                            val textStyle = htmlStyle.merge(rangeStyle)
                            val textShaper = shaper(textStyle)
                            val width = textShaper.shape(cluster).widthsPx.firstOrNull() ?: 0f
                            // The paragraph already owns the base line box (including the special
                            // subtitle bounds). Style overrides and baseline-shift spans need
                            // per-glyph metrics so their visual extents can expand the shared line.
                            val hasBaselineShift = item.style.superscript || item.style.subscript
                            // Highlight rules are paint-only except for an explicit size
                            // offset.  Letting color/underline/typeface/weight matches change
                            // the shared row metrics made line and paragraph spacing vary with
                            // the text a rule happened to match.  HTML baseline shifts and a
                            // requested size offset still need their own visual extents.
                            val lineMetrics = textShaper.fontLineMetrics.takeIf {
                                hasBaselineShift || rangeStyle?.fontSizeOffsetPx != 0f
                            }
                            val baselineShift = lineMetrics?.let { metrics ->
                                (if (item.style.superscript) -metrics.ascentPx / 2f else 0f) +
                                    (if (item.style.subscript) metrics.descentPx / 2f else 0f)
                            } ?: 0f
                            inline += ReaderMeasuredInlineItem.Text(
                                value = cluster,
                                widthPx = width,
                                style = textStyle,
                                chapterPosition = position,
                                link = item.style.link,
                                markingId = rangeStyle?.markingId,
                                lineHeightPx = lineMetrics?.heightPx,
                                baselineOffsetPx = lineMetrics?.baselineOffsetPx,
                                baselineShiftPx = baselineShift,
                            )
                            offset += cluster.length
                        }
                    }
                    is ReaderChapterInlineSource.Image -> {
                        // A broken image must not make the entire chapter disappear. The bitmap
                        // loader already supplies an error image; reserve stable line geometry
                        // until real dimensions are available.
                        val placeholderExtent = (lineHeight ?: baseStyle.fontSizePx).coerceAtLeast(1f)
                        val originalSize = imageDimensionsResolver.resolve(item.source)
                            ?: ReaderImageDimensions(placeholderExtent, placeholderExtent)
                        val options = imageOptionsResolver.resolve(item.source)
                        val requestedWidth = options?.requestedWidthFraction?.let { fraction ->
                            style.imageAvailableWidthPx?.times(fraction)
                        } ?: options?.requestedWidthPx
                        val size = originalSize.withWidth(requestedWidth)
                        val mode = options?.layoutMode ?: if (
                            style.imagePageBreakBefore && style.imagePageBreakAfter
                        ) ReaderImageLayoutMode.SINGLE_PAGE else style.imageLayoutMode
                        val standalone = mode != ReaderImageLayoutMode.INLINE && (
                            mode == ReaderImageLayoutMode.STANDALONE ||
                            mode == ReaderImageLayoutMode.FULL_WIDTH ||
                            mode == ReaderImageLayoutMode.SINGLE_PAGE ||
                            size.widthPx >= style.standaloneImageThresholdPx ||
                            size.heightPx >= style.standaloneImageThresholdPx)
                        if (standalone) {
                            flushInline(skipBlank = true)
                            hasStandaloneImage = true
                            blocks += ReaderMeasuredBlock.Image(
                                source = item.source,
                                intrinsicWidthPx = size.widthPx,
                                intrinsicHeightPx = size.heightPx,
                                chapterPosition = item.chapterPosition,
                                action = options?.action,
                                horizontalAlignment = options?.horizontalAlignment ?: ReaderTextAlignment.CENTER,
                                scaleMode = mode.toScaleMode(),
                                pageBreakBefore = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                                pageBreakAfter = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                            )
                        } else {
                            val maxHeight = lineHeight ?: baseStyle.fontSizePx
                            val scale = minOf(1f, maxHeight / size.heightPx.coerceAtLeast(1f))
                            inline += ReaderMeasuredInlineItem.Image(
                                source = item.source,
                                widthPx = size.widthPx * scale,
                                heightPx = size.heightPx * scale,
                                chapterPosition = item.chapterPosition,
                                action = options?.action,
                            )
                        }
                    }
                    is ReaderChapterInlineSource.BlankLine -> Unit
                }
            }
            flushInline(skipBlank = hasStandaloneImage)
            return null
        }
        source.blocks.forEach { block ->
            when (block) {
                is ReaderChapterSourceBlock.Text -> {
                    addStyledParagraph(
                        listOf(ReaderChapterInlineSource.Text(block.value, block.chapterPosition)),
                        block.isTitle,
                        block.fontSizeScale,
                        block.isSubtitle,
                    )?.let { return it }
                }
                is ReaderChapterSourceBlock.Image -> {
                    val placeholderExtent = (style.bodyLineHeightPx ?: style.bodyStyle.fontSizePx)
                        .coerceAtLeast(1f)
                    val originalSize = imageDimensionsResolver.resolve(block.source)
                        ?: ReaderImageDimensions(placeholderExtent, placeholderExtent)
                    val options = imageOptionsResolver.resolve(block.source)
                    val requestedWidth = options?.requestedWidthFraction?.let { fraction ->
                        style.imageAvailableWidthPx?.times(fraction)
                    } ?: options?.requestedWidthPx
                    val size = originalSize.withWidth(requestedWidth)
                    val mode = options?.layoutMode ?: if (
                        style.imagePageBreakBefore && style.imagePageBreakAfter
                    ) ReaderImageLayoutMode.SINGLE_PAGE else style.imageLayoutMode
                    blocks += ReaderMeasuredBlock.Image(
                        source = block.source,
                        intrinsicWidthPx = size.widthPx,
                        intrinsicHeightPx = size.heightPx,
                        chapterPosition = block.chapterPosition,
                        action = options?.action,
                        horizontalAlignment = options?.horizontalAlignment ?: ReaderTextAlignment.CENTER,
                        scaleMode = mode.toScaleMode(),
                        pageBreakBefore = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                        pageBreakAfter = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                    )
                }
                is ReaderChapterSourceBlock.Paragraph -> {
                    addStyledParagraph(block.items, false)?.let { return it }
                }
                is ReaderChapterSourceBlock.Html -> {
                    val paragraphs = try {
                        htmlSourceResolver.resolve(block.value, block.chapterPosition)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                        ?: fallbackHtmlParagraphs(source, block)
                    paragraphs.forEach { paragraph ->
                        addStyledParagraph(
                            items = paragraph.items,
                            isTitle = false,
                            applyBodyIndent = false,
                            firstLineMarginPx = paragraph.firstLineMarginPx,
                            restLineMarginPx = paragraph.restLineMarginPx,
                            alignmentOverride = paragraph.alignment,
                            decorations = paragraph.decorations,
                            justifyAtWordBoundaries = true,
                        )?.let { return it }
                    }
                }
                is ReaderChapterSourceBlock.PageBreak -> blocks += ReaderMeasuredBlock.PageBreak
            }
        }
        return ReaderChapterMeasureResult.Success(blocks)
    }
}

/**
 * Preserves readable content and the parser-owned chapter-position space when styled HTML
 * conversion is unavailable. Normal HTML conversion remains the preferred path.
 */
private fun fallbackHtmlParagraphs(
    source: ReaderChapterSource,
    block: ReaderChapterSourceBlock.Html,
): List<ReaderHtmlParagraph> {
    val start = block.chapterPosition.coerceIn(0, source.semanticContent.length)
    val end = (start + block.semanticLength).coerceIn(start, source.semanticContent.length)
    val semanticText = source.semanticContent.substring(start, end)
    if (semanticText.isEmpty()) return emptyList()

    val paragraphs = mutableListOf<ReaderHtmlParagraph>()
    var paragraphStart = 0
    semanticText.forEachIndexed { index, character ->
        if (character != '\n') return@forEachIndexed
        val value = semanticText.substring(paragraphStart, index)
        val position = start + paragraphStart
        paragraphs += ReaderHtmlParagraph(
            if (value.isEmpty()) {
                listOf(ReaderChapterInlineSource.BlankLine(position))
            } else {
                listOf(ReaderChapterInlineSource.Text(value, position))
            },
        )
        paragraphStart = index + 1
    }
    if (paragraphStart < semanticText.length) {
        paragraphs += ReaderHtmlParagraph(
            listOf(ReaderChapterInlineSource.Text(
                semanticText.substring(paragraphStart),
                start + paragraphStart,
            )),
        )
    }
    return paragraphs
}

private fun ReaderImageDimensions.withWidth(requestedWidthPx: Float?): ReaderImageDimensions {
    val width = requestedWidthPx?.takeIf { it > 0f && widthPx > 0f } ?: return this
    return ReaderImageDimensions(width, heightPx * width / widthPx)
}

private fun ReaderImageLayoutMode.toScaleMode(): ReaderImageScaleMode = when (this) {
    ReaderImageLayoutMode.FULL_WIDTH -> ReaderImageScaleMode.FIT_WIDTH
    ReaderImageLayoutMode.SINGLE_PAGE -> ReaderImageScaleMode.FIT_PAGE
    else -> ReaderImageScaleMode.CONTAIN_NO_UPSCALE
}

private fun ReaderTextStyle.merge(override: ReaderInlineSourceStyle): ReaderTextStyle = copy(
    colorArgb = override.colorArgb ?: colorArgb,
    backgroundArgb = override.backgroundArgb ?: backgroundArgb,
    fontWeight = override.fontWeight ?: fontWeight,
    italic = override.italic ?: italic,
    fontSizePx = fontSizePx * override.fontSizeScale,
    fontFamily = override.fontFamily ?: fontFamily,
    strikeThrough = override.strikeThrough || strikeThrough,
    nativeUnderline = override.underline || nativeUnderline,
)

private fun ReaderTextStyle.merge(override: ReaderCharacterStyle?): ReaderTextStyle {
    if (override == null) return this
    return copy(
        colorArgb = override.colorArgb ?: colorArgb,
        backgroundArgb = override.backgroundArgb ?: backgroundArgb,
        underline = override.underline ?: underline,
        fontPath = override.fontPath ?: fontPath,
        fontWeight = override.fontWeight ?: fontWeight,
        italic = override.italic ?: italic,
        fontSizePx = (fontSizePx + override.fontSizeOffsetPx).coerceAtLeast(1f),
        backgroundImage = override.backgroundImage ?: backgroundImage,
    )
}
