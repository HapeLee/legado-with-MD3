package io.legado.app.data.rules

import io.legado.app.data.entities.ReplaceRule as ReplaceRuleEntity
import io.legado.app.domain.rules.ReplaceRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [ReplaceRuleMapper] 的等价性基线（M3-1）。
 *
 * ⚠️ **不能**写成 `assertEquals(entity, domain.toEntity())`：实体与领域模型的
 * `equals`/`hashCode` 都**只按 `id` 判等**（UI 层依赖这一点避免无谓重组），整对象比较
 * 在任何字段漏映射时都照样通过。本用例因此逐字段断言，并单独钉住默认值集合。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 */
class ReplaceRuleMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = ReplaceRuleEntity(
        id = 1_700_000_000_123L,
        name = "去除广告与推广",
        group = "净化,常用",
        pattern = "广告|推荐语",
        replacement = "【已过滤】",
        scope = "某本书",
        scopeTitle = true,
        scopeContent = false,
        excludeScope = "正文附录",
        isEnabled = false,
        isRegex = false,
        timeoutMillisecond = 500L,
        order = -7,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.group, domain.group)
        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.replacement, domain.replacement)
        assertEquals(entity.scope, domain.scope)
        assertEquals(entity.scopeTitle, domain.scopeTitle)
        assertEquals(entity.scopeContent, domain.scopeContent)
        assertEquals(entity.excludeScope, domain.excludeScope)
        assertEquals(entity.isEnabled, domain.isEnabled)
        assertEquals(entity.isRegex, domain.isRegex)
        assertEquals(entity.timeoutMillisecond, domain.timeoutMillisecond)
        assertEquals(entity.order, domain.order)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = ReplaceRule(
            id = 1_700_000_000_456L,
            name = "繁简转换",
            group = "转换",
            pattern = "後",
            replacement = "后",
            scope = "全局",
            scopeTitle = false,
            scopeContent = true,
            excludeScope = "代码块",
            isEnabled = true,
            isRegex = false,
            timeoutMillisecond = 1234L,
            order = 42,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.group, entity.group)
        assertEquals(domain.pattern, entity.pattern)
        assertEquals(domain.replacement, entity.replacement)
        assertEquals(domain.scope, entity.scope)
        assertEquals(domain.scopeTitle, entity.scopeTitle)
        assertEquals(domain.scopeContent, entity.scopeContent)
        assertEquals(domain.excludeScope, entity.excludeScope)
        assertEquals(domain.isEnabled, entity.isEnabled)
        assertEquals(domain.isRegex, entity.isRegex)
        assertEquals(domain.timeoutMillisecond, entity.timeoutMillisecond)
        assertEquals(domain.order, entity.order)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.group, roundTripped.group)
        assertEquals(entity.pattern, roundTripped.pattern)
        assertEquals(entity.replacement, roundTripped.replacement)
        assertEquals(entity.scope, roundTripped.scope)
        assertEquals(entity.scopeTitle, roundTripped.scopeTitle)
        assertEquals(entity.scopeContent, roundTripped.scopeContent)
        assertEquals(entity.excludeScope, roundTripped.excludeScope)
        assertEquals(entity.isEnabled, roundTripped.isEnabled)
        assertEquals(entity.isRegex, roundTripped.isRegex)
        assertEquals(entity.timeoutMillisecond, roundTripped.timeoutMillisecond)
        assertEquals(entity.order, roundTripped.order)
    }

    /**
     * 默认值集合必须与实体一致：新建规则（`ReplaceRule()` 走 `systemTimeMillis()` 生成 id、
     * `scopeContent = true`、`order = Int.MIN_VALUE` 等）的语义全靠它们。两边都显式传同一个
     * `id`，只比其余字段——`id` 的默认值不可能相等（两次 `systemTimeMillis()` 不保证同毫秒）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = ReplaceRule(id = 1L)
        val entity = ReplaceRuleEntity(id = 1L)

        assertEquals(entity.name, domain.name)
        assertEquals(entity.group, domain.group)
        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.replacement, domain.replacement)
        assertEquals(entity.scope, domain.scope)
        assertEquals(entity.scopeTitle, domain.scopeTitle)
        assertEquals(entity.scopeContent, domain.scopeContent)
        assertEquals(entity.excludeScope, domain.excludeScope)
        assertEquals(entity.isEnabled, domain.isEnabled)
        assertEquals(entity.isRegex, domain.isRegex)
        assertEquals(entity.timeoutMillisecond, domain.timeoutMillisecond)
        assertEquals(entity.order, domain.order)

        // 显式钉住几个「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals(true, domain.scopeContent)
        assertEquals(false, domain.scopeTitle)
        assertEquals(3000L, domain.timeoutMillisecond)
        assertEquals(Int.MIN_VALUE, domain.order)
    }

    @Test
    fun `equals 与 hashCode 只按 id 判等`() {
        val a = ReplaceRule(id = 5L, name = "a")
        val b = ReplaceRule(id = 5L, name = "b")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == ReplaceRule(id = 6L, name = "a"))
    }

    @Test
    fun `isValid 与实体行为一致`() {
        // isRegex = true：空 pattern、非法正则、结尾悬空 `|` 都非法；其余合法。
        listOf("", "abc", "a|b", "(", "a|", """a\|""").forEach { pattern ->
            val entity = ReplaceRuleEntity(id = 1L, pattern = pattern, isRegex = true)
            val domain = ReplaceRule(id = 1L, pattern = pattern, isRegex = true)
            assertEquals(
                entity.isValid(),
                domain.isValid(),
                "pattern=$pattern（isRegex=true）的 isValid 结果与实体不一致",
            )
        }

        // isRegex = false：只看 pattern 是否为空，`(` 这类非法正则也能过。
        listOf("", "(", "abc").forEach { pattern ->
            val entity = ReplaceRuleEntity(id = 1L, pattern = pattern, isRegex = false)
            val domain = ReplaceRule(id = 1L, pattern = pattern, isRegex = false)
            assertEquals(
                entity.isValid(),
                domain.isValid(),
                "pattern=$pattern（isRegex=false）的 isValid 结果与实体不一致",
            )
        }

        // 再显式钉住行为本身——只比"两边相等"的话，两边一起改错也照样绿。
        assertFalse(ReplaceRule(id = 1L, pattern = "").isValid())
        // `isRegex` 默认 true，"abc" 是合法正则 ⇒ 合法（不是"普通串一律放过"）。
        assertTrue(ReplaceRule(id = 1L, pattern = "abc").isValid())
        assertFalse(ReplaceRule(id = 1L, pattern = "(", isRegex = true).isValid())
        assertTrue(ReplaceRule(id = 1L, pattern = "(", isRegex = false).isValid())
        assertFalse(ReplaceRule(id = 1L, pattern = "a|", isRegex = true).isValid())
        assertTrue(ReplaceRule(id = 1L, pattern = """a\|""", isRegex = true).isValid())
    }

    @Test
    fun `getValidTimeoutMillisecond 与实体行为一致`() {
        listOf(-1L, 0L, 1L, 3000L, 5000L).forEach { timeout ->
            val entity = ReplaceRuleEntity(id = 1L, timeoutMillisecond = timeout)
            val domain = ReplaceRule(id = 1L, timeoutMillisecond = timeout)
            assertEquals(
                entity.getValidTimeoutMillisecond(),
                domain.getValidTimeoutMillisecond(),
                "timeoutMillisecond=$timeout 的回落结果与实体不一致",
            )
        }
        assertEquals(3000L, ReplaceRule(id = 1L, timeoutMillisecond = 0L).getValidTimeoutMillisecond())
    }
}
