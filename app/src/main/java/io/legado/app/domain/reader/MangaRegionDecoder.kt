package io.legado.app.domain.reader

/** Coordinates are in the displayed, EXIF-corrected original image, before sampling. */
data class MangaImageSize(val width: Int, val height: Int)

data class MangaImageRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Platform decoding boundary. The host chooses the tile representation [Tile]; no platform
 * handle or renderer type is part of this contract. Unsupported formats fail explicitly.
 * Calls are serialized by the implementation. close() is idempotent and waits for native work;
 * decoded tiles remain usable after closing the decoder. Cancellation must not publish a tile.
 * This contract currently lives in :app, not in a verified KMP module.
 */
interface MangaRegionDecoder<Tile> {
    val imageSize: MangaImageSize
    suspend fun decodeRegion(region: MangaImageRegion, sampleSize: Int): Tile
    fun close()
}

/** EXIF's eight orientations, independent of Android and Compose. */
internal class MangaImageOrientation(private val value: Int) {
    private val swapsAxes get() = value in 5..8

    fun displayedSize(raw: MangaImageSize): MangaImageSize =
        if (swapsAxes) MangaImageSize(raw.height, raw.width) else raw

    fun rawRegion(region: MangaImageRegion, raw: MangaImageSize): MangaImageRegion {
        fun inverse(x: Int, y: Int): Pair<Int, Int> = when (value) {
            2 -> raw.width - x to y
            3 -> raw.width - x to raw.height - y
            4 -> x to raw.height - y
            5 -> y to x
            6 -> y to raw.height - x
            7 -> raw.width - y to raw.height - x
            8 -> raw.width - y to x
            else -> x to y
        }

        val corners = listOf(
            inverse(region.left, region.top), inverse(region.right, region.top),
            inverse(region.left, region.bottom), inverse(region.right, region.bottom)
        )
        return MangaImageRegion(
            corners.minOf { it.first }, corners.minOf { it.second },
            corners.maxOf { it.first }, corners.maxOf { it.second })
    }
}
