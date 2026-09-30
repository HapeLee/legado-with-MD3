package io.legado.app.feature.reader.core.model

data class ReaderPageId(val chapterIndex: Int, val pageIndex: Int)

data class ReaderRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun offsetY(deltaY: Float) = copy(top = top + deltaY, bottom = bottom + deltaY)
}

data class ReaderTextStyle(
    val colorArgb: Int,
    val fontSizePx: Float,
    val fontPath: String = "",
    val fontWeight: Int = 400,
    val italic: Boolean = false,
    val backgroundArgb: Int? = null,
    val underline: ReaderUnderline? = null,
    val shadow: ReaderTextShadow? = null,
    val backgroundImage: ReaderTextBackgroundImage? = null,
    val fontFamily: String = "sans-serif",
    val linearText: Boolean = false,
    val strikeThrough: Boolean = false,
    /** Font-native underline used by HTML UnderlineSpan; custom reader underlines stay separate. */
    val nativeUnderline: Boolean = false,
    /**
     * 命中字距（px）：这一格是命中段的首字时要在它左边让出的空白。段内的字恒为 0——
     * 留白属于命中段与相邻字之间，不属于段内。见 [io.legado.app.feature.reader.legacy.LegacyReaderStyleRangeMapper]。
     */
    val matchSpacingBeforePx: Float = 0f,
    /** 命中字距（px）：这一格是命中段的末字时要在它右边让出的空白。 */
    val matchSpacingAfterPx: Float = 0f,
    /** 命中行行距（px）：包含命中的那一行整行往下抬这一截，行盒本身高度不变。 */
    val linePadTopPx: Float = 0f,
    /** 命中行行距（px）：这一行之后多留的空白。 */
    val linePadBottomPx: Float = 0f,
)

data class ReaderTextBackgroundImage(
    val source: String,
    val fit: Int,
    val scale: Float,
    val ninePatchLeft: Float = 0.1f,
    val ninePatchRight: Float = 0.1f,
    val ninePatchTop: Float = 0.1f,
    val ninePatchBottom: Float = 0.1f,
    /** 九宫格左偏移（px）：中间那一格向左多拉（负值少拉）这么多，左边那两条边跟着平移。 */
    val lengthOffsetLeftPx: Float = 0f,
    /** 九宫格右偏移（px）：见 [lengthOffsetLeftPx]，方向相反。 */
    val lengthOffsetRightPx: Float = 0f,
    val contentInsetLeftPx: Float = 0f,
    val contentInsetRightPx: Float = 0f,
    val contentInsetTopPx: Float = 0f,
    val contentInsetBottomPx: Float = 0f,
    /**
     * 上下两条切线之间那一横条（中间带）的自然高度，也就是不缩放时它在屏幕上占多高。
     * 纵向的等比倍率以它为基准（见 [verticalScalePx]）。
     */
    val contentBandHeightPx: Float = 0f,
) {
    val hasNinePatchBorder: Boolean
        get() = source.substringBefore('?').substringBefore('#')
            .endsWith(".9.png", ignoreCase = true)
}

/**
 * 把切线分数换算成四条边的自然厚度（像素）与中间带高度：横向按位图宽、纵向按位图高，
 * 各自再乘 [scale]。这些只是「不缩放时长什么样」，画之前还要按行盒高等比换算一次
 * （见 [verticalScalePx]），所以整张图随字号一起变大小。
 */
fun ReaderTextBackgroundImage.withBitmapSize(widthPx: Int, heightPx: Int): ReaderTextBackgroundImage {
    if (fit != 3 || widthPx <= 0) return this
    val borderPx = if (hasNinePatchBorder) 1 else 0
    val contentWidthPx = (widthPx - borderPx * 2).coerceAtLeast(0)
    val contentHeightPx = (heightPx - borderPx * 2).coerceAtLeast(0)
    val fixedScale = scale.coerceIn(0.1f, 5f)
    val left = ninePatchLeft.coerceIn(0f, 1f)
    val right = ninePatchRight.coerceIn(0f, 1f)
    val top = ninePatchTop.coerceIn(0f, 1f)
    val bottom = ninePatchBottom.coerceIn(0f, 1f)
    return copy(
        contentInsetLeftPx = contentWidthPx * left * fixedScale,
        contentInsetRightPx = contentWidthPx * right * fixedScale,
        contentInsetTopPx = contentHeightPx * top * fixedScale,
        contentInsetBottomPx = contentHeightPx * bottom * fixedScale,
        contentBandHeightPx = contentHeightPx * (1f - top - bottom).coerceAtLeast(0f) * fixedScale,
    )
}

