package io.legado.app.domain.model.readaloud

data class CloudTtsEngine(
    val id: String,
    val name: String,
    val provider: CloudTtsProviderType,
    val baseUrl: String = "",
    val apiKey: String = "",
    val secretKey: String = "",
    val region: String = "",
    val appId: String = "",
    val model: String = "",
    val optionsJson: String = "{}",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class CloudTtsProviderType(val storageValue: String) {
    OpenAiSpeech("openai_speech"),
    GeminiTts("gemini_tts"),
    Mimo("mimo"),
    AzureSpeech("azure_speech"),
    AlibabaCloud("alibaba_cloud"),
    AwsPolly("aws_polly"),
    Volcengine("volcengine");

    companion object {
        fun fromStorage(value: String): CloudTtsProviderType =
            entries.firstOrNull { it.storageValue == value } ?: Mimo
    }
}

data class CloudTtsProviderProfile(
    val displayName: String,
    val defaultModel: String = "",
    val apiKeyLabel: String = "API Key",
    val modelLabel: String = "模型",
    val modelOptions: List<String> = emptyList(),
    val regionOptions: List<String> = emptyList(),
    val audioFormats: List<String> = listOf("mp3"),
    val voiceCatalogType: CloudTtsVoiceCatalogType = CloudTtsVoiceCatalogType.BuiltIn,
)

enum class CloudTtsVoiceCatalogType {
    BuiltIn,
    Remote,
    Partial,
}

val CloudTtsProviderType.profile: CloudTtsProviderProfile
    get() = when (this) {
        CloudTtsProviderType.OpenAiSpeech -> CloudTtsProviderProfile(
            displayName = "OpenAI Speech",
            defaultModel = "gpt-4o-mini-tts",
            modelOptions = listOf(
                "gpt-4o-mini-tts",
                "gpt-4o-mini-tts-2025-12-15",
                "tts-1",
                "tts-1-hd",
            ),
            audioFormats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm"),
        )
        CloudTtsProviderType.GeminiTts -> CloudTtsProviderProfile(
            displayName = "Google Gemini TTS",
            defaultModel = "gemini-3.1-flash-tts-preview",
            modelOptions = listOf(
                "gemini-3.1-flash-tts-preview",
                "gemini-2.5-flash-preview-tts",
                "gemini-2.5-pro-preview-tts",
            ),
            audioFormats = listOf("wav"),
        )
        CloudTtsProviderType.Mimo -> CloudTtsProviderProfile(
            displayName = "Xiaomi MiMo",
            defaultModel = "mimo-v2.5-tts",
            audioFormats = listOf("wav"),
        )
        CloudTtsProviderType.AzureSpeech -> CloudTtsProviderProfile(
            displayName = "Microsoft Azure Speech",
            apiKeyLabel = "Speech Key",
            modelLabel = "模型（可选）",
            regionOptions = listOf(
                "eastasia", "southeastasia", "australiaeast", "northeurope", "westeurope",
                "eastus", "eastus2", "southcentralus", "westus2", "westus3",
                "japaneast", "koreacentral", "centralindia",
            ),
            audioFormats = listOf("mp3", "wav", "ogg"),
            voiceCatalogType = CloudTtsVoiceCatalogType.Remote,
        )
        CloudTtsProviderType.AlibabaCloud -> CloudTtsProviderProfile(
            displayName = "Alibaba Cloud Model Studio",
            defaultModel = "qwen3-tts-flash",
            modelOptions = listOf("qwen3-tts-flash", "qwen3-tts-instruct-flash"),
            audioFormats = listOf("wav"),
            voiceCatalogType = CloudTtsVoiceCatalogType.Partial,
        )
        CloudTtsProviderType.AwsPolly -> CloudTtsProviderProfile(
            displayName = "Amazon Polly",
            defaultModel = "neural",
            apiKeyLabel = "Access Key ID",
            modelLabel = "合成引擎",
            modelOptions = listOf("standard", "neural", "long-form", "generative"),
            regionOptions = listOf(
                "us-east-1", "us-east-2", "us-west-2", "ca-central-1",
                "eu-central-1", "eu-west-1", "eu-west-2", "eu-west-3",
                "ap-south-1", "ap-southeast-1", "ap-southeast-2",
                "ap-northeast-1", "ap-northeast-2",
            ),
            audioFormats = listOf("mp3", "ogg_vorbis", "wav"),
            voiceCatalogType = CloudTtsVoiceCatalogType.Remote,
        )
        CloudTtsProviderType.Volcengine -> CloudTtsProviderProfile(
            displayName = "Volcengine Speech",
            apiKeyLabel = "Access Token",
            modelLabel = "模型（可选）",
            audioFormats = listOf("mp3", "ogg_opus", "wav", "pcm"),
            voiceCatalogType = CloudTtsVoiceCatalogType.Partial,
        )
    }

data class CloudTtsVoiceDescriptor(
    val id: String,
    val displayName: String,
    val locale: String = "",
    val gender: String = "",
    val styles: List<String> = emptyList(),
    val roles: List<String> = emptyList(),
    val supportedModels: List<String> = emptyList(),
    val sampleRate: Int? = null,
)

data class CloudTtsSynthesisRequest(
    val text: String,
    val voiceId: String,
    val locale: String = "",
    val style: String = "",
    val role: String = "",
    val instructions: String = "",
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val volume: Float = 1f,
    val format: String = "mp3",
    val sampleRate: Int? = null,
)

data class CloudTtsAudio(
    val bytes: ByteArray,
    val format: String,
    val sampleRate: Int? = null,
)
