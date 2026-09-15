package io.legado.app.data.rules

import io.legado.app.data.entities.HighlightTagRule as HighlightTagRuleEntity
import io.legado.app.domain.rules.HighlightTagRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [HighlightTagRuleMapper] 的等价性基线（M3-2，样板见 M3-1 的 `ReplaceRuleMapperTest`）。
 *
 * ⚠️ **不能**写成 `assertEquals(entity, domain.toEntity())`：实体与领域模型的
 * `equals`/`hashCode` 都**只按 `id` 判等**（`HighlightTagRuleItemUi.rule` 与列表 key 依赖
 * 这一点避免无谓重组），整对象比较在任何字段漏映射时都照样通过。本用例因此逐字段断言，
 * 并单独钉住默认值集合。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 */
class HighlightTagRuleMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = HighlightTagRuleEntity(
        id = 1_700_000_000_123L,
        title = "重点标记",
        pattern = "第.+章",
        enabled = false,
        order = -7,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.order, domain.order)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = HighlightTagRule(
            id = 1_700_000_000_456L,
            title = "卷标",
            pattern = "^(上|下)册$",
            enabled = true,
            order = 42,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.pattern, entity.pattern)
        assertEquals(domain.enabled, entity.enabled)
        assertEquals(domain.order, entity.order)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.title, roundTripped.title)
        assertEquals(entity.pattern, roundTripped.pattern)
        assertEquals(entity.enabled, roundTripped.enabled)
        assertEquals(entity.order, roundTripped.order)
    }

    /**
     * 默认值集合必须与实体一致：新建规则（`HighlightTagRule()` 走 `systemTimeMillis()` 生成 id、
     * `enabled = true`、`order = 0`）的语义全靠它们。两边都显式传同一个 `id`，只比其余字段
     * ——`id` 的默认值不可能相等（两次 `systemTimeMillis()` 不保证同毫秒）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = HighlightTagRule(id = 1L)
        val entity = HighlightTagRuleEntity(id = 1L)

        assertEquals(entity.title, domain.title)
        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.order, domain.order)

        // 显式钉住几个「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals("", domain.title)
        assertEquals("", domain.pattern)
        assertEquals(true, domain.enabled)
        // ⚠️ 与 ReplaceRule 不同：这里的 order 默认是 0，不是 Int.MIN_VALUE。
        assertEquals(0, domain.order)
    }

    @Test
    fun `equals 与 hashCode 只按 id 判等`() {
        val a = HighlightTagRule(id = 5L, title = "a")
        val b = HighlightTagRule(id = 5L, title = "b")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == HighlightTagRule(id = 6L, title = "a"))
    }

    /**
     * 集合形态的两个重载（`toDomainList` / `toEntityArray`）也必须逐元素恒等——
     * `flowAll` / `getEnabled` 走前者，`insert` / `update` / `moveOrder` 走后者，
     * 一旦写反（例如把 index 当 order）只有这些用例能发现。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            HighlightTagRuleEntity(id = 1L, title = "甲", pattern = "a", enabled = true, order = 0),
            HighlightTagRuleEntity(id = 2L, title = "乙", pattern = "b", enabled = false, order = 3),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf(1L, 2L), domains.map { it.id })
        assertEquals(listOf("甲", "乙"), domains.map { it.title })
        assertEquals(listOf("a", "b"), domains.map { it.pattern })
        assertEquals(listOf(true, false), domains.map { it.enabled })
        assertEquals(listOf(0, 3), domains.map { it.order })

        val back = domains.toEntityArray()
        assertEquals(listOf(1L, 2L), back.map { it.id })
        assertEquals(listOf(0, 3), back.map { it.order })
    }
}
