package io.legado.app.data.rules

import io.legado.app.data.entities.RuleSub as RuleSubEntity
import io.legado.app.data.entities.RuleSubType as RuleSubTypeEntity
import io.legado.app.domain.rules.RuleSub
import io.legado.app.domain.rules.RuleSubType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `RuleSubMapper` 的等价性基线（M3-5，样板见 M3-1 的 `ReplaceRuleMapperTest` /
 * M3-2 的 `HighlightTagRuleMapperTest` / M3-3 的 `DictRuleMapperTest` /
 * M3-4 的 `TxtTocRuleMapperTest`）。
 *
 * ⚠️ **本片与前四片有一处语义相反，写用例时不要"向样板看齐"**：
 * `RuleSub` 的实体是全仓六个规则实体里**唯一没有重写 `equals`** 的，领域模型照抄了这一点，
 * 所以判等是 data class 的**全字段**比较，而不是其余五片的「只按主键判等」。后果有两面：
 * - 本测试**可以**用整对象 `assertEquals(实体, 领域.toEntity())`——那已经覆盖了全部字段
 *   （前四片这么写会在漏映射时照样通过，所以它们被禁止这么写）。这里仍保留逐字段断言，
 *   只为失败信息更精确；
 * - 需要**一条反向用例**（`判等按全字段而非主键`）把「只改 `name` 的两条规则不相等」钉住，
 *   否则后来者很可能"对齐前四片"给它补一个 id-only 的 `equals`，让领域模型与实体的判等分叉。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 */
class RuleSubMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = RuleSubEntity(
        id = 1_700_000_000_123L,
        name = "铅笔小说",
        url = "https://www.example.com/rule.json",
        type = RuleSubTypeEntity.REPLACE_RULE,
        customOrder = 3,
        autoUpdate = true,
        update = 1_700_000_000_999L,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.url, domain.url)
        assertEquals(entity.type, domain.type)
        assertEquals(entity.customOrder, domain.customOrder)
        assertEquals(entity.autoUpdate, domain.autoUpdate)
        assertEquals(entity.update, domain.update)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = RuleSub(
            id = 1_700_000_000_456L,
            name = "某人的订阅源",
            url = "https://www.example.org/rss.json",
            type = RuleSubType.RSS_SOURCE,
            customOrder = 8,
            autoUpdate = false,
            update = 1_700_000_000_111L,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.url, entity.url)
        assertEquals(domain.type, entity.type)
        assertEquals(domain.customOrder, entity.customOrder)
        assertEquals(domain.autoUpdate, entity.autoUpdate)
        assertEquals(domain.update, entity.update)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.url, roundTripped.url)
        assertEquals(entity.type, roundTripped.type)
        assertEquals(entity.customOrder, roundTripped.customOrder)
        assertEquals(entity.autoUpdate, roundTripped.autoUpdate)
        assertEquals(entity.update, roundTripped.update)

        // 本片独有的强断言：两边都是全字段判等的 data class，整对象比较才成立。
        assertEquals(entity, roundTripped)
    }

    /**
     * 默认值集合必须与实体一致：新建订阅（`RuleSub(customOrder = 列表长度 + 1)`，
     * 见 `:app` 的 `RuleSubIntent.Add`）只显式传 `customOrder`，其余字段全靠默认值。
     *
     * 两边都显式传同一个 `id`，只比其余字段——`id` 与 `update` 的默认值都是
     * `systemTimeMillis()`，两次调用不保证落在同一毫秒。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = RuleSub(id = 1L)
        val entity = RuleSubEntity(id = 1L)

        assertEquals(entity.name, domain.name)
        assertEquals(entity.url, domain.url)
        assertEquals(entity.type, domain.type)
        assertEquals(entity.customOrder, domain.customOrder)
        assertEquals(entity.autoUpdate, domain.autoUpdate)

        // 显式钉住「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals("", domain.name)
        assertEquals("", domain.url)
        assertEquals(RuleSubTypeEntity.BOOK_SOURCE, domain.type)
        assertEquals(0, domain.customOrder)
        assertEquals(false, domain.autoUpdate)
    }

    /**
     * `id` 与 `update` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的 `expect fun`）。
     * 领域模型能引用它，是因为 `:domain:rules` 已经 `implementation(project(":core:platform"))`
     * ——pure 模块引用它不越界（G2 只拦 `android.*` / `java.io.*` / `kotlin.jvm.*` / 三方实现库）。
     *
     * 只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。
     */
    @Test
    fun `id 与 update 的默认值取当前毫秒`() {
        val domain = RuleSub()

        assertTrue(domain.id > 0L, "默认 id 应取 systemTimeMillis()，实际 ${domain.id}")
        assertTrue(domain.update > 0L, "默认 update 应取 systemTimeMillis()，实际 ${domain.update}")

        // 实体侧同样是这两个默认值——本片不改变新建语义。
        val entity = RuleSubEntity()
        assertTrue(entity.id > 0L)
        assertTrue(entity.update > 0L)
    }

    /**
     * ⚠️ **与前四片相反的一条**：判等是**全字段**比较，不是只按主键。
     *
     * `RuleSub` 的实体本来就没重写 `equals`（其余五个规则实体都重写了），领域模型照抄该语义。
     * 本用例同时钉住两侧，这样无论谁"顺手对齐前四片"给领域模型（或实体）补一个 id-only 的
     * `equals`，都会立刻变红。
     */
    @Test
    fun `判等按全字段而非主键`() {
        val a = RuleSub(id = 5L, name = "a", url = "https://a.example.com")
        val b = RuleSub(id = 5L, name = "b", url = "https://a.example.com")

        assertNotEquals(a, b, "领域模型只改 name、id 相同 ⇒ 按全字段判等应不相等")
        assertEquals(a, RuleSub(id = 5L, name = "a", url = "https://a.example.com"))

        // 实体侧同样必须是全字段判等——本片不改变实体的判等语义。
        assertNotEquals(
            RuleSubEntity(id = 5L, name = "a", url = "https://a.example.com"),
            RuleSubEntity(id = 5L, name = "b", url = "https://a.example.com"),
        )
    }

    /**
     * `RuleSubType` 在领域侧是**语义镜像**（实体那份被实体自己的 `type` 默认值引用，而
     * `:core:data` 不能反向依赖 `:domain:rules`，所以只能两份）。本用例是两边的同步护栏：
     * 任何一侧加了类型常量、或改了数值，都会在这里红。
     *
     * 数值本身是**持久化契约**（`RuleSub.type` 以 `Int` 落库，UI 侧 `R.array.rule_type`
     * 的下标也正是这四个值），所以连数字一起钉住。
     */
    @Test
    fun `RuleSubType 常量与实体侧镜像一致且数值固定`() {
        assertEquals(RuleSubTypeEntity.BOOK_SOURCE, RuleSubType.BOOK_SOURCE)
        assertEquals(RuleSubTypeEntity.RSS_SOURCE, RuleSubType.RSS_SOURCE)
        assertEquals(RuleSubTypeEntity.REPLACE_RULE, RuleSubType.REPLACE_RULE)
        assertEquals(RuleSubTypeEntity.AUTO, RuleSubType.AUTO)

        assertEquals(0, RuleSubType.BOOK_SOURCE)
        assertEquals(1, RuleSubType.RSS_SOURCE)
        assertEquals(2, RuleSubType.REPLACE_RULE)
        assertEquals(3, RuleSubType.AUTO)
    }

    /**
     * 集合形态的两个重载（`toDomainList` / `Array.toEntityArray`）也必须逐元素恒等——
     * `observeAll` / `all` 走第一个，`update` 的 `vararg` 走第二个。一旦字段串位（例如把
     * `customOrder` 写进 `type`）只有这些用例能发现。
     *
     * 本片没有第三个重载（`List<领域>` → 实体），那是 `saveOrder` 专用的，本域没有保存排序
     * 的方法。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            RuleSubEntity(
                id = 1L,
                name = "甲订阅",
                url = "https://a.example.com/rule.json",
                type = RuleSubTypeEntity.BOOK_SOURCE,
                customOrder = 1,
                autoUpdate = false,
                update = 1_700_000_000_001L,
            ),
            RuleSubEntity(
                id = 2L,
                name = "乙订阅",
                url = "https://b.example.com/rule.json",
                type = RuleSubTypeEntity.AUTO,
                customOrder = 2,
                autoUpdate = true,
                update = 1_700_000_000_002L,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf(1L, 2L), domains.map { it.id })
        assertEquals(listOf("甲订阅", "乙订阅"), domains.map { it.name })
        assertEquals(
            listOf("https://a.example.com/rule.json", "https://b.example.com/rule.json"),
            domains.map { it.url },
        )
        assertEquals(listOf(RuleSubType.BOOK_SOURCE, RuleSubType.AUTO), domains.map { it.type })
        assertEquals(listOf(1, 2), domains.map { it.customOrder })
        assertEquals(listOf(false, true), domains.map { it.autoUpdate })
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_002L), domains.map { it.update })

        // 第二条重载（`Array<out RuleSub>`）只从 `update` 的 `vararg` 进得来——函数体里的
        // `vararg` 是 `Array<out T>` 而不是 `List<T>`。
        val viaArray = domains.toTypedArray().toEntityArray()

        assertEquals(listOf(1L, 2L), viaArray.map { it.id })
        assertEquals(listOf("甲订阅", "乙订阅"), viaArray.map { it.name })
        assertEquals(listOf(1, 2), viaArray.map { it.customOrder })
        assertEquals(listOf(false, true), viaArray.map { it.autoUpdate })

        // 集合往返整体无损（同样因为两侧都是全字段判等）。
        assertEquals(entities, viaArray.toList())
    }
}
