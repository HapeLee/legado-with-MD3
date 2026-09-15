package io.legado.app.data.ai

import io.legado.app.data.entities.AiPromptPreset as AiPromptPresetEntity
import io.legado.app.domain.ai.AiPromptPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `AiPromptPresetMapper` 的等价性基线（M4-1；样板见 M3-1 的 `ReplaceRuleMapperTest`，
 * 同侧参照 M3-5 的 `RuleSubMapperTest`）。
 *
 * ⚠️ **本片在"全字段判等"的一侧，与上一片（M3-6 `TagGroupRule`）相反**：实体与领域模型
 * **都没有**重写 `equals` / `hashCode`，判等是 data class 的全字段比较。后果有两面：
 * - 本测试**可以**用整对象 `assertEquals(实体, 领域.toEntity())`——它已覆盖全部字段
 *   （M3-6 那种只按主键判等的域明令禁止这么写，漏映射照样通过）。这里仍保留逐字段断言，
 *   只为失败信息更精确；
 * - 需要一条**反向用例**（[`判等按全字段而非主键`]）钉住「仅 `name` 不同、`id` 相同的两条预设
 *   不相等」，否则后来者很可能"对齐上一片"补一个 id-only 的 `equals`，让两侧语义分叉。
 *
 * 用例本身也是「字段集合一致」的活文档：实体加字段而映射器没跟上时，默认值用例会红。
 */
class AiPromptPresetMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = AiPromptPresetEntity(
        id = "preset_polish",
        taskType = "REWRITE_TEXT",
        name = "润色",
        instruction = "请润色这段文字，使其更通顺。",
        enabled = false,
        builtIn = true,
        sortNumber = 7,
        createdAt = 1_700_000_000_123L,
        updatedAt = 1_700_000_000_999L,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.taskType, domain.taskType)
        assertEquals(entity.name, domain.name)
        assertEquals(entity.instruction, domain.instruction)
        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.builtIn, domain.builtIn)
        assertEquals(entity.sortNumber, domain.sortNumber)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = AiPromptPreset(
            id = "preset_concise",
            taskType = "SUMMARY",
            name = "精简",
            instruction = "请把这段文字压缩到 100 字以内。",
            enabled = false,
            builtIn = true,
            sortNumber = 3,
            createdAt = 1_700_000_000_456L,
            updatedAt = 1_700_000_000_111L,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.taskType, entity.taskType)
        assertEquals(domain.name, entity.name)
        assertEquals(domain.instruction, entity.instruction)
        assertEquals(domain.enabled, entity.enabled)
        assertEquals(domain.builtIn, entity.builtIn)
        assertEquals(domain.sortNumber, entity.sortNumber)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.taskType, roundTripped.taskType)
        assertEquals(entity.name, roundTripped.name)
        assertEquals(entity.instruction, roundTripped.instruction)
        assertEquals(entity.enabled, roundTripped.enabled)
        assertEquals(entity.builtIn, roundTripped.builtIn)
        assertEquals(entity.sortNumber, roundTripped.sortNumber)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)

        // 本片可用的强断言：两边都是全字段判等的 data class，整对象比较才成立。
        assertEquals(entity, roundTripped)
    }

    /**
     * 默认值集合必须与实体一致：`enabled` / `builtIn` / `sortNumber` 三个布尔与整数默认值
     * （`true` / `false` / `0`）是持久化契约的一部分——DAO 的查询里就写着
     * `where taskType = :taskType and enabled = 1`，默认值一改，"新建预设默认启用" 会静默改变。
     *
     * 两侧都显式传同一个 `createdAt` / `updatedAt`，只比其余字段——那两个的默认值是
     * `systemTimeMillis()`，两次调用不保证落在同一毫秒（见下一条用例）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = AiPromptPreset(
            id = "p",
            taskType = "REWRITE_TEXT",
            name = "n",
            instruction = "i",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val entity = AiPromptPresetEntity(
            id = "p",
            taskType = "REWRITE_TEXT",
            name = "n",
            instruction = "i",
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertEquals(entity.enabled, domain.enabled)
        assertEquals(entity.builtIn, domain.builtIn)
        assertEquals(entity.sortNumber, domain.sortNumber)

        // 显式钉住「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals(true, domain.enabled)
        assertEquals(false, domain.builtIn)
        assertEquals(0, domain.sortNumber)
    }

    /**
     * `createdAt` / `updatedAt` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的
     * `expect fun`）。领域模型能引用它，是因为 `:domain:ai` 从建模块起就
     * `implementation(project(":core:platform"))`——pure 模块引用它不越界（G2 的 pure 判据
     * 只看 import 前缀，`io.legado.app.core.platform` 不在禁列）。
     *
     * 只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。
     */
    @Test
    fun `createdAt 与 updatedAt 的默认值取当前毫秒`() {
        val domain = AiPromptPreset(
            id = "p",
            taskType = "REWRITE_TEXT",
            name = "n",
            instruction = "i",
        )

        assertTrue(domain.createdAt > 0L, "默认 createdAt 应取 systemTimeMillis()，实际 ${domain.createdAt}")
        assertTrue(domain.updatedAt > 0L, "默认 updatedAt 应取 systemTimeMillis()，实际 ${domain.updatedAt}")

        // 实体侧同样是这两个默认值——本片不改变新建语义。
        val entity = AiPromptPresetEntity(
            id = "p",
            taskType = "REWRITE_TEXT",
            name = "n",
            instruction = "i",
        )
        assertTrue(entity.createdAt > 0L)
        assertTrue(entity.updatedAt > 0L)
    }

    /**
     * ⚠️ **与上一片（M3-6 `TagGroupRule`）相反的一条**：判等是**全字段**比较，不是只按主键。
     *
     * 本用例同时钉住两侧，这样无论谁"顺手对齐上一片"给领域模型（或实体）补一个 id-only 的
     * `equals`，都会立刻变红。
     */
    @Test
    fun `判等按全字段而非主键`() {
        val a = AiPromptPreset(
            id = "same_id",
            taskType = "REWRITE_TEXT",
            name = "甲",
            instruction = "i",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val b = a.copy(name = "乙")

        assertNotEquals(a, b, "领域模型只改 name、id 相同 ⇒ 按全字段判等应不相等")
        assertEquals(
            a,
            AiPromptPreset(
                id = "same_id",
                taskType = "REWRITE_TEXT",
                name = "甲",
                instruction = "i",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        // 实体侧同样必须是全字段判等——本片不改变实体的判等语义。
        assertNotEquals(
            AiPromptPresetEntity(
                id = "same_id",
                taskType = "REWRITE_TEXT",
                name = "甲",
                instruction = "i",
                createdAt = 1L,
                updatedAt = 1L,
            ),
            AiPromptPresetEntity(
                id = "same_id",
                taskType = "REWRITE_TEXT",
                name = "乙",
                instruction = "i",
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    /**
     * 集合形态的两个重载（`toDomainList` / `toEntityList`）也必须逐元素恒等——
     * `getEnabledByTaskType` 走第一个，`savePresets` 走第二个。一旦字段串位（例如把
     * `sortNumber` 写进 `builtIn`）只有这些用例能发现。
     *
     * ⚠️ 本域**没有** `Array<out 领域>` 形态的重载——端口方法收的都是 `List`（与 M3-6 的
     * `vararg` 不同），不要照抄第三个重载。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            AiPromptPresetEntity(
                id = "preset_1",
                taskType = "REWRITE_TEXT",
                name = "甲预设",
                instruction = "甲指令",
                enabled = true,
                builtIn = false,
                sortNumber = 1,
                createdAt = 1_700_000_000_001L,
                updatedAt = 1_700_000_000_002L,
            ),
            AiPromptPresetEntity(
                id = "preset_2",
                taskType = "REWRITE_TEXT",
                name = "乙预设",
                instruction = "乙指令",
                enabled = false,
                builtIn = true,
                sortNumber = 2,
                createdAt = 1_700_000_000_003L,
                updatedAt = 1_700_000_000_004L,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("preset_1", "preset_2"), domains.map { it.id })
        assertEquals(listOf("甲预设", "乙预设"), domains.map { it.name })
        assertEquals(listOf("甲指令", "乙指令"), domains.map { it.instruction })
        assertEquals(listOf(true, false), domains.map { it.enabled })
        assertEquals(listOf(false, true), domains.map { it.builtIn })
        assertEquals(listOf(1, 2), domains.map { it.sortNumber })
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_003L), domains.map { it.createdAt })
        assertEquals(listOf(1_700_000_000_002L, 1_700_000_000_004L), domains.map { it.updatedAt })

        // 集合往返整体无损（同样因为两侧都是全字段判等）。
        assertEquals(entities, domains.toEntityList())
    }
}
