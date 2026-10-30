package io.legado.app.data.repository.manga.translation

import io.legado.app.domain.model.manga.MangaTextPoint
import io.legado.app.domain.model.manga.MangaTextQuad
import kotlin.math.hypot

internal fun MangaTextQuad.pixelCorners(width: Int, height: Int): FloatArray = FloatArray(8) { i ->
    if (i % 2 == 0) points[i / 2].x * width / 1000 else points[i / 2].y * height / 1000
}

internal fun mangaSourceQuad(corners: FloatArray, width: Int, height: Int): MangaTextQuad? {
    if (corners.size != 8 || corners.any { !it.isFinite() }) return null
    val quad = MangaTextQuad((0..3).map { i -> MangaTextPoint(
        (corners[i * 2] * 1000 / width).coerceIn(0f, 1000f),
        (corners[i * 2 + 1] * 1000 / height).coerceIn(0f, 1000f)) })
    return quad.takeIf { runCatching { it.validate() }.isSuccess }
}

internal fun MangaTextQuad.edgeLengths(width: Int, height: Int): Pair<Float, Float> {
    val p = pixelCorners(width, height)
    fun distance(a: Int, b: Int) = hypot(p[a * 2] - p[b * 2], p[a * 2 + 1] - p[b * 2 + 1])
    return (distance(0, 1) + distance(3, 2)) / 2 to (distance(0, 3) + distance(1, 2)) / 2
}
