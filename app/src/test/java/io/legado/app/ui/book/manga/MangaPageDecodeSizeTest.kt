package io.legado.app.ui.book.manga

import androidx.compose.ui.unit.IntSize
import coil3.size.Dimension
import coil3.size.Size
import io.legado.app.ui.book.manga.config.MangaPageScaleType
import io.legado.app.ui.book.manga.config.MangaWidePageMode
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaPageDecodeSizeTest {
    private fun decodeSize(
        scale: Int,
        paged: Boolean = true,
        wideMode: Int = MangaWidePageMode.NORMAL,
        padding: Int = 0,
    ) = mangaPageDecodeSize(IntSize(1080, 2400), paged, padding, scale, wideMode)

    @Test
    fun `fit height does not constrain a landscape page to portrait window width`() {
        assertEquals(
            Size(Dimension.Undefined, Dimension(2400)),
            decodeSize(MangaPageScaleType.FIT_HEIGHT),
        )
    }

    @Test
    fun `stretch supplies both target axes and original preserves intrinsic dimensions`() {
        assertEquals(Size(1080, 2400), decodeSize(MangaPageScaleType.STRETCH))
        assertEquals(Size.ORIGINAL, decodeSize(MangaPageScaleType.ORIGINAL))
    }

    @Test
    fun `rotated wide page has sufficient width before its aspect ratio is known`() {
        assertEquals(
            Size(Dimension(2400), Dimension.Undefined),
            decodeSize(MangaPageScaleType.FIT_WIDTH, wideMode = MangaWidePageMode.ROTATE_TO_FIT),
        )
    }

    @Test
    fun `wide page width override and ordinary page height fit are both covered`() {
        assertEquals(
            Size(2400, 1080),
            mangaPageDecodeSize(
                IntSize(2400, 1080), true, 0,
                MangaPageScaleType.FIT_HEIGHT, MangaWidePageMode.FIT_WIDTH,
            ),
        )
    }

    @Test
    fun `webtoon ignores paged scale and uses padded width with unconstrained height`() {
        for (scale in listOf(MangaPageScaleType.FIT_HEIGHT, MangaPageScaleType.STRETCH, MangaPageScaleType.ORIGINAL)) {
            assertEquals(
                Size(Dimension(864), Dimension.Undefined),
                decodeSize(scale, paged = false, padding = 10),
            )
        }
        assertEquals(
            Size(Dimension(108), Dimension.Undefined),
            decodeSize(MangaPageScaleType.FIT_WIDTH, paged = false, padding = 90),
        )
    }
}
