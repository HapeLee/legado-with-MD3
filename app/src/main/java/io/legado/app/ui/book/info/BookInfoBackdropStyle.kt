package io.legado.app.ui.book.info

import io.legado.app.ui.config.themeConfig.ThemeConfig

internal data class BookInfoBackdropStyle(
    val showCover: Boolean,
    val blurCover: Boolean,
)

internal fun resolveBookInfoBackdropStyle(backgroundMode: String): BookInfoBackdropStyle {
    return when (backgroundMode) {
        ThemeConfig.BOOK_INFO_BACKGROUND_BLUR_OFF -> BookInfoBackdropStyle(
            showCover = true,
            blurCover = false,
        )

        ThemeConfig.BOOK_INFO_BACKGROUND_COVER_HIDDEN -> BookInfoBackdropStyle(
            showCover = false,
            blurCover = false,
        )

        else -> BookInfoBackdropStyle(
            showCover = true,
            blurCover = true,
        )
    }
}
