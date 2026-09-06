package io.legado.app.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapExtensionsTest {

    @Test
    fun `case-insensitive hash map queries retain exact-match defaults`() {
        val values = hashMapOf("Header" to "value")

        assertFalse(values.has("header"))
        assertTrue(values.has("header", ignoreCase = true))
        assertEquals("value", values.get("header", ignoreCase = true))
        assertNull(values.get("missing", ignoreCase = true))
    }

    @Test
    fun `bounded get or put caches a computed value while capacity remains`() {
        val values = mutableMapOf<String, String>()
        var computations = 0

        assertEquals("created", values.getOrPutLimit("key", 1) { computations++; "created" })
        assertEquals("created", values.getOrPutLimit("key", 1) { computations++; "different" })
        assertEquals(1, computations)
        assertEquals(mapOf("key" to "created"), values)
    }

    @Test
    fun `bounded get or put returns but does not store values after capacity`() {
        val values = mutableMapOf("existing" to "cached")
        var computations = 0

        assertEquals("first", values.getOrPutLimit("new", 1) { computations++; "first" })
        assertEquals("second", values.getOrPutLimit("new", 1) { computations++; "second" })
        assertEquals(2, computations)
        assertEquals(mapOf("existing" to "cached"), values)
    }
}
