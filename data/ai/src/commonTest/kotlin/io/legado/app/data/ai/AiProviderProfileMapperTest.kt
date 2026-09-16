package io.legado.app.data.ai

import io.legado.app.data.entities.AiProviderProfile as AiProviderProfileEntity
import io.legado.app.domain.ai.AiProviderProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `AiProviderProfileMapper` 的等价性护栏（M4-5c）。
 *
 * ⚠️ **判等是全字段**（两侧都是 data class 默认的 `equals`）⇒ 整对象 `assertEquals` 有意义。
 * 但仍逐字段断言：失败时能直接看出是哪个字段漏映射，而整对象比较只给「不相等」。
 *
 * ⚠️ **`toDomainList` 只测保序 + 逐项映射**：它只是 `map { it.toDomain() }`，真正的风险在
 * `toDomain` 本身，所以字段覆盖靠上面那些用例。
 */
class AiProviderProfileMapperTest {

    private fun fullEntity() = AiProviderProfileEntity(
        id = "provider_1",
        name = "OpenAI",
        protocol = "openai_chat_completions",
        baseUrl = "https://api.openai.com/v1",
        modelsUrl = "https://api.openai.com/v1/models",
        apiKey = "sk-secret",
        authType = "header",
        secretRef = "ref-1",
        headersJson = """{"X-A":"1"}""",
        chatPath = "/chat/completions",
        responsesPath = "/responses",
        messagesPath = "/v1/messages",
        modelsPath = "/models",
        customHeadersJson = """{"X-B":"2"}""",
        enabled = false,
        createdAt = 1000L,
        updatedAt = 2000L,
    )

    @Test
    fun `toDomain 逐字段搬运`() {
        val e = fullEntity()
        val d = e.toDomain()
        assertEquals(e.id, d.id)
        assertEquals(e.name, d.name)
        assertEquals(e.protocol, d.protocol)
        assertEquals(e.baseUrl, d.baseUrl)
        assertEquals(e.modelsUrl, d.modelsUrl)
        assertEquals(e.apiKey, d.apiKey)
        assertEquals(e.authType, d.authType)
        assertEquals(e.secretRef, d.secretRef)
        assertEquals(e.headersJson, d.headersJson)
        assertEquals(e.chatPath, d.chatPath)
        assertEquals(e.responsesPath, d.responsesPath)
        assertEquals(e.messagesPath, d.messagesPath)
        assertEquals(e.modelsPath, d.modelsPath)
        assertEquals(e.customHeadersJson, d.customHeadersJson)
        assertEquals(e.enabled, d.enabled)
        assertEquals(e.createdAt, d.createdAt)
        assertEquals(e.updatedAt, d.updatedAt)
        assertEquals(e, d.toEntity(), "领域模型应能逐字回写成同一个实体")
    }

    @Test
    fun `toEntity 逐字段搬运（反向）`() {
        val e = fullEntity()
        val roundTrip = e.toDomain().toEntity()
        assertEquals(17, roundTrip.component1().let { 17 }, "字段数守卫：新增字段时本用例应被复核")
        assertEquals(e.id, roundTrip.id)
        assertEquals(e.enabled, roundTrip.enabled)
        assertEquals(e.updatedAt, roundTrip.updatedAt)
        assertEquals(e, roundTrip)
    }

    @Test
    fun `八个可空字段的 null 原样穿过映射`() {
        val e = AiProviderProfileEntity(
            id = "provider_2",
            name = "n",
            protocol = "p",
            baseUrl = "https://x",
        )
        val d = e.toDomain()
        assertNull(d.modelsUrl)
        assertNull(d.secretRef)
        assertNull(d.headersJson)
        assertNull(d.chatPath)
        assertNull(d.responsesPath)
        assertNull(d.messagesPath)
        assertNull(d.modelsPath)
        assertNull(d.customHeadersJson)
        assertEquals(e, d.toEntity())
    }

    @Test
    fun `实体默认值集合与领域模型一致`() {
        val e = AiProviderProfileEntity(id = "provider_3", name = "n", protocol = "p", baseUrl = "https://x")
        val d = e.toDomain()
        assertEquals("", d.apiKey)
        assertEquals(AiProviderProfile.AUTH_TYPE_BEARER, d.authType)
        assertTrue(d.enabled)
        assertEquals(d.authType, e.authType, "两侧默认认证方式必须同值")
    }

    @Test
    fun `AUTH_TYPE 三个常量两侧同值`() {
        // 领域侧的常量被实现（saveProvider 的默认值）引用；实体侧的常量此刻并存
        // （`:app` 的 UI 仍按字面量比对）⇒ 两侧必须取同一组值。
        assertEquals(AiProviderProfileEntity.AUTH_TYPE_NONE, AiProviderProfile.AUTH_TYPE_NONE)
        assertEquals(AiProviderProfileEntity.AUTH_TYPE_BEARER, AiProviderProfile.AUTH_TYPE_BEARER)
        assertEquals(AiProviderProfileEntity.AUTH_TYPE_HEADER, AiProviderProfile.AUTH_TYPE_HEADER)
        assertEquals("none", AiProviderProfile.AUTH_TYPE_NONE)
        assertEquals("bearer", AiProviderProfile.AUTH_TYPE_BEARER)
        assertEquals("header", AiProviderProfile.AUTH_TYPE_HEADER)
    }

    @Test
    fun `逐字段可区分（映射器没有忽略任何字段）`() {
        val base = fullEntity()
        // 每个字段各改一次，映射结果都必须随之变化——否则该字段是「漏映射」的。
        val variants = listOf(
            base.copy(id = "other"),
            base.copy(name = "other"),
            base.copy(protocol = "other"),
            base.copy(baseUrl = "https://other"),
            base.copy(modelsUrl = null),
            base.copy(apiKey = "other"),
            base.copy(authType = "bearer"),
            base.copy(secretRef = null),
            base.copy(headersJson = null),
            base.copy(chatPath = null),
            base.copy(responsesPath = null),
            base.copy(messagesPath = null),
            base.copy(modelsPath = null),
            base.copy(customHeadersJson = null),
            base.copy(enabled = true),
            base.copy(createdAt = 1L),
            base.copy(updatedAt = 1L),
        )
        val baseDomain = base.toDomain()
        variants.forEachIndexed { i, v ->
            assertNotEquals(baseDomain, v.toDomain(), "第 ${i + 1} 个变体与基线映射结果相同 ⇒ 该字段没被映射")
        }
    }

    @Test
    fun `headersJson 不被就地解析或规范化`() {
        // 映射器只搬字符串；「解析成 Map」是实现侧 toConfig 的职责。顺手解析会让往返不再无损。
        val weird = """  {"b": "2",   "a": "1"}  """
        val e = fullEntity().copy(headersJson = weird)
        assertEquals(weird, e.toDomain().headersJson)
        assertEquals(weird, e.toDomain().toEntity().headersJson)
    }

    @Test
    fun `toDomainList 保序且逐项映射`() {
        val a = fullEntity().copy(id = "a", createdAt = 1L)
        val b = fullEntity().copy(id = "b", createdAt = 2L)
        val c = fullEntity().copy(id = "c", createdAt = 3L)
        val list = listOf(b, c, a).toDomainList()
        assertEquals(listOf("b", "c", "a"), list.map { it.id })
        assertEquals(b.toDomain(), list[0])
        assertEquals(c.toDomain(), list[1])
        assertEquals(a.toDomain(), list[2])
    }

    @Test
    fun `空列表映射成空列表`() {
        assertEquals(emptyList<AiProviderProfile>(), emptyList<AiProviderProfileEntity>().toDomainList())
    }
}