/**
 * 纵向的等比缩放倍率：上下两条切线之间就是文字的显示区域，所以让中间带正好等于行盒高，
 * 整张图按这一个倍率变大小——上下边条跟着一起长缩，任何一格都不做纵向拉伸。
 * 上下切线挤到一起时中间带趋近 0、倍率会趋于无穷，所以两条边合计最高只让到行盒的
 * [maxEdgeHeightRatio] 倍（整张图因此不超过行盒的 4 倍）；切线交叉到没有中间带
 * （[contentBandHeightPx] 为 0）时按 1 倍原样画。
 */
fun ReaderTextBackgroundImage.verticalScalePx(lineHeightPx: Float): Float {
    if (fit != 3 || contentBandHeightPx <= 0f || lineHeightPx <= 0f) return 1f
    // 两条边都没有厚度时本来就不存在溢出，倍率只由中间带决定（除以 0 得无穷，min 自然取前者）。
    val edgeHeightPx = contentInsetTopPx + contentInsetBottomPx
    return minOf(
        lineHeightPx / contentBandHeightPx,
        lineHeightPx * maxEdgeHeightRatio / edgeHeightPx,
    )
}

/** 九宫格上沿要在行盒上方再外扩多少 = 上边条按 [verticalScalePx] 换算后的厚度。非九宫格不外扩。 */
fun ReaderTextBackgroundImage.frameTopPx(lineHeightPx: Float): Float =
    if (fit != 3) 0f else contentInsetTopPx * verticalScalePx(lineHeightPx)

/** [frameTopPx] 的下沿那一半。 */
fun ReaderTextBackgroundImage.frameBottomPx(lineHeightPx: Float): Float =
    if (fit != 3) 0f else contentInsetBottomPx * verticalScalePx(lineHeightPx)

/** 上下切线交叉时等比倍率会爆掉：两条边合计最高只让到行盒的这么多倍，整张图不超过行盒 4 倍。 */
private const val maxEdgeHeightRatio = 3f

/**
 * 左/右偏移各自换算成中间那一格被推出去的量：正值往外拉，负值往里缩。
 *
 * 缩到底只能把中间那一格挤成零宽，不许它翻过去和另一条切分线交叉，所以下限取 `-文字宽/2`
 * （两侧各让一半恰好归零）。外框（[io.legado.app.feature.reader.core.model.nineSliceFrame]）和
 * 绘制切分（[ReaderNineSliceLayout]）共用这一份口径，气泡才会刚好包住画出来的那一格。
 */
fun ReaderTextBackgroundImage.stretchLeftPx(textWidthPx: Float): Float =
    lengthOffsetLeftPx.coerceAtLeast(-textWidthPx / 2f)

/** [stretchLeftPx] 的右侧那一半。 */
fun ReaderTextBackgroundImage.stretchRightPx(textWidthPx: Float): Float =
    lengthOffsetRightPx.coerceAtLeast(-textWidthPx / 2f)

data class ReaderTextShadow(
    val colorArgb: Int,
    val radiusPx: Float,
    val dxPx: Float,
    val dyPx: Float,
)

data class ReaderUnderline(
    val mode: Int,
    val colorArgb: Int,
    val widthPx: Float,
    val offsetPx: Float,
    val svgPath: String = "",
    val dashOnPx: Float = 8f,
    val dashOffPx: Float = 5f,
    val waveAmplitudePx: Float = 3f,
    val waveLengthPx: Float = 12f,
    val doubleLineGapPx: Float = 3f,
)

sealed interface ReaderElement {
    val bounds: ReaderRect

