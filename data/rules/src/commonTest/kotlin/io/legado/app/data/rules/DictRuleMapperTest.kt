package io.legado.app.data.rules

import io.legado.app.data.entities.DictRule as DictRuleEntity
import io.legado.app.domain.rules.DictRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * [DictRuleMapper] 的等价性基线（M3-3，样板见 M3-1 的 `ReplaceRuleMapperTest` /
 * M3-2 的 `HighlightTagRuleMapperTest`）。
 *
 * ⚠️ **不能**写成 `assertEquals(entity, domain.toEntity())`：实体与领域模型的
 * `equals`/`hashCode` 都**只按 `name` 判等**（那是主键，且 `DictRuleItemUi.id` /
 * `_selectedIds: Set<String>` 全链路上都拿它当 key），整对象比较在任何字段漏映射时都照样
 * 通过。本用例因此逐字段断言，并单独钉住默认值集合。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 */
class DictRuleMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = DictRuleEntity(
        name = "汉典",
        urlRule = "https://www.zdic.net/hans/\${key}",
        showRule = "$.content",
        enabled = false,
        sortNumber = -7,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.name, domain.name)
        assertEquals(entity.urlRule, domain.urlRule)
        assertEquals(entity.showRule, domain.showRule)
        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.sortNumber, domain.sortNumber)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = DictRule(
            name = "维基词典",
            urlRule = "https://zh.wiktionary.org/w/index.php?search=\${key}",
            showRule = "#mw-content-text",
            enabled = true,
            sortNumber = 42,
        )

        val entity = domain.toEntity()

        assertEquals(domain.name, entity.name)
        assertEquals(domain.urlRule, entity.urlRule)
        assertEquals(domain.showRule, entity.showRule)
        assertEquals(domain.enabled, entity.enabled)
        assertEquals(domain.sortNumber, entity.sortNumber)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.urlRule, roundTripped.urlRule)
        assertEquals(entity.showRule, roundTripped.showRule)
        assertEquals(entity.enabled, roundTripped.enabled)
        assertEquals(entity.sortNumber, roundTripped.sortNumber)
    }

    /**
     * 默认值集合必须与实体一致：新建规则（`DictRule()` 走 `name = ""`、`enabled = true`、
     * `sortNumber = 0`）的语义全靠它们。`name` 是主键且两边默认为空串，可以直接连同
     * 其余字段一起比。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = DictRule()
        val entity = DictRuleEntity()

        assertEquals(entity.name, domain.name)
        assertEquals(entity.urlRule, domain.urlRule)
        assertEquals(entity.showRule, domain.showRule)
        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.sortNumber, domain.sortNumber)

        // 显式钉住几个「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals("", domain.name)
        assertEquals("", domain.urlRule)
        assertEquals("", domain.showRule)
        assertEquals(true, domain.enabled)
        // ⚠️ 与 HighlightTagRule 的 name 主键不同：这里是空串主键，不是 systemTimeMillis()。
        assertEquals(0, domain.sortNumber)
    }

    /**
     * 判等只看主键 `name`——这是本类型**最容易踩**的语义（主键是 String 而不是 `id: Long`）：
     * 两条规则即使 `urlRule` / `showRule` / `enabled` / `sortNumber` 完全不同，只要 `name`
     * 相同就算同一个。反过来说，改 `urlRule` 而不改 `name` 时 `equals` 仍为真，
     * `DictRuleTransferSpec.hasChanged` 才要自己逐字段比（它确实是那么写的）。
     */
    @Test
    fun `equals 与 hashCode 只按 name 判等`() {
        val a = DictRule(name = "汉典", urlRule = "https://a.example")
        val b = DictRule(name = "汉典", urlRule = "https://b.example", enabled = false)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == DictRule(name = "维基词典", urlRule = "https://a.example"))
    }

    /**
     * 集合形态的三个重载（`toDomainList` / `Array.toEntityArray` / `List.toEntityArray`）
     * 也必须逐元素恒等——`flowAll` / `getEnabled` 走第一个，`insert` / `delete` / `update`
     * 的 `vararg` 走第二个，`moveOrder` 走第三个。一旦字段串位（例如把 `urlRule` 写进
     * `showRule`）只有这些用例能发现。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            DictRuleEntity(name = "甲典", urlRule = "https://a.example", showRule = "a", enabled = true, sortNumber = 1),
            DictRuleEntity(name = "乙典", urlRule = "https://b.example", showRule = "b", enabled = false, sortNumber = 2),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("甲典", "乙典"), domains.map { it.name })
        assertEquals(listOf("https://a.example", "https://b.example"), domains.map { it.urlRule })
        assertEquals(listOf("a", "b"), domains.map { it.showRule })
        assertEquals(listOf(true, false), domains.map { it.enabled })
        assertEquals(listOf(1, 2), domains.map { it.sortNumber })

        val back = domains.toEntityArray()
        assertEquals(listOf("甲典", "乙典"), back.map { it.name })
        assertEquals(listOf("https://a.example", "https://b.example"), back.map { it.urlRule })
        assertEquals(listOf(1, 2), back.map { it.sortNumber })

        // 第三条重载（`Array<out DictRule>`）只从 `insert` / `delete` / `update` 的 `vararg`
        // 进得来——函数体里的 `vararg` 是 `Array<out T>` 而不是 `List<T>`，两种形态都缺不得。
        val viaArray = domains.toTypedArray().toEntityArray()
        assertEquals(listOf("甲典", "乙典"), viaArray.map { it.name })
        assertEquals(listOf("a", "b"), viaArray.map { it.showRule })
        assertEquals(listOf(true, false), viaArray.map { it.enabled })
    }
}
