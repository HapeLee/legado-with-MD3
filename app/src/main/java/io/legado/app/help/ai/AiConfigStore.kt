package io.legado.app.help.ai

/**
 * AI 配置的简易存储（内存版，避免对 SharedPreferences 和 App 上下文的依赖）
 * 提供所有 AI 模块 ViewModel 需要的读写入口
 */
object AiConfigStore {

    // ===== Provider 默认值 =====
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

    // ===== 运行时状态 =====
    private val overrides: MutableMap<AiProvider, AiProviderConfig> = mutableMapOf()

    var activeProvider: String = AiProvider.OPENAI.name
    var streamEnabled: Boolean = true
    var defaultImageSize: String = "1024x1024"
    var defaultImageCount: Int = 1
    var defaultTemperature: Float = 0.7f

    private val customPresets: MutableList<AiPreset> = mutableListOf()

    // ===== API =====
    fun currentConfig(): AiProviderConfig {
        val provider = try {
            AiProvider.valueOf(activeProvider)
        } catch (_: Exception) {
            AiProvider.OPENAI
        }
        return overrides[provider] ?: (defaults[provider] ?: defaults[AiProvider.OPENAI]!!)
    }

    fun load(provider: AiProvider): AiProviderConfig {
        return overrides[provider] ?: (defaults[provider] ?: defaults[AiProvider.OPENAI]!!)
    }

    fun loadProviderConfigs(): Map<AiProvider, AiProviderConfig> {
        val map = mutableMapOf<AiProvider, AiProviderConfig>()
        for (provider in AiProvider.values()) {
            map[provider] = load(provider)
        }
        return map
    }

    fun saveProviderConfigs(configs: Map<AiProvider, AiProviderConfig>) {
        overrides.clear()
        overrides.putAll(configs)
    }

    fun save(config: AiProviderConfig) {
        overrides[config.provider] = config
    }

    fun setProvider(provider: AiProvider) {
        activeProvider = provider.name
    }

    fun loadCustomPresets(): List<AiPreset> {
        return customPresets.toList()
    }

    fun saveCustomPresets(presets: List<AiPreset>) {
        customPresets.clear()
        customPresets.addAll(presets)
    }
}
