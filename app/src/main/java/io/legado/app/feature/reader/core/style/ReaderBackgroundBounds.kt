package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect

/**
 * 一条合并后的背景色带：填充色可空（只描边不铺色的边框带），边框三件套只在
 * [borderColorArgb] 非空时参与绘制；圆角同时作用于填充与描边，两者才对得上。
 */
data class ReaderBackgroundBand(
    val fillColorArgb: Int?,
    val bounds: ReaderRect,
    val borderColorArgb: Int? = null,
    val borderWidthPx: Float = 0f,
    val borderRadiusPx: Float = 0f,
)

/**
 * Joins sequential same-color background boxes on the same visual line into one continuous
 * band — the background-color counterpart of selection's mergeSelectionBounds.
 *
 * 书源 HTML 与替换规则可能逐字声明背景色（BackgroundColorSpan），排版按样式段切分元素后
 * 逐元素绘制会出现字间缝隙；合并按元素顺序进行，几何判定（同行重叠 + 间隙容差）与
 * mergeSelectionBounds 一致，仅多一个颜色键。带边框的元素额外以边框三件套为键：
 * 边框不一样的两段不许并成一条，否则描边会把两种框画进同一个外框里。
 *
 * 带背景图的那一列只画图、不铺色（对照旧 `TextLine.drawBgColorSegments` 里
 * 「只有在没有背景图片时才绘制背景颜色」那一条）：色块是按行盒画的直边矩形，压在九宫格气泡
 * 上面就会把气泡切出一截直边。它同时也是一道隔断——两侧同色的色块不许跨过它并成一条。
 */
fun List<ReaderElement.Text>.mergeBackgroundBounds(
    minimumLineOverlap: Float = 0.5f,
): List<ReaderBackgroundBand> {
    if (isEmpty()) return emptyList()
    val bands = ArrayList<ReaderBackgroundBand>(size)
    var brokenByImage = false
    forEach { element ->
        if (element.style.backgroundImage != null) {
            brokenByImage = true
            return@forEach
        }
        val colorArgb = element.style.backgroundArgb
        val border = element.style.takeIf { it.borderWidthPx > 0f }
            ?.let { it.borderColorArgb?.let { color -> Triple(color, it.borderWidthPx, it.borderRadiusPx) } }
        if (colorArgb == null && border == null) return@forEach
        val rect = element.bounds
        val previous = bands.lastOrNull()?.takeUnless { brokenByImage }
        brokenByImage = false
        val verticalOverlap = previous?.let {
            (minOf(it.bounds.bottom, rect.bottom) - maxOf(it.bounds.top, rect.top)).coerceAtLeast(0f)
        } ?: 0f
        val sameLine = previous != null && verticalOverlap >=
            minOf(previous.bounds.height, rect.height) * minimumLineOverlap.coerceIn(0f, 1f)
        val joinGapPx = previous?.let { minOf(it.bounds.height, rect.height) * 0.5f } ?: 0f
        val horizontalGap = previous?.let {
            maxOf(rect.left - it.bounds.right, it.bounds.left - rect.right, 0f)
        } ?: Float.POSITIVE_INFINITY
        if (
            sameLine &&
            previous.fillColorArgb == colorArgb &&
            previous.border == border &&
            horizontalGap <= joinGapPx
        ) {
            bands[bands.lastIndex] = previous.copy(
                bounds = ReaderRect(
                    left = minOf(previous.bounds.left, rect.left),
                    top = minOf(previous.bounds.top, rect.top),
                    right = maxOf(previous.bounds.right, rect.right),
                    bottom = maxOf(previous.bounds.bottom, rect.bottom),
                ),
            )
        } else {
            bands += ReaderBackgroundBand(
                fillColorArgb = colorArgb,
                bounds = rect,
                borderColorArgb = border?.first,
                borderWidthPx = border?.second ?: 0f,
                borderRadiusPx = border?.third ?: 0f,
            )
        }
    }
    return bands
}

/** 边框三件套合并键：颜色、粗细、圆角任意一项不同就是两条带。 */
private val ReaderBackgroundBand.border: Triple<Int, Float, Float>?
    get() = borderColorArgb?.let { Triple(it, borderWidthPx, borderRadiusPx) }
