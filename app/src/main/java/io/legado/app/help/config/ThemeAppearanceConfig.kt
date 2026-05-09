package io.legado.app.help.config

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.ui.config.themeConfig.TagColorPair
import io.legado.app.ui.config.themeConfig.ThemeConfig
import io.legado.app.utils.GSON
import androidx.core.graphics.toColorInt

data class ThemeAppearanceConfig(
    var enableDeepPersonalization: Boolean = false,
    var themeColor: Int = 0,
    var secondaryThemeColor: Int = 0,
    var primaryTextColor: Int = 0,
    var secondaryTextColor: Int = 0,
    var backgroundColor: Int = 0,
    var labelContainerColor: Int = 0,
    var bookInfoInputColor: Int = 0,
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
) {

    val hasDeepColorOverrides: Boolean
        get() = themeColor != 0 ||
                secondaryThemeColor != 0 ||
                primaryTextColor != 0 ||
                secondaryTextColor != 0 ||
                backgroundColor != 0 ||
                labelContainerColor != 0

    fun applyToThemeConfig(appFontPathOverride: String? = appFontPath) {
        ThemeConfig.enableDeepPersonalization = enableDeepPersonalization
        ThemeConfig.themeColor = themeColor
        ThemeConfig.secondaryThemeColor = secondaryThemeColor
        ThemeConfig.primaryTextColor = primaryTextColor
        ThemeConfig.secondaryTextColor = secondaryTextColor
        ThemeConfig.themeBackgroundColor = backgroundColor
        ThemeConfig.labelContainerColor = labelContainerColor

        ThemeConfig.bookInfoInputColor = bookInfoInputColor

        ThemeConfig.appFontPath = appFontPathOverride

        ThemeConfig.enableContainerBorder = enableContainerBorder
        ThemeConfig.containerBorderWidth = containerBorderWidth
        ThemeConfig.containerBorderStyle = containerBorderStyle.ifBlank { "solid" }
        ThemeConfig.containerBorderDashWidth = containerBorderDashWidth
        ThemeConfig.containerBorderColor = containerBorderColor

        ThemeConfig.enableItemDivider = enableItemDivider
        ThemeConfig.itemDividerWidth = itemDividerWidth
        ThemeConfig.itemDividerLength = itemDividerLength
        ThemeConfig.itemDividerColor = itemDividerColor

        ThemeConfig.enableCustomTagColors = enableCustomTagColors
        ThemeConfig.customTagColorsJson = ThemeAppearanceJson.convertTagColorsFromHex(customTagColorsJson)

        ThemeConfig.enableBlur = enableBlur
        ThemeConfig.topBarBlurRadius = topBarBlurRadius
        ThemeConfig.bottomBarBlurRadius = bottomBarBlurRadius
        ThemeConfig.topBarBlurAlpha = topBarBlurAlpha
        ThemeConfig.bottomBarBlurAlpha = bottomBarBlurAlpha
        ThemeConfig.bottomBarLensRadius = bottomBarLensRadius
    }

    companion object {
        fun fromCurrent(): ThemeAppearanceConfig {
            return ThemeAppearanceConfig(
                enableDeepPersonalization = ThemeConfig.enableDeepPersonalization,
                themeColor = ThemeConfig.themeColor,
                secondaryThemeColor = ThemeConfig.secondaryThemeColor,
                primaryTextColor = ThemeConfig.primaryTextColor,
                secondaryTextColor = ThemeConfig.secondaryTextColor,
                backgroundColor = ThemeConfig.themeBackgroundColor,
                labelContainerColor = ThemeConfig.labelContainerColor,
                bookInfoInputColor = ThemeConfig.bookInfoInputColor,
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
        }
    }
}

object ThemeAppearanceJson {

    fun writeTo(obj: JsonObject, src: ThemeAppearanceConfig) {
        obj.addProperty("enableDeepPersonalization", src.enableDeepPersonalization)
        obj.addProperty("themeColor", colorToHex(src.themeColor))
        obj.addProperty("secondaryThemeColor", colorToHex(src.secondaryThemeColor))
        obj.addProperty("primaryTextColor", colorToHex(src.primaryTextColor))
        obj.addProperty("secondaryTextColor", colorToHex(src.secondaryTextColor))
        obj.addProperty("backgroundColor", colorToHex(src.backgroundColor))
        obj.addProperty("labelContainerColor", colorToHex(src.labelContainerColor))
        obj.addProperty("bookInfoInputColor", colorToHex(src.bookInfoInputColor))
        obj.addProperty("appFontPath", src.appFontPath)
        obj.addProperty("enableContainerBorder", src.enableContainerBorder)
        obj.addProperty("containerBorderWidth", src.containerBorderWidth)
        obj.addProperty("containerBorderStyle", src.containerBorderStyle)
        obj.addProperty("containerBorderDashWidth", src.containerBorderDashWidth)
        obj.addProperty("containerBorderColor", colorToHex(src.containerBorderColor))
        obj.addProperty("enableItemDivider", src.enableItemDivider)
        obj.addProperty("itemDividerWidth", src.itemDividerWidth)
        obj.addProperty("itemDividerLength", src.itemDividerLength)
        obj.addProperty("itemDividerColor", colorToHex(src.itemDividerColor))
        obj.addProperty("enableCustomTagColors", src.enableCustomTagColors)
        obj.addProperty("customTagColorsJson", convertTagColorsToHex(src.customTagColorsJson))
        obj.addProperty("enableBlur", src.enableBlur)
        obj.addProperty("topBarBlurRadius", src.topBarBlurRadius)
        obj.addProperty("bottomBarBlurRadius", src.bottomBarBlurRadius)
        obj.addProperty("topBarBlurAlpha", src.topBarBlurAlpha)
        obj.addProperty("bottomBarBlurAlpha", src.bottomBarBlurAlpha)
        obj.addProperty("bottomBarLensRadius", src.bottomBarLensRadius)
    }

    fun readFrom(obj: JsonObject): ThemeAppearanceConfig {
        val appearance = ThemeAppearanceConfig(
            themeColor = readColor(obj, "themeColor", "md3Primary"),
            secondaryThemeColor = readColor(obj, "secondaryThemeColor", "md3Secondary", "md3OnPrimary"),
            primaryTextColor = readColor(obj, "primaryTextColor", "md3OnSurface"),
            secondaryTextColor = readColor(obj, "secondaryTextColor", "md3OnPrimaryContainer"),
            backgroundColor = readColor(obj, "backgroundColor", "md3Background"),
            labelContainerColor = readColor(obj, "labelContainerColor", "md3SurfaceContainerLow"),
            bookInfoInputColor = readColor(obj, "bookInfoInputColor"),
            appFontPath = obj.getString("appFontPath", null),
            enableContainerBorder = obj.getBoolean("enableContainerBorder", false),
            containerBorderWidth = obj.getFloat("containerBorderWidth", 1f),
            containerBorderStyle = obj.getString("containerBorderStyle", "solid") ?: "solid",
            containerBorderDashWidth = obj.getFloat("containerBorderDashWidth", 4f),
            containerBorderColor = readColor(obj, "containerBorderColor"),
            enableItemDivider = obj.getBoolean("enableItemDivider", true),
            itemDividerWidth = obj.getFloat("itemDividerWidth", 1f),
            itemDividerLength = obj.getFloat("itemDividerLength", 80f),
            itemDividerColor = readColor(obj, "itemDividerColor"),
            enableCustomTagColors = obj.getBoolean("enableCustomTagColors", false),
            customTagColorsJson = obj.getString("customTagColorsJson", null),
            enableBlur = obj.getBoolean("enableBlur", false),
            topBarBlurRadius = obj.getInt("topBarBlurRadius", 24),
            bottomBarBlurRadius = obj.getInt("bottomBarBlurRadius", 8),
            topBarBlurAlpha = obj.getInt("topBarBlurAlpha", 73),
            bottomBarBlurAlpha = obj.getInt("bottomBarBlurAlpha", 40),
            bottomBarLensRadius = obj.getFloat("bottomBarLensRadius", 24f)
        )
        appearance.enableDeepPersonalization = if (obj.has("enableDeepPersonalization")) {
            obj.getBoolean("enableDeepPersonalization", false)
        } else {
            appearance.hasDeepColorOverrides
        }
        return appearance
    }

    fun convertTagColorsFromHex(json: String?): String? {
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

    private fun convertTagColorsToHex(json: String?): String? {
        if (json.isNullOrBlank()) return json
        return kotlin.runCatching {
            val colors = GSON.fromJson(json, Array<TagColorPair>::class.java)
            val converted = colors.map { pair ->
                val textHex = colorToHex(pair.textColor)
                val bgHex = colorToHex(pair.bgColor)
                """{"textColor":"$textHex","bgColor":"$bgHex"}"""
            }
            "[\n  ${converted.joinToString(",\n  ")}\n]"
        }.getOrNull() ?: json
    }

    private fun parseColorFromJson(elem: JsonElement?): Int {
        if (elem == null || elem.isJsonNull) return 0
        val prim = elem.asJsonPrimitive
        return when {
            prim.isNumber -> prim.asNumber.toInt()
            prim.isString -> parseColorString(prim.asString)
            else -> 0
        }
    }

    fun readColor(obj: JsonObject, vararg names: String): Int {
        for (name in names) {
            val color = obj.getInt(name, Int.MIN_VALUE)
            if (color != Int.MIN_VALUE) return color
        }
        return 0
    }

    fun colorToHex(color: Int): String = "#${String.format("%08X", color)}"

    private fun JsonObject.getString(name: String, default: String?): String? {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isString || prim.isNumber) prim.asString else default
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
                when {
                    prim.isNumber -> prim.asNumber.toInt()
                    prim.isString -> parseColorString(prim.asString)
                    else -> default
                }
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
                if (prim.isNumber) prim.asNumber.toFloat() else default
            }
            else -> default
        }
    }

    private fun JsonObject.getBoolean(name: String, default: Boolean): Boolean {
        val elem = get(name)
        return when {
            elem == null || elem.isJsonNull -> default
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                if (prim.isBoolean) prim.asBoolean else default
            }
            else -> default
        }
    }

    private fun parseColorString(colorStr: String): Int {
        val text = colorStr.trim()
        if (text.isEmpty()) return 0
        return kotlin.runCatching {
            if (text.startsWith("#")) {
                text.substring(1).toLong(16).toInt()
            } else {
                text.toLong().toInt()
            }
        }.getOrElse {
            kotlin.runCatching { text.toColorInt() }.getOrDefault(0)
        }
    }

}
