package io.legado.app.help.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.legado.app.constant.PreferKey
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.FileUtils
import io.legado.app.utils.hexString
import splitties.init.appCtx
import java.io.File
import java.lang.reflect.Type

object PersonalizationThemeConfig {
    const val configFileName = "personalizationThemeConfig.json"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    private val COLOR_FIELDS = setOf(
        "md3Primary", "md3OnPrimary", "md3PrimaryContainer", "md3OnPrimaryContainer",
        "md3Secondary", "md3OnSecondary", "md3SecondaryContainer",
        "md3Tertiary", "md3Error", "md3Surface", "md3OnSurface",
        "md3Background", "md3Outline", "md3SurfaceContainerLow", "md3SurfaceVariant",
        "topBarColor", "navBarColor", "fontColor", "bgColor", "bookInfoInputColor",
        "containerBorderColor", "itemDividerColor"
    )

    private val THEME_GSON: Gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(Config::class.java, ConfigSerializer())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create()
    }

    private val _configList: ArrayList<Config> by lazy {
        val cList = getConfigs() ?: emptyList()
        ArrayList(cList)
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
        }.getOrNull() ?: false
    }

    private fun convertTagColorsFromHex(json: String?): String? {
        if (json.isNullOrBlank()) return json
        return kotlin.runCatching {
            val root = com.google.gson.JsonParser.parseString(json)
            if (root.isJsonArray) {
                val arr = root.asJsonArray
                val converted = arr.map { elem ->
                    val obj = elem.asJsonObject
                    val textColor = parseColorFromJson(obj.get("textColor"))
                    val bgColor = parseColorFromJson(obj.get("bgColor"))
                    """{"textColor":$textColor,"bgColor":$bgColor}"""
                }
                "[\n  ${converted.joinToString(",\n  ")}\n]"
            } else json
        }.getOrNull() ?: json
    }

    private fun parseColorFromJson(elem: com.google.gson.JsonElement?): Int {
        if (elem == null || elem.isJsonNull) return 0
        val prim = elem.asJsonPrimitive
        return if (prim.isNumber) prim.asNumber.toInt()
        else if (prim.isString) parseColorString(prim.asString)
        else 0
    }

    private fun parseColorString(colorStr: String): Int {
        return if (colorStr.startsWith("#")) {
            colorStr.substring(1).toLong(16).toInt()
        } else {
            colorStr.toLongOrNull()?.toInt() ?: 0
        }
    }

    fun delConfig(index: Int) {
        if (index in _configList.indices) {
            _configList.removeAt(index)
            save()
        }
    }

    fun addConfig(json: String): Boolean {
        kotlin.runCatching {
            val config = THEME_GSON.fromJson(json, Config::class.java)
            addConfig(config)
            return true
        }.onFailure {}
        return false
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
        // 应用颜色设置
        ThemeConfig.cMD3Primary = config.md3Primary
        ThemeConfig.cMD3OnPrimary = config.md3OnPrimary
        ThemeConfig.cMD3PrimaryContainer = config.md3PrimaryContainer
        ThemeConfig.cMD3OnPrimaryContainer = config.md3OnPrimaryContainer
        ThemeConfig.cMD3Secondary = config.md3Secondary
        ThemeConfig.cMD3OnSecondary = config.md3OnSecondary
        ThemeConfig.cMD3SecondaryContainer = config.md3SecondaryContainer
        ThemeConfig.cMD3Tertiary = config.md3Tertiary
        ThemeConfig.cMD3Error = config.md3Error
        ThemeConfig.cMD3Surface = config.md3Surface
        ThemeConfig.cMD3OnSurface = config.md3OnSurface
        ThemeConfig.cMD3Background = config.md3Background
        ThemeConfig.cMD3Outline = config.md3Outline
        ThemeConfig.cMD3SurfaceContainerLow = config.md3SurfaceContainerLow
        ThemeConfig.cMD3SurfaceVariant = config.md3SurfaceVariant

        // 应用非 MD3 颜色设置
        ThemeConfig.cTopBarColor = config.topBarColor
        ThemeConfig.cNavBarColor = config.navBarColor
        ThemeConfig.cFontColor = config.fontColor
        ThemeConfig.cBgColor = config.bgColor
        ThemeConfig.cBookInfoInputColor = config.bookInfoInputColor

        // 应用字体设置
        ThemeConfig.appFontPath = config.appFontPath

        // 应用边框设置
        ThemeConfig.enableContainerBorder = config.enableContainerBorder
        ThemeConfig.containerBorderWidth = config.containerBorderWidth
        ThemeConfig.containerBorderStyle = config.containerBorderStyle ?: "solid"
        ThemeConfig.containerBorderDashWidth = config.containerBorderDashWidth
        ThemeConfig.containerBorderColor = config.containerBorderColor

        // 应用中间单线设置
        ThemeConfig.enableItemDivider = config.enableItemDivider
        ThemeConfig.itemDividerWidth = config.itemDividerWidth
        ThemeConfig.itemDividerLength = config.itemDividerLength
        ThemeConfig.itemDividerColor = config.itemDividerColor

        // 应用标签颜色设置
        ThemeConfig.enableCustomTagColors = config.enableCustomTagColors
        config.customTagColorsJson?.let {
            ThemeConfig.customTagColorsJson = convertTagColorsFromHex(it)
        }

        // 应用模糊设置
        ThemeConfig.enableBlur = config.enableBlur
        ThemeConfig.topBarBlurRadius = config.topBarBlurRadius
        ThemeConfig.bottomBarBlurRadius = config.bottomBarBlurRadius
        ThemeConfig.topBarBlurAlpha = config.topBarBlurAlpha
        ThemeConfig.bottomBarBlurAlpha = config.bottomBarBlurAlpha
        ThemeConfig.bottomBarLensRadius = config.bottomBarLensRadius
    }

    fun savePersonalizationTheme(name: String): Config {
        val config = Config(
            themeName = name,
            md3Primary = ThemeConfig.cMD3Primary,
            md3OnPrimary = ThemeConfig.cMD3OnPrimary,
            md3PrimaryContainer = ThemeConfig.cMD3PrimaryContainer,
            md3OnPrimaryContainer = ThemeConfig.cMD3OnPrimaryContainer,
            md3Secondary = ThemeConfig.cMD3Secondary,
            md3OnSecondary = ThemeConfig.cMD3OnSecondary,
            md3SecondaryContainer = ThemeConfig.cMD3SecondaryContainer,
            md3Tertiary = ThemeConfig.cMD3Tertiary,
            md3Error = ThemeConfig.cMD3Error,
            md3Surface = ThemeConfig.cMD3Surface,
            md3OnSurface = ThemeConfig.cMD3OnSurface,
            md3Background = ThemeConfig.cMD3Background,
            md3Outline = ThemeConfig.cMD3Outline,
            md3SurfaceContainerLow = ThemeConfig.cMD3SurfaceContainerLow,
            md3SurfaceVariant = ThemeConfig.cMD3SurfaceVariant,
            topBarColor = ThemeConfig.cTopBarColor,
            navBarColor = ThemeConfig.cNavBarColor,
            fontColor = ThemeConfig.cFontColor,
            bgColor = ThemeConfig.cBgColor,
            bookInfoInputColor = ThemeConfig.cBookInfoInputColor,
            appFontPath = ThemeConfig.appFontPath,
            enableContainerBorder = ThemeConfig.enableContainerBorder,
            containerBorderWidth = ThemeConfig.containerBorderWidth,
            containerBorderStyle = ThemeConfig.containerBorderStyle,
            containerBorderDashWidth = ThemeConfig.containerBorderDashWidth,
            containerBorderColor = ThemeConfig.containerBorderColor,
            enableItemDivider = ThemeConfig.enableItemDivider,
            itemDividerWidth = ThemeConfig.itemDividerWidth,
            itemDividerLength = ThemeConfig.itemDividerLength,
            itemDividerColor = ThemeConfig.itemDividerColor,
            enableCustomTagColors = ThemeConfig.enableCustomTagColors,
            customTagColorsJson = ThemeConfig.customTagColorsJson,
            enableBlur = ThemeConfig.enableBlur,
            topBarBlurRadius = ThemeConfig.topBarBlurRadius,
            bottomBarBlurRadius = ThemeConfig.bottomBarBlurRadius,
            topBarBlurAlpha = ThemeConfig.topBarBlurAlpha,
            bottomBarBlurAlpha = ThemeConfig.bottomBarBlurAlpha,
            bottomBarLensRadius = ThemeConfig.bottomBarLensRadius
        )
        addConfig(config)
        return config
    }

    data class Config(
        var themeName: String = "",
        var md3Primary: Int,
        var md3OnPrimary: Int,
        var md3PrimaryContainer: Int,
        var md3OnPrimaryContainer: Int,
        var md3Secondary: Int,
        var md3OnSecondary: Int,
        var md3SecondaryContainer: Int,
        var md3Tertiary: Int,
        var md3Error: Int,
        var md3Surface: Int,
        var md3OnSurface: Int,
        var md3Background: Int,
        var md3Outline: Int,
        var md3SurfaceContainerLow: Int,
        var md3SurfaceVariant: Int,
        var topBarColor: Int,
        var navBarColor: Int,
        var fontColor: Int,
        var bgColor: Int,
        var bookInfoInputColor: Int,
        var appFontPath: String? = null,
        var enableContainerBorder: Boolean = false,
        var containerBorderWidth: Float = 1f,
        var containerBorderStyle: String = "solid",
        var containerBorderDashWidth: Float = 4f,
        var containerBorderColor: Int = 0,
        var enableItemDivider: Boolean = true,
        var itemDividerWidth: Float = 1f,
        var itemDividerLength: Float = 80f,
        var itemDividerColor: Int = 0,
        var enableCustomTagColors: Boolean = false,
        var customTagColorsJson: String? = null,
        var enableBlur: Boolean = false,
        var topBarBlurRadius: Int = 24,
        var bottomBarBlurRadius: Int = 8,
        var topBarBlurAlpha: Int = 73,
        var bottomBarBlurAlpha: Int = 40,
        var bottomBarLensRadius: Float = 24f
    )
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
        obj.addProperty("md3Primary", src.md3Primary.toHexString())
        obj.addProperty("md3OnPrimary", src.md3OnPrimary.toHexString())
        obj.addProperty("md3PrimaryContainer", src.md3PrimaryContainer.toHexString())
        obj.addProperty("md3OnPrimaryContainer", src.md3OnPrimaryContainer.toHexString())
        obj.addProperty("md3Secondary", src.md3Secondary.toHexString())
        obj.addProperty("md3OnSecondary", src.md3OnSecondary.toHexString())
        obj.addProperty("md3SecondaryContainer", src.md3SecondaryContainer.toHexString())
        obj.addProperty("md3Tertiary", src.md3Tertiary.toHexString())
        obj.addProperty("md3Error", src.md3Error.toHexString())
        obj.addProperty("md3Surface", src.md3Surface.toHexString())
        obj.addProperty("md3OnSurface", src.md3OnSurface.toHexString())
        obj.addProperty("md3Background", src.md3Background.toHexString())
        obj.addProperty("md3Outline", src.md3Outline.toHexString())
        obj.addProperty("md3SurfaceContainerLow", src.md3SurfaceContainerLow.toHexString())
        obj.addProperty("md3SurfaceVariant", src.md3SurfaceVariant.toHexString())
        obj.addProperty("topBarColor", src.topBarColor.toHexString())
        obj.addProperty("navBarColor", src.navBarColor.toHexString())
        obj.addProperty("fontColor", src.fontColor.toHexString())
        obj.addProperty("bgColor", src.bgColor.toHexString())
        obj.addProperty("bookInfoInputColor", src.bookInfoInputColor.toHexString())
        obj.addProperty("appFontPath", src.appFontPath)
        obj.addProperty("enableContainerBorder", src.enableContainerBorder)
        obj.addProperty("containerBorderWidth", src.containerBorderWidth)
        obj.addProperty("containerBorderStyle", src.containerBorderStyle)
        obj.addProperty("containerBorderDashWidth", src.containerBorderDashWidth)
        obj.addProperty("containerBorderColor", src.containerBorderColor.toHexString())
        obj.addProperty("enableItemDivider", src.enableItemDivider)
        obj.addProperty("itemDividerWidth", src.itemDividerWidth)
        obj.addProperty("itemDividerLength", src.itemDividerLength)
        obj.addProperty("itemDividerColor", src.itemDividerColor.toHexString())
        obj.addProperty("enableCustomTagColors", src.enableCustomTagColors)
        obj.addProperty("customTagColorsJson", convertTagColorsToHex(src.customTagColorsJson))
        obj.addProperty("enableBlur", src.enableBlur)
        obj.addProperty("topBarBlurRadius", src.topBarBlurRadius)
        obj.addProperty("bottomBarBlurRadius", src.bottomBarBlurRadius)
        obj.addProperty("topBarBlurAlpha", src.topBarBlurAlpha)
        obj.addProperty("bottomBarBlurAlpha", src.bottomBarBlurAlpha)
        obj.addProperty("bottomBarLensRadius", src.bottomBarLensRadius)
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
            md3Primary = obj.getInt("md3Primary", 0),
            md3OnPrimary = obj.getInt("md3OnPrimary", 0),
            md3PrimaryContainer = obj.getInt("md3PrimaryContainer", 0),
            md3OnPrimaryContainer = obj.getInt("md3OnPrimaryContainer", 0),
            md3Secondary = obj.getInt("md3Secondary", 0),
            md3OnSecondary = obj.getInt("md3OnSecondary", 0),
            md3SecondaryContainer = obj.getInt("md3SecondaryContainer", 0),
            md3Tertiary = obj.getInt("md3Tertiary", 0),
            md3Error = obj.getInt("md3Error", 0),
            md3Surface = obj.getInt("md3Surface", 0),
            md3OnSurface = obj.getInt("md3OnSurface", 0),
            md3Background = obj.getInt("md3Background", 0),
            md3Outline = obj.getInt("md3Outline", 0),
            md3SurfaceContainerLow = obj.getInt("md3SurfaceContainerLow", 0),
            md3SurfaceVariant = obj.getInt("md3SurfaceVariant", 0),
            topBarColor = obj.getInt("topBarColor", 0),
            navBarColor = obj.getInt("navBarColor", 0),
            fontColor = obj.getInt("fontColor", 0),
            bgColor = obj.getInt("bgColor", 0),
            bookInfoInputColor = obj.getInt("bookInfoInputColor", 0),
            appFontPath = obj.getString("appFontPath", null),
            enableContainerBorder = obj.getBoolean("enableContainerBorder", false) ?: false,
            containerBorderWidth = obj.getFloat("containerBorderWidth", 1f),
            containerBorderStyle = obj.getString("containerBorderStyle", "solid") ?: "solid",
            containerBorderDashWidth = obj.getFloat("containerBorderDashWidth", 4f),
            containerBorderColor = obj.getInt("containerBorderColor", 0),
            enableItemDivider = obj.getBoolean("enableItemDivider", true) ?: true,
            itemDividerWidth = obj.getFloat("itemDividerWidth", 1f),
            itemDividerLength = obj.getFloat("itemDividerLength", 80f),
            itemDividerColor = obj.getInt("itemDividerColor", 0),
            enableCustomTagColors = obj.getBoolean("enableCustomTagColors", false) ?: false,
            customTagColorsJson = obj.getString("customTagColorsJson", null),
            enableBlur = obj.getBoolean("enableBlur", false) ?: false,
            topBarBlurRadius = obj.getInt("topBarBlurRadius", 24),
            bottomBarBlurRadius = obj.getInt("bottomBarBlurRadius", 8),
            topBarBlurAlpha = obj.getInt("topBarBlurAlpha", 73),
            bottomBarBlurAlpha = obj.getInt("bottomBarBlurAlpha", 40),
            bottomBarLensRadius = obj.getFloat("bottomBarLensRadius", 24f)
        )
    }

    private fun Int.toHexString(): String {
        return "#${String.format("%08X", this)}"
    }

    private fun JsonObject.getString(name: String, default: String?): String? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isString) prim.asString
                else if (prim.isNumber) prim.asString
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getInt(name: String, default: Int): Int {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toInt()
                else if (prim.isString) parseColorString(prim.asString)
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getInt(name: String, default: Int?): Int? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toInt()
                else if (prim.isString) parseColorString(prim.asString)
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getFloat(name: String, default: Float): Float {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isNumber) prim.asNumber.toFloat()
                else default
            }
            else -> default
        }
    }

    private fun JsonObject.getBoolean(name: String, default: Boolean?): Boolean? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isBoolean) prim.asBoolean
                else default
            }
            else -> default
        }
    }

    private fun parseColorString(colorStr: String): Int {
        return if (colorStr.startsWith("#")) {
            colorStr.substring(1).toLong(16).toInt()
        } else {
            colorStr.toLongOrNull()?.toInt() ?: 0
        }
    }

    private fun convertTagColorsToHex(json: String?): String? {
        if (json.isNullOrBlank()) return json
        return kotlin.runCatching {
            val colors = GSON.fromJson(json, Array<TagColorPair>::class.java)
            val converted = colors.map { pair ->
                val textHex = pair.textColor.toHexString()
                val bgHex = pair.bgColor.toHexString()
                """{"textColor":"$textHex","bgColor":"$bgHex"}"""
            }
            "[\n  ${converted.joinToString(",\n  ")}\n]"
        }.getOrNull() ?: json
    }
}
