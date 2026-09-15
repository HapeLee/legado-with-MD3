package io.legado.app.data.rules

import io.legado.app.data.entities.TxtTocRule as TxtTocRuleEntity
import io.legado.app.domain.rules.TxtTocRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * [TxtTocRuleMapper] 的等价性基线（M3-4，样板见 M3-1 的 `ReplaceRuleMapperTest` /
 * M3-2 的 `HighlightTagRuleMapperTest` / M3-3 的 `DictRuleMapperTest`）。
 *
 * ⚠️ **不能**写成 `assertEquals(entity, domain.toEntity())`：实体与领域模型的
 * `equals`/`hashCode` 都**只按 `id` 判等**（`TxtTocRuleItemUi.rule` 与
 * `_selectedIds: Set<Long>` 全链路上都拿它当 key，拖拽排序的 key 也是），整对象比较在任何
 * 字段漏映射时都照样通过。本用例因此逐字段断言，并单独钉住默认值集合与可空字段。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，这里的默认值用例会红。
 *
 * ⚠️ 本类型比前四片多两个**容易被"顺手改好"**的点，这里各有一条用例钉住：
 * - [TxtTocRule.serialNumber] 默认 **`-1`**（不是 0）；
 * - [TxtTocRule.example] **可空**，`null` 不得被归一成 `""`。
 */
class TxtTocRuleMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = TxtTocRuleEntity(
        id = 1_700_000_000_123L,
        name = "轻小说",
        chapterRule = "^第\\d+话",
        volumeRule = "^第\\d+卷",
        example = "第一章 起点",
        serialNumber = 7,
        enable = false,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.chapterRule, domain.chapterRule)
        assertEquals(entity.volumeRule, domain.volumeRule)
        assertEquals(entity.example, domain.example)
        assertEquals(entity.serialNumber, domain.serialNumber)
        assertEquals(entity.enable, domain.enable)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = TxtTocRule(
            id = 1_700_000_000_456L,
            name = "出版书",
            chapterRule = "^\\s*第[一二三四五六七八九十]+章",
            volumeRule = "^\\s*第[一二三四五六七八九十]+卷",
            example = "第一章 楔子",
            serialNumber = 42,
            enable = true,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.chapterRule, entity.chapterRule)
        assertEquals(domain.volumeRule, entity.volumeRule)
        assertEquals(domain.example, entity.example)
        assertEquals(domain.serialNumber, entity.serialNumber)
        assertEquals(domain.enable, entity.enable)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.chapterRule, roundTripped.chapterRule)
        assertEquals(entity.volumeRule, roundTripped.volumeRule)
        assertEquals(entity.example, roundTripped.example)
        assertEquals(entity.serialNumber, roundTripped.serialNumber)
        assertEquals(entity.enable, roundTripped.enable)
    }

    /**
     * `example` 是**可空**字段：`null` 必须原样穿过映射，**不得**归一成 `""`。
     *
     * 为什么单独一条：备份文件里"缺 `example` 键"与"`example: ""`"是两种不同的写法
     * （`Backup.kt` 直接把实体交给 GSON 序列化，缺省 `null` 的字段会按 Gson 的默认行为写出），
     * 而 `TxtTocRuleItemUi.example` 的展示兜底是 `example ?: ""`——归一化会把这个兜底变成
     * 永远不会走到的死代码，也会让"往返无损"在 `null` 输入上失效。
     */
    @Test
    fun `example 的 null 原样穿过映射`() {
        val entity = TxtTocRuleEntity(id = 9L, name = "无示例", chapterRule = "^章")

        val domain = entity.toDomain()

        assertNull(domain.example)
        assertNull(domain.toEntity().example)

        // 反向也要成立：领域模型的 `null` 不会在落库前被偷偷换成空串。
        assertNull(TxtTocRule(id = 9L, chapterRule = "^章").toEntity().example)
    }

    /**
     * 默认值集合必须与实体一致：新建规则（`TxtTocRule()` 走 `systemTimeMillis()` 生成 id、
     * `serialNumber = -1`、`enable = true`、`example = null`）的语义全靠它们。两边都显式传
     * 同一个 `id`，只比其余字段——`id` 的默认值不可能相等（两次 `systemTimeMillis()` 不保证
     * 同毫秒）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = TxtTocRule(id = 1L)
        val entity = TxtTocRuleEntity(id = 1L)

        assertEquals(entity.name, domain.name)
        assertEquals(entity.chapterRule, domain.chapterRule)
        assertEquals(entity.volumeRule, domain.volumeRule)
        assertEquals(entity.example, domain.example)
        assertEquals(entity.serialNumber, domain.serialNumber)
        assertEquals(entity.enable, domain.enable)

        // 显式钉住几个「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals("", domain.name)
        assertEquals("", domain.chapterRule)
        assertEquals("", domain.volumeRule)
        assertNull(domain.example)
        // ⚠️ 不是 0：迁移前 DAO 用 `select ifNull(min(serialNumber), 0)` 表达"没有规则"，
        // `-1` 是"尚未排序"的既有标记（`HighlightTagRule.order` / `DictRule.sortNumber`
        // 的默认值都是 0，三片不要互相"对齐"）。
        assertEquals(-1, domain.serialNumber)
        // ⚠️ 字段名是 `enable`，不是 `enabled`（`HighlightTagRule` 那边才叫 `enabled`）。
        assertEquals(true, domain.enable)
    }

    /**
     * 判等只看主键 `id`——两条规则即使 `name` / `chapterRule` / `volumeRule` / `example` /
     * `serialNumber` / `enable` 全都不同，只要 `id` 相同就算同一个。反过来说，改 `chapterRule`
     * 而不改 `id` 时 `equals` 仍为真，所以
     * `TxtTocRuleTransferSpec.hasChanged` 才要自己逐字段比（它确实是那么写的）。
     */
    @Test
    fun `equals 与 hashCode 只按 id 判等`() {
        val a = TxtTocRule(id = 5L, name = "a", chapterRule = "^甲")
        val b = TxtTocRule(id = 5L, name = "b", chapterRule = "^乙", enable = false)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == TxtTocRule(id = 6L, name = "a", chapterRule = "^甲"))
    }

    /**
     * 集合形态的三个重载（`toDomainList` / `Array.toEntityArray` / `List.toEntityArray`）
     * 也必须逐元素恒等——`flowAll` / `all` 走第一个，`insert` / `update` / `delete` 的
     * `vararg` 走第二个，`saveOrder` 走第三个。一旦字段串位（例如把 `chapterRule` 写进
     * `volumeRule`）只有这些用例能发现。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            TxtTocRuleEntity(
                id = 1L,
                name = "甲规",
                chapterRule = "^甲章",
                volumeRule = "^甲卷",
                example = "甲示例",
                serialNumber = 1,
                enable = true,
            ),
            TxtTocRuleEntity(
                id = 2L,
                name = "乙规",
                chapterRule = "^乙章",
                volumeRule = "^乙卷",
                example = null,
                serialNumber = 2,
                enable = false,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf(1L, 2L), domains.map { it.id })
        assertEquals(listOf("甲规", "乙规"), domains.map { it.name })
        assertEquals(listOf("^甲章", "^乙章"), domains.map { it.chapterRule })
        assertEquals(listOf("^甲卷", "^乙卷"), domains.map { it.volumeRule })
        assertEquals(listOf("甲示例", null), domains.map { it.example })
        assertEquals(listOf(1, 2), domains.map { it.serialNumber })
        assertEquals(listOf(true, false), domains.map { it.enable })

        val back = domains.toEntityArray()
        assertEquals(listOf(1L, 2L), back.map { it.id })
        assertEquals(listOf("^甲章", "^乙章"), back.map { it.chapterRule })
        assertEquals(listOf("^甲卷", "^乙卷"), back.map { it.volumeRule })
        assertEquals(listOf("甲示例", null), back.map { it.example })

        // 第三条重载（`Array<out TxtTocRule>`）只从 `insert` / `update` / `delete` 的
        // `vararg` 进得来——函数体里的 `vararg` 是 `Array<out T>` 而不是 `List<T>`，
        // 两种形态都缺不得。
        val viaArray = domains.toTypedArray().toEntityArray()
        assertEquals(listOf(1L, 2L), viaArray.map { it.id })
        assertEquals(listOf("甲规", "乙规"), viaArray.map { it.name })
        assertEquals(listOf(1, 2), viaArray.map { it.serialNumber })
        assertEquals(listOf(true, false), viaArray.map { it.enable })
    }
}
