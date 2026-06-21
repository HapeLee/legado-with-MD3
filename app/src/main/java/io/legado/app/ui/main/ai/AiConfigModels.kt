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
data class AiChatCompanionConfig(
    val id: String = UUID.randomUUID().toString(),
    val type: String = TYPE_DEFAULT,
    val name: String,
    val avatar: String = "",
    val bookKey: String = "",
    val characterId: String = "",
    val prompt: String = "",
    val worldBookIds: List<String> = emptyList(),
    val ttsRouteJson: String = "",
    val order: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE_DEFAULT = "default"
        const val TYPE_CHARACTER = "character"
        const val DEFAULT_COMPANION_ID = "default_assistant"
    }
}

@Keep
data class AiWorldBookConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val version: Int = 1,
    val type: String = TYPE_LOREBOOK,
    val scope: String = SCOPE_GLOBAL,
    val bookKey: String = "",
    val enabled: Boolean = true,
    val bindingVersion: Int = 1,
    val maxEntries: Int = 12,
    val bindings: List<AiWorldBookBinding> = emptyList(),
    val order: Int = 0,
    val entries: List<AiWorldBookEntry> = emptyList()
) {
    companion object {
        const val TYPE_LOREBOOK = "lorebook"
        const val SCOPE_GLOBAL = "global"
        const val SCOPE_BOOK = "book"
        const val SCOPE_SESSION = "session"
    }
}

@Keep
data class AiWorldBookBinding(
    val id: String = UUID.randomUUID().toString(),
    val targetType: String = TARGET_GLOBAL,
    val targetKey: String = "",
    val enabled: Boolean = true,
    val order: Int = 0
) {
    companion object {
        const val TARGET_GLOBAL = "global"
        const val TARGET_COMPANION = "companion"
        const val TARGET_CHAT = "chat"
        const val TARGET_READ_AI = "read_ai"
        const val TARGET_BOOK = "book"
        const val TARGET_SESSION = "session"
    }
}

@Keep
data class AiWorldBookEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val name: String = "",
    val content: String,
    val keys: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val secondaryKeys: List<String> = emptyList(),
    val excludeKeys: List<String> = emptyList(),
    val regexEnabled: Boolean = false,
    val useRegex: Boolean = false,
    val caseSensitive: Boolean = false,
    val enabled: Boolean = true,
    val constant: Boolean = false,
    val constantActive: Boolean = false,
    val priority: Int = 50,
    val position: String = POSITION_AFTER_SYSTEM_PROMPT,
    val injectDepth: Int = 4,
    val role: String = ROLE_USER,
    val scanDepth: Int = 8,
    val maxMatches: Int = 1,
    val order: Int = 0
) {
    companion object {
        const val POSITION_AFTER_SYSTEM_PROMPT = "after_system_prompt"
        const val POSITION_BEFORE_PROMPT = "before_prompt"
        const val POSITION_INJECT_DEPTH = "inject_depth"
        const val POSITION_BEFORE_LAST_USER = "before_last_user"

        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

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
