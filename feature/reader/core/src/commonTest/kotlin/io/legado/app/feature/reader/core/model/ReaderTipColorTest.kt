package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderTipColorTest {

    @Test
    fun `zero configured color follows body text instead of becoming transparent`() {
        assertEquals(
            0xFF123456.toInt(),
            resolveReaderTipColor(configuredColorArgb = 0, bodyTextColorArgb = 0xFF123456.toInt()),
        )
    }

    @Test
    fun `explicit tip color is preserved`() {
        assertEquals(
            0xFFABCDEF.toInt(),
            resolveReaderTipColor(
                configuredColorArgb = 0xFFABCDEF.toInt(),
                bodyTextColorArgb = 0xFF123456.toInt(),
            ),
        )
    }
}
