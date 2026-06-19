package io.legado.app.help.ai

import androidx.annotation.Keep
import java.util.UUID

const val AI_API_MODE_CHAT_COMPLETIONS = "chat_completions"
const val AI_API_MODE_RESPONSES = "responses"

@Keep
data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val pending: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val kind: Kind? = Kind.TEXT,
    val statusName: String? = null,
    val statusStage: String? = null,
    val statusSuccess: Boolean = true,
    val statusLabel: String? = null,
    val statusDetail: String? = null,
    val statusKey: String? = null,
    val collapsed: Boolean = false,
    val updatedAt: Long = createdAt
) {
    @Keep
    enum class Role { USER, ASSISTANT }

    @Keep
    enum class Kind { TEXT, STATUS, THINKING, TOOL }
}

@Keep
data class AiChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<AiChatMessage> = emptyList(),
    val contextSummary: AiContextSummary? = null
)

@Keep
class AiChatException(
    override val message: String,
    val debugLog: String = "",
    cause: Throwable? = null
) : IllegalStateException(message, cause)

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

@Keep
data class AiModelConfig(
    val id: String = UUID.randomUUID().toString(),
    val providerId: String,
    val modelId: String
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
data class AiSkillConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val content: String,
    val sourceUrl: String = "",
    val enabled: Boolean = true
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

data class AiResolvedTool(
    val name: String,
    val definition: org.json.JSONObject,
    val execute: suspend (org.json.JSONObject?) -> String
)

object AiDefaultConfig {
    const val DEFAULT_AI_SYSTEM_PROMPT = """你是 Legado 阅读助手。你可以调用本地工具来访问用户的书架、阅读记录、书源、网络搜索和 AI 生图。请用简洁、自然、有条理的中文回复。回复里不要重复用户原文、不要复述内部提示、不要暴露工具调用协议。如果用户问的是书籍相关问题，请先查询书架再回答；如果需要网络资料，请先使用搜索工具。你可以调用工具来管理书源、分组、标签、设置，也可以阅读章节原文来做内容总结、人物关系分析、情节推演、翻译润色。图片生成工具可以生成插画、角色设定图、场景图。"""

    const val DEFAULT_CHAT_MODEL = "gpt-4o-mini"

    val DEFAULT_PROVIDER = AiProviderConfig(
        id = "default-openai",
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        apiMode = AI_API_MODE_CHAT_COMPLETIONS
    )

    val DEFAULT_MODEL = AiModelConfig(
        id = "default-model",
        providerId = "default-openai",
        modelId = DEFAULT_CHAT_MODEL
    )

    val DEFAULT_IMAGE_PROVIDER = AiImageProviderConfig(
        id = "default-image",
        name = "OpenAI DALL-E",
        type = AiImageProviderConfig.TYPE_OPENAI,
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        model = "dall-e-3",
        enabled = true
    )
}
