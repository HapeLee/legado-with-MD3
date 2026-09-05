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
)

data class ReaderTextBackgroundImage(
    val source: String,
    val fit: Int,
    val scale: Float,
    val ninePatchLeft: Float = 0.1f,
    val ninePatchRight: Float = 0.1f,
    val ninePatchTop: Float = 0.1f,
    val ninePatchBottom: Float = 0.1f,
    val contentInsetLeftPx: Float = 0f,
    val contentInsetRightPx: Float = 0f,
    val contentInsetTopPx: Float = 0f,
    val contentInsetBottomPx: Float = 0f,
)

fun ReaderTextBackgroundImage.withBitmapWidth(widthPx: Int): ReaderTextBackgroundImage {
    return withBitmapSize(widthPx, 0)
}

fun ReaderTextBackgroundImage.withBitmapSize(widthPx: Int, heightPx: Int): ReaderTextBackgroundImage {
    if (fit != 3 || widthPx <= 0) return this
    return copy(
        contentInsetLeftPx = (widthPx * ninePatchLeft.coerceIn(0f, 1f)).toInt().toFloat(),
        contentInsetRightPx = (widthPx * ninePatchRight.coerceIn(0f, 1f)).toInt().toFloat(),
        contentInsetTopPx = (heightPx.coerceAtLeast(0) * ninePatchTop.coerceIn(0f, 1f)).toInt().toFloat(),
        contentInsetBottomPx = (heightPx.coerceAtLeast(0) * ninePatchBottom.coerceIn(0f, 1f)).toInt().toFloat(),
    )
}

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
    /** Dynamic read-aloud paragraph, likewise independent of the pagination layout. */
    val readAloudParagraphIndex: Int? = null,
    /** 邻章未装载时预置的"加载中"占位页，分页批次落地后被同 id 真实页替换。 */
    val isPlaceholder: Boolean = false,
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
    /** 下下页：不参与绘制，供滚动渲染层提前预热绘制数据（对照 shutiao 的四页流）。 */
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
)

data class ReaderPageDecoration(
    val header: ReaderTipRow? = null,
    val footer: ReaderTipRow? = null,
    val bookmarkBadge: ReaderBookmarkBadge? = null,
)
