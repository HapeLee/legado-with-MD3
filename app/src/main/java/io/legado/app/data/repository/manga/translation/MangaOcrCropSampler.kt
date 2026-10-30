// SPDX-License-Identifier: Apache-2.0
// Bicubic perspective sampler adapted from ciddwd/overlay-translator (64a7aa2).
package io.legado.app.data.repository.manga.translation

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pixel-level port of PaddleX `CropByPolys.get_rotate_crop_image`:
 * `cv2.warpPerspective(img, M, (w, h), borderMode=BORDER_REPLICATE, flags=INTER_CUBIC)`.
 *
 * Kept free of android.graphics so it runs in JVM unit tests.
 */
internal object MangaOcrCropSampler {
    private const val CUBIC_A = -0.75
    /** OpenCV INTER_TAB_SIZE: warp coordinates are quantized to 1/32 pixel. */
    private const val TAB_SIZE = 32

    /** Bicubic support needs one pixel before and two after the sample position. */
    const val SOURCE_MARGIN = 3

    /**
     * @param source ARGB pixels of the image region starting at ([originX], [originY]). The region
     *   must contain the quad bounds plus [SOURCE_MARGIN] wherever the image extends that far, so
     *   clamping to the region edge is exactly BORDER_REPLICATE at the image edge.
     * @param points quad corners in image coordinates, ordered LT, RT, RB, LB.
     */
    fun warp(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        originX: Int,
        originY: Int,
        points: FloatArray,
        width: Int,
        height: Int,
    ): IntArray {
        require(points.size == 8) { "expected 4 points" }
        require(width > 0 && height > 0) { "crop must be non-empty" }
        require(sourceWidth > 0 && sourceHeight > 0 && source.size >= sourceWidth * sourceHeight)
        val global = DoubleArray(8) { i -> points[i].toDouble() }
        // Same correspondences as cv2.getPerspectiveTransform(box, pts_std), solved in the
        // inverse direction warpPerspective samples with (destination -> source).
        val m = perspectiveTransform(
            doubleArrayOf(0.0, 0.0, width.toDouble(), 0.0, width.toDouble(), height.toDouble(), 0.0, height.toDouble()),
            global,
        )
        val out = IntArray(width * height)
        val wx = DoubleArray(4)
        val wy = DoubleArray(4)
        val xs = IntArray(4)
        val ys = IntArray(4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val w = m[6] * x + m[7] * y + m[8]
                val scale = if (w != 0.0) TAB_SIZE / w else 0.0
                // Quantize in the common image frame, then apply the integer ROI origin.
                // Solving separate local homographies changes half-bin rounding at subpixels.
                val qx = saturateRound((m[0] * x + m[1] * y + m[2]) * scale) - originX * TAB_SIZE
                val qy = saturateRound((m[3] * x + m[4] * y + m[5]) * scale) - originY * TAB_SIZE
                val ix = Math.floorDiv(qx, TAB_SIZE)
                val iy = Math.floorDiv(qy, TAB_SIZE)
                cubicWeights(Math.floorMod(qx, TAB_SIZE) / TAB_SIZE.toDouble(), wx)
                cubicWeights(Math.floorMod(qy, TAB_SIZE) / TAB_SIZE.toDouble(), wy)
                for (k in 0 until 4) {
                    xs[k] = (ix - 1 + k).coerceIn(0, sourceWidth - 1)
                    ys[k] = (iy - 1 + k).coerceIn(0, sourceHeight - 1)
                }
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (j in 0 until 4) {
                    val row = ys[j] * sourceWidth
                    for (i in 0 until 4) {
                        val weight = wx[i] * wy[j]
                        val pixel = source[row + xs[i]]
                        r += weight * ((pixel ushr 16) and 0xFF)
                        g += weight * ((pixel ushr 8) and 0xFF)
                        b += weight * (pixel and 0xFF)
                    }
                }
                out[y * width + x] = (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
            }
        }
        return out
    }

    /** OpenCV `interpolateCubic`. */
    private fun cubicWeights(t: Double, out: DoubleArray) {
        out[0] = ((CUBIC_A * (t + 1) - 5 * CUBIC_A) * (t + 1) + 8 * CUBIC_A) * (t + 1) - 4 * CUBIC_A
        out[1] = ((CUBIC_A + 2) * t - (CUBIC_A + 3)) * t * t + 1
        out[2] = ((CUBIC_A + 2) * (1 - t) - (CUBIC_A + 3)) * (1 - t) * (1 - t) + 1
        out[3] = 1.0 - out[0] - out[1] - out[2]
    }

    private fun channel(value: Double): Int = value.roundToInt().coerceIn(0, 255)

    private fun saturateRound(value: Double): Int = when {
        value.isNaN() -> 0
        value >= Int.MAX_VALUE -> Int.MAX_VALUE
        value <= Int.MIN_VALUE -> Int.MIN_VALUE
        else -> Math.rint(value).toInt()
    }

    /** 3x3 row-major homography mapping each [from] point onto the matching [to] point. */
    internal fun perspectiveTransform(from: DoubleArray, to: DoubleArray): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i * 2]
            val y = from[i * 2 + 1]
            val u = to[i * 2]
            val v = to[i * 2 + 1]
            a[i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
            a[i + 4] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
        }
        for (col in 0 until 8) {
            val pivot = (col until 8).maxBy { kotlin.math.abs(a[it][col]) }
            require(kotlin.math.abs(a[pivot][col]) > 1e-12) { "degenerate quad" }
            val swap = a[col]; a[col] = a[pivot]; a[pivot] = swap
            for (row in 0 until 8) {
                if (row == col) continue
                val factor = a[row][col] / a[col][col]
                if (factor == 0.0) continue
                for (k in col until 9) a[row][k] -= factor * a[col][k]
            }
        }
        return DoubleArray(9) { i -> if (i == 8) 1.0 else a[i][8] / a[i][i] }
    }

    /** Integer source bounds [left, top, right, bottom) covering the quad plus the cubic margin. */
    fun sourceBounds(points: FloatArray, imageWidth: Int, imageHeight: Int): IntArray {
        val xs = floatArrayOf(points[0], points[2], points[4], points[6])
        val ys = floatArrayOf(points[1], points[3], points[5], points[7])
        return intArrayOf(
            (floor(xs.min()).toInt() - SOURCE_MARGIN).coerceIn(0, imageWidth - 1),
            (floor(ys.min()).toInt() - SOURCE_MARGIN).coerceIn(0, imageHeight - 1),
            (kotlin.math.ceil(xs.max()).toInt() + SOURCE_MARGIN + 1).coerceIn(1, imageWidth),
            (kotlin.math.ceil(ys.max()).toInt() + SOURCE_MARGIN + 1).coerceIn(1, imageHeight),
        )
    }
}
