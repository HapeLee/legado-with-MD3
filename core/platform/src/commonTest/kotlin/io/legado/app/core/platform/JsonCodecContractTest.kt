package io.legado.app.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonCodecContractTest {

    @Test
    fun `toJson of map matches Gson pretty printing bytes`() {
        val json = JsonCodec.toJson(mapOf("a" to "b", "c" to "d"))
        // Gson setPrettyPrinting 输出：2 空格缩进 + ": " 分隔，键序按插入顺序。
        assertEquals("{\n  \"a\": \"b\",\n  \"c\": \"d\"\n}", json)
    }

    @Test
    fun `toJson of empty map is empty object`() {
        assertEquals("{}", JsonCodec.toJson(emptyMap<String, String>()))
    }

    @Test
    fun `toJson of null is null literal`() {
        assertEquals("null", JsonCodec.toJson(null))
    }

    @Test
    fun `decodeStringMap round trips`() {
        val map = mapOf("k1" to "v1", "k2" to "v2")
        val json = JsonCodec.toJson(map)
        val decoded = JsonCodec.decodeStringMap(json)
        assertEquals(map, decoded)
    }

    @Test
    fun `decodeStringMap of null input returns null`() {
        assertNull(JsonCodec.decodeStringMap(null))
    }

    @Test
    fun `decodeAnyMap fixes int to long`() {
        val decoded = JsonCodec.decodeAnyMap("{\"int\": 42, \"double\": 3.5, \"str\": \"x\", \"bool\": true}")
        requireNotNull(decoded)
        // 数字修复语义：整数 → Long，非整数 → Double。
        assertEquals(42L, decoded["int"])
        assertEquals(3.5, decoded["double"])
        assertEquals("x", decoded["str"])
        assertEquals(true, decoded["bool"])
    }

    @Test
    fun `decodeAnyMap of null input returns null`() {
        assertNull(JsonCodec.decodeAnyMap(null))
    }

    @Test
    fun `fromJsonObject decodes concrete class`() {
        val json = JsonCodec.toJson(Sample("hello", 7))
        val decoded = JsonCodec.fromJsonObject(json, Sample::class)
        assertEquals(Sample("hello", 7), decoded)
    }

    @Test
    fun `fromJsonObject of null input returns null`() {
        assertNull(JsonCodec.fromJsonObject<Sample>(null, Sample::class))
    }

    private data class Sample(val name: String, val count: Int)
}
