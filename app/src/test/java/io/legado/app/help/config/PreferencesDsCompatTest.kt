package io.legado.app.help.config

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesDsCompatTest {

    @Test
    fun `按目标类型直接读取`() {
        val prefs = mutablePreferencesOf(
            intPreferencesKey("i") to 5,
            booleanPreferencesKey("b") to true,
            longPreferencesKey("l") to 7L,
            floatPreferencesKey("f") to 1.5f,
            stringPreferencesKey("s") to "hello",
            stringSetPreferencesKey("set") to setOf("a", "b"),
        )
        assertEquals(5, prefs.compatDsInt("i"))
        assertEquals(true, prefs.compatDsBoolean("b"))
        assertEquals(7L, prefs.compatDsLong("l"))
        assertEquals(1.5f, prefs.compatDsFloat("f"))
        assertEquals("hello", prefs.compatDsString("s"))
        assertEquals(setOf("a", "b"), prefs.compatDsStringSet("set"))
    }

    @Test
    fun `string 形式的历史值可解析为目标类型`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("i") to "5",
            stringPreferencesKey("b") to "true",
            stringPreferencesKey("l") to "7",
            stringPreferencesKey("f") to "1.5",
        )
        assertEquals(5, prefs.compatDsInt("i"))
        assertEquals(true, prefs.compatDsBoolean("b"))
        assertEquals(7L, prefs.compatDsLong("l"))
        assertEquals(1.5f, prefs.compatDsFloat("f"))
    }

    @Test
    fun `int 存储的值可作为 long 与 float 读取`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("k") to 3)
        assertEquals(3L, prefs.compatDsLong("k"))
        assertEquals(3f, prefs.compatDsFloat("k"))
    }

    @Test
    fun `缺失或无法解析的值返回 null 而不抛异常`() {
        val prefs = mutablePreferencesOf(
            stringPreferencesKey("garbage") to "not a number",
            intPreferencesKey("wrongType") to 1,
        )
        assertNull(prefs.compatDsInt("absent"))
        assertNull(prefs.compatDsInt("garbage"))
        assertNull(prefs.compatDsBoolean("garbage"))
        assertNull(prefs.compatDsBoolean("wrongType"))
        assertNull(prefs.compatDsString("wrongType"))
        assertNull(prefs.compatDsStringSet("garbage"))
    }

    @Test
    fun `boolean 解析大小写严格`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("b") to "True")
        assertNull(prefs.compatDsBoolean("b"))
    }

    @Test
    fun `setPrefValue 覆盖同名旧类型条目`() {
        val prefs = mutablePreferencesOf(stringPreferencesKey("k") to "1")
        prefs.setPrefValue("k", 2)
        assertEquals(2, prefs.compatDsInt("k"))
        assertNull(prefs.compatDsString("k"))
    }

    @Test
    fun `setPrefValue null 移除任意类型条目`() {
        val prefs = mutablePreferencesOf(intPreferencesKey("k") to 1)
        prefs.setPrefValue("k", null)
        assertNull(prefs.rawPrefValue("k"))
    }

    @Test
    fun `setPrefValue 写入各类型`() {
        val prefs = mutablePreferencesOf()
        prefs.setPrefValue("s", "v")
        prefs.setPrefValue("i", 1)
        prefs.setPrefValue("b", true)
        prefs.setPrefValue("l", 2L)
        prefs.setPrefValue("f", 3f)
        prefs.setPrefValue("set", setOf("x"))
        assertEquals("v", prefs.compatDsString("s"))
        assertEquals(1, prefs.compatDsInt("i"))
        assertEquals(true, prefs.compatDsBoolean("b"))
        assertEquals(2L, prefs.compatDsLong("l"))
        assertEquals(3f, prefs.compatDsFloat("f"))
        assertEquals(setOf("x"), prefs.compatDsStringSet("set"))
    }
}
