package io.legado.app.data.json

import io.legado.app.core.platform.ImportFieldValue
import io.legado.app.core.platform.ImportJsonField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [GsonImportJsonEditor] 的行为基线。
 *
 * 这些断言逐条对应迁移前 `:core:ui` `ImportComponents.kt` 里的私有辅助函数
 * （`toImportJsonObject` / `toImportDataLike` / `toImportEditText` / `toImportJsonElement`）
 * 与 `BatchImportJsonField` 的渲染分支；改动这里前请先读 [GsonImportJsonEditor] 的类注释。
 */
class GsonImportJsonEditorTest {

    private val editor = GsonImportJsonEditor()

    // ---------- fieldsOf ----------

    @Test
    fun fieldsOfMapsValueKinds() {
        val fields = editor.fieldsOf(
            Sample(
                name = "a",
                count = 3,
                enabled = true,
                nested = Nested("x"),
                tags = listOf("p", "q"),
            )
        )

        assertEquals(
            listOf("name", "count", "enabled", "nested", "tags"),
            fields?.map { it.name },
        )
        assertEquals(ImportFieldValue.Text("a"), fields?.get(0)?.value)
        assertEquals(ImportFieldValue.Text("3"), fields?.get(1)?.value)
        assertEquals(ImportFieldValue.Bool(true), fields?.get(2)?.value)
        assertTrue(fields?.get(3)?.value is ImportFieldValue.Json)
        assertTrue(fields?.get(4)?.value is ImportFieldValue.Json)
    }

    @Test
    fun fieldsOfJsonTextIsPrettyPrintedAndUnescaped() {
        // 对齐迁移前 `GSON.toJson(element)`：prettyPrinting + disableHtmlEscaping。
        val fields = editor.fieldsOf(Sample(nested = Nested("<b>&</b>")))
        val json = (fields!!.first { it.name == "nested" }.value as ImportFieldValue.Json).text

        assertTrue(json.contains("\n"), "应为 prettyPrinting 的多行输出：$json")
        assertTrue(json.contains("<b>&</b>"), "HTML 不应被转义：$json")
    }

    @Test
    fun fieldsOfReturnsNullForNonObject() {
        assertNull(editor.fieldsOf(null))
        assertNull(editor.fieldsOf(listOf(1, 2)))
        assertNull(editor.fieldsOf("plain"))
        assertNull(editor.fieldsOf(42))
    }

    // ---------- withEditedText ----------

    @Test
    fun withEditedTextOnStringFieldKeepsRawWhitespace() {
        // 迁移前：`JsonPrimitive(this)` 用的是**未 trim** 的原文。
        val updated = editor.withEditedText(Sample(name = "a"), "name", "  b  ") as Sample

        assertEquals("  b  ", updated.name)
        assertEquals(3, updated.count, "其它字段应保持不变")
    }

    @Test
    fun withEditedTextOnNumberFieldParsesLong() {
        val updated = editor.withEditedText(Sample(count = 3), "count", "42") as Sample
        assertEquals(42, updated.count)
    }

    @Test
    fun withEditedTextOnNumberFieldWithGarbageIsRejected() {
        assertNull(editor.withEditedText(Sample(count = 3), "count", "abc"))
    }

    @Test
    fun withEditedTextOnNumberFieldWithEmptyTextBecomesNull() {
        // 迁移前：数字字段清空 → JsonNull → 反序列化回默认值（data class 的 count 无默认 null，
        // 故这里用可空数字字段验证「被清空」）。
        val updated = editor.withEditedText(Box(count = 5), "count", "") as Box
        assertNull(updated.count)
    }

    @Test
    fun withEditedTextOnJsonFieldParsesText() {
        val updated = editor.withEditedText(Sample(nested = Nested("x")), "nested", """{"inner":"y"}""") as Sample
        assertEquals("y", updated.nested.inner)
    }

    @Test
    fun withEditedTextOnJsonFieldWithInvalidJsonIsRejected() {
        assertNull(editor.withEditedText(Sample(nested = Nested("x")), "nested", "{oops"))
    }

    @Test
    fun withEditedTextOnJsonFieldWithEmptyTextBecomesNull() {
        val updated = editor.withEditedText(Box(nested = Nested("x")), "nested", "") as Box
        assertNull(updated.nested)
    }

    @Test
    fun withEditedTextOnUnknownFieldIsRejected() {
        assertNull(editor.withEditedText(Sample(), "notAField", "x"))
    }

    // ---------- withBoolean ----------

    @Test
    fun withBooleanWritesPrimitive() {
        val updated = editor.withBoolean(Sample(enabled = true), "enabled", false) as Sample
        assertEquals(false, updated.enabled)
        assertEquals("a", updated.name)
    }

    @Test
    fun withBooleanOnNonObjectIsRejected() {
        assertNull(editor.withBoolean("plain", "enabled", true))
    }

    // ---------- 夹具 ----------

    private data class Sample(
        val name: String = "a",
        val count: Int = 3,
        val enabled: Boolean = true,
        val nested: Nested = Nested(),
        val tags: List<String> = emptyList(),
    )

    private data class Box(
        val count: Int? = 5,
        val nested: Nested? = Nested("x"),
    )

    private data class Nested(val inner: String = "x")
}
