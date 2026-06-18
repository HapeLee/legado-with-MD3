package io.legado.app.help.ai

/**
 * AI 请求/响应数据模型。兼容 OpenAI 风格 API。
 */
data class AiMessage(
    val role: String,
    val content: String
)

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

data class ImageGenerationRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val size: String = "1024x1024",
    val response_format: String = "url"
)

data class ImageGenerationResponse(
    val data: List<ImageItem>? = null,
    val error: ChatCompletionResponse.ChatError? = null
) {
    data class ImageItem(val url: String?, val b64_json: String?)
}

data class VideoGenerationRequest(
    val model: String,
    val prompt: String,
    val aspect_ratio: String? = null,
    val duration_seconds: Int? = null
)

data class VideoGenerationResponse(
    val data: VideoItem? = null,
    val error: ChatCompletionResponse.ChatError? = null
) {
    data class VideoItem(val url: String?)
}

/**
 * AI API 配置。
 */
data class AiApiConfig(
    val endpoint: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val chatModel: String = "gpt-4o-mini",
    val imageModel: String = "dall-e-3",
    val videoModel: String = "sora",
    val temperature: Float = 0.7f,
    val imageSize: String = "1024x1024",
    val timeoutSeconds: Long = 60
)
