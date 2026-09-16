package io.legado.app.data.ai

import io.legado.app.data.entities.AiModelProfile as AiModelProfileEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiModelProfileMapper` 的等价性护栏（M4-5c）。
 *
 * 重点在两条别处没有的约束：
 * - `capabilities` / `defaultParamsJson` **都是字符串列**，映射不解析、不排序、不去重；
 * - `id` 是 `stableModelId` 生成的**稳定 UUID v3 派生物**，映射必须原样搬运、**不得重算**
 *   （重算等于把既有用户的档案 ID 改掉）。
 */
class AiModelProfileMapperTest {

    private fun fullEntity() = AiModelProfileEntity(
        id = "model_143a836ff1b13b9cb09e5a86ef00b88d",
        providerId = "provider_1",
        displayName = "GPT-4.1 mini",
        modelId = "gpt-4.1-mini",
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
        capabilities = "tools,vision,streaming",
        defaultParamsJson = """{"temperature": 0.7}""",
        enabled = false,
        sortNumber = 3,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    @Test
    fun `toDomain 逐字段搬运`() {
        val e = fullEntity()
        val d = e.toDomain()
        assertEquals(e.id, d.id)
        assertEquals(e.providerId, d.providerId)
        assertEquals(e.displayName, d.displayName)
        assertEquals(e.modelId, d.modelId)
        assertEquals(e.contextWindow, d.contextWindow)
        assertEquals(e.maxOutputTokens, d.maxOutputTokens)
        assertEquals(e.capabilities, d.capabilities)
        assertEquals(e.defaultParamsJson, d.defaultParamsJson)
        assertEquals(e.enabled, d.enabled)
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
    fun `可空字段 defaultParamsJson 的 null 原样穿过`() {
        val e = AiModelProfileEntity(
            id = "model_x",
            providerId = "provider_1",
            displayName = "n",
            modelId = "m",
        )
        assertNull(e.toDomain().defaultParamsJson)
        assertNull(e.toDomain().toEntity().defaultParamsJson)
    }

    @Test
    fun `实体默认值集合与领域模型一致`() {
        val e = AiModelProfileEntity(id = "model_x", providerId = "p", displayName = "n", modelId = "m")
        val d = e.toDomain()
        assertEquals(0, d.contextWindow)
        assertEquals(0, d.maxOutputTokens)
        assertEquals("", d.capabilities)
        assertTrue(d.enabled)
        assertEquals(0, d.sortNumber)
        assertEquals(d.enabled, e.enabled)
        assertEquals(d.sortNumber, e.sortNumber)
    }

    @Test
    fun `capabilities 字符串原样搬运且不被拆分重排`() {
        // 故意给「有重复、有空白、顺序非字母序」的串：归一化（去重/trim/排序）都不许发生。
        val weird = " streaming , tools ,tools,  vision "
        val e = fullEntity().copy(capabilities = weird)
        assertEquals(weird, e.toDomain().capabilities)
        assertEquals(weird, e.toDomain().toEntity().capabilities)
    }

    @Test
    fun `defaultParamsJson 不被就地编解码`() {
        val weird = """  {"temperature":0.5,"reasoningLevel":"HIGH"}  """
        val e = fullEntity().copy(defaultParamsJson = weird)
        assertEquals(weird, e.toDomain().defaultParamsJson)
        assertEquals(weird, e.toDomain().toEntity().defaultParamsJson)
    }

    @Test
    fun `id 原样搬运而不是按 providerId 与 modelId 重算`() {
        // 刻意让 id 与「按 providerId:modelId 重算」的结果不同 ⇒ 只要映射器动了重算的念头就会红。
        val e = fullEntity().copy(id = "model_手工值", providerId = "p", modelId = "m")
        assertEquals("model_手工值", e.toDomain().id)
        assertEquals("model_手工值", e.toDomain().toEntity().id)
    }

    @Test
    fun `逐字段可区分（映射器没有忽略任何字段）`() {
        val base = fullEntity()
        val variants = listOf(
            base.copy(id = "other"),
            base.copy(providerId = "other"),
            base.copy(displayName = "other"),
            base.copy(modelId = "other"),
            base.copy(contextWindow = 1),
            base.copy(maxOutputTokens = 1),
            base.copy(capabilities = "other"),
            base.copy(defaultParamsJson = null),
            base.copy(enabled = true),
            base.copy(sortNumber = 99),
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
        val a = fullEntity().copy(id = "a")
        val b = fullEntity().copy(id = "b")
        val c = fullEntity().copy(id = "c")
        val list = listOf(c, a, b).toDomainList()
        assertEquals(listOf("c", "a", "b"), list.map { it.id })
        assertEquals(c.toDomain(), list[0])
        assertEquals(a.toDomain(), list[1])
        assertEquals(b.toDomain(), list[2])
    }

    /** 领域模型与实体都是全字段判等 ⇒ 只差一个字段也必须 `!=`。 */
    @Test
    fun `判等是全字段而不是只按主键`() {
        val e = fullEntity()
        assertNotEquals(e.toDomain(), e.copy(sortNumber = e.sortNumber + 1).toDomain())
        assertEquals(e.toDomain(), e.copy().toDomain())
    }
}
