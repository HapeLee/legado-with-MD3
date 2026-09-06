package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteArrayExtensionsTest {

    @Test
    fun `finds the first matching byte sequence`() {
        assertEquals(2, "xx<head>content".encodeToByteArray().indexOf("<head>".encodeToByteArray()))
        assertEquals(-1, "content".encodeToByteArray().indexOf("<head>".encodeToByteArray()))
    }

    @Test
    fun `uses failure table for overlapping patterns`() {
        val data = "ababab".encodeToByteArray()
        val pattern = "abab".encodeToByteArray()

        assertEquals(0, data.indexOf(pattern))
        assertEquals(2, data.indexOf(pattern, start = 1))
    }

    @Test
    fun `does not match beyond the requested stop boundary`() {
        val data = "ababab".encodeToByteArray()
        val pattern = "abab".encodeToByteArray()

        assertEquals(-1, data.indexOf(pattern, start = 1, stop = 5))
        assertEquals(2, data.indexOf(pattern, start = 1, stop = 6))
    }
}
