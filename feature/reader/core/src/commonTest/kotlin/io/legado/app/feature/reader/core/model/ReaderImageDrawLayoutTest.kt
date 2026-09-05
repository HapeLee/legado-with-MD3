package io.legado.app.feature.reader.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class ReaderImageDrawLayoutTest {
    private val container = ReaderRect(10f, 20f, 110f, 220f)

    @Test
    fun `wide image is centered vertically without distortion`() {
        val result = ReaderImageDrawLayout.fitCenter(container, 200, 100)!!

        assertEquals(10f, result.leftPx, 0f)
        assertEquals(95f, result.topPx, 0f)
        assertEquals(100f, result.widthPx, 0f)
        assertEquals(50f, result.heightPx, 0f)
    }

    @Test
    fun `portrait image is centered horizontally without distortion`() {
        val result = ReaderImageDrawLayout.fitCenter(container, 50, 200)!!

        assertEquals(35f, result.leftPx, 0f)
        assertEquals(20f, result.topPx, 0f)
        assertEquals(50f, result.widthPx, 0f)
        assertEquals(200f, result.heightPx, 0f)
    }

    @Test
    fun `invalid image or container dimensions do not produce draw geometry`() {
        assertNull(ReaderImageDrawLayout.fitCenter(container, 0, 100))
        assertNull(ReaderImageDrawLayout.fitCenter(ReaderRect(0f, 0f, 0f, 10f), 10, 10))
    }
}
