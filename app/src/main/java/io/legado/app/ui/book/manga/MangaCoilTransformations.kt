package io.legado.app.ui.book.manga

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import coil3.size.Size
import coil3.transform.Transformation

internal object MangaGrayscaleTransformation : Transformation() {
    override val cacheKey: String = "manga-grayscale-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return input.filteredBitmap(ColorMatrix().apply { setSaturation(0f) })
    }
}

internal class MangaEInkTransformation(private val threshold: Int) : Transformation() {
    override val cacheKey: String = "manga-eink-v1-$threshold"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val result = input.filteredBitmap(ColorMatrix().apply { setSaturation(0f) })
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        pixels.indices.forEach { index ->
            pixels[index] = if (Color.red(pixels[index]) < threshold) Color.BLACK else Color.WHITE
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        return result
    }
}

private fun Bitmap.filteredBitmap(matrix: ColorMatrix): Bitmap {
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(this, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    })
    return output
}
