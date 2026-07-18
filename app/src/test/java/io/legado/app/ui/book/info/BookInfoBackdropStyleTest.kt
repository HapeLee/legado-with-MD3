package io.legado.app.ui.book.info

import io.legado.app.ui.config.themeConfig.ThemeConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoBackdropStyleTest {

    @Test
    fun backgroundModesKeepTheirExpectedCoverTreatment() {
        assertEquals(
            BookInfoBackdropStyle(
                showCover = true,
                blurCover = false,
                applySeedOverlay = true,
            ),
            resolveBookInfoBackdropStyle(ThemeConfig.BOOK_INFO_BACKGROUND_BLUR_OFF)
        )
        assertEquals(
            BookInfoBackdropStyle(
                showCover = true,
                blurCover = true,
                applySeedOverlay = true,
            ),
            resolveBookInfoBackdropStyle(ThemeConfig.BOOK_INFO_BACKGROUND_BLUR_ON)
        )
        assertEquals(
            BookInfoBackdropStyle(
                showCover = false,
                blurCover = false,
                applySeedOverlay = false,
            ),
            resolveBookInfoBackdropStyle(ThemeConfig.BOOK_INFO_BACKGROUND_COVER_HIDDEN)
        )
    }
}
