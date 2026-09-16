package io.legado.app.data.ai

import io.legado.app.data.entities.AiTaskPreset as AiTaskPresetEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiTaskPresetMapper` 的等价性护栏（M4-5c）。
 *
 * 重点：`paramsJson` / `chunkPolicyJson` 是字符串列（不解析），
 * `isDefault` / `enabled` / `sortNumber` 参与 DAO 的 `getDefaultPreset` 排序 ⇒ 不许写死或错位。
 */
class AiTaskPresetMapperTest {

    private fun fullEntity() = AiTaskPresetEntity(
        id = "default_translate_chapter",
        taskType = "translate_chapter",
        name = "Default Translation",
        modelProfileId = "model_abc",
        promptTemplate = "prompt",
        paramsJson = """{"temperature": 0.7}""",
        chunkPolicyJson = """{"maxInputChars": 10000}""",
        enabled = false,
        isDefault = true,
        sortNumber = 4,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    @Test
    fun `toDomain 逐字段搬运`() {
        val e = fullEntity()
        val d = e.toDomain()
        assertEquals(e.id, d.id)
        assertEquals(e.taskType, d.taskType)
        assertEquals(e.name, d.name)
        assertEquals(e.modelProfileId, d.modelProfileId)
        assertEquals(e.promptTemplate, d.promptTemplate)
        assertEquals(e.paramsJson, d.paramsJson)
        assertEquals(e.chunkPolicyJson, d.chunkPolicyJson)
        assertEquals(e.enabled, d.enabled)
        assertEquals(e.isDefault, d.isDefault)
        assertEquals(e.sortNumber, d.sortNumber)
        assertEquals(e.createdAt, d.createdAt)
        assertEquals(e.updatedAt, d.updatedAt)
        assertEquals(e, d.toEntity(), "领域模型应能逐字回写成同一个实体")
    }

    @Test
    fun `toEntity 逐字段搬运（反向）`() {
        val e = fullEntity()
        assertEquals(e, e.toDomain().toEntity())
    }

    @Test
    fun `两个可空 JSON 列的 null 原样穿过`() {
        val e = AiTaskPresetEntity(
            id = "p1",
            taskType = "chat",
            name = "n",
            modelProfileId = "m",
            promptTemplate = "t",
        )
        assertNull(e.toDomain().paramsJson)
        assertNull(e.toDomain().chunkPolicyJson)
        assertNull(e.toDomain().toEntity().paramsJson)
        assertNull(e.toDomain().toEntity().chunkPolicyJson)
    }

    @Test
    fun `实体默认值集合与领域模型一致`() {
        val e = AiTaskPresetEntity(id = "p1", taskType = "chat", name = "n", modelProfileId = "m", promptTemplate = "t")
        val d = e.toDomain()
        assertTrue(d.enabled)
        assertEquals(false, d.isDefault, "实体默认 isDefault = false；只有内建预设显式写 true")
        assertEquals(0, d.sortNumber)
        assertEquals(d.enabled, e.enabled)
        assertEquals(d.isDefault, e.isDefault)
        assertEquals(d.sortNumber, e.sortNumber)
    }

    /** `isDefault` / `enabled` 参与 `getDefaultPreset` 的 `where enabled = 1 order by isDefault desc`
     *  ⇒ 映射写死任一侧都会让「取默认预设」选错行。 */
    @Test
    fun `isDefault 与 enabled 都不会被写死`() {
        val a = fullEntity().copy(isDefault = true, enabled = true)
        val b = fullEntity().copy(isDefault = false, enabled = true)
        val c = fullEntity().copy(isDefault = true, enabled = false)
        val d = fullEntity().copy(isDefault = false, enabled = false)
        assertEquals(listOf(true, false, true, false), listOf(a, b, c, d).map { it.toDomain().isDefault })
        assertEquals(listOf(true, true, false, false), listOf(a, b, c, d).map { it.toDomain().enabled })
    }

    @Test
    fun `内建预设的固定 id 原样搬运不被重算`() {
        val ids = listOf("default_translate_chapter", "default_summarize_chapter", "default_chat")
        ids.forEach { id ->
            assertEquals(id, fullEntity().copy(id = id).toDomain().id)
        }
    }

    @Test
    fun `paramsJson 与 chunkPolicyJson 不被就地编解码`() {
        val weirdParams = """  {"temperature":0.5}  """
        val weirdPolicy = """  {"retryCount":2}  """
        val e = fullEntity().copy(paramsJson = weirdParams, chunkPolicyJson = weirdPolicy)
        assertEquals(weirdParams, e.toDomain().paramsJson)
        assertEquals(weirdPolicy, e.toDomain().chunkPolicyJson)
        assertEquals(weirdParams, e.toDomain().toEntity().paramsJson)
        assertEquals(weirdPolicy, e.toDomain().toEntity().chunkPolicyJson)
    }

    @Test
    fun `逐字段可区分（映射器没有忽略任何字段）`() {
        val base = fullEntity()
        val variants = listOf(
            base.copy(id = "other"),
            base.copy(taskType = "other"),
            base.copy(name = "other"),
            base.copy(modelProfileId = "other"),
            base.copy(promptTemplate = "other"),
            base.copy(paramsJson = null),
            base.copy(chunkPolicyJson = null),
            base.copy(enabled = true),
            base.copy(isDefault = false),
            base.copy(sortNumber = 9),
            base.copy(createdAt = 1L),
            base.copy(updatedAt = 1L),
        )
        val baseDomain = base.toDomain()
        variants.forEachIndexed { i, v ->
            assertNotEquals(baseDomain, v.toDomain(), "第 ${i + 1} 个变体与基线映射结果相同 ⇒ 该字段没被映射")
        }
    }

    @Test
    fun `toDomainList 保序且逐项映射`() {
        val a = fullEntity().copy(id = "a", sortNumber = 1)
        val b = fullEntity().copy(id = "b", sortNumber = 2)
        val c = fullEntity().copy(id = "c", sortNumber = 3)
        val list = listOf(b, a, c).toDomainList()
        assertEquals(listOf("b", "a", "c"), list.map { it.id })
        assertEquals(b.toDomain(), list[0])
        assertEquals(a.toDomain(), list[1])
        assertEquals(c.toDomain(), list[2])
    }
}
