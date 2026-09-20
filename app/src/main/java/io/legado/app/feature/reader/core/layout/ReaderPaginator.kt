package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderEmphasisUnderline
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.model.ReaderPageDecoration
import io.legado.app.feature.reader.core.model.ReaderPageId
import io.legado.app.feature.reader.core.model.ReaderRect
import io.legado.app.feature.reader.core.model.ReaderTextBackgroundImage
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import kotlin.math.max

/**
 * 按这一行上下真正能借到的留白，把九宫格**四边等比**收紧。
 *
 * [topBudgetPx] / [bottomBudgetPx] 由调用方按邻行情况给出：邻行没有高亮框时是**整个行距**
 * （那段留白反正空着），邻行也有框时才各让**半个行距**——两个框正好相接、不重叠。
 *
 * 四边共用一个因子是这里的要点：旧 View `TextLine.drawNineSliceFrames` 只把
 * `overflowScale = halfGap / max(上厚, 下厚)` 用在上下边，左右边永远按原图厚度画，于是行距
 * 一紧就变成「左右两条宽竖边 + 上下两条发丝横线、四角被纵向抹平」的各向异性；而只做各向
 * 同性的等比例收紧时，框又会整体被钉死在半个行距上（默认行距 1.2、行高 60px 只有 6px）。
 * 放宽纵向预算 + 等比收紧，两个毛病一起解掉。
 *
 * 上下边为 0 的色带型图片纵向没有约束，左右按 图片 × scale 原样画；行距为 0 时上下边归零，
 * [ReaderNineSliceLayout] 会跳过高度为 0 的上下两行，自然回落成旧 View 的「中心 + 左右两条边」。
 */
private fun ReaderTextBackgroundImage.fitIntoLineBudget(
    topBudgetPx: Float,
    bottomBudgetPx: Float,
): ReaderTextBackgroundImage {
    if (maxOf(contentInsetTopPx, contentInsetBottomPx) <= 0f) return this
    val frameScale = minOf(
        1f,
        if (contentInsetTopPx > 0f) topBudgetPx / contentInsetTopPx else 1f,
        if (contentInsetBottomPx > 0f) bottomBudgetPx / contentInsetBottomPx else 1f,
    ).coerceIn(0f, 1f)
    if (frameScale >= 1f) return this
    if (frameScale <= 0f) return copy(contentInsetTopPx = 0f, contentInsetBottomPx = 0f)
    return copy(
        contentInsetLeftPx = contentInsetLeftPx * frameScale,
        contentInsetRightPx = contentInsetRightPx * frameScale,
        contentInsetTopPx = contentInsetTopPx * frameScale,
        contentInsetBottomPx = contentInsetBottomPx * frameScale,
    )
}

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
    /**
     * 单图样式（旧 `Book.imgStyleSingle`，`TextChapterLayout.isSingleImageStyle`）。旧实现
     * `setTypeText` 对**每一行文本**都做 `if (textPage.height < visibleHeight) textPage.height =
     * visibleHeight`，因此该样式下含文本的页在滚动堆叠时恒为一屏；纯图片页不受影响
     * （`setTypeImage` 只把 `durY` 移到竖直居中位置）。见 [ReaderPaginator.paginateBlocks]。
     */
    val singleImageStyle: Boolean = false,
    /**
     * 章末页在堆叠高度上额外留出的空档。旧 `TextChapterLayout.setTypeText` 收尾时统一
     * `height = max(height, durY + 20.dpToPx())`，让下一章正文与本章末尾之间不会贴在一起。
     * 只在连续滚动模式生效——分页模式每页高度恒为一屏，这个量不参与布局。
     */
    val chapterEndPaddingPx: Float = 0f,
    val textBottomJustify: Boolean = false,
    /**
     * HTML 段落的行盒附加间距，对照旧 `TextChapterLayout.setTypeHtml` 的
     * `setLineSpacing(paragraphSpacing.toFloat(), lineSpacingExtra)`：行距之外每行再加一次
     * `paragraphSpacing`（原始设置值），并随后由行距乘数再放大一次（旧 `durY += lineHeight *
     * lineSpacingExtra`，乘数被算两次）。只作用于 HTML 块（测量期以 `justifyAtWordBoundaries` 标记）。
     */
    val htmlLineSpacingAddPx: Float = 0f,
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

    /**
     * 整章批次入口：一次拿回全部页。逐页流出走 [ReaderPaginationSession]——旧 View
     * `TextChapterLayout.onPageCompleted()` 是每完成一页就 `channel.trySend`，批次只是它的收尾。
     */
    fun paginateBlocks(
        blocks: List<ReaderMeasuredBlock>,
        config: ReaderPaginationConfig,
    ): List<ReaderPage> {
        if (blocks.isEmpty()) return emptyList()
        val session = ReaderPaginationSession(config)
        blocks.forEach(session::accept)
        return session.finish()
    }
}