    data class Text(
        override val bounds: ReaderRect,
        val baselinePx: Float,
        val value: String,
        val style: ReaderTextStyle,
        val selected: Boolean,
        val emphasized: Boolean,
        val readAloud: Boolean = false,
        val searchResult: Boolean = false,
        val emphasisUnderline: ReaderEmphasisUnderline? = null,
        val link: String? = null,
        val markingId: String? = null,
        val chapterPosition: Int,
        val paragraphIndex: Int = -1,
        val backgroundFrameTopPx: Float = 0f,
        val backgroundFrameBottomPx: Float = 0f,
        /** 同一行内紧随同背景图元素之后（对照旧 View TextLine 的行内连续绘制）。 */
        val continuesBackgroundRun: Boolean = false,
    ) : ReaderElement {
        /** HTML links keep the legacy reader's accent priority, including during read-aloud. */
        fun resolvedColorArgb(accentColorArgb: Int): Int =
            if (link != null || readAloud || searchResult) accentColorArgb else style.colorArgb

        val drawsLinkUnderline: Boolean
            get() = link != null
    }

    data class Image(
        override val bounds: ReaderRect,
        val source: String,
        val action: String?,
        val chapterPosition: Int = 0,
        /**
         * 文字嵌入（行内图），对照旧 View `TextChapterLayout` 的 `ImageColumn`：**宽恒为一个
         * 字符格**，高按实际加载到的位图长宽比换算，竖直居中于行盒且允许高于当前行。
         *
         * [bounds] 里的高是测量期由 `imageDimensionsResolver` 给出的长宽比，只用于行盒预留；
         * 绘制期必须按位图重算（见 `ReaderImageDrawLayout.forElement`），否则测量期长宽比与
         * 位图不一致时 `fitCenter` 会在格内留白、把图片画小。
         */
        val inline: Boolean = false,
    ) : ReaderElement

    data class Review(
        override val bounds: ReaderRect,
        val count: Int,
        val paragraphIndex: Int,
        val baselinePx: Float = bounds.bottom,
        val textSizePx: Float = bounds.height,
    ) : ReaderElement

    data class Action(
        override val bounds: ReaderRect,
        val key: String,
    ) : ReaderElement

    /**
     * 多角色分配胶囊：对话开引号后的 <<名字（池）>> 标记在页面上的呈现，点击打开分配弹层。
     *
     * @param quoteOrdinal 所在章的开引号锚点序号（ChapterRoleAssignment 的键）。
     * @param characterId 已分配时的角色档案 id（未分配为空串）。
     * @param avatarUri 角色头像本地路径；无头像为空串（绘字符占位圆）。
     * @param voiceEffectMark 这一句带变声器（段级或角色全局），右端画均衡器小标记。
     */
    data class RoleCast(
        override val bounds: ReaderRect,
        val name: String,
        val voicePoolLabel: String,
        val avatarUri: String,
        val assigned: Boolean,
        val voiceEffectMark: Boolean,
        val quoteOrdinal: Int,
        val characterId: String,
        val chapterPosition: Int,
    ) : ReaderElement

    /**
     * 段首背景音乐胶囊：点击打开场景配乐弹层。
     *
     * @param paragraphIndex 正文段落序号（bgm_scene_marks 的键），不是章节字符偏移。
     */
    data class BgmScene(
        override val bounds: ReaderRect,
        val paragraphIndex: Int,
        val poolName: String,
        val trackName: String,
        val chapterPosition: Int,
    ) : ReaderElement

    data class Spacer(
        override val bounds: ReaderRect,
        val chapterPosition: Int,
        val paragraphIndex: Int,
    ) : ReaderElement

    data class ParagraphMarker(
        override val bounds: ReaderRect,
        val colorArgb: Int,
        val strokeWidthPx: Float,
        val circular: Boolean,
    ) : ReaderElement

    data class Rule(
        override val bounds: ReaderRect,
        val colorArgb: Int,
        val widthPx: Float,
        val dashed: Boolean,
        val dashOnPx: Float = 6f,
        val dashOffPx: Float = 6f,
        val overlayStyledUnderline: Boolean = false,
    ) : ReaderElement
}

