package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Test

class BookInfoBackdropStyleTest {

    @Test
    fun backgroundModesKeepTheirExpectedCoverTreatment() {
        assertEquals(
            BookInfoBackdropStyle(showCover = true, blurCover = false),
            resolveBookInfoBackdropStyle("off")
        )
        assertEquals(
            BookInfoBackdropStyle(showCover = true, blurCover = true),
            resolveBookInfoBackdropStyle("on")
        )
        assertEquals(
            BookInfoBackdropStyle(showCover = false, blurCover = false),
            resolveBookInfoBackdropStyle("off_for_default")
        )
    }
}
