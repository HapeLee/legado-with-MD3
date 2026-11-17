package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiHttpException
import io.legado.app.domain.model.AiProtocol
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThinkingFallbackTest {

    private val gateway400 = AiHttpException(
        400,
        "HTTP 400: {\"error\":{\"message\":\"\\\"thinking\\\" is not supported on /v1/chat/completions and was not applied. Use \\\"reasoning_effort\\\" to control thinking.\",\"type\":\"invalid_request_error\",\"param\":\"thinking\",\"code\":\"unsupported_parameter\"}}"
    )

    @Test
    fun `detects gateway thinking rejection`() {
        assertTrue(gateway400.isThinkingUnsupportedError())
        assertTrue(
            AiHttpException(400, "HTTP 400: thinking is not supported here").isThinkingUnsupportedError()
        )
    }

    @Test
    fun `ignores unrelated errors`() {
        assertFalse(AiHttpException(401, "HTTP 401: invalid api key").isThinkingUnsupportedError())
        assertFalse(AiHttpException(500, "HTTP 500: thinking server error").isThinkingUnsupportedError())
        assertFalse(
            AiHttpException(400, "HTTP 400: invalid temperature parameter").isThinkingUnsupportedError()
        )
        assertFalse(Exception("HTTP 400: thinking unsupported").isThinkingUnsupportedError())
    }

    @Test
    fun `strips thinking and adds standard effort`() {
        val body = mutableMapOf<String, Any?>("thinking" to mapOf("type" to "enabled"))
        body.useReasoningEffortInsteadOfThinking(AiReasoningLevel.MEDIUM)
        assertFalse(body.containsKey("thinking"))
        assertTrue(body["reasoning_effort"] == "medium")
    }

    @Test
    fun `preserves existing canonical effort`() {
        val body = mutableMapOf<String, Any?>(
            "thinking" to mapOf("type" to "enabled"),
            "reasoning_effort" to "high"
        )
        body.useReasoningEffortInsteadOfThinking(AiReasoningLevel.MEDIUM)
        assertFalse(body.containsKey("thinking"))
        assertTrue(body["reasoning_effort"] == "high")
    }

    @Test
    fun `off level drops thinking without adding effort`() {
        val body = mutableMapOf<String, Any?>("thinking" to mapOf("type" to "disabled"))
        body.useReasoningEffortInsteadOfThinking(AiReasoningLevel.OFF)
        assertFalse(body.containsKey("thinking"))
        assertFalse(body.containsKey("reasoning_effort"))
    }

    @Test
    fun `standard effort clamps high levels`() {
        assertTrue(AiReasoningLevel.MEDIUM.standardChatEffort() == "medium")
        assertTrue(AiReasoningLevel.HIGH.standardChatEffort() == "high")
        assertTrue(AiReasoningLevel.MAX.standardChatEffort() == "high")
        assertTrue(AiReasoningLevel.AUTO.standardChatEffort() == null)
    }

    @Test
    fun `compat remembers rejected endpoint per provider and model`() {
        val provider = AiProviderConfig("p1", "Gateway", AiProtocol.OPENAI_CHAT_COMPLETIONS, "https://gw.invalid/v1", "k")
        assertFalse(AiThinkingCompat.isRejected(provider, "glm-4.7"))
        AiThinkingCompat.markRejected(provider, "glm-4.7")
        assertTrue(AiThinkingCompat.isRejected(provider, "glm-4.7"))
        assertFalse(AiThinkingCompat.isRejected(provider, "other-model"))
        assertFalse(AiThinkingCompat.isRejected(provider.copy(id = "p2"), "glm-4.7"))
    }
}
