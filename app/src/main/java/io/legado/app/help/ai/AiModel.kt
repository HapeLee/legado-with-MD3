package io.legado.app.help.ai

import android.content.Context
import io.legado.app.utils.toast

/**
 * AI Providers + Config Store (简化实现)
 * 为所有新增 AI 屏幕提供可工作的默认配置。
 * 真实项目中会从 SharedPreferences / DataStore 加载。
 */
enum class AiProvider(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    LOCAL("本地 LLM"),
    OPENAI_COMPATIBLE("OpenAI 兼容 (代理/自建)")
}

data class AiProviderConfig(
    val provider: AiProvider,
    val endpoint: String,
    val apiKey: String,
    val chatModel: String = "gpt-4o-mini",
    val imageModel: String = "dall-e-3",
    val videoModel: String = "sora",
    val visionModel: String = "gpt-4o",
    val ttsModel: String = "tts-1",
    val temperature: Float = 0.7f,
    val timeoutSeconds: Int = 120
)

data class AiMessage(val role: String, val content: String)

data class AiPreset(
    val id: String,
    val name: String,
    val systemPrompt: String
)

data class GeneratedImage(
    val url: String? = null,
    val base64: String? = null,
    val revisedPrompt: String? = null
)

data class TextTool(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String
)

val BUILT_IN_PRESETS: List<AiPreset> = listOf(
    AiPreset("general", "通用助手", "你是一个有帮助的中文通用助手。"),
    AiPreset("novel", "小说顾问", "你是一个资深小说编辑和推荐者。"),
    AiPreset("booksource", "书源生成器", "你是书源规则专家。请根据网站信息输出符合 Legado 的 JSON 书源描述。"),
    AiPreset("validator", "书源校验师", "你负责评估书源是否有效，指出可能的问题字段。"),
    AiPreset("bookshelf", "书架分析", "你是一位阅读顾问，分析用户的书架并给出推荐和建议。"),
    AiPreset("translate", "翻译助手", "你是一位专业的翻译员。"),
    AiPreset("creative", "创意写作", "你是一位富有灵感的创意写作搭档。")
)

val TEXT_TOOLS: List<TextTool> = listOf(
    TextTool("polish", "润色", "润色文本，使表达更自然。", "你是一个润色助手。"),
    TextTool("grammar", "语法检查", "纠错和语法规范。", "你是一位文字校对员。"),
    TextTool("expand", "扩写", "补充细节使内容更丰富。", "你是一位扩写作家。"),
    TextTool("compress", "压缩", "保留核心意思的同时缩短。", "你是一位文本压缩编辑。")
)

object AiConfigStore {
    var streamEnabled: Boolean = true

    private val defaults: Map<AiProvider, AiProviderConfig> = mapOf(
        AiProvider.OPENAI to AiProviderConfig(
            provider = AiProvider.OPENAI,
            endpoint = "https://api.openai.com/v1",
            apiKey = ""
        ),
        AiProvider.ANTHROPIC to AiProviderConfig(
            provider = AiProvider.ANTHROPIC,
            endpoint = "https://api.anthropic.com/v1",
            apiKey = "",
            chatModel = "claude-3-5-sonnet-20241022"
        ),
        AiProvider.GEMINI to AiProviderConfig(
            provider = AiProvider.GEMINI,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "",
            chatModel = "gemini-2.0-flash"
        ),
        AiProvider.LOCAL to AiProviderConfig(
            provider = AiProvider.LOCAL,
            endpoint = "http://127.0.0.1:8080",
            apiKey = "",
            chatModel = "local-llm"
        ),
        AiProvider.OPENAI_COMPATIBLE to AiProviderConfig(
            provider = AiProvider.OPENAI_COMPATIBLE,
            endpoint = "https://api.deepseek.com/v1",
            apiKey = "",
            chatModel = "deepseek-chat"
        )
    )

    private var currentProvider: AiProvider = AiProvider.OPENAI
    private val overrides = mutableMapOf<AiProvider, AiProviderConfig>()

    fun setProvider(provider: AiProvider) {
        currentProvider = provider
    }

    fun currentProvider(): AiProvider = currentProvider

    fun defaultFor(provider: AiProvider): AiProviderConfig {
        return defaults[provider] ?: defaults[AiProvider.OPENAI]!!
    }

    fun save(config: AiProviderConfig) {
        overrides[config.provider] = config
    }

    fun load(provider: AiProvider): AiProviderConfig {
        return overrides[provider] ?: defaultFor(provider)
    }

    fun loadProviderConfigs(): Map<AiProvider, AiProviderConfig> {
        return AiProvider.values().associateWith { load(it) }
    }

    fun currentConfig(): AiProviderConfig {
        return load(currentProvider)
    }
}

object AiToastHelper {
    private var ctx: Context? = null
    fun attach(c: Context) { ctx = c.applicationContext }
    fun toast(msg: String) {
        ctx?.toast(msg)
    }
}