data class ReaderPage(
    val id: ReaderPageId,
    val chapterTitle: String,
    val text: String,
    val widthPx: Int,
    val heightPx: Int,
    val contentTopPx: Float,
    val contentBottomPx: Float,
    val elements: List<ReaderElement>,
    val revision: Long,
    /** Changes only when geometry/pagination changes; visual-only refreshes keep this stable. */
    val layoutRevision: Long = revision,
    val scrollExtentPx: Float = heightPx.toFloat(),
    val decoration: ReaderPageDecoration = ReaderPageDecoration(),
    val inlineImagesPreserveScrollLine: Boolean = true,
    val emphasisUnderlineStyle: ReaderEmphasisUnderline? = null,
    /** Dynamic search range, kept separate from immutable layout elements for draw-cache reuse. */
    val searchStart: Int? = null,
    val searchEndInclusive: Int? = null,
    /** Whether the dynamic search range is in the independent title coordinate space. */
    val searchIsTitle: Boolean = false,
    /** Dynamic read-aloud paragraph, likewise independent of the pagination layout. */
    val readAloudParagraphIndex: Int? = null,
    /** 邻章未装载时预置的"加载中"占位页，分页批次落地后被同 id 真实页替换。 */
    val isPlaceholder: Boolean = false,
    /**
     * 内容区左右边界，对照旧 `ChapterProvider.visibleRect` 的左右边
     * （`paddingLeft` / `viewWidth - paddingRight`）。放在构造参数末尾是为了不破坏按位置
     * 构造 `ReaderPage` 的既有调用点；默认值等价于"整页宽"，即不额外裁剪。
     */
    val contentLeftPx: Float = 0f,
    val contentRightPx: Float = widthPx.toFloat(),
) {
    fun elementAt(x: Float, y: Float): ReaderElement? =
        elements.firstOrNull { it.bounds.contains(x, y) }

    fun hasSameGeometryAs(other: ReaderPage): Boolean =
        id == other.id &&
            widthPx == other.widthPx && heightPx == other.heightPx &&
            contentTopPx == other.contentTopPx && contentBottomPx == other.contentBottomPx &&
            scrollExtentPx == other.scrollExtentPx &&
            elements.size == other.elements.size &&
            elements.indices.all { index ->
                elements[index]::class == other.elements[index]::class &&
                    elements[index].bounds == other.elements[index].bounds
            }
}

data class ReaderPageWindow(
    val previous: ReaderPage? = null,
    val current: ReaderPage? = null,
    val next: ReaderPage? = null,
    /** 下下页：滚动视口可露出它；分页模式仅将其作为预热页（对照 shutiao 的四页流）。 */
    val nextPlus: ReaderPage? = null,
)

enum class ReaderTipAlignment { START, CENTER, END }

enum class ReaderTipVisual { TEXT, BATTERY_OUTER, BATTERY_INNER, BATTERY_ICON, BATTERY_CLASSIC, ARROW }

data class ReaderPageTip(
    val text: String,
    val alignment: ReaderTipAlignment,
    val visual: ReaderTipVisual = ReaderTipVisual.TEXT,
    val batteryPercent: Int = 0,
)

data class ReaderTipRow(
    val visible: Boolean,
    val tips: List<ReaderPageTip>,
    val colorArgb: Int,
    val fontSizePx: Float,
    val fontPath: String,
    val paddingLeftPx: Float,
    val paddingTopPx: Float,
    val paddingRightPx: Float,
    val paddingBottomPx: Float,
    val dividerColorArgb: Int?,
    /** 未设页眉页脚字体时回落正文字体族（旧 `tipTypeface ?: ChapterProvider.typeface`）。 */
    val fontFamily: String = "sans-serif",
    /** 根层安全区内缩：分隔线只画在内缩后的宽度里（旧 `vwRoot` 的刘海 padding）。 */
    val insetLeftPx: Float = 0f,
    val insetRightPx: Float = 0f,
)

data class ReaderPageDecoration(
    val header: ReaderTipRow? = null,
    val footer: ReaderTipRow? = null,
    val bookmarkBadge: ReaderBookmarkBadge? = null,
)
