package io.legado.app.feature.reader.core.transition

import io.legado.app.feature.reader.core.model.ReaderElement
import io.legado.app.feature.reader.core.model.ReaderPage
import io.legado.app.feature.reader.core.transition.ReaderScrollPolicy.apply
import kotlin.math.abs

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
     * "保留一行"的基准是连续的相邻页合成内容（对照旧 getCurVisiblePage）：页底已露出下一页
     * 行、或页顶已露出上一页行时，目标行在邻页里，步距自然跨过页边界（跨页折算由
     * [apply] 在动画帧内完成），而不是停在当前页边缘。
     */
    fun pageStep(
        page: ReaderPage,
        offsetPx: Float,
        direction: ReaderTurnDirection,
        previous: ReaderPage? = null,
        next: ReaderPage? = null,
        nextPlus: ReaderPage? = null,
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
            next?.let { nextPage ->
                collect(nextPage, page.scrollExtentPx)
                nextPlus?.let { following ->
                    collect(following, page.scrollExtentPx + nextPage.scrollExtentPx)
                }
            }
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

    /**
     * 点击/按键滚动翻页的时长：对照旧 `PageDelegate.startScroll` 的
     * `animationSpeed * |dy| / viewHeight`（`animationSpeed` 即
     * `ReadView.defaultAnimationSpeed = 300`）。
     *
     * 时长随步距缩放，而不是固定帧数：旧版整屏步距（图片页）与"保留一行"步距（文本页，
     * 约为一屏减一行）落在这条曲线的不同位置，固定 18 帧会把短步距拖成与整屏一样久。
     */
    fun stepDurationMillis(
        distancePx: Float,
        viewportExtentPx: Float,
        animationSpeedMillis: Int = LEGACY_ANIMATION_SPEED_MILLIS,
    ): Int {
        if (viewportExtentPx <= 0f) return animationSpeedMillis
        val scaled = animationSpeedMillis * abs(distancePx) / viewportExtentPx
        return scaled.coerceIn(1f, Int.MAX_VALUE.toFloat()).toInt()
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

    /** 旧 `ReadView.defaultAnimationSpeed`：滚动与翻页动画的基准速度（ms / 一屏）。 */
    private const val LEGACY_ANIMATION_SPEED_MILLIS = 300
}
