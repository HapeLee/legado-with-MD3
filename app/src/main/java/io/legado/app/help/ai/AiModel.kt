package io.legado.app.help.ai

import android.graphics.Bitmap

/**
 * AI 提供商枚举
 */
enum class AiProvider(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE("Google Gemini"),
    DEEPSEEK("DeepSeek"),
    QWEN("通义千问"),
    SILICONFLOW("硅基流动"),
    XFYUN("科大讯飞"),
    ZHIPU("智谱AI"),
    OLLAMA("Ollama"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter")
}

/**
 * AI 消息
 */
data class AiMessage(
    val role: String,
    val content: String
)

/**
 * 单条流式响应
 */
data class ChatStreamChunk(
    val delta: String?,
    val error: String?,
    val done: Boolean = false
)

/**
 * 生成的图像
 */
data class GeneratedImage(
    val url: String?,
    val prompt: String,
    val revisedPrompt: String? = null,
    val base64: String? = null,
    val bitmap: Bitmap? = null
)

/**
 * AI 预设角色
 */
data class AiPreset(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val greeting: String? = null
)

/**
 * 文本工具
 */
data class TextTool(
    val id: String,
    val name: String,
    val systemPrompt: String,
    val inputHint: String = "在此输入要处理的文本...",
    val actionName: String = "处理"
)

/**
 * AI 提供商配置
 */
data class AiProviderConfig(
    val provider: AiProvider = AiProvider.OPENAI,
    val endpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val imageModel: String = "dall-e-3",
    val videoModel: String = "sora",
    val visionModel: String = "gpt-4o",
    val ttsModel: String = "tts-1",
    val temperature: Float = 0.7f,
    val timeoutSeconds: Int = 120
)

/**
 * 每个提供商的默认值
 */
fun defaultProviderConfig(provider: AiProvider): AiProviderConfig {
    return when (provider) {
        AiProvider.OPENAI -> AiProviderConfig(
            provider = AiProvider.OPENAI,
            endpoint = "https://api.openai.com/v1",
            chatModel = "gpt-4o-mini",
            imageModel = "dall-e-3",
            visionModel = "gpt-4o",
            ttsModel = "tts-1"
        )
        AiProvider.ANTHROPIC -> AiProviderConfig(
            provider = AiProvider.ANTHROPIC,
            endpoint = "https://api.anthropic.com/v1",
            chatModel = "claude-3-5-haiku-latest",
            imageModel = "claude-3-5-sonnet",
            visionModel = "claude-3-5-sonnet-latest",
            ttsModel = ""
        )
        AiProvider.GOOGLE -> AiProviderConfig(
            provider = AiProvider.GOOGLE,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            chatModel = "gemini-2.0-flash",
            imageModel = "imagen-3.0-generate-002",
            visionModel = "gemini-2.0-flash",
            ttsModel = ""
        )
        AiProvider.DEEPSEEK -> AiProviderConfig(
            provider = AiProvider.DEEPSEEK,
            endpoint = "https://api.deepseek.com/v1",
            chatModel = "deepseek-chat",
            imageModel = "",
            visionModel = "deepseek-chat",
            ttsModel = ""
        )
        AiProvider.QWEN -> AiProviderConfig(
            provider = AiProvider.QWEN,
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            chatModel = "qwen-plus",
            imageModel = "wanx-v1",
            visionModel = "qwen-vl-max",
            ttsModel = ""
        )
        AiProvider.SILICONFLOW -> AiProviderConfig(
            provider = AiProvider.SILICONFLOW,
            endpoint = "https://api.siliconflow.cn/v1",
            chatModel = "Qwen/Qwen2.5-7B-Instruct",
            imageModel = "",
            visionModel = "",
            ttsModel = ""
        )
        AiProvider.XFYUN -> AiProviderConfig(
            provider = AiProvider.XFYUN,
            endpoint = "https://spark-api-open.xf-yun.com/v1",
            chatModel = "generalv3.5",
            imageModel = "",
            visionModel = "",
            ttsModel = ""
        )
        AiProvider.ZHIPU -> AiProviderConfig(
            provider = AiProvider.ZHIPU,
            endpoint = "https://open.bigmodel.cn/api/paas/v4",
            chatModel = "glm-4-flash",
            imageModel = "",
            visionModel = "glm-4v-flash",
            ttsModel = ""
        )
        AiProvider.OLLAMA -> AiProviderConfig(
            provider = AiProvider.OLLAMA,
            endpoint = "http://localhost:11434/v1",
            chatModel = "llama3.2",
            imageModel = "",
            visionModel = "llava",
            ttsModel = ""
        )
        AiProvider.GROQ -> AiProviderConfig(
            provider = AiProvider.GROQ,
            endpoint = "https://api.groq.com/openai/v1",
            chatModel = "llama-3.3-70b-versatile",
            imageModel = "",
            visionModel = "llama-3.2-90b-vision-preview",
            ttsModel = ""
        )
        AiProvider.OPENROUTER -> AiProviderConfig(
            provider = AiProvider.OPENROUTER,
            endpoint = "https://openrouter.ai/api/v1",
            chatModel = "openai/gpt-4o-mini",
            imageModel = "",
            visionModel = "openai/gpt-4o",
            ttsModel = ""
        )
    }
}

/**
 * 内置聊天预设
 */
val BUILT_IN_PRESETS: List<AiPreset> = listOf(
    AiPreset(
        id = "general",
        name = "通用助手",
        systemPrompt = "你是一个乐于助人的 AI 助手，请用中文回答用户问题。"
    ),
    AiPreset(
        id = "creative-writer",
        name = "创意写作",
        systemPrompt = "你是一位富有想象力的创意写作助手。请帮助用户构思小说情节、人物设定、场景描写等，用中文回答。"
    ),
    AiPreset(
        id = "translator",
        name = "翻译助手",
        systemPrompt = "你是一位专业的翻译助手。请准确、自然地进行中英文互译，保留原有风格与语气。"
    ),
    AiPreset(
        id = "code-helper",
        name = "编程助手",
        systemPrompt = "你是一位资深的编程助手，熟悉 Kotlin、Java、Python、JavaScript、Android 开发。请提供简洁可运行的代码与清晰解释。"
    ),
    AiPreset(
        id = "summarizer",
        name = "摘要助手",
        systemPrompt = "你是一位文档摘要专家。请用简洁的中文总结用户提供的文本，保留关键信息与逻辑要点。"
    ),
    AiPreset(
        id = "book-recommender",
        name = "阅读顾问",
        systemPrompt = "你是一位资深的阅读顾问，熟悉小说、传记、科技、历史等多个领域。请基于用户的阅读偏好给出书籍推荐与分析，用中文回答。"
    )
)

/**
 * 文本工具箱
 */
val TEXT_TOOLS: List<TextTool> = listOf(
    TextTool(
        id = "translate-en",
        name = "中译英",
        systemPrompt = "请将用户提供的中文文本翻译成地道、自然的英文。",
        inputHint = "输入要翻译的中文文本...",
        actionName = "翻译"
    ),
    TextTool(
        id = "translate-zh",
        name = "英译中",
        systemPrompt = "请将用户提供的英文文本翻译成准确、流畅的中文。",
        inputHint = "输入要翻译的英文文本...",
        actionName = "翻译"
    ),
    TextTool(
        id = "polish",
        name = "润色",
        systemPrompt = "请对用户提供的中文文本进行润色，保持原意不变，使表达更准确、更优雅、更通顺。",
        inputHint = "输入要润色的文本...",
        actionName = "润色"
    ),
    TextTool(
        id = "summarize",
        name = "摘要",
        systemPrompt = "请用简洁的中文为用户提供的文本生成一份不超过 200 字的摘要，保留关键信息。",
        inputHint = "输入要摘要的长文本...",
        actionName = "摘要"
    ),
    TextTool(
        id = "continue-writing",
        name = "续写",
        systemPrompt = "请根据用户提供的文本进行自然、富有创造力的中文续写，保持风格一致，续写不少于 150 字。",
        inputHint = "输入要续写的内容...",
        actionName = "续写"
    ),
    TextTool(
        id = "keywords",
        name = "提取关键词",
        systemPrompt = "请从用户提供的中文文本中提取 5~10 个最具代表性的关键词，用顿号分隔。",
        inputHint = "输入要处理的文本...",
        actionName = "提取"
    ),
    TextTool(
        id = "proofread",
        name = "校对",
        systemPrompt = "请检查用户提供的文本中的错别字、标点与语法问题，列出问题并给出修正版本。",
        inputHint = "输入要校对的文本...",
        actionName = "校对"
    )
)
