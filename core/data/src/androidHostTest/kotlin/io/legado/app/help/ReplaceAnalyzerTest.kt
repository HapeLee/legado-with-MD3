package io.legado.app.help

import io.legado.app.exception.NoStackTraceException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReplaceAnalyzer] 的行为基线。
 *
 * 本类随第十片的 `replaceRules` 前置工作从 `:app` 下沉到 `:core:data/androidMain`，
 * 断言逐条对应迁移前既有实现的两条分支：
 *   1. **标准分支**——`GSON.fromJsonObject` 能解出非空 `pattern`，直接返回；
 *   2. **旧格式分支**——解不出或 `pattern` 为空时退回 JSONPath 读
 *      `$.regex` / `$.replaceSummary` / `$.replacement` / `$.isRegex` / `$.useTo` /
 *      `$.enable` / `$.serialNumber` / `$.id`（旧备份兼容路径，`pattern` 仍为空则抛
 *      [NoStackTraceException]）。
 * 数组入口 `jsonToReplaceRules` 再按 `ReplaceRule.isValid()` 过滤掉正则非法的条目，
 * 但**单条解析失败会让它整体失败**（见 [jsonToReplaceRulesFailsWhenAnyEntryLacksPattern]）。
 *
 * 改这里前请先读 [ReplaceAnalyzer] 源码——它是替换规则导入导出的唯一入口，
 * 书源/备份文件的字段兼容全靠这两条分支。
 */
class ReplaceAnalyzerTest {

    /** 标准分支：JSON 含非空 `pattern`，走 Gson 直接反序列化。 */
    @Test
    fun standardJsonParsesViaGson() {
        val json = """
            {"name":"广告","pattern":"第.{1,3}章","replacement":"","isRegex":true,"isEnabled":true,"scope":"content","order":5}
        """.trimIndent()

        val rule = ReplaceAnalyzer.jsonToReplaceRule(json).getOrThrow()

        assertEquals("第.{1,3}章", rule.pattern)
        assertEquals("广告", rule.name)
        assertTrue(rule.isRegex)
        assertTrue(rule.isEnabled)
        assertEquals("content", rule.scope)
        assertEquals(5, rule.order)
    }

    /** 旧格式分支：无 `pattern`，退回 JSONPath 读旧键名。 */
    @Test
    fun legacyJsonFallsBackToJsonPath() {
        val json = """
            {"id":123,"regex":"abc","replaceSummary":"摘要","replacement":"xyz","isRegex":false,"useTo":"title","enable":true,"serialNumber":7}
        """.trimIndent()

        val rule = ReplaceAnalyzer.jsonToReplaceRule(json).getOrThrow()

        assertEquals(123L, rule.id)
        assertEquals("abc", rule.pattern)
        assertEquals("摘要", rule.name)
        assertEquals("xyz", rule.replacement)
        assertFalse(rule.isRegex)
        assertEquals("title", rule.scope)
        assertTrue(rule.isEnabled)
        assertEquals(7, rule.order)
    }

    /** 旧格式缺 `id` 时不失败（回落到当前时间戳）。 */
    @Test
    fun legacyJsonWithoutIdStillSucceeds() {
        val json = """{"regex":"abc"}"""

        val rule = ReplaceAnalyzer.jsonToReplaceRule(json).getOrThrow()

        assertEquals("abc", rule.pattern)
    }

    /** 两条分支都拿不到 `pattern` → 抛 [NoStackTraceException]（不是普通 Exception）。 */
    @Test
    fun emptyPatternThrowsNoStackTraceException() {
        val json = """{"regex":""}"""

        assertFailsWith<NoStackTraceException> {
            ReplaceAnalyzer.jsonToReplaceRule(json).getOrThrow()
        }
    }

    /** 数组入口：全部条目有效时按原序返回。 */
    @Test
    fun jsonToReplaceRulesParsesAllValidEntries() {
        val json = """
            [{"pattern":"a","isRegex":false},{"pattern":"b","isRegex":false},{"pattern":"c","isRegex":false}]
        """.trimIndent()

        val rules = ReplaceAnalyzer.jsonToReplaceRules(json).getOrThrow()

        assertEquals(3, rules.size)
        assertEquals("a", rules[0].pattern)
        assertEquals("b", rules[1].pattern)
        assertEquals("c", rules[2].pattern)
    }

    /**
     * **既有语义（全有或全无）**：数组里只要有一条拿不到 `pattern`，
     * `jsonToReplaceRules` 就整体失败，而不是跳过该条。
     *
     * 因为循环体是 `jsonToReplaceRule(...).getOrThrow()`——单条解析出
     * [NoStackTraceException] 时直接抛出，被外层 `runCatching` 捕获成整体 failure。
     * 这条断言是为了**钉住现状**：若将来改成「跳过无效条目继续解析」，这里会红，
     * 提醒你这是一次有意的行为变更（导入旧备份时会从「全部拒绝」变成「部分导入」）。
     */
    @Test
    fun jsonToReplaceRulesFailsWhenAnyEntryLacksPattern() {
        val json = """
            [{"pattern":"a","isRegex":false},{"pattern":"","isRegex":false}]
        """.trimIndent()

        assertFailsWith<NoStackTraceException> {
            ReplaceAnalyzer.jsonToReplaceRules(json).getOrThrow()
        }
    }

    /** `isValid()` 的正则保护：非法正则即使 `isRegex=true` 也被过滤，不会进库。 */
    @Test
    fun jsonToReplaceRulesDropsInvalidRegex() {
        val json = """[{"pattern":"[","isRegex":true}]"""

        val rules = ReplaceAnalyzer.jsonToReplaceRules(json).getOrThrow()

        assertTrue(rules.isEmpty())
    }

    /** 输入不是 JSON 时返回 failure，不抛异常（调用方按 Result 处理）。 */
    @Test
    fun malformedJsonReturnsFailure() {
        assertTrue(ReplaceAnalyzer.jsonToReplaceRule("not a json").isFailure)
    }
}
