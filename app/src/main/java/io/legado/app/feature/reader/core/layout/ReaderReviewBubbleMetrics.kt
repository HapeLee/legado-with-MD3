package io.legado.app.feature.reader.core.layout

/**
 * 段评气泡尺寸：测量期用它占位，绘制期用同一套比例，气泡不会画得比占位大或小。
 *
 * 高度取字号的一小截（正常行高约 1.5–1.8 倍字号），字号调到最大时也不会把行盒撑开；
 * 宽度按气泡模板自身的宽高比（`viewBox`）算，模板形状不会被拉伸。
 */
object ReaderReviewBubbleMetrics {
    private const val HEIGHT_RATIO = 1.3f
    const val MIN_SIZE_SCALE = 0.5f
    const val MAX_SIZE_SCALE = 1.5f

    /** 模板没有可用 `viewBox` 时的宽高比。 */
    const val DEFAULT_ASPECT_RATIO = 1.5f

    fun heightPx(fontSizePx: Float, sizeScale: Float = 1f): Float =
        fontSizePx * HEIGHT_RATIO * sizeScale.coerceIn(MIN_SIZE_SCALE, MAX_SIZE_SCALE)

    fun widthPx(heightPx: Float, aspectRatio: Float): Float {
        val ratio = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: DEFAULT_ASPECT_RATIO
        return heightPx * ratio
    }
}
