package io.legado.app.help.ai

import io.legado.app.ui.config.prefDelegate
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.toJson

/**
 * AI 模块配置存储。使用项目已有的 prefDelegate (SharedPreferences + DataStore)。
 */
object AiConfigStore {

    // ======== 通用设置 ========
    var activeProvider by prefDelegate(
        key = "ai_active_provider",
        defaultValue = AiProvider.OPENAI.name
    )

    var enabledProvidersJson by prefDelegate(
        key = "ai_providers_json",
        defaultValue = _defaultProvidersJson(),
        sync = true
    )

    var customPresetsJson by prefDelegate(
        key = "ai_custom_presets_json",
        defaultValue = "[]",
        sync = true
    )

    var conversationsJson by prefDelegate(
        key = "ai_conversations_json",
        defaultValue = "[]",
        sync = true
    )

    var defaultImageSize by prefDelegate(
        key = "ai_default_image_size",
        defaultValue = "1024x1024"
    )

    var defaultImageCount by prefDelegate(
        key = "ai_default_image_count",
        defaultValue = 1
    )

    var defaultTtsVoice by prefDelegate(
        key = "ai_default_tts_voice",
        defaultValue = "alloy"
    )

    var defaultTtsSpeed by prefDelegate(
        key = "ai_default_tts_speed",
        defaultValue = 1.0f
    )

    var defaultTemperature by prefDelegate(
        key = "ai_default_temperature",
        defaultValue = 0.7f
    )

    var streamEnabled by prefDelegate(
        key = "ai_stream_enabled",
        defaultValue = true
    )

    var useMarkdown by prefDelegate(
        key = "ai_use_markdown",
        defaultValue = true
    )

    // ======== 读写辅助 ========
    fun loadProviderConfigs(): Map<AiProvider, AiProviderConfig> {
        val list = runCatching {
            GSON.fromJsonArray<AiProviderConfig>(enabledProvidersJson)
        }.getOrNull() ?: emptyList()
        val map = mutableMapOf<AiProvider, AiProviderConfig>()
        list.forEach { map[it.provider] = it }
        // 保证每个供应商至少有默认配置
        AiProvider.values().forEach { p ->
            if (p !in map) map[p] = defaultProviderConfig(p)
        }
        return map
    }

    fun saveProviderConfigs(configs: Map<AiProvider, AiProviderConfig>) {
        enabledProvidersJson = GSON.toJson(configs.values.toList())
    }

    fun updateProviderConfig(config: AiProviderConfig) {
        val configs = loadProviderConfigs().toMutableMap()
        configs[config.provider] = config
        saveProviderConfigs(configs)
    }

    fun loadCustomPresets(): List<AiPreset> {
        return runCatching {
            GSON.fromJsonArray<AiPreset>(customPresetsJson)
        }.getOrNull() ?: emptyList()
    }

    fun saveCustomPresets(presets: List<AiPreset>) {
        customPresetsJson = GSON.toJson(presets)
    }

    fun allPresets(): List<AiPreset> = BUILT_IN_PRESETS + loadCustomPresets()

    fun loadConversations(): List<AiConversation> {
        return runCatching {
            GSON.fromJsonArray<AiConversation>(conversationsJson)
        }.getOrNull() ?: emptyList()
    }

    fun saveConversations(conversations: List<AiConversation>) {
        conversationsJson = GSON.toJson(conversations)
    }

    fun upsertConversation(conversation: AiConversation) {
        val list = loadConversations().toMutableList()
        val idx = list.indexOfFirst { it.id == conversation.id }
        val updated = conversation.copy(updatedAt = System.currentTimeMillis())
        if (idx >= 0) list[idx] = updated else list.add(0, updated)
        // 限制最多 50 个对话
        while (list.size > 50) list.removeLast()
        saveConversations(list)
    }

    fun deleteConversation(id: String) {
        val list = loadConversations().filterNot { it.id == id }
        saveConversations(list)
    }

    fun getActiveConfig(): AiProviderConfig? {
        val name = runCatching { AiProvider.valueOf(activeProvider) }.getOrNull()
            ?: AiProvider.OPENAI
        return loadProviderConfigs()[name]
    }

    private fun _defaultProvidersJson(): String {
        val defaults = AiProvider.values().associateWith { defaultProviderConfig(it) }
        return GSON.toJson(defaults.values.toList())
    }
}
