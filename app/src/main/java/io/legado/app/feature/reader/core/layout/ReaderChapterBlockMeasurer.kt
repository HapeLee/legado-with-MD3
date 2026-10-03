package io.legado.app.feature.reader.core.layout

import io.legado.app.feature.reader.core.cast.CastMarkers
import io.legado.app.feature.reader.core.model.ReaderTextStyle
import io.legado.app.feature.reader.core.source.ReaderChapterInlineSource
import io.legado.app.feature.reader.core.source.ReaderChapterSource
import io.legado.app.feature.reader.core.source.ReaderChapterSourceBlock
import io.legado.app.feature.reader.core.source.ReaderInlineSourceStyle
import io.legado.app.feature.reader.core.style.ReaderCharacterStyle
import io.legado.app.feature.reader.core.style.ReaderCharacterStyleResolver
import io.legado.app.feature.reader.core.style.ReaderCompiledStyleRanges
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

/**
 * 决定行内图**宽**的那一个字符：旧 View 的行内图占位宽不是字号，而是该字符在**本段 paint**
 * 下的实际 advance。前两项是旧 `ChapterProvider` 插进正文的替换字；第三项是框架给 `ImageSpan`
 * 插入的 `\uFFFC`，只用于旧 `setTypeHtml` 的行末特例。
 */
enum class ReaderInlineImagePlaceholder(val placeholderChar: String) {
    /**
     * 旧 `srcReplaceChar`（`袮`）：书级 `imgStyleText`、单图 style 精确为 `"text"`，以及
     * "小图自动嵌入"（旧 `iStyle = "text"`）。
     */
    SRC_REPLACE("袮"),

    /** 旧 `reviewChar`（`꧁`）：仅单图 style 精确为 `"TEXT"`（段评图标）。 */
    REVIEW("꧁"),

    /**
     * 框架给 `ImageSpan` 插入的对象替换字符 `\uFFFC`。旧 `setTypeHtml` 在**行末**取不到下一字
     * 横向位置时改用 `measureText("\uFFFC")`，因此它也是旧版行末行内图的绘制宽。
     */
    OBJECT_REPLACEMENT("\uFFFC"),
}

data class ReaderImageOptions(
    val layoutMode: ReaderImageLayoutMode? = null,
    val requestedWidthPx: Float? = null,
    val requestedWidthFraction: Float? = null,
    val horizontalAlignment: ReaderTextAlignment? = null,
    val action: String? = null,
    /** 只在 [ReaderImageLayoutMode.INLINE] 下参与排版：用哪个占位字量行内图的宽。 */
    val inlinePlaceholder: ReaderInlineImagePlaceholder = ReaderInlineImagePlaceholder.SRC_REPLACE,
)

fun interface ReaderImageDimensionsResolver {
    suspend fun resolve(source: String): ReaderImageDimensions?
}

fun interface ReaderImageOptionsResolver {
    /**
     * @param htmlParagraph 该图来自旧 `TextChapterLayout.setTypeHtml` 的 HTML 段落（`<usehtml>`）。
     *   旧实现里两条路径对单图 style 的规则并不一致：HTML 用 `iStyle?.uppercase() == "TEXT"`
     *   （不区分大小写），纯文本用字面量 `iStyle == "text" || iStyle == "TEXT"`。解析器需要知道
     *   是替哪条路径解析，才能给出同样的结果。
     */
    fun resolve(source: String, htmlParagraph: Boolean): ReaderImageOptions?
}

