package io.legado.app.ui.book.manga

import androidx.compose.ui.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MangaColorFilterTest {
    private fun ColorMatrix.apply(red: Float, green: Float, blue: Float, alpha: Float = 255f): List<Float> =
        (0..3).map { row ->
            this[row, 0] * red + this[row, 1] * green + this[row, 2] * blue +
                this[row, 3] * alpha + this[row, 4]
        }

    @Test
    fun grayscaleRemovesSaturationFromOriginalTilePixels() {
        val matrix = requireNotNull(mangaColorMatrix(MangaReaderSettings(enableGray = true)))
        for (pixel in listOf(Triple(255f, 0f, 0f), Triple(0f, 255f, 0f), Triple(50f, 120f, 200f))) {
            val result = matrix.apply(pixel.first, pixel.second, pixel.third)
            assertEquals(result[0], result[1], 0.001f)
            assertEquals(result[1], result[2], 0.001f)
            assertEquals(255f, result[3], 0.001f)
        }
    }

    @Test
    fun channelFiltersApplyAfterGrayscaleAsBefore() {
        val gray = requireNotNull(mangaColorMatrix(MangaReaderSettings(enableGray = true)))
        val tinted = requireNotNull(mangaColorMatrix(MangaReaderSettings(
            enableGray = true, filterRed = 128, filterAlpha = 64,
        )))
        val original = gray.apply(255f, 0f, 0f)
        val result = tinted.apply(255f, 0f, 0f)
        assertEquals(original[0] * 127f / 255f, result[0], 0.001f)
        assertEquals(original[1], result[1], 0.001f)
        assertEquals(191f, result[3], 0.001f)
    }

    @Test
    fun einkKeepsItsThresholdTransformationAndDefaultHasNoFilter() {
        assertNull(mangaColorMatrix(MangaReaderSettings()))
        assertNull(mangaColorMatrix(MangaReaderSettings(enableGray = true, enableEInk = true)))
    }
}
