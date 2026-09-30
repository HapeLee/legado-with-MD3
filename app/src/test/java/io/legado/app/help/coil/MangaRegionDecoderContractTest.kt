package io.legado.app.help.coil

import io.legado.app.domain.reader.MangaImageOrientation
import io.legado.app.domain.reader.MangaImageRegion
import io.legado.app.domain.reader.MangaImageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaRegionDecoderContractTest {
    @Test
    fun `all EXIF orientations map displayed subregions back to raw pixels`() {
        val raw = MangaImageSize(8, 6)
        val region = MangaImageRegion(1, 2, 3, 4)
        val expected = listOf(
            region, MangaImageRegion(5, 2, 7, 4),
            MangaImageRegion(5, 2, 7, 4), region, MangaImageRegion(2, 1, 4, 3),
            MangaImageRegion(2, 3, 4, 5), MangaImageRegion(4, 3, 6, 5),
            MangaImageRegion(4, 1, 6, 3)
        )
        for (value in 1..8) {
            val orientation = MangaImageOrientation(value)
            assertEquals(expected[value - 1], orientation.rawRegion(region, raw))
            val size = orientation.displayedSize(raw)
            assertEquals(if (value < 5) raw else MangaImageSize(6, 8), size)
            assertEquals(
                MangaImageRegion(0, 0, 8, 6),
                orientation.rawRegion(MangaImageRegion(0, 0, size.width, size.height), raw)
            )
        }
    }
}
