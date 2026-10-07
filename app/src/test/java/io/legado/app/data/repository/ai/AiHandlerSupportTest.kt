package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHandlerSupportTest {

    @Test
    fun detectsOpenCodeByBaseUrlEvenForCustomProfile() {
        assertTrue(provider(baseUrl = "https://opencode.ai/zen/go/v1").requiresSessionHeader())
        assertTrue(provider(id = "opencode_go", name = "OpenCode Go").requiresSessionHeader())
        assertFalse(provider().requiresSessionHeader())
    }

    @Test
    fun sessionHeaderUsesConversationIdThenFallsBackToProviderId() {
        val openCode = provider(baseUrl = "https://opencode.ai/zen/go/v1")

        assertEquals(mapOf(AI_SESSION_HEADER to "chat_123"), openCode.sessionHeaders("chat_123"))
        assertEquals(mapOf(AI_SESSION_HEADER to "provider_1"), openCode.sessionHeaders(null))
        // A blank conversation id is treated as "no conversation" and falls back to the provider id.
        assertEquals(mapOf(AI_SESSION_HEADER to "provider_1"), openCode.sessionHeaders("   "))
    }

    @Test
    fun nonOpenCodeProviderNeverSendsSessionHeader() {
        assertEquals(emptyMap<String, String>(), provider().sessionHeaders("chat_123"))
    }

    @Test
    fun userCustomHeaderOverridesGeneratedSessionHeader() {
        val openCode = provider(
            baseUrl = "https://opencode.ai/zen/go/v1",
            customHeaders = mapOf(AI_SESSION_HEADER to "user-value")
        )

        val merged = openCode.aiRequestHeaders("chat_123", mapOf("Authorization" to "Bearer key"))

        assertEquals("user-value", merged[AI_SESSION_HEADER])
        assertEquals("Bearer key", merged["Authorization"])
    }

    private fun provider(
        id: String = "provider_1",
        name: String = "Custom Provider",
        baseUrl: String = "https://example.com/v1",
        customHeaders: Map<String, String> = emptyMap()
    ) = AiProviderConfig(
        id = id,
        name = name,
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = baseUrl,
        apiKey = "test",
        customHeaders = customHeaders
    )
}
