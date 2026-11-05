package io.legado.app.data.repository.manga.translation

import android.graphics.Bitmap
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Tensor preparation and mask composition stay in the shared layer: they are pure pixel math and
 * the repository needs [mangaCompositeInpainting] to keep repaired pixels inside the mask even
 * when a runtime returns a full-frame result.
 */
internal data class MangaInpaintInput(
    val pixels: IntArray,
    val mask: BooleanArray,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/** Preserve aspect ratio, edge-pad, and max-pool the mask so downscaling cannot lose small strokes. */
internal fun mangaInpaintInput(bitmap: Bitmap, mask: BooleanArray): MangaInpaintInput {
    require(mask.size == bitmap.width * bitmap.height)
    val scale = minOf(512f / bitmap.width, 512f / bitmap.height)
    val width = (bitmap.width * scale).roundToInt().coerceIn(1, 512)
    val height = (bitmap.height * scale).roundToInt().coerceIn(1, 512)
    val left = (512 - width) / 2; val top = (512 - height) / 2
    val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
    val source = IntArray(width * height)
    try { resized.getPixels(source, 0, width, 0, 0, width, height) } finally { if (resized !== bitmap) resized.recycle() }
    val pixels = IntArray(512 * 512) { index -> source[(index / 512 - top).coerceIn(0, height - 1) * width + (index % 512 - left).coerceIn(0, width - 1)] }
    val holes = BooleanArray(pixels.size)
    // RGB uses bilinear filtering: include all contributing source pixels. Nearest-only masks
    // expose blurred lettering to the model as supposedly intact background context.
    for (y in 0 until height) for (x in 0 until width) {
        val sx = floor((x + .5) * bitmap.width / width - .5).toInt()
        val sy = floor((y + .5) * bitmap.height / height - .5).toInt()
        holes[(y + top) * 512 + x + left] = (0..1).any { dy -> (0..1).any { dx ->
            mask[(sy + dy).coerceIn(0, bitmap.height - 1) * bitmap.width + (sx + dx).coerceIn(0, bitmap.width - 1)]
        } }
    }
    mask.forEachIndexed { index, selected -> if (selected)
        holes[(index / bitmap.width * height / bitmap.height + top) * 512 + index % bitmap.width * width / bitmap.width + left] = true
    }
    return MangaInpaintInput(pixels, holes, left, top, width, height)
}

internal fun mangaCompositeInpainting(original: IntArray, repaired: IntArray, mask: BooleanArray): IntArray {
    require(original.size == repaired.size && original.size == mask.size)
    return IntArray(original.size) { i -> if (mask[i]) (original[i] and 0xff000000.toInt()) or (repaired[i] and 0x00ffffff) else original[i] }
}