/**
 * 推送式分页会话：`accept` 推入一个已测量 block，页一成型就经 [onPage] 流出，[finish] 收尾。
 *
 * 与旧 View `TextChapterLayout` 的对应：`accept` = 排版游标前进（`prepareNextPageIfNeed` 判切页）；
 * 一页成型 = `onPageCompleted()`（旧版在此 `channel.trySend`）。章末页延迟到 [finish] 才流出，
 * 因为只有章末才知道它的堆叠高度要加 [ReaderPaginationConfig.chapterEndPaddingPx]
 * （旧版同样在章末才 `textPage.height = durY + 20dp`）。
 */
internal class ReaderPaginationSession(private val config: ReaderPaginationConfig) {

    /** 每页成型即回调；章末页在 [finish] 内回调。 */
    var onPage: ((ReaderPage) -> Unit)? = null

    private val pages = mutableListOf<MutableList<ReaderElement>>()
    private val pageTexts = mutableListOf<StringBuilder>()
    private val pageExtents = mutableListOf<Float>()
    private val resultPages = mutableListOf<ReaderPage>()
    private var elements = mutableListOf<ReaderElement>()
    private var pageText = StringBuilder()
    private var y = config.paddingTopPx
    private var columnIndex = 0
    private var columnElementStart = 0
    private var columnRows = mutableListOf<ReaderLayoutRow>()
    private var centeredPageShiftPx = 0f

    /**
     * 上一个视觉行是否画了九宫格框。九宫格上下边要借用邻行的留白：邻行没有框时可以
     * 用满整个行距，邻行也有框时只能各让半个。空行 / 图片 / 分隔线 / 分页本身就带空隙，
     * 在这些位置复位。
     */
    private var previousLineHadFrame = false

    /** 1-block 前瞻：标题间距要读下一个 block，与旧 `blocks.getOrNull(index + 1)` 同义。 */
    private var pendingBlock: ReaderMeasuredBlock? = null
    private var pendingIndex = -1

    /** 推入一个已测量 block。 */
    fun accept(block: ReaderMeasuredBlock) {
        val previous = pendingBlock
        if (previous != null) {
            processBlock(
                previous,
                pendingIndex,
                hasFollowingBlock = true,
                nextIsTitle = block.isTitle()
            )
        }
        pendingIndex += 1
        pendingBlock = block
    }

    /** 收尾：处理最后一个 block、结束末页、返回全部页（章末页在此流出）。 */
    fun finish(): List<ReaderPage> {
        pendingBlock?.let {
            processBlock(
                it,
                pendingIndex,
                hasFollowingBlock = false,
                nextIsTitle = false
            )
        }
        pendingBlock = null
        if (pages.isEmpty() && elements.isEmpty()) return emptyList()
        finishPage()
        // 旧 `TextChapterLayout.kt:977-1010` 只在"标题单独占本章第一页、页内还没有正文行"时把
        // 标题块垂直居中，且只对空正文与 `imgStyleSingle` 生效（`else -> durY + titleTopSpacing`
        // 不居中）。判据是"第一页且页内只有标题"，不是"整章只有一页"——后者会把有正文的卷章
        // 整体下移，又漏掉单图样式里"标题页 + 正文页"的居中。
        val firstPageElements = pages.firstOrNull()
        if (config.titlePageCenterVertical &&
            firstPageElements != null &&
            firstPageElements.isTitleOnlyPage()
        ) {
            // 只有标题块：按字形实际占位整体下移到内容区垂直中点。布局期平移
            // 保证命中测试、选区与进度映射共用同一几何。
            val top = firstPageElements.minOf { it.bounds.top }
            val bottom = firstPageElements.maxOf { it.bounds.bottom }
            val available = config.contentBottomPx - config.paddingTopPx
            if (bottom - top < available) {
                val delta = config.paddingTopPx + (available - (bottom - top)) / 2f - top
                if (delta != 0f) {
                    for (elementIndex in firstPageElements.indices) {
                        firstPageElements[elementIndex] =
                            shiftElement(firstPageElements[elementIndex], delta)
                    }
                    centeredPageShiftPx = delta
                }
            }
        }
        pages.lastIndex.takeIf { it >= 0 }?.let { emitPage(it, isLastPage = true) }
        return resultPages
    }

