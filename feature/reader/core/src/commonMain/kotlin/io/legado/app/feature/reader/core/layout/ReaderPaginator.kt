package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageDecoration
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.math.max

enum class ReaderTextAlignment { START, CENTER, END, JUSTIFY }
enum class ReaderImageScaleMode { CONTAIN_NO_UPSCALE, FIT_WIDTH, FIT_PAGE }

data class ReaderPageUnderline(
    val colorArgb: Int,
    val widthPx: Float,
    val offsetPx: Float,
    val extendToColumn: Boolean,
    val dashed: Boolean,
    val dashOnPx: Float = 6f,
    val dashOffPx: Float = 6f,
)

data class ReaderPaginationConfig(
    val chapterIndex: Int,
    val chapterTitle: String,
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val paddingLeftPx: Float,
    val paddingTopPx: Float,
    val paddingRightPx: Float,
    val paddingBottomPx: Float,
    val lineHeightPx: Float,
    val baselineOffsetPx: Float,
    val paragraphSpacingPx: Float = 0f,
    val letterSpacingPx: Float = 0f,
    val revision: Long = 0L,
    val decoration: ReaderPageDecoration = ReaderPageDecoration(),
    val titleTopSpacingPx: Float = 0f,
    val titleBottomSpacingPx: Float = 0f,
    val titleParagraphSpacingPx: Float? = null,
    val titleSegmentSpacingPx: Float = 0f,
    /** 卷/空正文章整页只有标题时，标题块在内容区内垂直居中（对照原版空章标题页）。 */
    val titlePageCenterVertical: Boolean = false,
    val columnCount: Int = 1,
    val lineSpacingMultiplier: Float = 1f,
    val continuousScroll: Boolean = false,
    val textBottomJustify: Boolean = false,
    val pageUnderline: ReaderPageUnderline? = null,
    val inlineImagesPreserveScrollLine: Boolean = true,
    val emphasisUnderlineStyle: ReaderEmphasisUnderline? = null,
) {
    init {
        require(columnCount in 1..2)
    }
    // Both columns have their own left/right padding, matching the View reader's gutter.
    val columnStridePx get() = viewportWidthPx / columnCount
    val contentWidthPx get() = (columnStridePx - paddingLeftPx - paddingRightPx).coerceAtLeast(0f)
    val contentBottomPx get() = (viewportHeightPx - paddingBottomPx).coerceAtLeast(paddingTopPx)
}

private data class ReaderLayoutRow(
    val elementStart: Int,
    val elementEnd: Int,
    val top: Float,
    val bottom: Float,
    val standaloneImage: Boolean = false,
)

/** A shaped paragraph. Android supplies glyph-cluster widths using the same font used by Canvas. */
data class ReaderMeasuredParagraph(
    val text: String,
    val clusters: List<String>,
    val clusterWidthsPx: List<Float>,
    val style: ReaderTextStyle,
    val chapterPosition: Int,
    val indentCharacters: Int = 0,
    val alignment: ReaderTextAlignment = ReaderTextAlignment.START,
    val isTitle: Boolean = false,
    val link: String? = null,
    val lineHeightPx: Float? = null,
    val baselineOffsetPx: Float? = null,
    val lineSpacingMultiplier: Float? = null,
    val letterSpacingPx: Float? = null,
    val indentWidthPx: Float? = null,
) {
    init {
        require(clusters.size == clusterWidthsPx.size)
        require(clusters.joinToString("") == text)
    }
}

sealed interface ReaderMeasuredBlock {
    data class Paragraph(val value: ReaderMeasuredParagraph) : ReaderMeasuredBlock

    data class Image(
        val source: String,
        val intrinsicWidthPx: Float,
        val intrinsicHeightPx: Float,
        val chapterPosition: Int,
        val action: String? = null,
        val horizontalAlignment: ReaderTextAlignment = ReaderTextAlignment.CENTER,
        val scaleMode: ReaderImageScaleMode = ReaderImageScaleMode.CONTAIN_NO_UPSCALE,
        val pageBreakBefore: Boolean = false,
        val pageBreakAfter: Boolean = false,
    ) : ReaderMeasuredBlock

