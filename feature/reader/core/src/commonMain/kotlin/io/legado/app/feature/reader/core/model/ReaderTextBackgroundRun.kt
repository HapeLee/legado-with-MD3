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
                    right = text.bounds.right,
                    top = minOf(previous.bounds.top, text.bounds.top - text.backgroundFrameTopPx),
                    bottom = maxOf(previous.bounds.bottom, text.bounds.bottom + text.backgroundFrameBottomPx),
                ),
                contentBounds = previous.contentBounds.copy(right = text.bounds.right),
            )
        } else {
            runs += ReaderTextBackgroundRun(
                bounds = text.bounds.copy(
                    top = text.bounds.top - text.backgroundFrameTopPx,
                    bottom = text.bounds.bottom + text.backgroundFrameBottomPx,
                ),
                contentBounds = text.bounds,
                image = image,
            )
        }
        previousElement = text
    }
    return runs.map { run ->
        if (run.image.fit != 3) run else run.copy(
            bounds = run.bounds.copy(
                left = run.bounds.left - run.image.contentInsetLeftPx,
                right = run.bounds.right + run.image.contentInsetRightPx,
            ),
        )
    }
}
