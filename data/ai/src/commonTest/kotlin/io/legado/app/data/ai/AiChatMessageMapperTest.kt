package io.legado.app.data.ai

import io.legado.app.data.entities.AiChatMessage as AiChatMessageEntity
import io.legado.app.domain.ai.AiChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiChatMessageMapper` 的等价性基线（M4-4）。
 *
 * ⚠️ 本片与 M4-1 / M4-2 / M4-3 同侧（**全字段判等**，两侧都没重写 `equals`）⇒ 整对象断言可用。
 *
 * ⚠️ 本实体比前几片多两处**必须专门钉住**的东西：
 * ① `partsJson` 是**字符串列**，映射不得在边界上 encode/decode；
 * ② 四个有默认值的字段（`branchIndex` / `isSelected` / `parentMessageId` / `thinkingDuration`）
 *    全部参与业务语义——尤其 `isSelected`：分支切换靠 `upsert` 改写同一 `id` 的行的它，
 *    写错会让整条分支在界面上消失（`observeSelectedMessages` 只查 `isSelected = 1`）。
 */
class AiChatMessageMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = AiChatMessageEntity(
        id = "message_abc",
        conversationId = "chat_1",
        role = "assistant",
        partsJson = """[{"type":"text","text":"你好"}]""",
        createdAt = 1_700_000_000_123L,
        branchIndex = 2,
        isSelected = false,
        parentMessageId = "message_parent",
        thinkingDuration = 7,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.conversationId, domain.conversationId)
        assertEquals(entity.role, domain.role)
        assertEquals(entity.partsJson, domain.partsJson)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.branchIndex, domain.branchIndex)
        assertEquals(entity.isSelected, domain.isSelected)
        assertEquals(entity.parentMessageId, domain.parentMessageId)
        assertEquals(entity.thinkingDuration, domain.thinkingDuration)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = AiChatMessage(
            id = "message_xyz",
            conversationId = "chat_2",
            role = "user",
            partsJson = """[{"type":"text","text":"重写这段"}]""",
            createdAt = 1_700_000_000_456L,
            branchIndex = 0,
            isSelected = true,
            parentMessageId = null,
            thinkingDuration = 0,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.conversationId, entity.conversationId)
        assertEquals(domain.role, entity.role)
        assertEquals(domain.partsJson, entity.partsJson)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.branchIndex, entity.branchIndex)
        assertEquals(domain.isSelected, entity.isSelected)
        assertEquals(domain.parentMessageId, entity.parentMessageId)
        assertEquals(domain.thinkingDuration, entity.thinkingDuration)
    }

    /** 往返无损：逐字段断言 + 整对象断言（后者仅因两侧都是全字段判等才成立）。 */
    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.conversationId, roundTripped.conversationId)
        assertEquals(entity.role, roundTripped.role)
        assertEquals(entity.partsJson, roundTripped.partsJson)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.branchIndex, roundTripped.branchIndex)
        assertEquals(entity.isSelected, roundTripped.isSelected)
        assertEquals(entity.parentMessageId, roundTripped.parentMessageId)
        assertEquals(entity.thinkingDuration, roundTripped.thinkingDuration)

        assertEquals(entity, roundTripped)
    }

    /**
     * ⚠️ **`partsJson` 是字符串列，映射必须原样搬运**：既不 encode 也不 decode，
     * **也不做任何字符串规范化**（`trim()` / 大小写 / 转义）。
     *
     * 输入是刻意构造的：**首尾带空白 + 不是合法 JSON**。两个特征各挡一类实现偏差 ——
     * - 「不是合法 JSON」挡住「顺手 `decode` 再 `encode`」：非法输入会被吞成空列表、
     *   合法输入会被规范化（键序 / 转义 / 空格），往返不再恒等；
     * - **「首尾带空白」挡住「顺手 `trim()`」**：只放一个干净字符串的话，`trim()` 这种
     *   实现偏差在断言上完全看不出来（M4-4 的变异验证实测漏抓过这一条 —— 补上空白才钉住）。
     */
    @Test
    fun `partsJson 原样搬运不编解码不规范化`() {
        val rawJson = "  not-a-json-at-all { unbalanced  "

        val domain = AiChatMessageEntity(
            id = "m",
            conversationId = "c",
            role = "assistant",
            partsJson = rawJson,
            createdAt = 1L,
        ).toDomain()

        assertEquals(rawJson, domain.partsJson, "partsJson 不得被解析或规范化")
        assertEquals(rawJson, domain.toEntity().partsJson)
    }

    /**
     * ⚠️ **唯一的可空字段 `parentMessageId` 必须原样搬运 `null`**。
     *
     * `null` = 「用户发的根消息」；非空 = 「对某条消息的回复，可被重新生成」。
     * 界面上「是否显示重新生成按钮」正是 `parentMessageId != null` ⇒ 归一化成空串会让
     * **所有**根消息都长出重新生成按钮。
     */
    @Test
    fun `可空字段 parentMessageId 的 null 双向原样搬运`() {
        val entity = AiChatMessageEntity(
            id = "m_root",
            conversationId = "c",
            role = "user",
            partsJson = "[]",
            createdAt = 1L,
            parentMessageId = null,
        )

        val domain = entity.toDomain()

        assertNull(domain.parentMessageId, "parentMessageId 的 null 不得归一化成空串")
        assertEquals(entity, domain.toEntity())
    }

    /**
     * 默认值集合必须与实体一致：`branchIndex` = 0、`isSelected` = `true`、
     * `parentMessageId` = `null`、`thinkingDuration` = 0。
     *
     * ⚠️ `isSelected` 的默认 `true` 不是随手取的：新落库的消息默认在选中路径上；
     * `observeSelectedMessages` 只查 `isSelected = 1`，默认成 `false` 会让新消息**写进去就看不见**。
     * `branchIndex` 从 0 起是因为「第一条分支」的下标就是 0（`countBranches` 返回 0 = 还没有兄弟）。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = AiChatMessage(id = "m", conversationId = "c", role = "user", partsJson = "[]", createdAt = 1L)
        val entity = AiChatMessageEntity(id = "m", conversationId = "c", role = "user", partsJson = "[]", createdAt = 1L)

        assertEquals(entity.branchIndex, domain.branchIndex)
        assertEquals(entity.isSelected, domain.isSelected)
        assertEquals(entity.parentMessageId, domain.parentMessageId)
        assertEquals(entity.thinkingDuration, domain.thinkingDuration)

        // 显式钉住字面量，免得两侧一起被改错还保持一致。
        assertEquals(0, domain.branchIndex)
        assertEquals(true, domain.isSelected)
        assertNull(domain.parentMessageId)
        assertEquals(0, domain.thinkingDuration)
    }

    /**
     * `createdAt` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的 `expect fun`）。
     * 实体上还挂着 `@ColumnInfo(defaultValue = "0")`（Room 的**建表**默认值，用于旧库升级），
     * 领域模型**不复刻注解**——这一点不影响 Kotlin 侧的默认值（两侧都是 `systemTimeMillis()`）。
     */
    @Test
    fun `createdAt 的默认值取当前毫秒`() {
        val domain = AiChatMessage(id = "m", conversationId = "c", role = "user", partsJson = "[]")

        assertTrue(domain.createdAt > 0L, "默认 createdAt 应取 systemTimeMillis()，实际 ${domain.createdAt}")
    }

    /**
     * ⚠️ **判等按全字段而非主键**——本域这条比前几片更要紧：分支切换（`AiChatRepositoryImpl.selectBranch`
     * 与 `saveRegeneratedMessage`）正是靠 `upsert` **改写同一个 `id` 的行**的 `isSelected`。
     * 若给模型补一个只按 `id` 判等的 `equals`，同一消息「选中 / 未选中」两个状态会被判为相等，
     * 依赖它比较状态的代码会静默失效。
     */
    @Test
    fun `判等按全字段而非主键`() {
        val a = AiChatMessage(
            id = "same_id",
            conversationId = "c",
            role = "assistant",
            partsJson = "[]",
            createdAt = 1L,
            isSelected = true,
        )

        assertNotEquals(a, a.copy(isSelected = false), "仅 isSelected 不同 ⇒ 应不相等（分支切换靠它）")
        assertNotEquals(a, a.copy(branchIndex = 1), "仅 branchIndex 不同 ⇒ 应不相等")
        assertNotEquals(a, a.copy(partsJson = "[{\"type\":\"text\",\"text\":\"x\"}]"), "仅 partsJson 不同 ⇒ 应不相等")

        assertEquals(a, a.copy(thinkingDuration = 0))
    }

    /**
     * 集合形态的重载 `toDomainList` 必须逐元素恒等且保序——`observeSelectedMessages` 直接用它。
     * ⚠️ DAO 的 `ORDER BY createdAt ASC` 决定顺序（时间线顺序），串位会让对话显示乱序。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            AiChatMessageEntity(
                id = "m1",
                conversationId = "c1",
                role = "user",
                partsJson = """[{"type":"text","text":"第一句"}]""",
                createdAt = 1_700_000_000_001L,
                branchIndex = 0,
                isSelected = true,
                parentMessageId = null,
                thinkingDuration = 0,
            ),
            AiChatMessageEntity(
                id = "m2",
                conversationId = "c1",
                role = "assistant",
                partsJson = """[{"type":"text","text":"第二句"}]""",
                createdAt = 1_700_000_000_003L,
                branchIndex = 1,
                isSelected = false,
                parentMessageId = "m1",
                thinkingDuration = 5,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("m1", "m2"), domains.map { it.id })
        assertEquals(listOf("c1", "c1"), domains.map { it.conversationId })
        assertEquals(listOf("user", "assistant"), domains.map { it.role })
        assertEquals(
            listOf("""[{"type":"text","text":"第一句"}]""", """[{"type":"text","text":"第二句"}]"""),
            domains.map { it.partsJson },
        )
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_003L), domains.map { it.createdAt })
        assertEquals(listOf(0, 1), domains.map { it.branchIndex })
        assertEquals(listOf(true, false), domains.map { it.isSelected })
        assertEquals(listOf(null, "m1"), domains.map { it.parentMessageId })
        assertEquals(listOf(0, 5), domains.map { it.thinkingDuration })
        assertEquals(entities, domains.map { it.toEntity() })
    }
}
