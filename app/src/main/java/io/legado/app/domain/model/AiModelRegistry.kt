package io.legado.app.domain.model

/**
 * Lightweight model capability registry inspired by RikkaHub's ModelRegistry.
 *
 * Infers model capabilities (tools, reasoning, vision, streaming) from the model ID
 * using token-based pattern matching. Used during model import to auto-populate
 * the capabilities field, and during request building to decide which parameters to send.
 */
object AiModelRegistry {

    private data class ModelPattern(
        val tokens: List<String>,
        val notTokens: List<String> = emptyList(),
        val exactIds: Set<String> = emptySet(),
        val capabilities: Set<String>
    )

    private val patterns = listOf(
        // OpenAI GPT-4o / GPT-4.1 / GPT-5
        ModelPattern(
            tokens = listOf("gpt"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.VISION, AiCapability.STREAMING)
        ),
        // OpenAI o-series (reasoning models)
        ModelPattern(
            tokens = listOf("o1"),
            notTokens = listOf("mini"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.STREAMING)
        ),
        ModelPattern(
            tokens = listOf("o3"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.STREAMING)
        ),
        ModelPattern(
            tokens = listOf("o4"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.STREAMING)
        ),
        // Claude
        ModelPattern(
            tokens = listOf("claude"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.VISION, AiCapability.STREAMING)
        ),
        // DeepSeek Reasoner
        ModelPattern(
            tokens = listOf("deepseek", "reasoner"),
            capabilities = setOf(AiCapability.REASONING, AiCapability.STREAMING)
        ),
        // DeepSeek Chat
        ModelPattern(
            tokens = listOf("deepseek"),
            notTokens = listOf("reasoner"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
        // Gemini
        ModelPattern(
            tokens = listOf("gemini"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.VISION, AiCapability.STREAMING)
        ),
        // Xiaomi MiMo
        ModelPattern(
            tokens = listOf("mimo"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.REASONING, AiCapability.STREAMING)
        ),
        // Qwen
        ModelPattern(
            tokens = listOf("qwen"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
        // Grok
        ModelPattern(
            tokens = listOf("grok"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.VISION, AiCapability.STREAMING)
        ),
        // Doubao / Kimi / Step / GLM / MiniMax — assume tools + streaming
        ModelPattern(
            tokens = listOf("doubao"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
        ModelPattern(
            tokens = listOf("kimi"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
        ModelPattern(
            tokens = listOf("step"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
        ModelPattern(
            tokens = listOf("glm"),
            capabilities = setOf(AiCapability.TOOLS, AiCapability.STREAMING)
        ),
    )

    /**
     * Infer capabilities from a model ID using token-based matching.
     * Returns the capabilities of the highest-scoring pattern match.
     * Falls back to STREAMING only if nothing matches.
     */
    fun inferCapabilities(modelId: String): Set<String> {
        val lower = modelId.lowercase()
        val scored = patterns.mapNotNull { pattern ->
            if (pattern.exactIds.isNotEmpty() && modelId in pattern.exactIds) {
                return pattern.capabilities
            }
            if (pattern.notTokens.any { it in lower }) return@mapNotNull null
            if (pattern.tokens.all { it in lower }) {
                pattern to pattern.tokens.size
            } else null
        }
        return scored.maxByOrNull { it.second }?.first?.capabilities
            ?: setOf(AiCapability.STREAMING)
    }
}
