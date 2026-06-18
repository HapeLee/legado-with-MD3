package io.legado.app.help.ai

import android.content.Context
import io.legado.app.App
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString

/**
 * AI 配置的持久化存储
 * 使用 SharedPreferences，每个 provider 独立一份
 */
object AiConfigStore {

    private const val PREF_NAME = "ai_config"

    private const val KEY_ACTIVE_PROVIDER = "active_provider"
    private const val KEY_DEFAULT_IMAGE_SIZE = "default_image_size"
    private const val KEY_DEFAULT_IMAGE_COUNT = "default_image_count"
    private const val KEY_DEFAULT_TEMPERATURE = "default_temperature"
    private const val KEY_STREAM_ENABLED = "stream_enabled"

    private const val KEY_PREFIX_PROVIDER = "provider_config_"
    private const val KEY_CUSTOM_PRESETS = "custom_presets"

    private val context: Context get() = App.INSTANCE

    var activeProvider: String
        get() = context.getPrefString(PREF_NAME, KEY_ACTIVE_PROVIDER) ?: AiProvider.OPENAI.name
        set(value) = context.putPrefString(PREF_NAME, KEY_ACTIVE_PROVIDER, value)

    var defaultImageSize: String
        get() = context.getPrefString(PREF_NAME, KEY_DEFAULT_IMAGE_SIZE) ?: "1024x1024"
        set(value) = context.putPrefString(PREF_NAME, KEY_DEFAULT_IMAGE_SIZE, value)

    var defaultImageCount: Int
        get() = context.getPrefString(PREF_NAME, KEY_DEFAULT_IMAGE_COUNT)?.toIntOrNull() ?: 1
        set(value) = context.putPrefString(PREF_NAME, KEY_DEFAULT_IMAGE_COUNT, value.toString())

    var defaultTemperature: Float
        get() = context.getPrefString(PREF_NAME, KEY_DEFAULT_TEMPERATURE)?.toFloatOrNull() ?: 0.7f
        set(value) = context.putPrefString(PREF_NAME, KEY_DEFAULT_TEMPERATURE, value.toString())

    var streamEnabled: Boolean
        get() = context.getPrefString(PREF_NAME, KEY_STREAM_ENABLED)?.toBooleanStrictOrNull() ?: true
        set(value) = context.putPrefString(PREF_NAME, KEY_STREAM_ENABLED, value.toString())

    /**
     * 加载所有 provider 的配置
     */
    fun loadProviderConfigs(): Map<AiProvider, AiProviderConfig> {
        val map = mutableMapOf<AiProvider, AiProviderConfig>()
        AiProvider.values().forEach { provider ->
            val json = context.getPrefString(PREF_NAME, KEY_PREFIX_PROVIDER + provider.name)
            if (!json.isNullOrEmpty()) {
                try {
                    GSON.fromJsonObject<AiProviderConfig>(json).getOrNull()?.let {
                        map[provider] = it
                    }
                } catch (_: Throwable) {
                    // 忽略解析错误
                }
            }
        }
        return map
    }

    fun saveProviderConfigs(configs: Map<AiProvider, AiProviderConfig>) {
        configs.forEach { (provider, config) ->
            context.putPrefString(PREF_NAME, KEY_PREFIX_PROVIDER + provider.name, GSON.toJson(config))
        }
    }

    fun loadCustomPresets(): List<AiPreset> {
        val json = context.getPrefString(PREF_NAME, KEY_CUSTOM_PRESETS)
            ?: return emptyList()
        return runCatching {
            // 用 JSON 解析
            val listType = com.google.gson.reflect.TypeToken.getParameterized(List::class.java, AiPreset::class.java)
            @Suppress("UNCHECKED_CAST")
            GSON.fromJson<List<AiPreset>>(json, listType) ?: emptyList()
        }.getOrElse { emptyList() }
    }

    fun saveCustomPresets(presets: List<AiPreset>) {
        context.putPrefString(PREF_NAME, KEY_CUSTOM_PRESETS, GSON.toJson(presets))
    }
}
