package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAiChatThinkingTest {

    @Test
    fun zhipuGlmDisablesThinkingWhenReasoningIsOff() {
        val body = mutableMapOf<String, Any?>()

        body.applyZhipuThinking(zhipuProvider(), "glm-4.7-flash", AiReasoningLevel.OFF)

        assertEquals(mapOf("type" to "disabled"), body["thinking"])
    }

    @Test
    fun zhipuGlmEnablesThinkingWhenReasoningIsEnabled() {
        val body = mutableMapOf<String, Any?>()

        body.applyZhipuThinking(zhipuProvider(), "glm-4.7-flash", AiReasoningLevel.MEDIUM)

        assertEquals(mapOf("type" to "enabled"), body["thinking"])
    }

    @Test
    fun unrelatedProviderDoesNotReceiveThinkingParameter() {
        val body = mutableMapOf<String, Any?>()
        val provider = AiProviderConfig(
            id = "openai",
            name = "OpenAI",
            protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "test"
        )

        body.applyZhipuThinking(provider, "gpt-5", AiReasoningLevel.OFF)

        assertFalse(body.containsKey("thinking"))
    }

    private fun zhipuProvider() = AiProviderConfig(
        id = "zhipu",
        name = "Zhipu AI",
        protocol = AiProtocol.OPENAI_CHAT_COMPLETIONS,
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        apiKey = "test"
    )
}
