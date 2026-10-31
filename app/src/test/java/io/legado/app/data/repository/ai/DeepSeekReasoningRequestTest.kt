package io.legado.app.data.repository.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.*
import io.legado.app.utils.GSON
import org.junit.Assert.*
import org.junit.Test

class DeepSeekReasoningRequestTest {
    private val protocols = listOf(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiProtocol.OPENAI_RESPONSES, AiProtocol.ANTHROPIC_MESSAGES)
    private fun request(protocol: String, level: AiReasoningLevel, capabilities: Set<String> = emptySet(),
        model: String = "deepseek-flash", providerId: String = "custom", url: String = "https://proxy.invalid/v1") = AiGenerateRequest(
        model = AiModelConfig("model", AiProviderConfig(providerId, providerId, protocol, url, "not-a-real-key"), "Model", model, capabilities = capabilities),
        messages = listOf(AiMessage(AiMessageRole.USER, "Translate")), params = AiGenerationParams(reasoningLevel = level),
    )
    private fun wire(request: AiGenerateRequest): JsonObject {
        val body = mutableMapOf<String, Any?>("model" to request.model.modelId)
        when (request.model.provider.protocol) {
            AiProtocol.OPENAI_CHAT_COMPLETIONS -> body.applyChatReasoning(request)
            AiProtocol.OPENAI_RESPONSES -> body.applyResponsesReasoning(request)
            AiProtocol.ANTHROPIC_MESSAGES -> body.applyAnthropicReasoning(request)
        }
        return JsonParser.parseString(GSON.toJson(body)).asJsonObject
    }

    @Test fun `DeepSeek flash off serializes an actual disable switch with or without capability metadata`() {
        for (capabilities in listOf(emptySet(), setOf(AiCapability.REASONING))) {
            val json = wire(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.OFF, capabilities))
            assertEquals("disabled", json.getAsJsonObject("thinking")?.get("type")?.asString)
            assertFalse(json.has("reasoning_effort"))
        }
    }

    @Test fun `all explicit levels use DeepSeek documented canonical values on every protocol`() {
        val levels = mapOf(AiReasoningLevel.LOW to "low", AiReasoningLevel.MEDIUM to "high", AiReasoningLevel.HIGH to "high",
            AiReasoningLevel.XHIGH to "high", AiReasoningLevel.MAX to "max")
        for (protocol in protocols) for ((level, expected) in levels) {
            val json = wire(request(protocol, level))
            val effort = when (protocol) {
                AiProtocol.OPENAI_RESPONSES -> json.getAsJsonObject("reasoning")?.get("effort")?.asString
                AiProtocol.ANTHROPIC_MESSAGES -> json.getAsJsonObject("output_config")?.get("effort")?.asString
                else -> json.get("reasoning_effort")?.asString
            }
            assertEquals("$protocol $level", expected, effort)
            if (protocol != AiProtocol.OPENAI_RESPONSES) assertEquals("enabled", json.getAsJsonObject("thinking").get("type").asString)
        }
    }

    @Test fun `DeepSeek responses and anthropic off omit conflicting effort and unsupported summary fields`() {
        val responses = wire(request(AiProtocol.OPENAI_RESPONSES, AiReasoningLevel.OFF))
        assertEquals("none", responses.getAsJsonObject("reasoning")?.get("effort")?.asString)
        assertFalse(responses.getAsJsonObject("reasoning").has("summary"))
        val anthropic = wire(request(AiProtocol.ANTHROPIC_MESSAGES, AiReasoningLevel.OFF))
        assertEquals("disabled", anthropic.getAsJsonObject("thinking")?.get("type")?.asString)
        assertFalse(anthropic.has("output_config"))
    }

    @Test fun `DeepSeek auto leaves reasoning choices to model defaults`() {
        for (protocol in protocols) {
            val json = wire(request(protocol, AiReasoningLevel.AUTO, setOf(AiCapability.REASONING)))
            assertEquals(setOf("model"), json.keySet())
        }
    }

    @Test fun `custom provider identity cannot silently suppress DeepSeek low effort`() {
        val json = wire(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.LOW, setOf(AiCapability.REASONING),
            providerId = "my-custom-service"))
        assertEquals("low", json.get("reasoning_effort")?.asString)
        assertEquals("enabled", json.getAsJsonObject("thinking")?.get("type")?.asString)
    }

    @Test fun `unrelated providers and legacy DeepSeek reasoner retain their existing behavior`() {
        val openai = wire(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.MEDIUM, setOf(AiCapability.REASONING), model = "gpt-5.2", providerId = "openai"))
        assertEquals("medium", openai.get("reasoning_effort").asString)
        assertFalse(openai.has("thinking"))
        val claude = wire(request(AiProtocol.ANTHROPIC_MESSAGES, AiReasoningLevel.HIGH, setOf(AiCapability.REASONING), model = "claude-sonnet-4", providerId = "anthropic"))
        assertEquals("adaptive", claude.getAsJsonObject("thinking").get("type").asString)
        val legacy = wire(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.OFF, model = "deepseek-reasoner"))
        assertEquals(setOf("model"), legacy.keySet())
    }

    @Test fun `modern aliases use native controls while legacy and unsupported protocols do not`() {
        for (model in listOf("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp", "DeepSeek/DeepSeek-V4.1-Flash", "deepseek-v4-pro")) {
            val json = wire(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.OFF, model = model))
            assertEquals(model, "disabled", json.getAsJsonObject("thinking")?.get("type")?.asString)
        }
        for (model in listOf("deepseek-reasoner", "deepseek-chat", "DeepSeek-R1", "deepseek-v3.2", "gpt-5.2")) {
            assertNull(request(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiReasoningLevel.OFF, model = model).deepSeekReasoningParameters())
        }
        assertNull(request(AiProtocol.GOOGLE_TRANSLATE, AiReasoningLevel.LOW).deepSeekReasoningParameters())
    }
}
