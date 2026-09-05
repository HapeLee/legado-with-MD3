package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ReaderBackgroundAlphaTest {

    @Test
    fun `legacy percentage is normalized for Canvas alpha`() {
        assertEquals(0f, readerBackgroundAlpha(0f), 0f)
        assertEquals(0.5f, readerBackgroundAlpha(50f), 0f)
        assertEquals(1f, readerBackgroundAlpha(100f), 0f)
    }

    @Test
    fun `out of range values are clamped`() {
        assertEquals(0f, readerBackgroundAlpha(-1f), 0f)
        assertEquals(1f, readerBackgroundAlpha(101f), 0f)
    }
}
