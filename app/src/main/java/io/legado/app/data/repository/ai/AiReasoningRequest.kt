package io.legado.app.data.repository.ai

import io.legado.app.domain.model.AiGenerateRequest
import io.legado.app.domain.model.AiReasoningLevel
import io.legado.app.domain.model.AiProtocol

private val deepSeekV4 = Regex("^deepseek-v4(?:[.:-]|$)")

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
