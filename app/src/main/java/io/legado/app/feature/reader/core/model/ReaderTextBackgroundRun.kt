package io.legado.app.feature.reader.core.model

import kotlin.math.abs

data class ReaderTextBackgroundRun(
    val bounds: ReaderRect,
    val contentBounds: ReaderRect,
    val image: ReaderTextBackgroundImage,
)

fun ReaderPage.textBackgroundRuns(): List<ReaderTextBackgroundRun> {
    val runs = mutableListOf<ReaderTextBackgroundRun>()
    // 行级合并对照旧 View TextLine.drawStyledBackgrounds：同一行内相邻且同背景图
    // 的字合并为一段，一次性绘制。元素相邻是硬条件（未被匹配的字会打断 run）；
    // 字间距/两端对齐产生的间隙由分页期标记 continuesBackgroundRun 放行，既避免
    // 逐字渲染背景，也不会把跨栏/跨行或隔着未匹配文字的同图段错误拼接。
    var previousElement: ReaderElement? = null
    elements.forEach { element ->
        // 胶囊（角色分配 / 背景音乐）只是压在气泡上的按钮，原文里没有它，它对背景完全透明：
        // 既不更新 previousElement 也不打断 run，前后的字照样并成一段，胶囊被同一个气泡包住。
        if (element is ReaderElement.RoleCast || element is ReaderElement.BgmScene) return@forEach
        val text = element as? ReaderElement.Text
        if (text == null) {
            previousElement = element
            return@forEach
        }
        val image = text.style.backgroundImage
        if (image == null) {
            previousElement = text
            return@forEach
        }
        val previous = runs.lastOrNull()
        val previousIsSameImage = (previousElement as? ReaderElement.Text)
            ?.style?.backgroundImage == image
        val sameRow = previous != null &&
            abs(previous.contentBounds.top - text.bounds.top) < 0.5f &&
                abs(previous.contentBounds.bottom - text.bounds.bottom) < 0.5f
        val contiguous = previous != null &&
            abs(previous.contentBounds.right - text.bounds.left) < 1f
        if (
            previous != null && previousIsSameImage && sameRow &&
            (contiguous || text.continuesBackgroundRun)
        ) {
            runs[runs.lastIndex] = previous.copy(
                bounds = previous.bounds.copy(
                    right = text.bounds.right + text.backgroundFrameRightPx,
                    top = minOf(previous.bounds.top, text.bounds.top - text.backgroundFrameTopPx),
                    bottom = maxOf(previous.bounds.bottom, text.bounds.bottom + text.backgroundFrameBottomPx),
                ),
                contentBounds = previous.contentBounds.copy(right = text.bounds.right),
            )
        } else {
            runs += ReaderTextBackgroundRun(
                bounds = text.bounds.copy(
                    left = text.bounds.left - text.backgroundFrameLeftPx,
                    right = text.bounds.right + text.backgroundFrameRightPx,
                    top = text.bounds.top - text.backgroundFrameTopPx,
                    bottom = text.bounds.bottom + text.backgroundFrameBottomPx,
                ),
                contentBounds = text.bounds,
                image = image,
            )
        }
        previousElement = text
    }
    return runs.map { run -> run.copy(bounds = run.image.nineSliceFrame(run.bounds, run.contentBounds.width)) }
}

/**
 * 外框的最后一笔：中间那一格按左/右偏移各自往两侧推出去多少（[stretchLeftPx]），
 * 夹住用的文字宽是 [textWidthPx] —— 必须是**纯文字宽**，不含四周一圈。
 *
 * 四周一圈的厚度不在这里加：上下左右四条边都是分页期按同一个等比倍率换算好的
 * （[frameTopPx] / [frameLeftPx]），在 [textBackgroundRuns] 里就已经并进 bounds 了。
 * 预览侧共用这个函数，气泡才会和正文一样宽。
 */
fun ReaderTextBackgroundImage.nineSliceFrame(content: ReaderRect, textWidthPx: Float): ReaderRect =
    if (fit != 3) content else content.copy(
        left = content.left - stretchLeftPx(textWidthPx),
        right = content.right + stretchRightPx(textWidthPx),
    )
