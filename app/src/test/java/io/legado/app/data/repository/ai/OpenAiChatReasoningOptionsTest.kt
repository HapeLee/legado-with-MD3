package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiGenerationParams
import io.legado.app.domain.model.AiModelConfig
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAiChatReasoningOptionsTest {

    @Test
    fun `deepseek v4 explicitly disables thinking`() {
        val body = mutableMapOf<String, Any?>()

        body.applyOpenAiChatReasoningOptions(request(AiReasoningLevel.OFF))

        assertEquals(mapOf("type" to "disabled"), body["thinking"])
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun `deepseek v4 explicitly enables high effort thinking`() {
        val body = mutableMapOf<String, Any?>()

        body.applyOpenAiChatReasoningOptions(request(AiReasoningLevel.HIGH))

        assertEquals(mapOf("type" to "enabled"), body["thinking"])
        assertEquals("high", body["reasoning_effort"])
    }

    private fun request(reasoningLevel: AiReasoningLevel): AiGenerateRequest {
        val provider = AiProviderConfig(
            id = "deepseek",
            name = "DeepSeek",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.deepseek.com",
            apiKey = "test-key",
        )
        return AiGenerateRequest(
            model = AiModelConfig(
                id = "deepseek-v4-flash",
                provider = provider,
                displayName = "DeepSeek V4 Flash",
                modelId = "deepseek-v4-flash",
            ),
            messages = emptyList(),
            params = AiGenerationParams(reasoningLevel = reasoningLevel),
        )
    }
}
