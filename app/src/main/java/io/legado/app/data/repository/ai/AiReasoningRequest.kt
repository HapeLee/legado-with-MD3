package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiHttpException
import io.legado.app.domain.model.AiProviderConfig
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiProtocol
import java.util.concurrent.ConcurrentHashMap

private val deepSeekV4 = Regex("^deepseek-v4(?:[.:-]|$)")

/**
 * Remembers provider/model endpoints whose chat-completions API rejected the vendor-specific
 * `thinking` object (typical of OpenAI-compatible gateways that only accept `reasoning_effort`).
 * Kept in memory: after the first detected rejection we build a compatible body up front, so a
 * user does not pay a failed request on every single chat turn.
 */
internal object AiThinkingCompat {
    private val rejected = ConcurrentHashMap.newKeySet<String>()
    private fun key(provider: AiProviderConfig, modelId: String): String = "${provider.id}|$modelId"

    fun isRejected(provider: AiProviderConfig, modelId: String): Boolean = key(provider, modelId) in rejected
    fun markRejected(provider: AiProviderConfig, modelId: String) {
        rejected.add(key(provider, modelId))
    }
}

/** True when a gateway returned 400 stating that `thinking` is unsupported (OpenAI-compatible proxies). */
internal fun Throwable.isThinkingUnsupportedError(): Boolean {
    if (this !is AiHttpException || statusCode != 400) return false
    val text = message.orEmpty().lowercase()
    if (!text.contains("thinking")) return false
    return text.contains("unsupported") || text.contains("not supported") || text.contains("invalid_request")
}

/** Broadly accepted OpenAI-style value, independent of provider identity, for gateways rejecting `thinking`. */
internal fun AiReasoningLevel.standardChatEffort(): String? = when (this) {
    // "none" 并非所有网关都接受；关闭思考时直接不发参数最稳妥。
    AiReasoningLevel.OFF, AiReasoningLevel.AUTO -> null
    AiReasoningLevel.LOW -> "low"
    AiReasoningLevel.MEDIUM -> "medium"
    AiReasoningLevel.HIGH, AiReasoningLevel.XHIGH, AiReasoningLevel.MAX -> "high"
}

/** Drop the rejected `thinking` object and express the same intent with `reasoning_effort`. */
internal fun MutableMap<String, Any?>.useReasoningEffortInsteadOfThinking(level: AiReasoningLevel) {
    remove("thinking")
    remove("output_config")
    // DeepSeek paths already set a canonical reasoning_effort; only fill it when absent.
    if (!containsKey("reasoning_effort")) {
        level.standardChatEffort()?.let { this["reasoning_effort"] = it }
    }
}

/** DeepSeek Flash / V4 have a model-specific switch, independent of imported capability flags.
 * Docs: https://api-docs.deepseek.com/guides/thinking_mode/ (checked 2026-10-27).
 * Legacy R1/reasoner and unrelated models keep their existing protocol behavior.
 */
internal fun AiGenerateRequest.deepSeekReasoningParameters(): Map<String, Any?>? {
    val id = model.modelId.lowercase().substringAfterLast('/')
    val modern = id == "deepseek-flash" || id.startsWith("deepseek-flash-") || id.startsWith("deepseek-flash:") ||
        deepSeekV4.containsMatchIn(id)
    if (!modern || model.provider.protocol !in setOf(AiProtocol.OPENAI_CHAT_COMPLETIONS, AiProtocol.OPENAI_RESPONSES, AiProtocol.ANTHROPIC_MESSAGES)) return null
    val level = params.reasoningLevel
    if (level == AiReasoningLevel.AUTO) return emptyMap()
    val effort = when (level) {
        AiReasoningLevel.OFF -> "none"
        AiReasoningLevel.LOW -> "low"
        AiReasoningLevel.MEDIUM, AiReasoningLevel.HIGH, AiReasoningLevel.XHIGH -> "high"
        AiReasoningLevel.MAX -> "max"
        AiReasoningLevel.AUTO -> error("Auto is handled above")
    }
    if (model.provider.protocol == AiProtocol.OPENAI_RESPONSES) return mapOf("reasoning" to mapOf("effort" to effort))
    return buildMap {
        put("thinking", mapOf("type" to if (level == AiReasoningLevel.OFF) "disabled" else "enabled"))
        if (level != AiReasoningLevel.OFF) {
            if (model.provider.protocol == AiProtocol.ANTHROPIC_MESSAGES) put("output_config", mapOf("effort" to effort))
            else put("reasoning_effort", effort)
        }
    }
}

/** Shared by streaming and non-streaming request construction. */
internal fun MutableMap<String, Any?>.applyChatReasoning(request: AiGenerateRequest) {
    request.deepSeekReasoningParameters()?.let { putAll(it); return }
    if (hasReasoningCapability(request.model.capabilities)) {
        request.params.reasoningLevel.effortFor(request.model.provider)?.let { this["reasoning_effort"] = it }
    }
    applyZhipuThinking(request.model.provider, request.model.modelId, request.params.reasoningLevel)
}

internal fun MutableMap<String, Any?>.applyResponsesReasoning(request: AiGenerateRequest) {
    request.deepSeekReasoningParameters()?.let { putAll(it); return }
    if (hasReasoningCapability(request.model.capabilities)) {
        this["reasoning"] = buildMap<String, Any> {
            put("summary", "auto")
            request.params.reasoningLevel.effortFor(request.model.provider)?.let { put("effort", it) }
        }
    }
}

internal fun MutableMap<String, Any?>.applyAnthropicReasoning(request: AiGenerateRequest) {
    request.deepSeekReasoningParameters()?.let {
        putAll(it)
        if (request.params.reasoningLevel == AiReasoningLevel.OFF) request.params.temperature?.let { value -> this["temperature"] = value }
        return
    }
    val level = request.params.reasoningLevel
    if (hasReasoningCapability(request.model.capabilities)) {
        this["thinking"] = buildMap<String, Any> {
            put("type", if (level == AiReasoningLevel.OFF) "disabled" else "adaptive")
            if (level != AiReasoningLevel.OFF) put("display", "summarized")
        }
        level.effortFor(request.model.provider)?.let { this["output_config"] = mapOf("effort" to it) }
    } else {
        request.params.temperature?.let { this["temperature"] = it }
    }
}
