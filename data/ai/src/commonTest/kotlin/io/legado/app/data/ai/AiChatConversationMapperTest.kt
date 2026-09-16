package io.legado.app.data.ai

import io.legado.app.data.entities.AiChatConversation as AiChatConversationEntity
import io.legado.app.domain.ai.AiChatConversation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiChatConversationMapper` 的等价性基线（M4-4）。
 *
 * ⚠️ **本片与 M4-1 / M4-2 / M4-3 同侧（全字段判等），与 M3-6 `TagGroupRule`（只按主键判等）相反**：
 * 实体与领域模型都没重写 `equals` / `hashCode`，所以这里**可以**用整对象
 * `assertEquals(实体, 领域.toEntity())`，但仍保留逐字段断言（失败信息更精确）与负向用例。
 *
 * 配对文件见 `AiChatMessageMapperTest`（本域两个实体各有自己的 mapper 测试）。
 */
class AiChatConversationMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = AiChatConversationEntity(
        id = "chat_abc",
        title = "聊聊《三体》",
        reasoningLevel = "high",
        modelProfileId = "model_gpt",
        createdAt = 1_700_000_000_123L,
        updatedAt = 1_700_000_000_999L,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.title, domain.title)
        assertEquals(entity.reasoningLevel, domain.reasoningLevel)
        assertEquals(entity.modelProfileId, domain.modelProfileId)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = AiChatConversation(
            id = "chat_xyz",
            title = "翻译练习",
            reasoningLevel = "low",
            modelProfileId = null,
            createdAt = 1_700_000_000_456L,
            updatedAt = 1_700_000_000_111L,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.title, entity.title)
        assertEquals(domain.reasoningLevel, entity.reasoningLevel)
        assertEquals(domain.modelProfileId, entity.modelProfileId)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    /** 往返无损：逐字段断言 + 整对象断言（后者仅因两侧都是全字段判等才成立）。 */
    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.title, roundTripped.title)
        assertEquals(entity.reasoningLevel, roundTripped.reasoningLevel)
        assertEquals(entity.modelProfileId, roundTripped.modelProfileId)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)

        assertEquals(entity, roundTripped)
    }

    /**
     * ⚠️ **唯一的可空字段 `modelProfileId` 必须原样搬运 `null`，不得归一化成空串**。
     *
     * `null` 表示「这条会话没有绑定模型档案」，调用方据此回落到默认模型；
     * `""` 是一个**看起来像答案的假值**，会让「有没有绑定」的判断静默改变行为。
     */
    @Test
    fun `可空字段 modelProfileId 的 null 双向原样搬运`() {
        val entity = AiChatConversationEntity(
            id = "chat_pending",
            title = "新会话",
            reasoningLevel = "auto",
            modelProfileId = null,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val domain = entity.toDomain()

        assertNull(domain.modelProfileId, "modelProfileId 的 null 不得归一化成空串")
        assertEquals(entity, domain.toEntity())
    }

    /**
     * 默认值集合必须与实体一致：`reasoningLevel` 默认 `"auto"`、`modelProfileId` 默认 `null`。
     *
     * ⚠️ `"auto"` 是**语义值**：它与 `:core:model` 的 `AiReasoningLevel.AUTO` 对应（调用方写的是
     * `level.name.lowercase()`），落到 UI 不认识的取值上不会编译报错。所以这里显式钉住字面量，
     * 免得两侧一起被改错还保持一致。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = AiChatConversation(id = "c", title = "t", createdAt = 1L, updatedAt = 1L)
        val entity = AiChatConversationEntity(id = "c", title = "t", createdAt = 1L, updatedAt = 1L)

        assertEquals(entity.reasoningLevel, domain.reasoningLevel)
        assertNull(domain.modelProfileId)
        assertEquals("auto", domain.reasoningLevel)
    }

    /**
     * `createdAt` / `updatedAt` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的
     * `expect fun`），与实体一致。只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。
     */
    @Test
    fun `createdAt 与 updatedAt 的默认值取当前毫秒`() {
        val domain = AiChatConversation(id = "c", title = "t")

        assertTrue(domain.createdAt > 0L, "默认 createdAt 应取 systemTimeMillis()，实际 ${domain.createdAt}")
        assertTrue(domain.updatedAt > 0L, "默认 updatedAt 应取 systemTimeMillis()，实际 ${domain.updatedAt}")
    }

    /**
     * ⚠️ **与 M3-6 `TagGroupRule` 相反的一条**：判等是**全字段**比较，不是只按主键
     * （本域主键是 `id: String`）。
     *
     * `updatedAt` 会被每一次消息动作刷新（`touchConversation` / `updateConversationTitle` /
     * `updateReasoningLevel`），而那正是「会话列表按最近活跃排序」的依据 ⇒ 吞掉它就等于吞掉排序依据。
     */
    @Test
    fun `判等按全字段而非主键`() {
        val a = AiChatConversation(id = "same_id", title = "t", createdAt = 1L, updatedAt = 1L)

        assertNotEquals(a, a.copy(updatedAt = 2L), "仅 updatedAt 不同 ⇒ 应不相等（会话排序靠它）")
        assertNotEquals(a, a.copy(title = "改名了"), "仅 title 不同 ⇒ 应不相等")
        assertEquals(a, AiChatConversation(id = "same_id", title = "t", createdAt = 1L, updatedAt = 1L))
    }

    /**
     * 集合形态的重载 `toDomainList` 必须逐元素恒等且保序——`observeConversations` 直接用它。
     * ⚠️ DAO 的 `ORDER BY updatedAt DESC` 决定顺序，「最近活跃在前」的界面语义就在这个顺序上。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            AiChatConversationEntity(
                id = "c1",
                title = "最近的",
                reasoningLevel = "high",
                modelProfileId = "m1",
                createdAt = 1_700_000_000_001L,
                updatedAt = 1_700_000_000_002L,
            ),
            AiChatConversationEntity(
                id = "c2",
                title = "更早的",
                reasoningLevel = "auto",
                modelProfileId = null,
                createdAt = 1_700_000_000_003L,
                updatedAt = 1_700_000_000_004L,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("c1", "c2"), domains.map { it.id })
        assertEquals(listOf("最近的", "更早的"), domains.map { it.title })
        assertEquals(listOf("high", "auto"), domains.map { it.reasoningLevel })
        assertEquals(listOf("m1", null), domains.map { it.modelProfileId })
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_003L), domains.map { it.createdAt })
        assertEquals(listOf(1_700_000_000_002L, 1_700_000_000_004L), domains.map { it.updatedAt })
        assertEquals(entities, domains.map { it.toEntity() })
    }
}
