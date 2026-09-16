package io.legado.app.data.ai

import io.legado.app.data.entities.AiArtifact as AiArtifactEntity
import io.legado.app.domain.ai.AiArtifact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiArtifactMapper` 的等价性基线（M4-3；样板见 M4-1 的 `AiPromptPresetMapperTest`，
 * 同侧参照 M3-5 的 `RuleSubMapperTest`）。
 *
 * ⚠️ **本片与 M4-1 / M4-2 同侧（全字段判等），与 M3-6 `TagGroupRule`（只按主键判等）相反**：
 * 实体与领域模型都没重写 `equals` / `hashCode`。所以这里**可以**用整对象
 * `assertEquals(实体, 领域.toEntity())`（M3-6 那种只按主键判等的域明令禁止这么写），
 * 但仍保留逐字段断言（失败信息更精确）与一条反向用例。
 *
 * ⚠️ **`STATUS_*` 常量两侧并存**：DAO 的 `@Query` 用**实体**的 `AiArtifact.STATUS_SUCCESS` 做
 * 字符串插值，`:app` 侧已改为引用**领域模型**的常量。两处定义若取值分叉，"查询成功产物"的条件
 * 与 UI 的 `when (status)` 分支就会错位，而两边都不会报错 ⇒ [常量取值与实体一致] 这条用例专门钉它。
 */
class AiArtifactMapperTest {

    /** 逐字段构造，刻意让每个字段都取**非默认值**，否则漏映射会被默认值掩盖。 */
    private fun nonDefaultEntity() = AiArtifactEntity(
        id = "artifact_abc",
        taskType = "chapter_summary",
        bookUrl = "https://example.com/book/1",
        chapterIndex = 42,
        contentHash = "content-hash-1",
        promptHash = "prompt-hash-1",
        modelProfileId = "model_gpt",
        status = AiArtifactEntity.STATUS_SUCCESS,
        output = "摘要内容",
        errorMessage = "上一次超时的报错",
        schemaVersion = 3,
        createdAt = 1_700_000_000_123L,
        updatedAt = 1_700_000_000_999L,
    )

    @Test
    fun `实体到领域模型逐字段一致`() {
        val entity = nonDefaultEntity()

        val domain = entity.toDomain()

        assertEquals(entity.id, domain.id)
        assertEquals(entity.taskType, domain.taskType)
        assertEquals(entity.bookUrl, domain.bookUrl)
        assertEquals(entity.chapterIndex, domain.chapterIndex)
        assertEquals(entity.contentHash, domain.contentHash)
        assertEquals(entity.promptHash, domain.promptHash)
        assertEquals(entity.modelProfileId, domain.modelProfileId)
        assertEquals(entity.status, domain.status)
        assertEquals(entity.output, domain.output)
        assertEquals(entity.errorMessage, domain.errorMessage)
        assertEquals(entity.schemaVersion, domain.schemaVersion)
        assertEquals(entity.createdAt, domain.createdAt)
        assertEquals(entity.updatedAt, domain.updatedAt)
    }

    @Test
    fun `领域模型到实体逐字段一致`() {
        val domain = AiArtifact(
            id = "artifact_xyz",
            taskType = "clean_text",
            bookUrl = "https://example.com/book/2",
            chapterIndex = 7,
            contentHash = "content-hash-2",
            promptHash = "prompt-hash-2",
            modelProfileId = "model_claude",
            status = AiArtifact.STATUS_FAILED,
            output = null,
            errorMessage = "请求失败",
            schemaVersion = 2,
            createdAt = 1_700_000_000_456L,
            updatedAt = 1_700_000_000_111L,
        )

        val entity = domain.toEntity()

        assertEquals(domain.id, entity.id)
        assertEquals(domain.taskType, entity.taskType)
        assertEquals(domain.bookUrl, entity.bookUrl)
        assertEquals(domain.chapterIndex, entity.chapterIndex)
        assertEquals(domain.contentHash, entity.contentHash)
        assertEquals(domain.promptHash, entity.promptHash)
        assertEquals(domain.modelProfileId, entity.modelProfileId)
        assertEquals(domain.status, entity.status)
        assertEquals(domain.output, entity.output)
        assertEquals(domain.errorMessage, entity.errorMessage)
        assertEquals(domain.schemaVersion, entity.schemaVersion)
        assertEquals(domain.createdAt, entity.createdAt)
        assertEquals(domain.updatedAt, entity.updatedAt)
    }

    @Test
    fun `实体到领域模型再到实体字段无损`() {
        val entity = nonDefaultEntity()

        val roundTripped = entity.toDomain().toEntity()

        assertEquals(entity.id, roundTripped.id)
        assertEquals(entity.taskType, roundTripped.taskType)
        assertEquals(entity.bookUrl, roundTripped.bookUrl)
        assertEquals(entity.chapterIndex, roundTripped.chapterIndex)
        assertEquals(entity.contentHash, roundTripped.contentHash)
        assertEquals(entity.promptHash, roundTripped.promptHash)
        assertEquals(entity.modelProfileId, roundTripped.modelProfileId)
        assertEquals(entity.status, roundTripped.status)
        assertEquals(entity.output, roundTripped.output)
        assertEquals(entity.errorMessage, roundTripped.errorMessage)
        assertEquals(entity.schemaVersion, roundTripped.schemaVersion)
        assertEquals(entity.createdAt, roundTripped.createdAt)
        assertEquals(entity.updatedAt, roundTripped.updatedAt)

        // 本片可用的强断言：两边都是全字段判等的 data class，整对象比较才成立。
        assertEquals(entity, roundTripped)
    }

    /**
     * ⚠️ **三个可空字段必须原样搬运 `null`，不得归一化成 `0` / `""`**。
     *
     * `chapterIndex == null` 表示「这本产物不属于任何章节」；`output == null` 表示「还没产出」；
     * `errorMessage == null` 表示「没有报错」。归一化会让 UI 的 `output.isNullOrBlank()` 判断
     * 与「待处理」状态机静默错位。
     */
    @Test
    fun `可空字段的 null 双向原样搬运`() {
        val entity = AiArtifactEntity(
            id = "artifact_pending",
            taskType = "chapter_summary",
            bookUrl = "https://example.com/book/3",
            chapterIndex = null,
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
            status = AiArtifactEntity.STATUS_PENDING,
            output = null,
            errorMessage = null,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val domain = entity.toDomain()

        assertNull(domain.chapterIndex, "chapterIndex 的 null 不得归一化成 0")
        assertNull(domain.output, "output 的 null 不得归一化成空串")
        assertNull(domain.errorMessage, "errorMessage 的 null 不得归一化成空串")
        assertEquals(entity, domain.toEntity())
    }

    /**
     * 默认值集合必须与实体一致：`chapterIndex` / `output` / `errorMessage` 三个可空字段默认
     * `null`，`status` 默认 `PENDING`，`schemaVersion` 默认 `1`。这些是持久化契约的一部分——
     * `getCachedArtifact` 只在 `status = 成功` 时命中缓存，默认值一改，新建产物的初始状态就变了。
     *
     * 两侧都显式传同一个 `createdAt` / `updatedAt`，只比其余字段——那两个的默认值是
     * `systemTimeMillis()`，两次调用不保证落在同一毫秒。
     */
    @Test
    fun `默认值集合与实体一致`() {
        val domain = AiArtifact(
            id = "a",
            taskType = "t",
            bookUrl = "u",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
            createdAt = 1L,
            updatedAt = 1L,
        )
        val entity = AiArtifactEntity(
            id = "a",
            taskType = "t",
            bookUrl = "u",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
            createdAt = 1L,
            updatedAt = 1L,
        )

        assertNull(domain.chapterIndex)
        assertNull(domain.output)
        assertNull(domain.errorMessage)
        assertEquals(entity.status, domain.status)
        assertEquals(entity.schemaVersion, domain.schemaVersion)

        // 显式钉住「肉眼容易看错」的默认值，免得两边一起被改错还保持一致。
        assertEquals(AiArtifact.STATUS_PENDING, domain.status)
        assertEquals(0, domain.status)
        assertEquals(1, domain.schemaVersion)
    }

    /**
     * ⚠️ **`STATUS_*` 常量两侧必须取同一组值**。
     *
     * `AiArtifactDao` 的两条 `@Query` 用**实体**的 `STATUS_SUCCESS` 做字符串插值（成为 SQL 里的
     * 字面量），而 `:app` 侧（4 个 UseCase 的构造点、`ReadAiDelegate` / `BookCharacterListViewModel`
     * 的 `when` 分支）已改为引用**领域模型**的常量。两处取值一分叉，"查询成功产物"与 UI 分支就会
     * 错位，而两侧都不会编译报错 ⇒ 本用例逐值比对。
     */
    @Test
    fun `常量取值与实体一致`() {
        assertEquals(AiArtifactEntity.STATUS_PENDING, AiArtifact.STATUS_PENDING)
        assertEquals(AiArtifactEntity.STATUS_RUNNING, AiArtifact.STATUS_RUNNING)
        assertEquals(AiArtifactEntity.STATUS_SUCCESS, AiArtifact.STATUS_SUCCESS)
        assertEquals(AiArtifactEntity.STATUS_FAILED, AiArtifact.STATUS_FAILED)

        // 显式钉住「四处引用点都在用的具体数值」，免得两侧一起被改错还保持一致。
        assertEquals(0, AiArtifact.STATUS_PENDING)
        assertEquals(1, AiArtifact.STATUS_RUNNING)
        assertEquals(2, AiArtifact.STATUS_SUCCESS)
        assertEquals(3, AiArtifact.STATUS_FAILED)
    }

    /**
     * `createdAt` / `updatedAt` 的默认值来自 `systemTimeMillis()`（`:core:platform` 的
     * `expect fun`）。领域模型能引用它，是因为 `:domain:ai` 从建模块起就
     * `implementation(project(":core:platform"))`——pure 模块引用它不越界（G2 的 pure 判据
     * 只看 import 前缀，`io.legado.app.core.platform` 不在禁列）。
     *
     * 只断言「取到了正数」：具体值不可断言，两次调用不保证同毫秒。注意调用方都显式传 `now`，
     * 默认值主要服务「不传」的构造点。
     */
    @Test
    fun `createdAt 与 updatedAt 的默认值取当前毫秒`() {
        val domain = AiArtifact(
            id = "a",
            taskType = "t",
            bookUrl = "u",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
        )

        assertTrue(domain.createdAt > 0L, "默认 createdAt 应取 systemTimeMillis()，实际 ${domain.createdAt}")
        assertTrue(domain.updatedAt > 0L, "默认 updatedAt 应取 systemTimeMillis()，实际 ${domain.updatedAt}")

        // 实体侧同样是这两个默认值——本片不改变新建语义。
        val entity = AiArtifactEntity(
            id = "a",
            taskType = "t",
            bookUrl = "u",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
        )
        assertTrue(entity.createdAt > 0L)
        assertTrue(entity.updatedAt > 0L)
    }

    /**
     * ⚠️ **与 M3-6 `TagGroupRule` 相反的一条**：判等是**全字段**比较，不是只按主键
     * （本域主键是 `id: String`）。
     *
     * 本用例同时钉住两侧，这样无论谁"顺手对齐上一片"给领域模型（或实体）补一个 id-only 的
     * `equals`，都会立刻变红——那种 `equals` 会吞掉 `status` 变化（而本域的状态流转正是靠
     * `upsert` 改写同一个 `id` 的行）。
     */
    @Test
    fun `判等按全字段而非主键`() {
        val a = AiArtifact(
            id = "same_id",
            taskType = "t",
            bookUrl = "u",
            contentHash = "c",
            promptHash = "p",
            modelProfileId = "m",
            status = AiArtifact.STATUS_RUNNING,
            createdAt = 1L,
            updatedAt = 1L,
        )

        // 只改 status（同一条产物的状态流转）⇒ 必须不相等。
        assertNotEquals(a, a.copy(status = AiArtifact.STATUS_SUCCESS), "仅 status 不同 ⇒ 应不相等")

        assertEquals(
            a,
            AiArtifact(
                id = "same_id",
                taskType = "t",
                bookUrl = "u",
                contentHash = "c",
                promptHash = "p",
                modelProfileId = "m",
                status = AiArtifact.STATUS_RUNNING,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        // 实体侧同样必须是全字段判等——本片不改变实体的判等语义。
        assertNotEquals(
            AiArtifactEntity(
                id = "same_id",
                taskType = "t",
                bookUrl = "u",
                contentHash = "c",
                promptHash = "p",
                modelProfileId = "m",
                status = AiArtifactEntity.STATUS_RUNNING,
                createdAt = 1L,
                updatedAt = 1L,
            ),
            AiArtifactEntity(
                id = "same_id",
                taskType = "t",
                bookUrl = "u",
                contentHash = "c",
                promptHash = "p",
                modelProfileId = "m",
                status = AiArtifactEntity.STATUS_SUCCESS,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }

    /**
     * 集合形态的重载 `toDomainList` 也必须逐元素恒等——`getArtifactsByContentHash` 直接用它，
     * `observeBookArtifacts` 在 `Flow` 内用它。一旦字段串位（例如把 `promptHash` 写进
     * `contentHash`）只有这些用例能发现。
     *
     * ⚠️ 本域**没有** `List<领域> → 实体` 的重载：`upsertArtifact` 收单条，不要照抄 M4-1 的第二条。
     */
    @Test
    fun `集合映射逐元素保序且字段一致`() {
        val entities = listOf(
            AiArtifactEntity(
                id = "a1",
                taskType = "chapter_summary",
                bookUrl = "u1",
                chapterIndex = 1,
                contentHash = "c1",
                promptHash = "p1",
                modelProfileId = "m1",
                status = AiArtifactEntity.STATUS_SUCCESS,
                output = "o1",
                schemaVersion = 1,
                createdAt = 1_700_000_000_001L,
                updatedAt = 1_700_000_000_002L,
            ),
            AiArtifactEntity(
                id = "a2",
                taskType = "clean_text",
                bookUrl = "u2",
                chapterIndex = null,
                contentHash = "c2",
                promptHash = "p2",
                modelProfileId = "m2",
                status = AiArtifactEntity.STATUS_FAILED,
                errorMessage = "e2",
                schemaVersion = 2,
                createdAt = 1_700_000_000_003L,
                updatedAt = 1_700_000_000_004L,
            ),
        )

        val domains = entities.toDomainList()

        assertEquals(listOf("a1", "a2"), domains.map { it.id })
        assertEquals(listOf("chapter_summary", "clean_text"), domains.map { it.taskType })
        assertEquals(listOf("u1", "u2"), domains.map { it.bookUrl })
        assertEquals(listOf(1, null), domains.map { it.chapterIndex })
        assertEquals(listOf("c1", "c2"), domains.map { it.contentHash })
        assertEquals(listOf("p1", "p2"), domains.map { it.promptHash })
        assertEquals(listOf("m1", "m2"), domains.map { it.modelProfileId })
        assertEquals(
            listOf(AiArtifact.STATUS_SUCCESS, AiArtifact.STATUS_FAILED),
            domains.map { it.status },
        )
        assertEquals(listOf("o1", null), domains.map { it.output })
        assertEquals(listOf(null, "e2"), domains.map { it.errorMessage })
        assertEquals(listOf(1, 2), domains.map { it.schemaVersion })
        assertEquals(listOf(1_700_000_000_001L, 1_700_000_000_003L), domains.map { it.createdAt })
        assertEquals(listOf(1_700_000_000_002L, 1_700_000_000_004L), domains.map { it.updatedAt })

        // 集合往返整体无损（同样因为两侧都是全字段判等）。
        assertEquals(entities, domains.map { it.toEntity() })
    }
}
