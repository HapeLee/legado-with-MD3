package io.legado.app.data.rules

import io.legado.app.data.entities.TagGroupRule as TagGroupRuleEntity
import io.legado.app.domain.rules.TagGroupRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `TagGroupRuleMapper` 的等价性基线（M3-6，样板见 M3-1 的 `ReplaceRuleMapperTest` /
 * M3-2 的 `HighlightTagRuleMapperTest` / M3-3 的 `DictRuleMapperTest` /
 * M3-4 的 `TxtTocRuleMapperTest` / M3-5 的 `RuleSubMapperTest`）。
 *
 * ⚠️ **不能**写成 `assertEquals(entity, domain.toEntity())`：实体与领域模型的 `equals` /
 * `hashCode` 都**只按 `id` 判等**（`TagGroupRuleItemUi.rule` 与 `_selectedIds: Set<Long>`
 * 全链路上混用它，拖拽排序的 key 也是 id），整对象比较在任何非 `id` 字段漏映射时都照样通过。
 * 本用例因此逐字段断言，并单独钉住默认值集合。
 *
 * ⚠️ 这是**与 M3-5 相反**的一侧：`RuleSub` 两侧都是全字段判等的 data class，那一片才可以用
 * 整对象断言、并需要一条"判等是全字段"的反向用例。本片恰好是「只按主键判等」那一族
 * （六个规则实体里的另外五个），既不能用整对象断言，也不需要反向用例。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 */
class TagGroupRuleMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = TagGroupRuleEntity(
        id = 1_700_000_000_123L,
        pattern = "^玄幻|^奇幻",
        groupName = "幻想",
        order = 3,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.groupName, domain.groupName)
        assertEquals(entity.order, domain.order)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = TagGroupRule(
            id = 1_700_000_000_456L,
            pattern = "^都市",
            groupName = "现代",
            order = 8,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.pattern, entity.pattern)
        assertEquals(domain.groupName, entity.groupName)
        assertEquals(domain.order, entity.order)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.pattern, roundTripped.pattern)
        assertEquals(entity.groupName, roundTripped.groupName)
        assertEquals(entity.order, roundTripped.order)
    }

    /**
     * 默认值集合必须与实体一致：`TagGroupRuleEditSheet` 用 `TagGroupRule()` 造新规则
     * （只显式传 `pattern` / `groupName`，`order` 靠默认值），`TagGroupRuleViewModel.pasteRule`
     * 与导入路径则整对象来自 Gson。两边都显式传同一个 `id`，只比其余字段——`id` 的默认值
     * 不可能相等（两次 `systemTimeMillis()` 不保证同毫秒）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = TagGroupRule(id = 1L)
        val entity = TagGroupRuleEntity(id = 1L)

        assertEquals(entity.pattern, domain.pattern)
        assertEquals(entity.groupName, domain.groupName)
        assertEquals(entity.order, domain.order)

        // 显式钉住「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals("", domain.pattern)
        assertEquals("", domain.groupName)
        assertEquals(0, domain.order)
    }

    /**
     * `id` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的 `expect fun`）。领域模型能
     * 引用它，是因为 `:domain:rules` 已经 `implementation(project(":core:platform"))`——pure
     * 模块引用它不越界（G2 只拦 `android.*` / `java.io.*` / `kotlin.jvm.*` / 三方实现库）。
     *
     * 只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。
     */
    @Test
    fun `id 的默认值取当前毫秒`() {
        val domain = TagGroupRule()

        assertTrue(domain.id > 0L, "默认 id 应取 systemTimeMillis()，实际 ${domain.id}")

        // 实体侧同样是这个默认值——本片不改变新建语义。
        assertTrue(TagGroupRuleEntity().id > 0L)
    }

    /**
     * 判等只看主键 `id`——两条规则即使 `pattern` / `groupName` / `order` 全都不同，只要 `id`
     * 相同就算同一个。反过来说，改 `pattern` 而不改 `id` 时 `equals` 仍为真，所以
     * `TagGroupRuleTransferSpec.hasChanged` 才要自己逐字段比（它确实是那么写的：只比
     * `groupName` 与 `pattern`）。
     *
     * 本用例同时钉住两侧：如果谁"顺手对齐 M3-5"把任一侧改成全字段判等，这里会立刻变红。
     */
    @Test
    fun `equals 与 hashCode 只按 id 判等`() {
        val a = TagGroupRule(id = 5L, pattern = "^甲", groupName = "甲组", order = 1)
        val b = TagGroupRule(id = 5L, pattern = "^乙", groupName = "乙组", order = 9)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == TagGroupRule(id = 6L, pattern = "^甲", groupName = "甲组", order = 1))

        // 实体侧同样必须是 id-only 判等——本片不改变实体的判等语义。
        assertEquals(
            TagGroupRuleEntity(id = 5L, pattern = "^甲", groupName = "甲组", order = 1),
            TagGroupRuleEntity(id = 5L, pattern = "^乙", groupName = "乙组", order = 9),
        )
    }

    /**
     * 集合形态的三个重载（`toDomainList` / `Array.toEntityArray` / `List.toEntityArray`）
     * 也必须逐元素恒等——`flowAll` 走第一个，`insert` / `update` / `delete` 的 `vararg`
     * 走第二个，`moveOrder` 走第三个。一旦字段串位（例如把 `pattern` 写进 `groupName`）
     * 只有这些用例能发现。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            TagGroupRuleEntity(
                id = 1L,
                pattern = "^玄幻",
                groupName = "幻想",
                order = 0,
            ),
            TagGroupRuleEntity(
                id = 2L,
                pattern = "^都市",
                groupName = "现代",
                order = 1,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf(1L, 2L), domains.map { it.id })
        assertEquals(listOf("^玄幻", "^都市"), domains.map { it.pattern })
        assertEquals(listOf("幻想", "现代"), domains.map { it.groupName })
        assertEquals(listOf(0, 1), domains.map { it.order })

        // 第二条重载（`Array<out TagGroupRule>`）只从 `insert` / `update` / `delete` 的
        // `vararg` 进得来——函数体里的 `vararg` 是 `Array<out T>` 而不是 `List<T>`。
        val viaArray = domains.toTypedArray().toEntityArray()

        assertEquals(listOf(1L, 2L), viaArray.map { it.id })
        assertEquals(listOf("^玄幻", "^都市"), viaArray.map { it.pattern })
        assertEquals(listOf("幻想", "现代"), viaArray.map { it.groupName })
        assertEquals(listOf(0, 1), viaArray.map { it.order })

        // 第三条重载（`List<TagGroupRule>`）只从 `moveOrder` 的 `List` 参数进得来。
        val viaList = domains.toEntityArray()

        assertEquals(listOf(1L, 2L), viaList.map { it.id })
        assertEquals(listOf("^玄幻", "^都市"), viaList.map { it.pattern })
        assertEquals(listOf("幻想", "现代"), viaList.map { it.groupName })
    }
}
