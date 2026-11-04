package io.legado.app.data.repository.manga.translation

import kotlin.math.ceil
import kotlin.math.hypot

/** Recognition-only padding in source pixels. Does not change source anchors or erasure. */
internal class MangaOcrCropPlan(val corners: FloatArray, val width: Int, val vertical: Boolean)

internal fun mangaOcrCropPlan(source: FloatArray, neighbors: List<FloatArray>, detectorCrop: FloatArray? = null): MangaOcrCropPlan {
    require(source.size == 8 && source.all(Float::isFinite))
    val w = hypot(source[2] - source[0], source[3] - source[1])
    val h = hypot(source[6] - source[0], source[7] - source[1])
    require(w >= 1 && h >= 1) { "Degenerate OCR source quad" }
    val ux = (source[2] - source[0]) / w; val uy = (source[3] - source[1]) / w
    val vx = (source[6] - source[0]) / h; val vy = (source[7] - source[1]) / h
    val pad = minOf(4f, minOf(w, h) * .2f)
    var left = pad; var right = pad; var top = pad; var bottom = pad
    // DB probability contours are shrunk training targets. Preserve the detector's unclip
    // restoration rather than replacing it with a small fixed pad and clipping real glyphs.
    detectorCrop?.let { crop ->
        require(crop.size == 8 && crop.all(Float::isFinite))
        val u = (0..3).map { i -> (crop[i * 2] - source[0]) * ux + (crop[i * 2 + 1] - source[1]) * uy }
        val v = (0..3).map { i -> (crop[i * 2] - source[0]) * vx + (crop[i * 2 + 1] - source[1]) * vy }
        left = maxOf(left, -u.min()); right = maxOf(right, u.max() - w)
        top = maxOf(top, -v.min()); bottom = maxOf(bottom, v.max() - h)
    }
    for (other in neighbors) {
        require(other.size == 8)
        val u = (0..3).map { i -> (other[i * 2] - source[0]) * ux + (other[i * 2 + 1] - source[1]) * uy }
        val v = (0..3).map { i -> (other[i * 2] - source[0]) * vx + (other[i * 2 + 1] - source[1]) * vy }
        val u0 = u.min(); val u1 = u.max(); val v0 = v.min(); val v1 = v.max()
        if (minOf(w, u1) - maxOf(0f, u0) > minOf(w, u1 - u0) * .25f) {
            if (v1 <= .1f) top = minOf(top, maxOf(0f, -v1 / 2))
            if (v0 >= h - .1f) bottom = minOf(bottom, maxOf(0f, (v0 - h) / 2))
        }
        if (minOf(h, v1) - maxOf(0f, v0) > minOf(h, v1 - v0) * .25f) {
            if (u1 <= .1f) left = minOf(left, maxOf(0f, -u1 / 2))
            if (u0 >= w - .1f) right = minOf(right, maxOf(0f, (u0 - w) / 2))
        }
    }
    val expanded = source.copyOf()
    for (i in 0..3) {
        val du = if (i == 0 || i == 3) -left else right
        val dv = if (i < 2) -top else bottom
        expanded[i * 2] += du * ux + dv * vx
        expanded[i * 2 + 1] += du * uy + dv * vy
    }
    val vertical = h > w * 1.5f
    // Rotate vertical source strips counterclockwise by changing correspondences. One cubic
    // sample goes directly to height 48, avoiding an intermediate bilinear resize or rotation.
    val oriented = if (vertical) FloatArray(8) { i -> expanded[(i + 2) % 8] } else expanded
    val ratio = if (vertical) (h + top + bottom) / (w + left + right) else (w + left + right) / (h + top + bottom)
    val targetWidth = ceil(48.0 * ratio).toInt().coerceAtLeast(1)
    check(targetWidth <= 3200) { "OCR line is too long; choose another OCR engine" }
    return MangaOcrCropPlan(oriented, targetWidth, vertical)
}
