package io.legado.app.help.ai

/**
 * AI 请求/响应数据模型。兼容 OpenAI 风格 API / Anthropic Claude / Gemini / 其他供应商。
 */

// ========== 通用类型 ==========
data class AiMessage(
    val role: String,           // system / user / assistant
    val content: String
)

/** 供应商类型 */
enum class AiProvider(val displayName: String, val supportsChat: Boolean, val supportsImage: Boolean, val supportsVideo: Boolean, val supportsAudio: Boolean, val supportsVision: Boolean) {
    OPENAI("OpenAI", true, true, false, true, true),
    ANTHROPIC("Anthropic Claude", true, false, false, false, true),
    GOOGLE("Google Gemini", true, true, true, true, true),
    DEEPSEEK("DeepSeek", true, false, false, false, true),
    QWEN("通义千问", true, true, false, true, true),
    WENXIN("文心一言", true, true, false, false, false),
    KIMI("Kimi (月之暗面)", true, false, false, false, true),
    OLLAMA("Ollama (本地)", true, false, false, false, true),
    CUSTOM("自定义 (OpenAI 兼容)", true, false, false, false, false);
}

/** 生成能力分类 */
enum class AiCapability(val displayName: String) {
    Chat("对话"),
    Image("图像生成"),
    Video("视频生成"),
    Audio("语音合成"),
    Vision("视觉分析"),
    Tools("文本工具箱")
}

/** 对话历史 */
data class AiConversation(
    val id: String,
    val title: String,
    val messages: MutableList<AiMessage> = mutableListOf(),
    val systemPrompt: String? = null,
    val temperature: Float = 0.7f,
    val model: String,
    val provider: AiProvider,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** 预设角色 */
data class AiPreset(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val suggestedFirstMessage: String? = null,
    val icon: String = "💬",
    val builtIn: Boolean = false
)

/** 生成任务状态 */
enum class GenerationStatus { Idle, Loading, Success, Error }

// ========== 聊天 ==========
data class ChatCompletionRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Float = 0.7f,
    val max_tokens: Int? = null,
    val stream: Boolean = false
)

data class ChatCompletionResponse(
    val choices: List<ChatChoice>? = null,
    val error: ChatError? = null
) {
    data class ChatChoice(val message: AiMessage?)
    data class ChatError(val message: String?)
}

data class ChatStreamChunk(val delta: String?, val done: Boolean, val error: String?)

// ========== 图像 ==========
data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    val response_format: String = "url",
    val quality: String? = null,
    val style: String? = null,
    val image_base64: String? = null   // 图生图
)

data class ImageGenerationResponse(
    val data: List<ImageItem>? = null,
    val error: ChatCompletionResponse.ChatError? = null
) {
    data class ImageItem(val url: String?, val b64_json: String?, val revised_prompt: String? = null)
}

data class GeneratedImage(
    val id: String,
    val prompt: String,
    val url: String?,
    val b64: String?,
    val revisedPrompt: String?,
    val model: String,
    val createdAt: Long
)

// ========== 视频 ==========
data class VideoGenerationRequest(
    val model: String,
    val prompt: String,
    val aspect_ratio: String? = "16:9",
    val duration_seconds: Int? = 10,
    val image_base64: String? = null
)

data class VideoGenerationResponse(
    val data: VideoItem? = null,
    val error: ChatCompletionResponse.ChatError? = null
) {
    data class VideoItem(val url: String?)
}

// ========== 语音 TTS ==========
data class TtsRequest(
    val model: String,
    val input: String,
    val voice: String = "alloy",
    val response_format: String = "mp3",
    val speed: Float = 1.0f
)

data class TtsResponse(
    val audioBytes: ByteArray? = null,
    val audioUrl: String? = null,
    val error: ChatCompletionResponse.ChatError? = null
)

// ========== 视觉 Vision ==========
data class VisionRequest(
    val model: String,
    val prompt: String,
    val imageBase64: String,
    val detail: String = "auto"
)