data class ReaderChapterMeasureStyle(
    val bodyStyle: ReaderTextStyle,
    val titleStyle: ReaderTextStyle,
    val bodyIndentCharacters: Int,
    val bodyAlignment: ReaderTextAlignment,
    val titleAlignment: ReaderTextAlignment,
    val imagePageBreakBefore: Boolean = false,
    val imagePageBreakAfter: Boolean = false,
    /** 单图样式：标题段排版结束后立即断页，让章标题独占一页（对齐旧 TextChapterLayout）。 */
    val titlePageBreakAfter: Boolean = false,
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
    /**
     * 书级 `imageStyle` 为 `LEFT`/`RIGHT` 时的整图对齐。旧 `setTypeImage` 用「有效样式」
     * （单图 style 优先）取对齐，单图 style 存在但不是 LEFT/RIGHT 时**不**回落到书级。
     */
    val imageAlignment: ReaderTextAlignment? = null,
    val imageAvailableWidthPx: Float? = null,
    /** true = 带 click 动作脚本的图片（段评气泡）不参与排版：不产出测量项，也不解析图片尺寸。 */
    val excludeActionImages: Boolean = false,
)

sealed interface ReaderChapterMeasureResult {
    data class Success(val blocks: List<ReaderMeasuredBlock>) : ReaderChapterMeasureResult
    data class Unsupported(val reason: String) : ReaderChapterMeasureResult
}

/** Optional clock supplied by the platform while profiling; core pagination stays platform-free. */
class ReaderChapterMeasureMetrics(private val nanoTime: () -> Long) {
    var shapingNs: Long = 0
        private set
    var styleLookupNs: Long = 0
        private set
    var shapingCalls: Long = 0
        private set
    var styleLookups: Long = 0
        private set

    fun shape(shaper: ReaderTextShaper, text: String): GlyphClusters {
        val start = nanoTime()
        return try {
            shaper.shape(text)
        } finally {
            shapingNs += nanoTime() - start
            shapingCalls++
        }
    }

    fun resolveStyle(
        ranges: ReaderCompiledStyleRanges,
        position: Int,
        isTitle: Boolean
    ): ReaderCharacterStyle? {
        val start = nanoTime()
        return try {
            ranges.resolve(position, isTitle)
        } finally {
            styleLookupNs += nanoTime() - start
            styleLookups++
        }
    }
}

