package io.legado.app.model.analyzeRule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RuleFoundationTest {

    @Test
    fun `regex extraction retains full match and capture groups`() {
        assertEquals(
            listOf("book: Legado", "Legado"),
            AnalyzeByRegex.getElement("prefix book: Legado suffix", arrayOf("book: (\\w+)")),
        )
        assertEquals(
            listOf(listOf("1", "1"), listOf("2", "2")),
            AnalyzeByRegex.getElements("a1 b2", arrayOf("(\\d)")),
        )
    }

    @Test
    fun `regex extraction applies each rule to concatenated prior matches`() {
        // 第一条规则先筛出 "<item>Legado</item>"（丢弃 Reader 项），第二条规则再在筛后结果上匹配。
        // 返回 groupValues，group 0 是整段匹配，group 1 是捕获组。
        assertEquals(
            listOf(">Legado<", "Legado"),
            AnalyzeByRegex.getElement("<item>Legado</item><item>Reader</item>", arrayOf("<item>Legado</item>", ">(\\w+)<")),
        )
    }

    @Test
    fun `regex extraction returns null when the first rule does not match`() {
        assertNull(AnalyzeByRegex.getElement("nothing here", arrayOf("<item>(\\w+)</item>")))
    }

    @Test
    fun `rule analyzer ignores separators inside balanced selectors`() {
        assertEquals(
            arrayListOf("a", "b[?(@.name=='&&')]", "c"),
            RuleAnalyzer("a&&b[?(@.name=='&&')]&&c").splitRule("&&"),
        )
    }

    @Test
    fun `rule data keeps small values inline and delegates large values`() {
        val data = FakeRuleData()
        assertEquals(true, data.putVariable("small", "value"))
        assertEquals("value", data.variableMap["small"])
        assertNull(data.bigVariables["small"])

        assertEquals(false, data.putVariable("large", "x".repeat(10_000)))
        assertNull(data.variableMap["large"])
        assertEquals("x".repeat(10_000), data.bigVariables["large"])
        assertEquals("x".repeat(10_000), data.getVariable("large"))
    }

    private class FakeRuleData : RuleDataInterface {
        override val variableMap = hashMapOf<String, String>()
        val bigVariables = hashMapOf<String, String>()

        override fun putBigVariable(key: String, value: String?) {
            if (value == null) bigVariables.remove(key) else bigVariables[key] = value
        }

        override fun getBigVariable(key: String): String? = bigVariables[key]
    }
}
