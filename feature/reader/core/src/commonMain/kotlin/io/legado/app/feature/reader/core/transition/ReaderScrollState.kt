package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage

enum class ReaderScrollCrossing { PREVIOUS, NEXT }

data class ReaderScrollResult(
    val offsetPx: Float,
    val crossing: ReaderScrollCrossing? = null,
    val hitBoundary: Boolean = false,
)

/**
 * One-frame continuous-scroll reducer. A crossing is resolved synchronously by the
 * host (the turn callback returns the replacement window in the same frame), so no
 * input delta is ever dropped between pages.
 */
object ReaderScrollPolicy {

    /**
     * Click paging keeps one visible text row for context, matching ScrollPageDelegate.
     * Non-inline image pages and empty pages move by one full viewport.
     *
     * "保留一行"的基准是三页合成可视内容（对照旧 getCurVisiblePage）：页底已露出下一页
     * 行、或页顶已露出上一页行时，目标行在邻页里，步距自然跨过页边界（跨页折算由
     * [apply] 在动画帧内完成），而不是停在当前页边缘。
     */
    fun pageStep(
        page: ReaderPage,
        offsetPx: Float,
        direction: ReaderTurnDirection,
        previous: ReaderPage? = null,
        next: ReaderPage? = null,
    ): Float {
        val viewport = (page.contentBottomPx - page.contentTopPx).coerceAtLeast(1f)
        data class Row(val element: ReaderElement, val stackOffset: Float)
        val visible = buildList {
            fun collect(source: ReaderPage, shift: Float) {
                val stackOffset = offsetPx + shift
                for (element in source.elements) {
                    if (element.bounds.bottom + stackOffset > page.contentTopPx &&
                        element.bounds.top + stackOffset < page.contentBottomPx
                    ) add(Row(element, stackOffset))
                }
            }
            previous?.let { collect(it, -it.scrollExtentPx) }
            collect(page, 0f)
            next?.let { collect(it, page.scrollExtentPx) }
        }
        val text = visible.filter { it.element is ReaderElement.Text }
        if (text.isEmpty() ||
            (!page.inlineImagesPreserveScrollLine && visible.any { it.element is ReaderElement.Image })
        ) return if (direction == ReaderTurnDirection.PREVIOUS) viewport else -viewport

        val distance = when (direction) {
            ReaderTurnDirection.NEXT ->
                text.maxOf { it.element.bounds.top + it.stackOffset } - page.contentTopPx
            ReaderTurnDirection.PREVIOUS ->
                viewport - (text.minOf { it.element.bounds.bottom + it.stackOffset } - page.contentTopPx)
        }.coerceIn(0f, viewport)
        val effective = distance.takeIf { it > 0f } ?: viewport
        return if (direction == ReaderTurnDirection.PREVIOUS) effective else -effective
    }

    fun apply(
        offsetPx: Float,
        deltaPx: Float,
        previousExtentPx: Float,
        currentExtentPx: Float,
        viewportExtentPx: Float,
        hasPrevious: Boolean,
        hasNext: Boolean,
    ): ReaderScrollResult {
        if (currentExtentPx <= 0f) return ReaderScrollResult(0f)
        val next = offsetPx + deltaPx
        if (next > 0f) {
            return if (hasPrevious && previousExtentPx > 0f) {
                ReaderScrollResult(next - previousExtentPx, ReaderScrollCrossing.PREVIOUS)
            } else ReaderScrollResult(0f, hitBoundary = true)
        }
        if (!hasNext && next < 0f && next + currentExtentPx < viewportExtentPx) {
            return ReaderScrollResult(
                offsetPx = minOf(0f, viewportExtentPx - currentExtentPx),
                hitBoundary = true,
            )
        }
        if (next < -currentExtentPx) {
            return if (hasNext) {
                ReaderScrollResult(next + currentExtentPx, ReaderScrollCrossing.NEXT)
            } else {
                val bottom = minOf(0f, viewportExtentPx - currentExtentPx)
                ReaderScrollResult(bottom, hitBoundary = true)
            }
        }
        return ReaderScrollResult(next)
    }
}
