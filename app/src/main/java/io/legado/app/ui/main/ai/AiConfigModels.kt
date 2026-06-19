package io.legado.app.ui.main.ai

import androidx.annotation.Keep
import java.util.UUID

@Keep
data class AiProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val baseUrl: String,
    val apiKey: String = "",
    val headers: String? = "",
    val apiMode: String = AI_API_MODE_CHAT_COMPLETIONS,
    val promptCache: Boolean = false
)

const val AI_API_MODE_CHAT_COMPLETIONS = "chat_completions"
const val AI_API_MODE_RESPONSES = "responses"

@Keep
data class AiModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String
)

@Keep
data class AiMcpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpoint: String,
    val apiKey: String = "",
    val enabled: Boolean = true
)

@Keep
data class AiSkillConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val content: String,
    val sourceUrl: String = "",
    val enabled: Boolean = true
)

@Keep
data class AiContextSummary(
    val summary: String = "",
    val sourceMessageCount: Int = 0,
    val sourceChars: Int = 0,
    val summaryChars: Int = 0,
    val summaryTokens: Int = 0,
    val recentTokens: Int = 0,
    val preparedTokens: Int = 0,
    val limitTokens: Int = 0,
    val lastMessageId: String = "",
    val lastMessageCreatedAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isValid: Boolean
        get() = summary.isNotBlank() && sourceMessageCount > 0
}

@Keep
data class AiPersonaConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val prompt: String,
    val current: Boolean = false
)

@Keep
data class AiImageProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = TYPE_OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val headers: String = "",
    val model: String = "",
    val defaultParamsJson: String = "",
    val stylePrompt: String = "",
    val jsLib: String = "",
    val loginUrl: String = "",
    val loginUi: String = "",
    val enabledCookieJar: Boolean = false,
    val script: String = "",
    val timeoutMillisecond: Long = 120_000L,
    val order: Int = 0,
    val enabled: Boolean = true
) {
    fun displayName(): String = name.ifBlank { type }

    fun validTimeout(): Long {
        val normalized = timeoutMillisecond.takeIf { it > 0L } ?: 300_000L
        return normalized.coerceIn(60_000L, 600_000L)
    }

    companion object {
        const val TYPE_OPENAI = "openai"
        const val TYPE_JS = "js"
    }
}
