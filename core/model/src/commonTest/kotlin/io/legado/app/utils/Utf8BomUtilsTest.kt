package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class Utf8BomUtilsTest {

    @Test
    fun `recognizes a BOM only when content follows the marker`() {
        assertTrue(Utf8BomUtils.hasBom(bytes(0xEF, 0xBB, 0xBF, 'x'.code)))
        assertFalse(Utf8BomUtils.hasBom(bytes(0xEF, 0xBB, 0xBF)))
        assertFalse(Utf8BomUtils.hasBom(bytes(0xEF, 0xBB, 0xBE, 'x'.code)))
    }

    @Test
    fun `removes BOM from text and bytes without changing content`() {
        assertEquals("中文 text", Utf8BomUtils.removeUTF8BOM("\uFEFF中文 text"))
        assertContentEquals(
            "中文 text".encodeToByteArray(),
            Utf8BomUtils.removeUTF8BOM("\uFEFF中文 text".encodeToByteArray()),
        )
    }

    @Test
    fun `returns the original byte array when no removable BOM exists`() {
        val plain = "plain".encodeToByteArray()
        val markerOnly = bytes(0xEF, 0xBB, 0xBF)

        assertSame(plain, Utf8BomUtils.removeUTF8BOM(plain))
        assertSame(markerOnly, Utf8BomUtils.removeUTF8BOM(markerOnly))
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { index -> values[index].toByte() }
}