    data class InlineParagraph(
        val items: List<ReaderMeasuredInlineItem>,
        val indentCharacters: Int,
        val alignment: ReaderTextAlignment,
        val lineHeightPx: Float,
        val baselineOffsetPx: Float,
        val baseTextSizePx: Float,
        val emphasized: Boolean = false,
        val titleSpacingScale: Float = 1f,
        val lineSpacingMultiplier: Float = 1f,
        val letterSpacingPx: Float? = null,
        val indentWidthPx: Float? = null,
        val restLineIndentWidthPx: Float = 0f,
        val leadingIndentItems: Int = 0,
        val decorations: List<ReaderParagraphDecoration> = emptyList(),
        /** Legacy HTML justification expands word spaces when a line contains several of them. */
        val justifyAtWordBoundaries: Boolean = false,
    ) : ReaderMeasuredBlock

    data class BlankLine(
        val chapterPosition: Int,
        val lineHeightPx: Float,
        val lineSpacingMultiplier: Float,
    ) : ReaderMeasuredBlock

    data class Rule(
        val colorArgb: Int,
        val widthPx: Float,
        val verticalPaddingPx: Float,
        val dashed: Boolean = false,
    ) : ReaderMeasuredBlock

    data object PageBreak : ReaderMeasuredBlock
}

sealed interface ReaderMeasuredInlineItem {
    val widthPx: Float
    val chapterPosition: Int

    data class Text(
        val value: String,
        override val widthPx: Float,
        val style: ReaderTextStyle,
        override val chapterPosition: Int,
        val link: String? = null,
        val markingId: String? = null,
        val lineHeightPx: Float? = null,
        val baselineOffsetPx: Float? = null,
        /** Positive values move this glyph below the line baseline; negative values move it above. */
        val baselineShiftPx: Float = 0f,
    ) : ReaderMeasuredInlineItem

    data class Image(
        val source: String,
        override val widthPx: Float,
        val heightPx: Float,
        override val chapterPosition: Int,
        val action: String? = null,
    ) : ReaderMeasuredInlineItem
}

/** Platform-free page assembler. It never creates or mutates legacy TextPage/TextLine objects. */
object ReaderPaginator {
    fun paginate(
        paragraphs: List<ReaderMeasuredParagraph>,
        config: ReaderPaginationConfig,
    ): List<ReaderPage> = paginateBlocks(paragraphs.map { ReaderMeasuredBlock.Paragraph(it) }, config)

