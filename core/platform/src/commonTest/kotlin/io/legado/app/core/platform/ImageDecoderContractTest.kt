package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Contract test for [ImageDecoder].
 *
 * Each target subclass provides valid PNG bytes via [validPng] — this
 * keeps JVM-only image APIs out of commonTest.
 */
abstract class ImageDecoderContractTest {

    protected abstract fun createDecoder(): ImageDecoder

    /** Valid PNG bytes (e.g. a small solid-color image). */
    protected abstract val validPng: ByteArray

    /** Expected width of [validPng]. */
    protected abstract val expectedWidth: Int

    /** Expected height of [validPng]. */
    protected abstract val expectedHeight: Int

    @Test
    fun decodes_valid_png() {
        val decoder = createDecoder()
        val result = decoder.decode(validPng)
        assertNotNull(result, "valid PNG should decode")
        assertEquals(expectedWidth, result.width, "width mismatch")
        assertEquals(expectedHeight, result.height, "height mismatch")
    }

    @Test
    fun returns_null_for_invalid_bytes() {
        val decoder = createDecoder()
        val result = decoder.decode(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))
        assertNull(result, "invalid bytes should return null, not crash")
    }

    @Test
    fun returns_null_for_empty_bytes() {
        val decoder = createDecoder()
        val result = decoder.decode(ByteArray(0))
        assertNull(result, "empty bytes should return null")
    }
}