// ========== 供应商配置 ==========
data class AiProviderConfig(
    val provider: AiProvider,
    val enabled: Boolean = false,
    val endpoint: String,
    val apiKey: String,
    val chatModel: String = "",
    val imageModel: String = "",
    val videoModel: String = "",
    val ttsModel: String = "",
    val visionModel: String = "",
    val temperature: Float = 0.7f,
    val timeoutSeconds: Long = 120,
    val customName: String? = null
) {
    val displayName: String get() = customName ?: provider.displayName
}

/** 默认图片尺寸选项 */
val IMAGE_SIZE_OPTIONS = listOf("256x256", "512x512", "1024x1024", "1024x1792", "1792x1024")

/** 默认语音选项 */
val TTS_VOICE_OPTIONS = listOf("alloy", "echo", "fable", "onyx", "nova", "shimmer")

/** 预设角色 */
val BUILT_IN_PRESETS: List<AiPreset> = listOf(
    AiPreset(
        id = "general",
        name = "通用助手",
        description = "通用的 AI 对话助手，可以问答、写作、头脑风暴。",
        systemPrompt = "你是一个乐于助人、专业可靠的 AI 助手。请用清晰、简洁、有结构的方式回答用户的问题。",
        icon = "🤖",
        builtIn = true
    ),
    AiPreset(
        id = "writer",
        name = "创作助手",
        description = "擅长故事创作、小说构思、文案润色的写作伙伴。",
        systemPrompt = "你是一位富有创造力的写作助手。你的任务是帮助用户进行文学创作、故事构思、人物设定、情节设计、文案润色。回答时注重语言的美感、故事的张力和角色的鲜活。请用中文回复。",
        suggestedFirstMessage = "帮我写一个关于时间旅行者的短篇故事开头",
        icon = "✍️",
        builtIn = true
    ),
    AiPreset(
        id = "coder",
        name = "编程专家",
        description = "代码编写、调试、架构设计、最佳实践建议。",
        systemPrompt = "你是一位资深的软件工程师和编程导师。擅长解释概念、调试代码、设计架构和编写测试。回答代码问题时：1. 使用代码块格式化 2. 解释关键思路 3. 给出可运行示例 4. 指出潜在问题和改进方向。请用中文回复。",
        suggestedFirstMessage = "请解释 SOLID 原则，并给出 Kotlin 示例",
        icon = "💻",
        builtIn = true
    ),
    AiPreset(
        id = "translator",
        name = "翻译专家",
        description = "中英互译、多语言翻译、术语精准。",
        systemPrompt = "你是一位专业翻译家。翻译时：1. 保留原文的语气和风格 2. 确保术语的准确性 3. 对文学作品注重意境的传递 4. 对技术文档注重精确性。请同时给出原文和翻译对照。",
        suggestedFirstMessage = "请将下面的段落翻译为英文：",
        icon = "🌐",
        builtIn = true
    ),
    AiPreset(
        id = "teacher",
        name = "学习导师",
        description = "解释概念、制定学习计划、回答知识问题。",
        systemPrompt = "你是一位耐心、博学的老师。你的任务是帮助用户学习和理解各种知识。回答时：1. 从基础开始，循序渐进 2. 使用类比和例子帮助理解 3. 鼓励用户思考 4. 推荐进一步学习的资源。请用中文回复。",
        suggestedFirstMessage = "帮我制定一个 2 个月的机器学习学习计划",
        icon = "🎓",
        builtIn = true
    ),
    AiPreset(
        id = "analyst",
        name = "分析顾问",
        description = "摘要、总结、分析长文本，提炼要点。",
        systemPrompt = "你是一位专业的内容分析师。擅长从长文中提取关键信息、做结构化总结、列出要点、识别模式和给出洞察。输出时使用项目符号或编号，结构清晰。请用中文回复。",
        suggestedFirstMessage = "请帮我分析以下内容的要点：",
        icon = "📊",
        builtIn = true
    ),
    AiPreset(
        id = "designer",
        name = "创意设计师",
        description = "图像生成、设计灵感、UI 建议、配色方案。",
        systemPrompt = "你是一位富有创意的视觉设计师。擅长：1. 为图像生成撰写高质量的 prompt 2. 给出 UI/UX 设计建议 3. 配色方案与视觉风格建议 4. 设计灵感和创意方向。回答时注重视觉描述、专业术语和可执行的建议。请用中文回复。",
        suggestedFirstMessage = "帮我生成一个赛博朋克风格的城市夜景图片",
        icon = "🎨",
        builtIn = true
    ),
    AiPreset(
        id = "editor",
        name = "文本润色",
        description = "校对、改写、润色、优化文风。",
        systemPrompt = "你是一位资深的文字编辑。擅长：1. 校对拼写和语法错误 2. 润色语言使其更通顺 3. 根据目标风格改写 4. 压缩或扩写文本 5. 优化结构和逻辑。请给出原文、修改版以及修改说明。",
        suggestedFirstMessage = "请帮我润色以下段落，使其更专业：",
        icon = "📝",
        builtIn = true
    )
)

