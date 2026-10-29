package io.legado.app.data.repository.manga

import kotlin.math.ceil
import kotlin.math.roundToInt

/** Matches AOT image sampling, filling enlarged pixels and retaining thin downscaled ink. */
internal fun mangaInpaintMask(
    mask: BooleanArray, width: Int, height: Int, size: Int, checkCancellation: () -> Unit = {},
): FloatArray {
    require(width > 0 && height > 0 && size > 0 && mask.size == width * height)
    val ratio = minOf(size.toFloat() / width, size.toFloat() / height)
    val scaledWidth = (width * ratio).roundToInt().coerceIn(1, size)
    val scaledHeight = (height * ratio).roundToInt().coerceIn(1, size)
    val output = FloatArray(size * size)
    for (y in 0 until scaledHeight) {
        checkCancellation()
        val top = (y / ratio).toInt().coerceIn(0, height - 1)
        val bottom = ceil((y + 1) / ratio).toInt().coerceIn(top + 1, height)
        for (x in 0 until scaledWidth) {
            val left = (x / ratio).toInt().coerceIn(0, width - 1)
            val right = ceil((x + 1) / ratio).toInt().coerceIn(left + 1, width)
            // Max pooling prevents a one-pixel stroke disappearing during reduction.
            if ((top until bottom).any { sy -> (left until right).any { sx -> mask[sy * width + sx] } })
                output[y * size + x] = 1f
        }
    }
    return output
}
