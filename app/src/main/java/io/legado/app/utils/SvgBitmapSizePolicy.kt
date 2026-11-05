package io.legado.app.utils

import kotlin.math.floor
import kotlin.math.min

internal data class SvgBitmapSize(val width: Int, val height: Int)

internal object SvgBitmapSizePolicy {
    fun fitWithin(sourceWidth: Int, sourceHeight: Int, maxWidth: Int?, maxHeight: Int?): SvgBitmapSize? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val widthScale = maxWidth?.takeIf { it > 0 }?.let { it.toDouble() / sourceWidth } ?: 1.0
        val heightScale = maxHeight?.takeIf { it > 0 }?.let { it.toDouble() / sourceHeight } ?: 1.0
        val scale = min(1.0, min(widthScale, heightScale))
        return SvgBitmapSize(
            width = floor(sourceWidth * scale).toInt().coerceAtLeast(1),
            height = floor(sourceHeight * scale).toInt().coerceAtLeast(1)
        )
    }
}