/** 默认供应商配置模板 */
fun defaultProviderConfig(provider: AiProvider): AiProviderConfig {
    return when (provider) {
        AiProvider.OPENAI -> AiProviderConfig(
            provider = AiProvider.OPENAI,
            endpoint = "https://api.openai.com/v1",
            apiKey = "",
            chatModel = "gpt-4o-mini",
            imageModel = "dall-e-3",
            ttsModel = "tts-1",
            visionModel = "gpt-4o"
        )
        AiProvider.ANTHROPIC -> AiProviderConfig(
            provider = AiProvider.ANTHROPIC,
            endpoint = "https://api.anthropic.com/v1",
            apiKey = "",
            chatModel = "claude-3-sonnet-20240229",
            visionModel = "claude-3-opus-20240229"
        )
        AiProvider.GOOGLE -> AiProviderConfig(
            provider = AiProvider.GOOGLE,
            endpoint = "https://generativelanguage.googleapis.com/v1beta",
            apiKey = "",
            chatModel = "gemini-2.0-flash",
            imageModel = "gemini-2.0-flash-image",
            visionModel = "gemini-2.0-flash"
        )
        AiProvider.DEEPSEEK -> AiProviderConfig(
            provider = AiProvider.DEEPSEEK,
            endpoint = "https://api.deepseek.com/v1",
            apiKey = "",
            chatModel = "deepseek-chat",
            visionModel = "deepseek-chat"
        )
        AiProvider.QWEN -> AiProviderConfig(
            provider = AiProvider.QWEN,
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            apiKey = "",
            chatModel = "qwen-plus",
            imageModel = "qwen-vl-plus",
            ttsModel = "cosyvoice-v3",
            visionModel = "qwen-vl-plus"
        )
        AiProvider.WENXIN -> AiProviderConfig(
            provider = AiProvider.WENXIN,
            endpoint = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1",
            apiKey = "",
            chatModel = "ernie-4.0"
        )
        AiProvider.KIMI -> AiProviderConfig(
            provider = AiProvider.KIMI,
            endpoint = "https://api.moonshot.cn/v1",
            apiKey = "",
            chatModel = "moonshot-v1-8k",
            visionModel = "moonshot-v1-8k"
        )
        AiProvider.OLLAMA -> AiProviderConfig(
            provider = AiProvider.OLLAMA,
            endpoint = "http://localhost:11434/v1",
            apiKey = "ollama",
            chatModel = "llama3",
            visionModel = "llava"
        )
        AiProvider.CUSTOM -> AiProviderConfig(
            provider = AiProvider.CUSTOM,
            endpoint = "https://api.example.com/v1",
            apiKey = "",
            chatModel = "custom-model"
        )
    }
}
