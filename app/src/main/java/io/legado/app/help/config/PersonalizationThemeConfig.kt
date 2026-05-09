package io.legado.app.help.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.legado.app.utils.FileUtils
import io.legado.app.utils.getString
import splitties.init.appCtx
import java.io.File
import java.lang.reflect.Type

object PersonalizationThemeConfig {
    const val configFileName = "personalizationThemeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    private val THEME_GSON: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Config::class.java, ConfigSerializer())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    private val _configList: ArrayList<Config> by lazy {
        ArrayList(getConfigs() ?: emptyList())
    }

    val configList: List<Config> get() = _configList

    fun save() {
        val json = THEME_GSON.toJson(_configList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    fun toJson(config: Config): String {
        return THEME_GSON.toJson(config)
    }

    fun toJson(): String {
        return THEME_GSON.toJson(_configList)
    }

    fun fromJson(json: String): Boolean {
        return kotlin.runCatching {
            val configs = THEME_GSON.fromJson(json, Array<Config>::class.java)
            _configList.clear()
            _configList.addAll(configs)
            save()
            true
        }.getOrDefault(false)
    }

    fun delConfig(index: Int) {
        if (index in _configList.indices) {
            _configList.removeAt(index)
            save()
        }
    }

    fun addConfig(json: String): Boolean {
        return kotlin.runCatching {
            val config = THEME_GSON.fromJson(json, Config::class.java)
            addConfig(config)
            true
        }.getOrDefault(false)
    }

    fun addConfig(newConfig: Config) {
        _configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName) {
                _configList[index] = newConfig
                save()
                return
            }
        }
        _configList.add(newConfig)
        save()
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return THEME_GSON.fromJson(json, Array<Config>::class.java).toList()
            }
        }
        return null
    }

    fun applyConfig(config: Config) {
        config.appearance.applyToThemeConfig()
    }

    fun savePersonalizationTheme(name: String): Config {
        val config = Config(
            themeName = name,
            appearance = ThemeAppearanceConfig.fromCurrent()
        )
        addConfig(config)
        return config
    }

    data class Config(
        var themeName: String = "",
        var appearance: ThemeAppearanceConfig = ThemeAppearanceConfig()
    ) {
        val themeColor: Int get() = appearance.themeColor
    }
}

class ConfigSerializer : JsonSerializer<PersonalizationThemeConfig.Config>,
    JsonDeserializer<PersonalizationThemeConfig.Config> {

    override fun serialize(
        src: PersonalizationThemeConfig.Config,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val obj = JsonObject()
        obj.addProperty("themeName", src.themeName)
        ThemeAppearanceJson.writeTo(obj, src.appearance)
        return obj
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): PersonalizationThemeConfig.Config {
        val obj = json.asJsonObject
        return PersonalizationThemeConfig.Config(
            themeName = obj.getString("themeName", "") ?: "",
            appearance = ThemeAppearanceJson.readFrom(obj)
        )
    }

}
