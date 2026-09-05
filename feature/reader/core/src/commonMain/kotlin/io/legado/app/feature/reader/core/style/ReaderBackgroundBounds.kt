package io.legado.app.feature.reader.core.style

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderRect

data class ReaderBackgroundBand(
    val colorArgb: Int,
    val bounds: ReaderRect,
)

/**
 * Joins sequential same-color background boxes on the same visual line into one continuous
 * band — the background-color counterpart of selection's mergeSelectionBounds.
 *
 * 书源 HTML 与替换规则可能逐字声明背景色（BackgroundColorSpan），排版按样式段切分元素后
 * 逐元素绘制会出现字间缝隙；合并按元素顺序进行，几何判定（同行重叠 + 间隙容差）与
 * mergeSelectionBounds 一致，仅多一个颜色键。
 */
fun List<ReaderElement.Text>.mergeBackgroundBounds(
    minimumLineOverlap: Float = 0.5f,
): List<ReaderBackgroundBand> {
    if (isEmpty()) return emptyList()
    val bands = ArrayList<ReaderBackgroundBand>(size)
    forEach { element ->
        val colorArgb = element.style.backgroundArgb ?: return@forEach
        val rect = element.bounds
        val previous = bands.lastOrNull()
        val verticalOverlap = previous?.let {
            (minOf(it.bounds.bottom, rect.bottom) - maxOf(it.bounds.top, rect.top)).coerceAtLeast(0f)
        } ?: 0f
        val sameLine = previous != null && verticalOverlap >=
            minOf(previous.bounds.height, rect.height) * minimumLineOverlap.coerceIn(0f, 1f)
        val joinGapPx = previous?.let { minOf(it.bounds.height, rect.height) * 0.5f } ?: 0f
        val horizontalGap = previous?.let {
            maxOf(rect.left - it.bounds.right, it.bounds.left - rect.right, 0f)
        } ?: Float.POSITIVE_INFINITY
        if (sameLine && previous.colorArgb == colorArgb && horizontalGap <= joinGapPx) {
            bands[bands.lastIndex] = previous.copy(
                bounds = ReaderRect(
                    left = minOf(previous.bounds.left, rect.left),
                    top = minOf(previous.bounds.top, rect.top),
                    right = maxOf(previous.bounds.right, rect.right),
                    bottom = maxOf(previous.bounds.bottom, rect.bottom),
                ),
            )
        } else {
            bands += ReaderBackgroundBand(colorArgb, rect)
        }
    }
    return bands
}