    /** 该页是否只有标题文字（对照旧 `isTitle && pendingTextPage.lines.isEmpty()`）。 */
    private fun MutableList<ReaderElement>.isTitleOnlyPage(): Boolean {
        var hasTitle = false
        for (element in this) {
            val text = element as? ReaderElement.Text ?: continue
            if (text.emphasized) hasTitle = true else return false
        }
        return hasTitle
    }

    private fun processBlock(
        block: ReaderMeasuredBlock,
        index: Int,
        hasFollowingBlock: Boolean,
        nextIsTitle: Boolean,
    ) {
        val isTitle = block.isTitle()
        if (isTitle && index == 0) y += config.titleTopSpacingPx
        when (block) {
            is ReaderMeasuredBlock.Paragraph -> addParagraph(block.value, index, hasFollowingBlock)
            is ReaderMeasuredBlock.Image -> addImage(block)
            is ReaderMeasuredBlock.InlineParagraph -> addInlineParagraph(
                block,
                index,
                hasFollowingBlock
            )

            is ReaderMeasuredBlock.BlankLine -> addBlankLine(block, index, hasFollowingBlock)
            is ReaderMeasuredBlock.Rule -> addRule(block)
            ReaderMeasuredBlock.PageBreak -> {
                previousLineHadFrame = false
                advanceColumn()
            }
        }
        if (isTitle) {
            y += if (nextIsTitle) {
                config.titleSegmentSpacingPx * ((block as? ReaderMeasuredBlock.InlineParagraph)?.titleSpacingScale
                    ?: 1f)
            } else config.titleBottomSpacingPx
        }
    }

    private fun columnLeft() = config.paddingLeftPx + columnIndex * config.columnStridePx
    private fun columnHasContent() = elements.size > columnElementStart

