// SPDX-License-Identifier: Apache-2.0
// Adapted from ciddwd/overlay-translator, commit 64a7aa2c9605c88dd35bdeea42ab5c0f1c18ce5e.
// Modifications: Legado package, geometry types and bounded Android pipeline integration.
package io.legado.app.data.repository.manga.translation


/**
 * Orders polygon pixels by distance while retaining row-major tie ordering.
 *
 * Each pixel's key packs its distance bits above its preparation position, so keys are unique and
 * compare exactly like a stable sort by distance. Callers only need minimum keys (overall and per
 * spatial extreme), so lookups scan once instead of sorting every polygon pixel up front.
 */
internal class MangaSeedSearchOrder private constructor(
    private val imageWidth: Int,
    private val globalIndices: IntArray,
    private val keys: LongArray,
) {
    /** Sample the center and spatial extremes so a letter counter cannot become the only seed. */
    fun spatialSeeds(candidate: BooleanArray, roi: MangaShapeRect): List<Int> {
        require(roi.width > 0 && roi.height > 0)
        require(candidate.size == roi.width * roi.height)
        var nearest = -1
        var left = -1
        var right = -1
        var top = -1
        var bottom = -1
        var nearestKey = Long.MAX_VALUE
        var leftKey = Long.MAX_VALUE
        var rightKey = Long.MAX_VALUE
        var topKey = Long.MAX_VALUE
        var bottomKey = Long.MAX_VALUE
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (position in keys.indices) {
            val globalIndex = globalIndices[position]
            val y = globalIndex / imageWidth
            val x = globalIndex - y * imageWidth
            if (x !in roi.left until roi.right || y !in roi.top until roi.bottom) continue
            val index = (y - roi.top) * roi.width + x - roi.left
            if (!candidate[index]) continue
            val key = keys[position]
            if (key < nearestKey) { nearestKey = key; nearest = index }
            // Equal coordinates resolve to the nearer pixel, as the first hit in distance order did.
            if (x < minX || (x == minX && key < leftKey)) { minX = x; leftKey = key; left = index }
            if (x > maxX || (x == maxX && key < rightKey)) { maxX = x; rightKey = key; right = index }
            if (y < minY || (y == minY && key < topKey)) { minY = y; topKey = key; top = index }
            if (y > maxY || (y == maxY && key < bottomKey)) { maxY = y; bottomKey = key; bottom = index }
        }
        return listOf(nearest, left, right, top, bottom).filter { it >= 0 }.distinct()
    }

    companion object {
        fun prepare(
            imageWidth: Int,
            globalIndices: IntArray,
            distanceSquared: FloatArray,
            count: Int = globalIndices.size,
        ): MangaSeedSearchOrder {
            require(imageWidth > 0)
            require(count in 0..globalIndices.size)
            require(count <= distanceSquared.size)
            val indices = globalIndices.copyOf(count)
            val keys = LongArray(count) { position ->
                val distance = distanceSquared[position]
                require(distance.isFinite() && distance >= 0f)
                (java.lang.Float.floatToRawIntBits(distance).toLong() shl 32) or
                    (position.toLong() and 0xffff_ffffL)
            }
            return MangaSeedSearchOrder(
                imageWidth = imageWidth,
                globalIndices = indices,
                keys = keys,
            )
        }
    }
}
