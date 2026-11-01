package io.legado.app.feature.reader.core.model

import kotlin.math.abs
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
 * 下划线最低像素相对文字行盒底边的距离。
 *
 * 实线/虚线只多出偏移和半个线宽；波浪还要算波幅；双线还要算第二条线与间距。
 * SVG 以绘制时的 50 基线为参照，路径落在标准 100 高视图内时最多再向下 50。
 */
private val ReaderUnderline.bottomOverhangPx: Float
    get() {
        val halfStroke = widthPx.coerceAtLeast(1f) / 2f
        val overhang = when (mode) {
            1, 2 -> offsetPx + halfStroke
            3 -> offsetPx + abs(waveAmplitudePx) + halfStroke
            4 -> offsetPx + doubleLineGapPx.coerceAtLeast(0f) + widthPx + halfStroke
            5 -> offsetPx + SVG_BASELINE_PX + halfStroke
            else -> 0f
        }
        return overhang.coerceAtLeast(0f)
    }

/** 只报告会画到行盒下方的装饰；删除线、半高亮等留在行盒内的样式不扩大裁剪。 */
private val ReaderElement.decorationBottomPx: Float?
    get() = when (this) {
        is ReaderElement.Text -> style.underline?.let { bounds.bottom + it.bottomOverhangPx }
        is ReaderElement.Rule -> bounds.bottom + widthPx.coerceAtLeast(1f) / 2f
        else -> null
    }

/**
 * 内容裁剪框：旧 `visibleRect` 的内容矩形 ∪ 每个背景实际画出来的矩形 ∪ 底部下划线装饰。
 *
 * 九宫格气泡天生要超出文字框——左右是四周一圈的原图厚度加长度偏移，上下是「整张图按
 * 图片大小锁死高度」高出行盒的那一截。只按阴影/斜体外扩会把气泡贴着页边距切成两截，
 * 所以裁剪必须跟着背景走：正文四周都要显示完整。
 *
 * 下划线只放宽下边界，不参与左右和上边界：它从行盒底边继续向下画，普通文字行盒仍按
 * 原来的内容区裁剪。
 */
fun ReaderPage.contentClipRect(backgroundRuns: List<ReaderTextBackgroundRun>): ReaderRect {
    val pad = contentClipPadPx
    var left = contentLeftPx - pad
    var top = contentTopPx - pad
    var right = contentRightPx + pad
    var bottom = contentBottomPx + pad
    elements.forEach { element ->
        element.decorationBottomPx?.let { bottom = max(bottom, it) }
    }
    backgroundRuns.forEach { run ->
        val drawn = run.drawnBounds()
        left = min(left, drawn.left)
        top = min(top, drawn.top)
        right = max(right, drawn.right)
        bottom = max(bottom, drawn.bottom)
    }
    return ReaderRect(left, top, right, bottom)
}

/**
 * 滚动四页共用固定视口，对照旧 ContentTextView 在绘制整栈前 clipRect(visibleRect)。
 * 邻页的局部坐标、提示页的尺寸与气泡高度不能扩大上下视口，否则正文会滚进页眉/页脚。
 * 字形保留阴影/斜体余量；气泡只放宽左右边缘，上下随正文一起在视口边界裁剪。
 */
fun ReaderPageWindow.contentClipRect(): ReaderRect? {
    val viewport = current ?: return null
    val pages = listOfNotNull(previous, viewport, next, nextPlus)
    val pad = pages.maxOf { it.contentClipPadPx }
    var left = viewport.contentLeftPx - pad
    var right = viewport.contentRightPx + pad
    pages.forEach { page ->
        page.textBackgroundRuns().forEach { run ->
            val drawn = run.drawnBounds()
            left = min(left, drawn.left)
            right = max(right, drawn.right)
        }
    }
    return ReaderRect(
        left = left,
        top = viewport.contentTopPx - pad,
        right = right,
        bottom = viewport.contentBottomPx + pad,
    )
}

private const val SHADOW_RADIUS_PADDING_PX = 2f
private const val ITALIC_PAD_RATIO = 0.25f
private const val SVG_BASELINE_PX = 50f