class ReaderChapterBlockMeasurer(
    bodyShaper: ReaderTextShaper,
    titleShaper: ReaderTextShaper,
    private val imageDimensionsResolver: ReaderImageDimensionsResolver,
    private val textShaperFactory: ReaderTextShaperFactory = ReaderTextShaperFactory { bodyShaper },
    private val htmlSourceResolver: ReaderHtmlSourceResolver = ReaderHtmlSourceResolver { _, _ -> null },
    private val imageOptionsResolver: ReaderImageOptionsResolver =
        ReaderImageOptionsResolver { _, _ -> null },
    private val metrics: ReaderChapterMeasureMetrics? = null,
    /**
     * 多角色分配：castTracker 非 null 时开启。Text 段按 <<名字（池）>> 标记切分，
     * 标记展开为胶囊测量项；锚点引号未跟标记时就地合成「未分配」占位胶囊。
     * 跟踪器与注入侧（ReaderChapterSourceParser）喂同一字符流、同规则，ordinal 一致。
     */
    private val castTracker: CastMarkers.CastQuoteTracker? = null,
    /** 胶囊宽度：(名字, 池标签, 是否带头像, 是否带变声器标记)。 */
    private val castCapsuleWidthPx: (
        name: String, poolLabel: String, withAvatar: Boolean, withEffect: Boolean,
    ) -> Float = { _, _, _, _ -> 0f },
    private val castCapsuleHeightPx: Float = 0f,
    /** 未分配占位胶囊宽度：只有一个人形图标。 */
    private val castPlaceholderWidthPx: () -> Float = { 0f },
    /** 这一句最终带不带变声器（段级优先，其次角色全局）。 */
    private val castHasVoiceEffect: (name: String, quoteOrdinal: Int) -> Boolean = { _, _ -> false },
    /** 背景音乐胶囊宽度/高度：(池名, 指定曲目)；宽度含 ♪ 前缀。 */
    private val bgmCapsuleWidthPx: (pool: String, track: String) -> Float = { _, _ -> 0f },
    private val bgmCapsuleHeightPx: Float = 0f,
) {
    /**
     * 测量整章。给出 [onBlock] 时每产出一个 block 就立即回调——旧 View
     * `TextChapterLayout` 边排版边 `channel.trySend`，分页光标因此可以在测量过程中推进，
     * 首屏不必等整章测完（见 `ReaderPaginationSession`）。
     */
    suspend fun measure(
        source: ReaderChapterSource,
        style: ReaderChapterMeasureStyle,
        onBlock: ((ReaderMeasuredBlock) -> Unit)? = null,
    ): ReaderChapterMeasureResult {
        val compiledStyleRanges = style.styleRanges.takeIf { it.isNotEmpty() }
            ?.let(ReaderCharacterStyleResolver::compile)
        // `blocks += x` 就是 `add(x)`：覆写 add 即可在每个追加点回调，无需在六处追加点重复。
        val blocks = object : ArrayList<ReaderMeasuredBlock>(source.blocks.size) {
            override fun add(element: ReaderMeasuredBlock): Boolean {
                onBlock?.invoke(element)
                return super.add(element)
            }
        }
        val shapers = mutableMapOf<ReaderTextStyle, ReaderTextShaper>()
        fun shaper(textStyle: ReaderTextStyle) = shapers.getOrPut(textStyle) {
            textShaperFactory.create(textStyle)
        }
        val bodyIndentText = style.bodyIndentText ?: "　".repeat(style.bodyIndentCharacters.coerceAtLeast(0))
        val bodyIndentWidth by lazy {
            val bodyShaper = shaper(style.bodyStyle)
            val shaped =
                metrics?.shape(bodyShaper, bodyIndentText) ?: bodyShaper.shape(bodyIndentText)
            shaped.widthsPx.sum() + (style.letterSpacingEm ?: 0f) * style.bodyStyle.fontSizePx * shaped.text.size
        }
        // 行内图的占位宽 = 旧 View 插入正文的占位字（`袮` / `꧁`）在**本段 paint** 下的 advance，
        // 不是字号。同一样式只求一次，避免每张图都新建 TextPaint 再量一遍。
        val inlinePlaceholderWidths = mutableMapOf<Pair<String, ReaderTextStyle>, Float>()
        fun inlinePlaceholderWidth(
            placeholder: ReaderInlineImagePlaceholder,
            textStyle: ReaderTextStyle,
        ): Float = inlinePlaceholderWidths.getOrPut(placeholder.placeholderChar to textStyle) {
            val placeholderShaper = shaper(textStyle)
            val shaped = metrics?.shape(placeholderShaper, placeholder.placeholderChar)
                ?: placeholderShaper.shape(placeholder.placeholderChar)
            // 字体缺字时可能量到 0 宽（旧版对应一个画不出来的零宽图），回落到字号保证至少一格。
            shaped.widthsPx.firstOrNull()?.takeIf { it > 0f } ?: textStyle.fontSizePx
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
            /**
             * 该段来自旧 `setTypeHtml` 的 HTML 块。它同时决定旧的整行补空格/附加行距
             * （[ReaderMeasuredBlock.InlineParagraph.justifyAtWordBoundaries]）与旧 HTML 路径
             * 的图片规则（见 [resolveImageLayout]）。
             */
            fromHtmlBlock: Boolean = false,
            /** HTML 段测量文本与注入侧不一致（标签剥离），不参与锚点计数。 */
            castFeed: Boolean = true,
        ) {
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
                return
            }
            val firstText = items.firstOrNull() as? ReaderChapterInlineSource.Text
            val prefixEnd = firstText?.takeIf {
                applyBodyIndent && !isTitle && bodyIndentText.isNotEmpty() &&
                    it.value.startsWith(bodyIndentText)
            }?.let { it.chapterPosition + bodyIndentText.length }
            var emittedContent = false
            var hasStandaloneImage = false
            var droppedActionImage = false
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
                    // 旧 HTML 块才有整行补空格与附加行距。
                    justifyAtWordBoundaries = fromHtmlBlock,
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
                        // 多角色分配：按标记切段；无标记时单段，行为与原版一致
                        val castActive = castTracker != null && !isTitle && castFeed
                        val castPieces: List<Pair<String, CastMarkers.Match?>> =
                            if (castActive) splitCastPieces(item.value) else listOf(item.value to null)
                        var offset = 0
                        for ((pieceText, pieceMarker) in castPieces) {
                        val pieceEnd = offset + pieceText.length
                        val initialShaper = shaper(htmlStyle)
                        val initiallyShaped = if (pieceText.isEmpty()) null
                            else metrics?.shape(initialShaper, pieceText) ?: initialShaper.shape(pieceText)
                        // 同一区间内相邻字形的解析结果是同一个实例：合并后的样式和它的 shaper
                        // 只算一次即可。切成多段配音后，每一段各带一套缓存。
                        var cachedRangeStyle: ReaderCharacterStyle? = null
                        var cachedTextStyle = htmlStyle
                        var cachedTextStyleIsPlain = true
                        var cachedShaper = initialShaper
                        var hasCachedStyle = false
                        initiallyShaped?.text?.forEachIndexed { clusterIndex, cluster ->
                            val position = item.chapterPosition + offset
                            val rangeStyle = compiledStyleRanges
                                ?.let {
                                    if (metrics != null) metrics.resolveStyle(it, position, isTitle)
                                    else it.resolve(position, isTitle)
                                }
                            if (!hasCachedStyle || rangeStyle !== cachedRangeStyle) {
                                cachedRangeStyle = rangeStyle
                                cachedTextStyle = htmlStyle.merge(rangeStyle)
                                cachedTextStyleIsPlain = cachedTextStyle == htmlStyle
                                cachedShaper = shaper(cachedTextStyle)
                                hasCachedStyle = true
                            }
                            val textStyle = cachedTextStyle
                            val textShaper = cachedShaper
                            // The paragraph was already shaped with htmlStyle to obtain its
                            // grapheme clusters. For the overwhelmingly common unstyled glyph,
                            // reuse that width instead of shaping the same glyph a second time.
                            val width = if (cachedTextStyleIsPlain) {
                                initiallyShaped.widthsPx.getOrElse(clusterIndex) { 0f }
                            } else {
                                (metrics?.shape(textShaper, cluster) ?: textShaper.shape(cluster))
                                    .widthsPx.firstOrNull() ?: 0f
                            }
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
                            if (castActive) {
                                var anchorHere = false
                                for (ch in cluster) if (castTracker!!.feed(ch)) anchorHere = true
                                // 锚点恰在段尾且后面是标记 → 交给标记的已分配胶囊
                                if (anchorHere && !(offset == pieceEnd && pieceMarker != null)) {
                                    inline += ReaderMeasuredInlineItem.RoleCast(
                                        widthPx = castPlaceholderWidthPx(),
                                        heightPx = castCapsuleHeightPx,
                                        chapterPosition = item.chapterPosition + offset,
                                        raw = "",
                                        name = "",
                                        voicePoolLabel = "",
                                        quoteOrdinal = castTracker.lastCastOrdinal,
                                    )
                                }
                            }
                        }
                        if (pieceMarker != null) {
                            val markerOrdinal = castTracker!!.lastCastOrdinal
                            val withEffect = castHasVoiceEffect(pieceMarker.name, markerOrdinal)
                            inline += ReaderMeasuredInlineItem.RoleCast(
                                widthPx = castCapsuleWidthPx(
                                    pieceMarker.name, pieceMarker.voicePoolLabel, true, withEffect
                                ),
                                heightPx = castCapsuleHeightPx,
                                chapterPosition = item.chapterPosition + offset,
                                raw = pieceMarker.raw,
                                name = pieceMarker.name,
                                voicePoolLabel = pieceMarker.voicePoolLabel,
                                quoteOrdinal = markerOrdinal,
                                voiceEffectMark = withEffect,
                            )
                            offset += pieceMarker.raw.length
                        }
                        }
                    }
                    is ReaderChapterInlineSource.BgmScene -> {
                        inline += ReaderMeasuredInlineItem.BgmScene(
                            widthPx = bgmCapsuleWidthPx(item.poolName, item.trackName),
                            heightPx = bgmCapsuleHeightPx,
                            chapterPosition = item.chapterPosition,
                            paragraphIndex = item.paragraphIndex,
                            poolName = item.poolName,
                            trackName = item.trackName,
                        )
                    }
                    is ReaderChapterInlineSource.Image -> {
                        // excludeActionImages 开启时：带动作脚本的行内图（段评气泡）整体
                        // 不参与排版，且在图片尺寸解析之前跳过（不触发任何取图请求）
                        val options = imageOptionsResolver.resolve(item.source, fromHtmlBlock)
                        if (style.excludeActionImages && options?.action != null) {
                            droppedActionImage = true
                            return@forEach
                        }
                        // A broken image must not make the entire chapter disappear. The bitmap
                        // loader already supplies an error image; reserve stable line geometry
                        // until real dimensions are available.
                        val placeholderExtent = (lineHeight ?: baseStyle.fontSizePx).coerceAtLeast(1f)
                        val originalSize = imageDimensionsResolver.resolve(item.source)
                            ?: ReaderImageDimensions(placeholderExtent, placeholderExtent)
                        val requestedWidth = options?.requestedWidthFraction?.let { fraction ->
                            style.imageAvailableWidthPx?.times(fraction)
                        } ?: options?.requestedWidthPx
                        val size = originalSize.withWidth(requestedWidth)
                        val layout = style.resolveImageLayout(options, fromHtmlBlock)
                        val mode = layout.mode
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
                                // 旧 `setTypeImage` 建 ImageColumn 时根本没传 click：整图不带动作。
                                action = null,
                                horizontalAlignment = layout.alignment,
                                scaleMode = mode.toScaleMode(),
                                pageBreakBefore = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                                pageBreakAfter = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                            )
                        } else {
                            // 文字嵌入（行内图）：对照旧 `TextChapterLayout` 的 ImageColumn —— 占位宽
                            // 取占位字（书级文字嵌入固定 `袮`；单图 style 精确为 `"TEXT"` 时是 `꧁`）
                            // 在**本段 paint** 下的实际 advance（不是字号），高按原图比例从该宽度换算，
                            // 因此立图可以高于当前行；扁平宽图只占一格宽、很矮。
                            // HTML 段落例外：旧 `setTypeHtml` 的占位宽/行盒高来自 ImageSpan 本身。
                            val span = item.htmlSpanExtent
                            val cellWidthPx = span?.widthPx
                                ?: inlinePlaceholderWidth(layout.placeholder, baseStyle)
                            val cellHeightPx = span?.heightPx ?: (
                                    cellWidthPx * (size.heightPx.coerceAtLeast(1f) /
                                            size.widthPx.coerceAtLeast(1f))
                                    )
                            inline += ReaderMeasuredInlineItem.Image(
                                source = item.source,
                                widthPx = cellWidthPx,
                                heightPx = cellHeightPx,
                                chapterPosition = item.chapterPosition,
                                action = if (layout.preservesAction) options?.action else null,
                                // 旧 `setTypeHtml` 行末图宽 = `measureText("\uFFFC")`。
                                lineFinalWidthPx = span?.let {
                                    inlinePlaceholderWidth(
                                        ReaderInlineImagePlaceholder.OBJECT_REPLACEMENT,
                                        baseStyle,
                                    )
                                },
                            )
                        }
                    }
                    is ReaderChapterInlineSource.BlankLine -> Unit
                }
            }
            // 被剔除的段评图视同独立图参与空白抑制：整行图片段自带的缩进/空白
            // 填充不再残留为空行（与「图不存在」的排版等价）
            flushInline(skipBlank = hasStandaloneImage || droppedActionImage)
        }
        source.blocks.forEachIndexed { index, block ->
            when (block) {
                is ReaderChapterSourceBlock.Text -> {
                    addStyledParagraph(
                        listOf(ReaderChapterInlineSource.Text(block.value, block.chapterPosition)),
                        block.isTitle,
                        block.fontSizeScale,
                        block.isSubtitle,
                    )
                    // 旧 TextChapterLayout 在单图样式的标题段排版完（`durY += titleBottomSpacing`
                    // 之后）直接 `prepareNextPageIfNeed()`——无参调用无条件结束当前页，于是标题
                    // 独占一页、正文从下一页开始。标题分成多段时只在最后一段之后断页。
                    if (block.isTitle && style.titlePageBreakAfter &&
                        (source.blocks.getOrNull(index + 1) as? ReaderChapterSourceBlock.Text)?.isTitle != true
                    ) {
                        blocks += ReaderMeasuredBlock.PageBreak
                    }
                }
                is ReaderChapterSourceBlock.Image -> {
                    // 独立图块是新 core 的概念，没有对应的旧分支，按纯文本路径规则处理。
                    val options = imageOptionsResolver.resolve(block.source, htmlParagraph = false)
                    if (style.excludeActionImages && options?.action != null) return@forEachIndexed
                    val layout = style.resolveImageLayout(options, htmlParagraph = false)
                    if (layout.mode == ReaderImageLayoutMode.INLINE) {
                        // 书级文字嵌入：独立图块同样按占位字行内排版（旧版所有 <img> 一律行内）。
                        addStyledParagraph(
                            items = listOf(
                                ReaderChapterInlineSource.Image(
                                    block.source,
                                    block.chapterPosition
                                ),
                            ),
                            isTitle = false,
                            applyBodyIndent = false,
                        )
                        return@forEachIndexed
                    }
                    val placeholderExtent = (style.bodyLineHeightPx ?: style.bodyStyle.fontSizePx)
                        .coerceAtLeast(1f)
                    val originalSize = imageDimensionsResolver.resolve(block.source)
                        ?: ReaderImageDimensions(placeholderExtent, placeholderExtent)
                    val requestedWidth = options?.requestedWidthFraction?.let { fraction ->
                        style.imageAvailableWidthPx?.times(fraction)
                    } ?: options?.requestedWidthPx
                    val size = originalSize.withWidth(requestedWidth)
                    val mode = layout.mode
                    blocks += ReaderMeasuredBlock.Image(
                        source = block.source,
                        intrinsicWidthPx = size.widthPx,
                        intrinsicHeightPx = size.heightPx,
                        chapterPosition = block.chapterPosition,
                        // 独立图块按纯文本路径走的是整图分支，旧 `setTypeImage` 不带 click。
                        action = null,
                        horizontalAlignment = layout.alignment,
                        scaleMode = mode.toScaleMode(),
                        pageBreakBefore = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                        pageBreakAfter = mode == ReaderImageLayoutMode.SINGLE_PAGE,
                    )
                }
                is ReaderChapterSourceBlock.Paragraph -> {
                    addStyledParagraph(block.items, false)
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
                            fromHtmlBlock = true,
                            castFeed = false,
                        )
                    }
                }
                is ReaderChapterSourceBlock.PageBreak -> blocks += ReaderMeasuredBlock.PageBreak
            }
        }
        return ReaderChapterMeasureResult.Success(blocks)
    }
}