    private fun addPageUnderline(underlineElementStart: Int, lineBottom: Float) {
        val underline = config.pageUnderline ?: return
        if (elements.size <= underlineElementStart) return
        val rowElements = elements.subList(underlineElementStart, elements.size)
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

    private fun shiftElement(element: ReaderElement, deltaY: Float): ReaderElement =
        when (element) {
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

    /** 返回最后一行实际下移量，滚动模式必须把它计入页高。 */
    private fun justifyColumnBottom(): Float {
        if (!config.textBottomJustify || columnRows.size <= 1) return 0f
        val last = columnRows.last()
        if (last.standaloneImage) return 0f
        val lastHeight = last.bottom - last.top
        val reservedLineSpacing = config.lineHeightPx * config.lineSpacingMultiplier
        if (config.contentBottomPx - (last.bottom + reservedLineSpacing) >= lastHeight) return 0f
        val surplus = config.contentBottomPx - last.bottom
        if (surplus <= 0f) return 0f
        val gap = surplus / (columnRows.size - 1)
        columnRows.forEachIndexed { rowIndex, row ->
            if (rowIndex == 0) return@forEachIndexed
            val deltaY = gap * rowIndex
            for (elementIndex in row.elementStart until row.elementEnd) {
                elements[elementIndex] = shiftElement(elements[elementIndex], deltaY)
            }
        }
        return surplus
    }

    private fun finishPage() {
        val bottomJustifyShift = justifyColumnBottom()
        if (elements.isNotEmpty()) {
            pages += elements
            pageTexts += pageText
            pageExtents += if (config.continuousScroll) {
                    // Legacy TextChapterLayout records the scroll-page height from its
                    // current layout cursor (durY). In particular, durY already contains
                    // the final line advance and paragraph spacing. Do not expand it to a
                    // viewport here: the next scroll page must begin immediately after
                    // that spacing, including when a paragraph boundary is also a page
                    // boundary.
                    // 底部对齐把行整体下移后，页高必须同样增加（对照旧 View
                    // TextPage.upLinesPosition 的 `height += surplus`），否则滚动模式下
                    // 下一页会压在当前页最后几行上。
                    (y + bottomJustifyShift - config.paddingTopPx).coerceAtLeast(0f)
                } else config.viewportHeightPx.toFloat()
                elements = mutableListOf()
                pageText = StringBuilder()
            // 延迟一页流出：刚收尾的页可能是章末页（堆叠高度要加 chapterEndPaddingPx），
            // 只有下一次收尾或 finish() 才知道它是不是最后一页。
            if (pages.size >= 2) emitPage(pages.size - 2, isLastPage = false)
            }
            y = config.paddingTopPx
            columnIndex = 0
            columnElementStart = 0
            columnRows = mutableListOf()
        }

    private fun advanceColumn() {
        if (!columnHasContent()) return
        // 换栏/换页后上一行已经不在同一屏，页边距本身就是空隙。
        previousLineHadFrame = false
        if (columnIndex + 1 < config.columnCount) {
            justifyColumnBottom()
            columnIndex++
            columnElementStart = elements.size
            columnRows = mutableListOf()
            y = config.paddingTopPx
        } else finishPage()
    }

    private fun addParagraph(
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
        // 这条路径（整段共用一个 style）不参与九宫格预算，但下一行仍要知道本段带框。
        previousLineHadFrame = paragraph.style.backgroundImage?.fit == 3
        if (appendSeparator) {
            pageText.append('\n')
            y += if (paragraph.isTitle) config.titleParagraphSpacingPx ?: config.paragraphSpacingPx
            else config.paragraphSpacingPx
        }
    }

    private fun addImage(image: ReaderMeasuredBlock.Image) {
        previousLineHadFrame = false
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
                config.contentWidthPx / sourceWidth, availableHeight / sourceHeight, 1f
            )

            ReaderImageScaleMode.FIT_WIDTH -> minOf(
                config.contentWidthPx / sourceWidth, availableHeight / sourceHeight
            )

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
        columnRows += ReaderLayoutRow(
            rowElementStart,
            elements.size,
            y,
            y + height,
            standaloneImage = true
        )
        pageText.append('\uFFFC')
        y += height + config.paragraphSpacingPx
        if (image.pageBreakAfter) advanceColumn()
    }

    private fun addInlineParagraph(
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
        val lineGapPx =
            (paragraph.lineSpacingMultiplier - 1f).coerceAtLeast(0f) * paragraph.lineHeightPx
        val halfLineGapPx = lineGapPx / 2f

        fun itemFrame(index: Int) =
            (paragraph.items[index] as? ReaderMeasuredInlineItem.Text)
                ?.style?.backgroundImage?.takeIf { it.fit == 3 }

        fun lineHasFrame(from: Int, until: Int): Boolean =
            (from until until.coerceAtMost(paragraph.items.size)).any { itemFrame(it) != null }

        fun frameOf(index: Int, topBudgetPx: Float, bottomBudgetPx: Float) =
            itemFrame(index)?.fitIntoLineBudget(topBudgetPx, bottomBudgetPx)

        /**
         * 元素绘制时真正使用的背景图：九宫格按行预算收紧，拉伸/裁剪/平铺按原样。
         *
         * 行内连续放行标记必须用「绘制用实例」比较，不能只看 [frameOf]（它只认 `fit == 3`）。
         * 旧 View `TextLine.drawStyledBackgrounds` 对行内连续的同图段无条件合并，新实现多了
         * 「几何相邻 < 1px」这条，靠分页期放行标记兜住字间距（默认 0.1em，远大于 1px）。
         * 放行标记若只发给九宫格，fit≠3 的背景图就会逐字绘制成一条条断开的气泡。
         */
        fun drawnBackgroundOf(index: Int, topBudgetPx: Float, bottomBudgetPx: Float) =
            (paragraph.items[index] as? ReaderMeasuredInlineItem.Text)
                ?.style?.backgroundImage?.let { image ->
                    if (image.fit == 3) image.fitIntoLineBudget(
                        topBudgetPx,
                        bottomBudgetPx
                    ) else image
                }

        fun backgroundInsetBefore(
            index: Int,
            lineStart: Int,
            topBudgetPx: Float,
            bottomBudgetPx: Float,
        ): Float {
            val image = frameOf(index, topBudgetPx, bottomBudgetPx) ?: return 0f
            return if (
                index == lineStart ||
                frameOf(index - 1, topBudgetPx, bottomBudgetPx) != image
            ) image.contentInsetLeftPx else 0f
        }

        fun backgroundInsetAfter(
            index: Int,
            lineEnd: Int,
            topBudgetPx: Float,
            bottomBudgetPx: Float,
        ): Float {
            val image = frameOf(index, topBudgetPx, bottomBudgetPx) ?: return 0f
            return if (
                index + 1 == lineEnd ||
                frameOf(index + 1, topBudgetPx, bottomBudgetPx) != image
            ) image.contentInsetRightPx else 0f
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
        // 每行的纵向预算在断行阶段就定死，绘制阶段直接复用——排版预留与绘制的外框必须
        // 用同一份 inset，否则框会压到相邻文字上或者留出多余的空档。
        val lineTopBudgets = mutableListOf<Float>()
        val lineBottomBudgets = mutableListOf<Float>()
        // The View reader reserves the left/right pieces around every visual-line run.
        // Refine the shaped line ends so those pieces cannot overlap adjacent text or
        // escape the column. Keep a forbidden Chinese break intact even if its frame has
        // to consume the remaining slack, matching the legacy punctuation priority.
        while (starts.last() < paragraph.items.size) {
            val from = starts.last()
            val lineIndent =
                if (starts.size == 1) indentWidth else paragraph.restLineIndentWidthPx
            val available = config.contentWidthPx - lineIndent
            val topBudgetPx = if (previousLineHadFrame) halfLineGapPx else lineGapPx
            // 下一行的范围在断行阶段还没最终定下来（本行可能被九宫格宽度挤短，把尾巴
            // 让给下一行），用原始断行结果近似探测那一行有没有框。
            val breakerEnd = originalEnds.getOrElse(starts.lastIndex) { paragraph.items.size }
                .coerceAtLeast(from + 1)
            val nextBreakerEnd =
                originalEnds.getOrElse(starts.lastIndex + 1) { paragraph.items.size }
            val bottomBudgetPx =
                if (lineHasFrame(breakerEnd, nextBreakerEnd)) halfLineGapPx else lineGapPx
            // Advance the original visual-line cursor even when a frame forces the
            // preceding row shorter. Reusing the first end after `from` would strand the
            // remainder of that row as an unnecessary one-character line.
            var until = breakerEnd

            fun occupiedWidth(endExclusive: Int): Float =
                (from until endExclusive).sumOf { index ->
                    (paragraph.items[index].widthPx +
                            backgroundInsetBefore(index, from, topBudgetPx, bottomBudgetPx) +
                            backgroundInsetAfter(
                                index,
                                endExclusive,
                                topBudgetPx,
                                bottomBudgetPx
                            )).toDouble()
                }.toFloat() + letterSpacing * (endExclusive - from - 1).coerceAtLeast(0)
            while (until - from > 1 && occupiedWidth(until) > available) {
                val candidate = until - 1
                if (ChineseLineBreaker.isForbiddenBreak(
                        clusters[candidate - 1],
                        clusters[candidate]
                    )
                ) break
                until = candidate
            }
            starts += until
            lineTopBudgets += topBudgetPx
            lineBottomBudgets += bottomBudgetPx
            previousLineHadFrame = lineHasFrame(from, until)
        }
        for (lineIndex in 0 until starts.lastIndex) {
            val from = starts[lineIndex]
            val until = starts[lineIndex + 1]
            val lineItems = paragraph.items.subList(from, until)
            val textItems = lineItems.filterIsInstance<ReaderMeasuredInlineItem.Text>()
            // Inline HTML may shrink every glyph in a row (<small>, font-size, etc.).
            // It changes glyph drawing but not the paragraph's base line box; otherwise a
            // small final row advances less and makes the following paragraph gap collapse.
            val maxTextScale = maxOf(
                1f,
                textItems.maxOfOrNull {
                    it.style.fontSizePx / paragraph.baseTextSizePx.coerceAtLeast(1f)
                } ?: 1f,
            )
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
                lineItems.filterIsInstance<ReaderMeasuredInlineItem.Image>()
                    .maxOfOrNull { it.heightPx } ?: 0f,
            )
            val lineBaselineOffset = lineAscent + (actualLineHeight - textLineHeight) / 2f
            if (y + actualLineHeight > config.contentBottomPx && columnHasContent()) advanceColumn()
            val indent = if (lineIndex == 0) indentWidth else paragraph.restLineIndentWidthPx
            val available = (config.contentWidthPx - indent).coerceAtLeast(0f)
            val topBudgetPx = lineTopBudgets[lineIndex]
            val bottomBudgetPx = lineBottomBudgets[lineIndex]
            fun backgroundInsetBefore(index: Int) =
                backgroundInsetBefore(from + index, from, topBudgetPx, bottomBudgetPx)

            fun backgroundInsetAfter(index: Int) =
                backgroundInsetAfter(from + index, until, topBudgetPx, bottomBudgetPx)

            val naturalWidth = lineItems.sumOf { it.widthPx.toDouble() }.toFloat() +
                    letterSpacing * (lineItems.size - 1).coerceAtLeast(0) +
                    lineItems.indices.sumOf {
                        (backgroundInsetBefore(it) + backgroundInsetAfter(it)).toDouble()
                    }.toFloat()
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
            // Processed body text can retain its indentation as real leading glyphs.
            // The View reader started a non-extended underline after those glyphs.
            val underlineElementStart = elements.size + indentItems
            lineItems.forEachIndexed { itemIndex, item ->
                x += backgroundInsetBefore(itemIndex)
                val itemBackground =
                    drawnBackgroundOf(from + itemIndex, topBudgetPx, bottomBudgetPx)
                when (item) {
                    is ReaderMeasuredInlineItem.Text -> {
                        val expandedWordSpace = if (item.value == " ") wordSpaceExtra else 0f
                        val itemStyle = if (itemBackground == null) item.style else {
                            item.style.copy(backgroundImage = itemBackground)
                        }
                        elements += ReaderElement.Text(
                            bounds = ReaderRect(
                                x, y, x + item.widthPx + expandedWordSpace, y + actualLineHeight,
                            ),
                            baselinePx = y + lineBaselineOffset + item.baselineShiftPx,
                            value = item.value,
                            style = itemStyle,
                            selected = false,
                            emphasized = paragraph.emphasized,
                            link = item.link,
                            markingId = item.markingId,
                            chapterPosition = item.chapterPosition,
                            paragraphIndex = paragraphIndex,
                            // 富文本逐项样式：与前一项同背景图才视作同一 run 的延续。
                            // 比较「绘制用实例」，非九宫格背景图同样要拿到放行标记，
                            // 否则字间距会把它切成逐字绘制。
                            continuesBackgroundRun = itemBackground != null &&
                                    itemIndex > 0 &&
                                    drawnBackgroundOf(
                                        from + itemIndex - 1,
                                        topBudgetPx,
                                        bottomBudgetPx
                                    ) ==
                                    itemBackground,
                            backgroundFrameTopPx = itemBackground?.contentInsetTopPx ?: 0f,
                            backgroundFrameBottomPx = itemBackground?.contentInsetBottomPx
                                ?: 0f,
                        )
                    }

                    is ReaderMeasuredInlineItem.Image -> {
                        val imageTop = y + (actualLineHeight - item.heightPx) / 2f
                        elements += ReaderElement.Image(
                            bounds = ReaderRect(
                                x,
                                imageTop,
                                x + item.widthPx,
                                imageTop + item.heightPx
                            ),
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
            addPageUnderline(underlineElementStart, y + actualLineHeight)
            columnRows += ReaderLayoutRow(rowElementStart, elements.size, y, y + actualLineHeight)
            // 按实际落笔顺序（含换页）记录，供下一段第一行判断上方有没有框。
            previousLineHadFrame = lineHasFrame(from, until)
            // 旧 `setTypeHtml` 的行推进：行盒含 spacingAdd，且行距乘数被算两次；
            // 纯文本段落只算一次（`setTypeText`）。HTML 块由测量期标记。
            val htmlSpacingAddPx =
                if (paragraph.justifyAtWordBoundaries) config.htmlLineSpacingAddPx else 0f
            y += if (htmlSpacingAddPx > 0f) {
                (actualLineHeight * paragraph.lineSpacingMultiplier + htmlSpacingAddPx) *
                        paragraph.lineSpacingMultiplier
            } else {
                actualLineHeight * paragraph.lineSpacingMultiplier
            }
        }
        if (appendSeparator) {
            pageText.append('\n')
            y += if (paragraph.emphasized) (config.titleParagraphSpacingPx
                ?: config.paragraphSpacingPx) * paragraph.titleSpacingScale
            else config.paragraphSpacingPx
        }
    }

    private fun addRule(rule: ReaderMeasuredBlock.Rule) {
        previousLineHadFrame = false
        val requiredHeight = rule.verticalPaddingPx * 2f + rule.widthPx
        if (y + requiredHeight > config.contentBottomPx && columnHasContent()) advanceColumn()
        val lineY = y + rule.verticalPaddingPx
        val rowElementStart = elements.size
        elements += ReaderElement.Rule(
            bounds = ReaderRect(
                columnLeft(),
                lineY,
                columnLeft() + config.contentWidthPx,
                lineY + rule.widthPx
            ),
            colorArgb = rule.colorArgb,
            widthPx = rule.widthPx,
            dashed = rule.dashed,
        )
        columnRows += ReaderLayoutRow(rowElementStart, elements.size, lineY, lineY + rule.widthPx)
        y += requiredHeight
    }

    private fun addBlankLine(
        blank: ReaderMeasuredBlock.BlankLine,
        paragraphIndex: Int,
        appendSeparator: Boolean,
    ) {
        val height = blank.lineHeightPx
        previousLineHadFrame = false
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

    private fun ReaderMeasuredBlock.isTitle() = when (this) {
        is ReaderMeasuredBlock.Paragraph -> value.isTitle
        is ReaderMeasuredBlock.InlineParagraph -> emphasized
        else -> false
    }

    /** 页成型即沉淀成 [ReaderPage] 并流出；非章末页在下一张页成型时流出（延迟一页）。 */
    private fun emitPage(pageIndex: Int, isLastPage: Boolean) {
        val page = buildPage(pageIndex, isLastPage)
        resultPages += page
        onPage?.invoke(page)
    }

    private fun buildPage(pageIndex: Int, isLastPage: Boolean): ReaderPage {
        val pageElements = pages[pageIndex]
        // 连续滚动模式按 scrollExtentPx 堆叠相邻页，页高一律是排版游标：对照旧
        // `ContentTextView.drawPage`（下一页画在 `相对偏移 + textPage.height`）与
        // `TextChapterLayout`（正文页 `textPage.height = durY`）。章末页只额外加
        // [chapterEndPaddingPx]（旧 `height = durY + 20dp`），**不**向内容区高度收口——
        // 收口会让残页后面的空白顶满一屏，必须滚过整屏空白才接上下一章正文。
        // 只有提示页才按可见高度收口（旧 `TextPage.format` 的 `isMsgPage`），那条路径
        // 在 `ReadBookController` 的占位页上，不经过这里。
        // 卷名/空正文章整页标题的垂直居中位移必须计入覆盖高度（[centeredPageShiftPx]），
        // 否则下一章正文会压在本页卷名上。
        val contentCoveredExtentPx = pageExtents[pageIndex] + centeredPageShiftPx
        // 单图样式的正文页恒为一屏：旧 `setTypeText` 逐行收口到 `visibleHeight`，
        // 保证一屏一页；纯图片页沿用排版游标（`setTypeImage` 只把 durY 移到竖直居中位置，
        // 不做收口），所以按「本页是否含文本/分隔等内容」区分，而不是整章一律收口。
        val isFullViewportPage = config.singleImageStyle &&
                pageElements.any { it !is ReaderElement.Image }
        val pageExtentPx = if (isFullViewportPage) {
            maxOf(contentCoveredExtentPx, config.contentBottomPx - config.paddingTopPx)
        } else contentCoveredExtentPx
        val stackedExtentPx = when {
            !config.continuousScroll -> pageExtents[pageIndex]
            isLastPage -> pageExtentPx + config.chapterEndPaddingPx
            else -> pageExtentPx
        }
        return ReaderPage(
            id = ReaderPageId(config.chapterIndex, pageIndex),
            chapterTitle = config.chapterTitle,
            text = pageTexts[pageIndex].toString(),
            widthPx = config.viewportWidthPx,
            heightPx = config.viewportHeightPx,
            contentTopPx = config.paddingTopPx,
            contentBottomPx = if (config.continuousScroll) {
                config.contentBottomPx
            } else pageExtents[pageIndex] - config.paddingBottomPx,
            contentLeftPx = config.paddingLeftPx,
            contentRightPx = config.viewportWidthPx - config.paddingRightPx,
            elements = pageElements,
            revision = config.revision,
            scrollExtentPx = stackedExtentPx,
            decoration = config.decoration,
            inlineImagesPreserveScrollLine = config.inlineImagesPreserveScrollLine,
            emphasisUnderlineStyle = config.emphasisUnderlineStyle,
        )
    }
}
