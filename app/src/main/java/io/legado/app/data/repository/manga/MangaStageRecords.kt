package io.legado.app.data.repository.manga

import android.graphics.Point
import android.graphics.Rect
import kotlinx.serialization.Serializable
import java.util.BitSet

@Serializable
internal data class MangaSavedRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun rect() = Rect(left, top, right, bottom)
}
internal fun Rect.saved() = MangaSavedRect(left, top, right, bottom)
@Serializable
internal data class MangaSavedPoint(val x: Int, val y: Int)
@Serializable
internal data class MangaSavedLine(val text: String, val bounds: MangaSavedRect, val glyphs: List<List<MangaSavedPoint>>)
@Serializable
internal data class MangaSavedRegion(val lines: List<MangaSavedLine>) {
    fun region() = MangaOcrRegion(lines.map { line -> MangaOcrLine(line.text, line.bounds.rect(), line.glyphs.map { g -> g.map { Point(it.x, it.y) } }) })
}
internal fun MangaOcrRegion.saved() = MangaSavedRegion(lines.map { line -> MangaSavedLine(line.text, line.bounds.saved(), line.glyphs.map { g -> g.map { MangaSavedPoint(it.x, it.y) } }) })
@Serializable
internal data class MangaSavedBubble(val bounds: MangaSavedRect, val tile: MangaSavedRect, val bits: ByteArray) {
    fun bubble(): MangaBubble {
        require(bits.size <= 8192)
        val bitset = BitSet.valueOf(bits)
        return MangaBubble(bounds.rect(), BooleanArray(65536) { bitset[it] }, tile.rect())
    }
}
internal fun MangaBubble.saved(): MangaSavedBubble {
    val bits = BitSet(mask.size)
    mask.indices.forEach { if (mask[it]) bits.set(it) }
    return MangaSavedBubble(bounds.saved(), tile.saved(), bits.toByteArray())
}