/** 按 <<名字（池）>> 标记把文本切成 (前段文本, 后随标记?) 序列；无标记时单元素。 */
private fun splitCastPieces(text: String): List<Pair<String, CastMarkers.Match?>> {
    val markers = CastMarkers.findMarkers(text)
    if (markers.isEmpty()) return listOf(text to null)
    val out = ArrayList<Pair<String, CastMarkers.Match?>>(markers.size + 1)
    var cursor = 0
    for (m in markers) {
        out += text.substring(cursor, m.start) to m
        cursor = m.end
    }
    out += text.substring(cursor) to null
    return out
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

/** 单张图的排版决策，行内源与独立图两个分支共用（见 [resolveImageLayout]）。 */
private data class ReaderResolvedImageLayout(
    val mode: ReaderImageLayoutMode,
    val placeholder: ReaderInlineImagePlaceholder,
    val alignment: ReaderTextAlignment,
    /**
     * 旧版只有行内（文字嵌入）图带 click；整图走 `setTypeImage`，那里压根没用 click 参数。
     * 另外纯文本的书级文字嵌入分支自己就没解析 JSON，连行内图也不带 click。
     */
    val preservesAction: Boolean,
)

/**
 * 复刻旧 `TextChapterLayout` 的两条图片路径，它们的规则并不一致：
 *
 * **纯文本（`getTextChapter` 的 `else` 分支）**
 * - 书级 `imgStyleText`（[ReaderImageLayoutMode.INLINE]）先整体短路：不解析单图 JSON，所有
 *   `<img>` 一律用一个 `srcReplaceChar`（`袮`）占位行内排版。
 * - 否则单图 style 才能覆盖书级；TEXT 只认字面量 `"text"`/`"TEXT"`，`"Text"` 走整图。
 *
 * **HTML 段落（`setTypeHtml`）**
 * - 不受书级短路影响：书级 `imageStyle` 只是"单图 style 缺失"时的 fallback。
 * - 单图 style 用 `iStyle?.uppercase() == "TEXT"`（不区分大小写）判内联。
 * - src 里没有 `,{...}` 时直接 `setTypeImage(imageStyle)`：**不做 <80px 自动行内**，书级 TEXT
 *   也会落到 `setTypeImage` 的 `else`（夹小整图）。
 *
 * `click` 也按旧版分支走：纯文本短路分支没有解析 JSON，连行内图都不带动作；整图在旧
 * `setTypeImage` 里也没有 click 参数。HTML 段落里带 `,{...}` 的行内图才有动作。
 */
private fun ReaderChapterMeasureStyle.resolveImageLayout(
    options: ReaderImageOptions?,
    htmlParagraph: Boolean,
): ReaderResolvedImageLayout {
    if (!htmlParagraph && imageLayoutMode == ReaderImageLayoutMode.INLINE) {
        return ReaderResolvedImageLayout(
            mode = ReaderImageLayoutMode.INLINE,
            placeholder = ReaderInlineImagePlaceholder.SRC_REPLACE,
            alignment = ReaderTextAlignment.CENTER,
            preservesAction = false,
        )
    }
    if (htmlParagraph && options == null) {
        return ReaderResolvedImageLayout(
            mode = ReaderImageLayoutMode.STANDALONE,
            placeholder = ReaderInlineImagePlaceholder.SRC_REPLACE,
            // 旧 `setTypeImage(imageStyle)`：对齐只认书级样式，默认居中。
            alignment = imageAlignment ?: ReaderTextAlignment.CENTER,
            preservesAction = false,
        )
    }
    val mode = options?.layoutMode ?: if (imagePageBreakBefore && imagePageBreakAfter) {
        ReaderImageLayoutMode.SINGLE_PAGE
    } else {
        imageLayoutMode
    }
    return ReaderResolvedImageLayout(
        mode = mode,
        placeholder = options?.inlinePlaceholder ?: ReaderInlineImagePlaceholder.SRC_REPLACE,
        // 旧版对齐取自"有效样式"（单图 style 优先，缺失才回落到书级），不是链式兜底。
        alignment = options?.horizontalAlignment ?: imageAlignment ?: ReaderTextAlignment.CENTER,
        preservesAction = true,
    )
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
        // 命中排版取较大者：两条规则在同一处命中时，留白要按「留得最多的那条」算，
        // 后一条把前一条抹成 0 会让已生效的间距凭空消失。
        matchSpacingBeforePx = maxOf(matchSpacingBeforePx, override.matchSpacingBeforePx),
        matchSpacingAfterPx = maxOf(matchSpacingAfterPx, override.matchSpacingAfterPx),
        linePadTopPx = maxOf(linePadTopPx, override.linePadTopPx),
        linePadBottomPx = maxOf(linePadBottomPx, override.linePadBottomPx),
    )
}
