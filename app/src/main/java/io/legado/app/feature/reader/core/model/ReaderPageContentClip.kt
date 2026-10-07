package io.legado.app.feature.reader.core.model

import kotlin.math.max
import kotlin.math.min

/**
 * 滚动模式内容裁剪的外扩量。
 *
 * 对照旧 View `ChapterProvider` 的 `visibleRect`：可见矩形按
 * `shadowPad = 阴影半径 + 2`（`Build.VERSION_CODES.Q` 及以上的 `shadowLayerRadius`）与
 * `italicPad = 字号 * 0.25`（仅斜体）向外扩，否则贴边裁切会把文字阴影和斜体字缘切掉。
 * 取页内所有文字元素的最大值，等价于旧版那个全局 Paint 的取值在混排样式下的推广。
 */
val ReaderPage.contentClipPadPx: Float
    get() {
        var pad = 0f
        elements.forEach { element ->
            val text = element as? ReaderElement.Text ?: return@forEach
            text.style.shadow?.let { shadow ->
                pad = maxOf(pad, shadow.radiusPx.coerceAtLeast(0f) + SHADOW_RADIUS_PADDING_PX)
            }
            if (text.style.italic) {
                pad = maxOf(pad, text.style.fontSizePx * ITALIC_PAD_RATIO)
            }
        }
        return pad
    }

/**
 * 背景图真正画出来的那一块。
 *
 * 只有九宫格会越出文字框：左右是四周一圈的原图厚度加长度偏移，上下是「整张图按图片大小
 * 锁死高度」高出行盒的那一截，外框就是 [ReaderTextBackgroundRun.bounds]。平铺/拉伸/裁剪
 * 三种适配在 [drawTextBackground] 里本来就按内容框绘制（后两种还先 clipRect 到内容框），
 * 所以它们的外沿就是内容框，不需要为它们放宽裁剪。
 */
fun ReaderTextBackgroundRun.drawnBounds(): ReaderRect =
    if (image.fit == 3) bounds else contentBounds

/**
 * 内容裁剪框：旧 `visibleRect` 的内容矩形 ∪ 每个背景实际画出来的矩形。
 *
 * 九宫格气泡天生要超出文字框——左右是四周一圈的原图厚度加长度偏移，上下是「整张图按
 * 图片大小锁死高度」高出行盒的那一截。只按阴影/斜体外扩会把气泡贴着页边距切成两截，
 * 所以裁剪必须跟着背景走：正文四周都要显示完整。
 */
fun ReaderPage.contentClipRect(backgroundRuns: List<ReaderTextBackgroundRun>): ReaderRect {
    val pad = contentClipPadPx
    var left = contentLeftPx - pad
    var top = contentTopPx - pad
    var right = contentRightPx + pad
    var bottom = contentBottomPx + pad
    backgroundRuns.forEach { run ->
        val drawn = run.drawnBounds()
        left = min(left, drawn.left)
        top = min(top, drawn.top)
        right = max(right, drawn.right)
        bottom = max(bottom, drawn.bottom)
    }
    return ReaderRect(left, top, right, bottom)
}

private const val SHADOW_RADIUS_PADDING_PX = 2f
private const val ITALIC_PAD_RATIO = 0.25f
