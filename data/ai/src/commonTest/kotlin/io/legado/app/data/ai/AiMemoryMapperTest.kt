package io.legado.app.data.ai

import io.legado.app.data.entities.AiMemory as AiMemoryEntity
import io.legado.app.domain.ai.AiMemory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `AiMemoryMapper` 的等价性基线（M4-2；样板见 M4-1 的 `AiPromptPresetMapperTest`，
 * 同侧参照 M3-5 的 `RuleSubMapperTest`）。
 *
 * ⚠️ **本片与 M4-1 同侧（全字段判等），与 M3-6 `TagGroupRule`（只按主键判等）相反**：实体与
 * 领域模型都没有重写 `equals` / `hashCode`。后果有两面：
 * - 本测试**可以**用整对象 `assertEquals(实体, 领域.toEntity())`（M3-6 那种只按主键判等的域
 *   明令禁止这么写，漏映射照样通过）。这里仍保留逐字段断言，只为失败信息更精确；
 * - 需要一条**反向用例**（[`判等按全字段而非复合主键`]）钉住「`conversationId` + `key` 相同、
 *   只有 `updatedAt` 不同的两条记忆不相等」。本域主键是复合的，后来者很容易顺手补一个
 *   「按主键判等」的 `equals`，那样 `upsert` 刷新 `updatedAt` 的差异就被吞掉了。
 *
 * ⚠️ **空 `conversationId` 是语义值，不是「没填」**：它表示全局记忆（DAO 查询写的是
 * `WHERE conversationId = ''`）。所以专门有一条用例（[`空的会话 id 在两侧都原样搬运`]）钉住
 * 映射**不做**归一化——一旦有人在映射里把空串换成别的哨兵值，全局记忆会被静默改写成会话记忆。
 */
class AiMemoryMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = AiMemoryEntity(
        conversationId = "conv_42",
        key = "favorite_genre",
        value = "科幻",
        updatedAt = 1_700_000_000_123L,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.conversationId, domain.conversationId)
        assertEquals(entity.key, domain.key)
        assertEquals(entity.value, domain.value)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = AiMemory(
            conversationId = "conv_7",
            key = "user_alias",
            value = "小明",
            updatedAt = 1_700_000_000_456L,
        )

        val entity = domain.toEntity()

        assertEquals(domain.conversationId, entity.conversationId)
        assertEquals(domain.key, entity.key)
        assertEquals(domain.value, entity.value)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.conversationId, roundTripped.conversationId)
        assertEquals(entity.key, roundTripped.key)
        assertEquals(entity.value, roundTripped.value)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)

        // 本片可用的强断言：两边都是全字段判等的 data class，整对象比较才成立。
        assertEquals(entity, roundTripped)
    }

    /**
     * `updatedAt` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的 `expect fun`）。
     * 领域模型能引用它，是因为 `:domain:ai` 从建模块起就 `implementation(":core:platform")`
     * ——pure 模块引用它不越界（G2 的 pure 判据只看 import 前缀）。
     *
     * 只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。
     */
    @Test
    fun `updatedAt 的默认值取当前毫秒`() {
        val domain = AiMemory(
            conversationId = "conv_1",
            key = "k",
            value = "v",
        )

        assertTrue(domain.updatedAt > 0L, "默认 updatedAt 应取 systemTimeMillis()，实际 ${domain.updatedAt}")

        // 实体侧同样是这个默认值——本片不改变新建语义。
        val entity = AiMemoryEntity(
            conversationId = "conv_1",
            key = "k",
            value = "v",
        )
        assertTrue(entity.updatedAt > 0L)
    }

    /**
     * ⚠️ **与 M3-6 `TagGroupRule` 相反的一条**：判等是**全字段**比较，不是按复合主键。
     *
     * 本用例同时钉住两侧，这样无论谁"顺手对齐上一片"给领域模型（或实体）补一个按
     * `conversationId` + `key` 判等的 `equals`，都会立刻变红。
     */
    @Test
    fun `判等按全字段而非复合主键`() {
        val a = AiMemory(
            conversationId = "conv_same",
            key = "same_key",
            value = "甲值",
            updatedAt = 1L,
        )

        // 只改 updatedAt：复合主键相同，但全字段判等 ⇒ 必须不相等。
        assertNotEquals(a, a.copy(updatedAt = 2L), "仅 updatedAt 不同 ⇒ 按全字段判等应不相等")

        // 只改 value：同样必须不相等（主键相同也不行）。
        assertNotEquals(a, a.copy(value = "乙值"), "仅 value 不同 ⇒ 应不相等")

        assertEquals(
            a,
            AiMemory(
                conversationId = "conv_same",
                key = "same_key",
                value = "甲值",
                updatedAt = 1L,
            ),
        )

        // 实体侧同样必须是全字段判等——本片不改变实体的判等语义。
        assertNotEquals(
            AiMemoryEntity(
                conversationId = "conv_same",
                key = "same_key",
                value = "甲值",
                updatedAt = 1L,
            ),
            AiMemoryEntity(
                conversationId = "conv_same",
                key = "same_key",
                value = "甲值",
                updatedAt = 2L,
            ),
        )
    }

    /**
     * ⚠️ **空 `conversationId` 表示全局记忆**（DAO 查询写的是 `WHERE conversationId = ''`），
     * 映射必须**原样搬运**、不做任何归一化：把空串换成哨兵值（或反过来）会让全局记忆被静默
     * 改写成会话记忆，而 Room 侧不会有任何报错。
     */
    @Test
    fun `空的会话 id 在两侧都原样搬运`() {
        val globalEntity = AiMemoryEntity(
            conversationId = "",
            key = "tone",
            value = "简洁",
            updatedAt = 1_700_000_000_001L,
        )

        val domain = globalEntity.toDomain()

        assertEquals("", domain.conversationId, "空会话 id 是「全局记忆」的语义值，映射不得改写")
        assertEquals(globalEntity, domain.toEntity())

        // 反方向同样成立。
        val globalDomain = AiMemory(
            conversationId = "",
            key = "tone",
            value = "简洁",
            updatedAt = 1_700_000_000_001L,
        )
        assertEquals("", globalDomain.toEntity().conversationId)
    }

    /**
     * 集合形态的重载 `toDomainList` 也必须逐元素恒等——`getForPrompt` 走它。一旦字段串位
     * （例如把 `value` 写进 `key`）只有这类用例能发现。
     *
     * ⚠️ 本域**没有** `List<领域> → 实体` 的重载：端口没有批量写入方法（`upsert` 收单条），
     * 不要照抄 M4-1 的第二条。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            AiMemoryEntity(
                conversationId = "",
                key = "tone",
                value = "简洁",
                updatedAt = 1_700_000_000_001L,
            ),
            AiMemoryEntity(
                conversationId = "conv_9",
                key = "genre",
                value = "科幻",
                updatedAt = 1_700_000_000_002L,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("", "conv_9"), domains.map { it.conversationId })
        assertEquals(listOf("tone", "genre"), domains.map { it.key })
        assertEquals(listOf("简洁", "科幻"), domains.map { it.value })
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_002L), domains.map { it.updatedAt })

        // 集合往返整体无损（同样因为两侧都是全字段判等）。
        assertEquals(entities, domains.map { it.toEntity() })
    }
}