    fun paginateBlocks(
        blocks: List<ReaderMeasuredBlock>,
        config: ReaderPaginationConfig,
    ): List<ReaderPage> {
        if (blocks.isEmpty()) return emptyList()
        val pages = mutableListOf<MutableList<ReaderElement>>()
        val pageTexts = mutableListOf<StringBuilder>()
        val pageExtents = mutableListOf<Float>()
        var elements = mutableListOf<ReaderElement>()
        var pageText = StringBuilder()
        var y = config.paddingTopPx
        var columnIndex = 0
        var columnElementStart = 0
        var columnRows = mutableListOf<ReaderLayoutRow>()

        fun columnLeft() = config.paddingLeftPx + columnIndex * config.columnStridePx
        fun columnHasContent() = elements.size > columnElementStart

        fun addPageUnderline(rowElementStart: Int, lineBottom: Float) {
            val underline = config.pageUnderline ?: return
            if (elements.size <= rowElementStart) return
            val rowElements = elements.subList(rowElementStart, elements.size)
            val start = if (underline.extendToColumn) columnLeft()
                else rowElements.minOf { it.bounds.left }
            val end = if (underline.extendToColumn) columnLeft() + config.contentWidthPx
                else rowElements.maxOf { it.bounds.right }
            val y = lineBottom + underline.offsetPx
            elements += ReaderElement.Rule(
                bounds = ReaderRect(start, y, end, y),
                colorArgb = underline.colorArgb,
                widthPx = underline.widthPx,
                dashed = underline.dashed,
                dashOnPx = underline.dashOnPx,
                dashOffPx = underline.dashOffPx,
                overlayStyledUnderline = true,
            )
        }

        fun shiftElement(element: ReaderElement, deltaY: Float): ReaderElement = when (element) {
            is ReaderElement.Text -> element.copy(
                bounds = element.bounds.offsetY(deltaY),
                baselinePx = element.baselinePx + deltaY,
            )
            is ReaderElement.Image -> element.copy(bounds = element.bounds.offsetY(deltaY))
            is ReaderElement.Review -> element.copy(
                bounds = element.bounds.offsetY(deltaY),
                baselinePx = element.baselinePx + deltaY,
            )
            is ReaderElement.Action -> element.copy(bounds = element.bounds.offsetY(deltaY))
            is ReaderElement.Spacer -> element.copy(bounds = element.bounds.offsetY(deltaY))
            is ReaderElement.Rule -> element.copy(bounds = element.bounds.offsetY(deltaY))
            is ReaderElement.ParagraphMarker -> element.copy(bounds = element.bounds.offsetY(deltaY))
        }

        fun justifyColumnBottom() {
            if (!config.textBottomJustify || columnRows.size <= 1) return
            val last = columnRows.last()
            if (last.standaloneImage) return
            val lastHeight = last.bottom - last.top
            val reservedLineSpacing = config.lineHeightPx * config.lineSpacingMultiplier
            if (config.contentBottomPx - (last.bottom + reservedLineSpacing) >= lastHeight) return
            val surplus = config.contentBottomPx - last.bottom
            if (surplus <= 0f) return
            val gap = surplus / (columnRows.size - 1)
            columnRows.forEachIndexed { rowIndex, row ->
                if (rowIndex == 0) return@forEachIndexed
                val deltaY = gap * rowIndex
                for (elementIndex in row.elementStart until row.elementEnd) {
                    elements[elementIndex] = shiftElement(elements[elementIndex], deltaY)
                }
            }
        }

        fun finishPage() {
            justifyColumnBottom()
            if (elements.isNotEmpty()) {
                pages += elements
                pageTexts += pageText
                pageExtents += if (config.continuousScroll) {
                    max(config.contentBottomPx - config.paddingTopPx, y - config.paddingTopPx)
                } else config.viewportHeightPx.toFloat()
                elements = mutableListOf()
                pageText = StringBuilder()
            }
            y = config.paddingTopPx
            columnIndex = 0
            columnElementStart = 0
            columnRows = mutableListOf()
        }

        fun advanceColumn() {
            if (!columnHasContent()) return
            if (columnIndex + 1 < config.columnCount) {
                justifyColumnBottom()
                columnIndex++
                columnElementStart = elements.size
                columnRows = mutableListOf()
                y = config.paddingTopPx
            } else finishPage()
        }

        fun addParagraph(
            paragraph: ReaderMeasuredParagraph,
            paragraphIndex: Int,
            appendSeparator: Boolean,
        ) {
            val lineHeight = paragraph.lineHeightPx ?: config.lineHeightPx
            val letterSpacing = paragraph.letterSpacingPx ?: config.letterSpacingPx
            val indentWidth = paragraph.indentWidthPx
                ?: (paragraph.style.fontSizePx + letterSpacing) * paragraph.indentCharacters
            val baselineOffset = paragraph.baselineOffsetPx ?: config.baselineOffsetPx
            val ideographWidth = paragraph.clusterWidthsPx.firstOrNull { it > 0f }
                ?: paragraph.style.fontSizePx
            val breaker = ChineseLineBreaker(
                clusters = paragraph.clusters,
                // The shared breaker expects advances including spacing; its width limit
                // compensates for the final, undrawn trailing gap (also used by legacy ZhLayout).
                widthsPx = paragraph.clusterWidthsPx.map { it + letterSpacing },
                indentCharacters = 0,
                widthPx = config.contentWidthPx.toInt(),
                ideographWidthPx = ideographWidth + letterSpacing,
                letterSpacingPx = letterSpacing,
                firstLineWidthPx = (config.contentWidthPx - indentWidth)
                    .coerceAtLeast(0f).toInt(),
            )
            val starts = breaker.lineClusterStarts
            for (lineIndex in 0 until breaker.lineCount) {
                if (y + lineHeight > config.contentBottomPx && columnHasContent()) advanceColumn()
                val from = starts[lineIndex]
                val until = starts[lineIndex + 1]
                val widths = paragraph.clusterWidthsPx.subList(from, until)
                val naturalWidth = widths.sum() + letterSpacing * (widths.size - 1).coerceAtLeast(0)
                val indent = if (lineIndex == 0) indentWidth else 0f
                val available = (config.contentWidthPx - indent).coerceAtLeast(0f)
                val justifyGap = if (
                    paragraph.alignment == ReaderTextAlignment.JUSTIFY &&
                    lineIndex < breaker.lineCount - 1 && widths.size > 1
                ) ((available - naturalWidth) / (widths.size - 1)).coerceAtLeast(0f) else 0f
                var x = columnLeft() + indent + when (paragraph.alignment) {
                    ReaderTextAlignment.CENTER -> (available - naturalWidth).coerceAtLeast(0f) / 2f
                    ReaderTextAlignment.END -> (available - naturalWidth).coerceAtLeast(0f)
                    else -> 0f
                }
                val rowElementStart = elements.size
                var characterOffset = paragraph.clusters.take(from).sumOf(String::length)
                for (clusterIndex in from until until) {
                    val value = paragraph.clusters[clusterIndex]
                    val width = max(paragraph.clusterWidthsPx[clusterIndex], 0f)
                    elements += ReaderElement.Text(
                        bounds = ReaderRect(x, y, x + width, y + lineHeight),
                        baselinePx = y + baselineOffset,
                        value = value,
                        style = paragraph.style,
                        selected = false,
                        emphasized = paragraph.isTitle,
                        link = paragraph.link,
                        chapterPosition = paragraph.chapterPosition + characterOffset,
                        paragraphIndex = paragraphIndex,
                        // 整段共用一个 style：同行内第二字起若带背景图，即与前一字同 run
                        continuesBackgroundRun = paragraph.style.backgroundImage != null && clusterIndex > from,
                    )
                    pageText.append(value)
                    characterOffset += value.length
                    x += width + letterSpacing + justifyGap
                }
                addPageUnderline(rowElementStart, y + lineHeight)
                columnRows += ReaderLayoutRow(rowElementStart, elements.size, y, y + lineHeight)
                y += lineHeight * (paragraph.lineSpacingMultiplier ?: config.lineSpacingMultiplier)
            }
            if (appendSeparator) {
                pageText.append('\n')
                y += if (paragraph.isTitle) config.titleParagraphSpacingPx ?: config.paragraphSpacingPx
                    else config.paragraphSpacingPx
            }
        }

        fun addImage(image: ReaderMeasuredBlock.Image) {
            if (image.pageBreakBefore) advanceColumn()
            val sourceWidth = image.intrinsicWidthPx.coerceAtLeast(1f)
            val sourceHeight = image.intrinsicHeightPx.coerceAtLeast(1f)
            val availableHeight = config.contentBottomPx - config.paddingTopPx
            val singleImage = image.scaleMode == ReaderImageScaleMode.FIT_PAGE ||
                image.pageBreakBefore && image.pageBreakAfter
            val continuousFullImage = config.continuousScroll &&
                image.scaleMode == ReaderImageScaleMode.FIT_WIDTH
            val scale = if (singleImage) {
                minOf(config.contentWidthPx / sourceWidth, availableHeight / sourceHeight)
            } else if (continuousFullImage) {
                config.contentWidthPx / sourceWidth
            } else when (image.scaleMode) {
                ReaderImageScaleMode.CONTAIN_NO_UPSCALE -> minOf(
                    config.contentWidthPx / sourceWidth, availableHeight / sourceHeight, 1f)
                ReaderImageScaleMode.FIT_WIDTH -> minOf(
                    config.contentWidthPx / sourceWidth, availableHeight / sourceHeight)
                ReaderImageScaleMode.FIT_PAGE -> error("single image handled above")
            }
            val width = sourceWidth * scale
            val height = sourceHeight * scale
            if (
                (if (continuousFullImage) y > config.contentBottomPx else y + height > config.contentBottomPx) &&
                columnHasContent()
            ) advanceColumn()
            if (singleImage) y = config.paddingTopPx + (availableHeight - height) / 2f
            val x = when (image.horizontalAlignment) {
                ReaderTextAlignment.START, ReaderTextAlignment.JUSTIFY -> columnLeft()
                ReaderTextAlignment.CENTER -> columnLeft() + (config.contentWidthPx - width) / 2f
                ReaderTextAlignment.END -> columnLeft() + config.contentWidthPx - width
            }
            val rowElementStart = elements.size
            elements += ReaderElement.Image(
                bounds = ReaderRect(x, y, x + width, y + height),
                source = image.source,
                action = image.action,
                chapterPosition = image.chapterPosition,
            )
            columnRows += ReaderLayoutRow(rowElementStart, elements.size, y, y + height, standaloneImage = true)
            pageText.append('\uFFFC')
            y += height + config.paragraphSpacingPx
            if (image.pageBreakAfter) advanceColumn()
        }

        fun addInlineParagraph(
            paragraph: ReaderMeasuredBlock.InlineParagraph,
            paragraphIndex: Int,
            appendSeparator: Boolean,
        ) {
            if (paragraph.items.isEmpty()) return
            val letterSpacing = paragraph.letterSpacingPx ?: config.letterSpacingPx
            val indentWidth = paragraph.indentWidthPx
                ?: (paragraph.baseTextSizePx + letterSpacing) * paragraph.indentCharacters
            val ideographWidth = paragraph.items.filterIsInstance<ReaderMeasuredInlineItem.Text>()
                .firstOrNull { it.widthPx > 0f }?.widthPx ?: paragraph.lineHeightPx
            val clusters = paragraph.items.map {
                when (it) {
                    is ReaderMeasuredInlineItem.Text -> it.value
                    is ReaderMeasuredInlineItem.Image -> "\uFFFC"
                }
            }
            fun backgroundImage(index: Int) = (paragraph.items[index] as? ReaderMeasuredInlineItem.Text)
                ?.style?.backgroundImage?.takeIf { it.fit == 3 }
            fun backgroundInsetBefore(index: Int, lineStart: Int): Float {
                val image = backgroundImage(index) ?: return 0f
                return if (index == lineStart || backgroundImage(index - 1) != image) image.contentInsetLeftPx else 0f
            }
            fun backgroundInsetAfter(index: Int, lineEnd: Int): Float {
                val image = backgroundImage(index) ?: return 0f
                return if (index + 1 == lineEnd || backgroundImage(index + 1) != image) image.contentInsetRightPx else 0f
            }
            val breaker = ChineseLineBreaker(
                clusters = clusters,
                widthsPx = paragraph.items.map { it.widthPx + letterSpacing },
                indentCharacters = 0,
                widthPx = (config.contentWidthPx - paragraph.restLineIndentWidthPx)
                    .coerceAtLeast(0f).toInt(),
                ideographWidthPx = ideographWidth + letterSpacing,
                letterSpacingPx = letterSpacing,
                firstLineWidthPx = (config.contentWidthPx - indentWidth)
                    .coerceAtLeast(0f).toInt(),
            )
            val originalEnds = breaker.lineClusterStarts.drop(1)
            val starts = mutableListOf(0)
            // ZhLayout reserves the nine-slice side pieces after shaping, then pulls an
            // overflowing line back into the column. Move whole clusters instead so Canvas
            // keeps stable hit-test and selection geometry.
            while (starts.last() < paragraph.items.size) {
                val from = starts.last()
                val lineIndent = if (starts.size == 1) indentWidth else paragraph.restLineIndentWidthPx
                val available = config.contentWidthPx - lineIndent
                var until = originalEnds.firstOrNull { it > from } ?: paragraph.items.size
                fun occupiedWidth(until: Int): Float = (from until until).sumOf { index ->
                    (paragraph.items[index].widthPx + backgroundInsetBefore(index, from) +
                        backgroundInsetAfter(index, until)).toDouble()
                }.toFloat() + letterSpacing * (until - from - 1).coerceAtLeast(0)
                while (until - from > 1 && occupiedWidth(until) > available) {
                    val candidate = until - 1
                    if (ChineseLineBreaker.isForbiddenBreak(clusters[candidate - 1], clusters[candidate])) break
                    until = candidate
                }
                starts += until
            }
            for (lineIndex in 0 until starts.lastIndex) {
                val from = starts[lineIndex]
                val until = starts[lineIndex + 1]
                val lineItems = paragraph.items.subList(from, until)
                val textItems = lineItems.filterIsInstance<ReaderMeasuredInlineItem.Text>()
                val maxTextScale = textItems
                    .maxOfOrNull { it.style.fontSizePx / paragraph.baseTextSizePx.coerceAtLeast(1f) } ?: 1f
                val fallbackLineHeight = paragraph.lineHeightPx * maxTextScale
                val fallbackBaseline = paragraph.baselineOffsetPx * maxTextScale
                // Keep the paragraph's unshifted line box as the minimum. A line containing
                // only <sup> or only <sub> must not cancel its own visual movement by moving
                // the shared baseline in the opposite direction.
                val lineAscent = maxOf(fallbackBaseline, textItems.maxOfOrNull {
                    (it.baselineOffsetPx ?: fallbackBaseline) - it.baselineShiftPx
                } ?: fallbackBaseline)
                val fallbackDescent = fallbackLineHeight - fallbackBaseline
                val lineDescent = maxOf(fallbackDescent, textItems.maxOfOrNull {
                    val height = it.lineHeightPx ?: fallbackLineHeight
                    height - (it.baselineOffsetPx ?: fallbackBaseline) + it.baselineShiftPx
                } ?: fallbackDescent)
                val textLineHeight = lineAscent + lineDescent
                val actualLineHeight = maxOf(
                    textLineHeight,
                    lineItems.filterIsInstance<ReaderMeasuredInlineItem.Image>().maxOfOrNull { it.heightPx } ?: 0f,
                )
                val lineBaselineOffset = lineAscent + (actualLineHeight - textLineHeight) / 2f
                if (y + actualLineHeight > config.contentBottomPx && columnHasContent()) advanceColumn()
                val indent = if (lineIndex == 0) indentWidth else paragraph.restLineIndentWidthPx
                val available = (config.contentWidthPx - indent).coerceAtLeast(0f)
                fun backgroundInsetBefore(index: Int) = backgroundInsetBefore(from + index, from)
                fun backgroundInsetAfter(index: Int) = backgroundInsetAfter(from + index, until)
                val naturalWidth = lineItems.sumOf { it.widthPx.toDouble() }.toFloat() +
                    letterSpacing * (lineItems.size - 1).coerceAtLeast(0) +
                    lineItems.indices.sumOf { (backgroundInsetBefore(it) + backgroundInsetAfter(it)).toDouble() }.toFloat()
                val indentItems = (paragraph.leadingIndentItems - from).coerceIn(0, lineItems.size)
                val stretchableGaps = (lineItems.size - indentItems - 1).coerceAtLeast(0)
                val shouldJustify =
                    paragraph.alignment == ReaderTextAlignment.JUSTIFY &&
                        lineIndex < starts.lastIndex - 1
                val residualWidth = if (shouldJustify) {
                    (available - naturalWidth).coerceAtLeast(0f)
                } else 0f
                val wordSpaceCount = if (paragraph.justifyAtWordBoundaries) {
                    lineItems.count { it is ReaderMeasuredInlineItem.Text && it.value == " " }
                } else 0
                val wordSpaceExtra = if (wordSpaceCount > 1) residualWidth / wordSpaceCount else 0f
                val justifyGap = if (wordSpaceExtra == 0f && stretchableGaps > 0) {
                    residualWidth / stretchableGaps
                } else 0f
                var x = columnLeft() + indent + when (paragraph.alignment) {
                    ReaderTextAlignment.CENTER -> (available - naturalWidth).coerceAtLeast(0f) / 2f
                    ReaderTextAlignment.END -> (available - naturalWidth).coerceAtLeast(0f)
                    else -> 0f
                }
                val rowElementStart = elements.size
                val markerColor = textItems.firstOrNull()?.style?.colorArgb
                    ?: 0xff000000.toInt()
                paragraph.decorations.forEach { decoration ->
                    when (decoration.kind) {
                        ReaderParagraphDecorationKind.QUOTE -> elements += ReaderElement.ParagraphMarker(
                            bounds = ReaderRect(
                                columnLeft() + decoration.leadingOffsetPx + decoration.sizePx / 2f,
                                y,
                                columnLeft() + decoration.leadingOffsetPx + decoration.sizePx / 2f,
                                y + actualLineHeight,
                            ),
                            colorArgb = decoration.colorArgb ?: markerColor,
                            strokeWidthPx = decoration.sizePx,
                            circular = false,
                        )
                        ReaderParagraphDecorationKind.BULLET -> if (lineIndex == 0) {
                            elements += ReaderElement.ParagraphMarker(
                                bounds = ReaderRect(
                                    columnLeft() + decoration.leadingOffsetPx + decoration.sizePx,
                                    y + actualLineHeight / 2f,
                                    columnLeft() + decoration.leadingOffsetPx + decoration.sizePx,
                                    y + actualLineHeight / 2f,
                                ),
                                colorArgb = decoration.colorArgb ?: markerColor,
                                strokeWidthPx = decoration.sizePx * 2f,
                                circular = true,
                            )
                        }
                    }
                }
                lineItems.forEachIndexed { itemIndex, item ->
                    x += backgroundInsetBefore(itemIndex)
                    val itemBackground = (item as? ReaderMeasuredInlineItem.Text)
                        ?.style?.backgroundImage
                    when (item) {
                        is ReaderMeasuredInlineItem.Text -> {
                            val expandedWordSpace = if (item.value == " ") wordSpaceExtra else 0f
                            elements += ReaderElement.Text(
                                bounds = ReaderRect(
                                    x, y, x + item.widthPx + expandedWordSpace, y + actualLineHeight,
                                ),
                                baselinePx = y + lineBaselineOffset + item.baselineShiftPx,
                                value = item.value,
                                style = item.style,
                                selected = false,
                                emphasized = paragraph.emphasized,
                                link = item.link,
                                markingId = item.markingId,
                                chapterPosition = item.chapterPosition,
                                paragraphIndex = paragraphIndex,
                                // 富文本逐项样式：与前一项同背景图才视作同一 run 的延续
                                continuesBackgroundRun = itemBackground != null &&
                                        itemIndex > 0 &&
                                        (lineItems[itemIndex - 1] as? ReaderMeasuredInlineItem.Text)
                                            ?.style?.backgroundImage == itemBackground,
                                backgroundFrameTopPx = item.style.backgroundImage?.takeIf { it.fit == 3 }?.let { image ->
                                    val halfGap = ((paragraph.lineSpacingMultiplier - 1f).coerceAtLeast(0f) * actualLineHeight) / 2f
                                    val scale = (halfGap / maxOf(image.contentInsetTopPx, image.contentInsetBottomPx)
                                        .coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                                    image.contentInsetTopPx * scale
                                } ?: 0f,
                                backgroundFrameBottomPx = item.style.backgroundImage?.takeIf { it.fit == 3 }?.let { image ->
                                    val halfGap = ((paragraph.lineSpacingMultiplier - 1f).coerceAtLeast(0f) * actualLineHeight) / 2f
                                    val scale = (halfGap / maxOf(image.contentInsetTopPx, image.contentInsetBottomPx)
                                        .coerceAtLeast(0.1f)).coerceIn(0f, 1f)
                                    image.contentInsetBottomPx * scale
                                } ?: 0f,
                            )
                        }
                        is ReaderMeasuredInlineItem.Image -> {
                            val imageTop = y + (actualLineHeight - item.heightPx) / 2f
                            elements += ReaderElement.Image(
                                bounds = ReaderRect(x, imageTop, x + item.widthPx, imageTop + item.heightPx),
                                source = item.source,
                                action = item.action,
                                chapterPosition = item.chapterPosition,
                            )
                        }
                    }
                    pageText.append(if (item is ReaderMeasuredInlineItem.Text) item.value else '\uFFFC')
                    x += item.widthPx + backgroundInsetAfter(itemIndex) + letterSpacing +
                        if (item is ReaderMeasuredInlineItem.Text && item.value == " ") wordSpaceExtra else 0f
                    x += if (itemIndex >= indentItems) justifyGap else 0f
                }
                addPageUnderline(rowElementStart, y + actualLineHeight)
                columnRows += ReaderLayoutRow(rowElementStart, elements.size, y, y + actualLineHeight)
                y += actualLineHeight * paragraph.lineSpacingMultiplier
            }
            if (appendSeparator) {
                pageText.append('\n')
                y += if (paragraph.emphasized) (config.titleParagraphSpacingPx ?: config.paragraphSpacingPx) * paragraph.titleSpacingScale
                    else config.paragraphSpacingPx
            }
        }

        fun addRule(rule: ReaderMeasuredBlock.Rule) {
            val requiredHeight = rule.verticalPaddingPx * 2f + rule.widthPx
            if (y + requiredHeight > config.contentBottomPx && columnHasContent()) advanceColumn()
            val lineY = y + rule.verticalPaddingPx
            val rowElementStart = elements.size
            elements += ReaderElement.Rule(
                bounds = ReaderRect(columnLeft(), lineY, columnLeft() + config.contentWidthPx, lineY + rule.widthPx),
                colorArgb = rule.colorArgb,
                widthPx = rule.widthPx,
                dashed = rule.dashed,
            )
            columnRows += ReaderLayoutRow(rowElementStart, elements.size, lineY, lineY + rule.widthPx)
            y += requiredHeight
        }

        fun addBlankLine(
            blank: ReaderMeasuredBlock.BlankLine,
            paragraphIndex: Int,
            appendSeparator: Boolean,
        ) {
            val height = blank.lineHeightPx
            if (y + height > config.contentBottomPx && columnHasContent()) advanceColumn()
            val rowElementStart = elements.size
            elements += ReaderElement.Spacer(
                bounds = ReaderRect(columnLeft(), y, columnLeft() + config.contentWidthPx, y + height),
                chapterPosition = blank.chapterPosition,
                paragraphIndex = paragraphIndex,
            )
            columnRows += ReaderLayoutRow(rowElementStart, elements.size, y, y + height)
            y += height * blank.lineSpacingMultiplier
            if (appendSeparator) {
                pageText.append('\n')
                y += config.paragraphSpacingPx
            }
        }

        fun ReaderMeasuredBlock.isTitle() = when (this) {
            is ReaderMeasuredBlock.Paragraph -> value.isTitle
            is ReaderMeasuredBlock.InlineParagraph -> emphasized
            else -> false
        }
        blocks.forEachIndexed { index, block ->
            val isTitle = block.isTitle()
            if (isTitle && index == 0) y += config.titleTopSpacingPx
            when (block) {
                is ReaderMeasuredBlock.Paragraph -> addParagraph(block.value, index, index < blocks.lastIndex)
                is ReaderMeasuredBlock.Image -> addImage(block)
                is ReaderMeasuredBlock.InlineParagraph -> addInlineParagraph(block, index, index < blocks.lastIndex)
                is ReaderMeasuredBlock.BlankLine -> addBlankLine(block, index, index < blocks.lastIndex)
                is ReaderMeasuredBlock.Rule -> addRule(block)
                ReaderMeasuredBlock.PageBreak -> advanceColumn()
            }
            if (isTitle) {
                y += if (blocks.getOrNull(index + 1)?.isTitle() == true) {
                    config.titleSegmentSpacingPx * ((block as? ReaderMeasuredBlock.InlineParagraph)?.titleSpacingScale ?: 1f)
                } else config.titleBottomSpacingPx
            }
        }
        finishPage()
        if (config.titlePageCenterVertical && pages.size == 1) {
            // 卷页只有标题块：按字形实际占位整体下移到内容区垂直中点。布局期平移
            // 保证命中测试、选区与进度映射共用同一几何。
            val pageElements = pages[0]
            val top = pageElements.minOf { it.bounds.top }
            val bottom = pageElements.maxOf { it.bounds.bottom }
            val available = config.contentBottomPx - config.paddingTopPx
            if (bottom - top < available) {
                val delta = config.paddingTopPx + (available - (bottom - top)) / 2f - top
                if (delta != 0f) {
                    for (elementIndex in pageElements.indices) {
                        pageElements[elementIndex] = shiftElement(pageElements[elementIndex], delta)
                    }
                }
            }
        }
        return pages.mapIndexed { pageIndex, pageElements ->
            ReaderPage(
                id = ReaderPageId(config.chapterIndex, pageIndex),
                chapterTitle = config.chapterTitle,
                text = pageTexts[pageIndex].toString(),
                widthPx = config.viewportWidthPx,
                heightPx = config.viewportHeightPx,
                contentTopPx = config.paddingTopPx,
                contentBottomPx = if (config.continuousScroll) {
                    config.contentBottomPx
                } else pageExtents[pageIndex] - config.paddingBottomPx,
                elements = pageElements,
                revision = config.revision,
                scrollExtentPx = pageExtents[pageIndex],
                decoration = config.decoration,
                inlineImagesPreserveScrollLine = config.inlineImagesPreserveScrollLine,
                emphasisUnderlineStyle = config.emphasisUnderlineStyle,
            )
        }
    }
}
