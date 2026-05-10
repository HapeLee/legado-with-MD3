package io.legado.app.help.config

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import io.legado.app.utils.getBoolean
import io.legado.app.utils.getInt
import io.legado.app.utils.getString
import java.lang.reflect.Type

class ThemePackSerializer : JsonSerializer<ThemePackConfig.ThemePack>,
    JsonDeserializer<ThemePackConfig.ThemePack> {

    override fun serialize(
        src: ThemePackConfig.ThemePack,
        typeOfSrc: Type,
        context: JsonSerializationContext
    ): JsonElement {
        val obj = JsonObject()
        obj.addProperty("name", src.name)
        ThemeAppearanceJson.writeTo(obj, src.appearance)
        obj.addProperty("bgImageLight", src.bgImageLight)
        obj.addProperty("bgImageDark", src.bgImageDark)
        obj.addProperty("bgImageBlurring", src.bgImageBlurring)
        obj.addProperty("bgImageNBlurring", src.bgImageNBlurring)
        obj.addProperty("navIconBookshelf", src.navIconBookshelf)
        obj.addProperty("navIconExplore", src.navIconExplore)
        obj.addProperty("navIconRss", src.navIconRss)
        obj.addProperty("navIconMy", src.navIconMy)
        obj.addProperty("coverTextColor", ThemeAppearanceJson.colorToHex(src.coverTextColor))
        obj.addProperty("coverShadowColor", ThemeAppearanceJson.colorToHex(src.coverShadowColor))
        obj.addProperty("coverTextColorN", ThemeAppearanceJson.colorToHex(src.coverTextColorN))
        obj.addProperty("coverShadowColorN", ThemeAppearanceJson.colorToHex(src.coverShadowColorN))
        obj.addProperty("coverShowName", src.coverShowName)
        obj.addProperty("coverShowAuthor", src.coverShowAuthor)
        obj.addProperty("coverShowNameN", src.coverShowNameN)
        obj.addProperty("coverShowAuthorN", src.coverShowAuthorN)
        obj.addProperty("coverDefaultColor", src.coverDefaultColor)
        obj.addProperty("coverShowShadow", src.coverShowShadow)
        obj.addProperty("coverShowStroke", src.coverShowStroke)
        obj.addProperty("coverInfoOrientation", src.coverInfoOrientation)
        obj.addProperty("defaultCover", src.defaultCover)
        obj.addProperty("defaultCoverDark", src.defaultCoverDark)
        return obj
    }

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ThemePackConfig.ThemePack {
        val obj = json.asJsonObject
        return ThemePackConfig.ThemePack(
            name = obj.getString("name", "") ?: "",
            appearance = ThemeAppearanceJson.readFrom(obj),
            bgImageLight = obj.getString("bgImageLight", null),
            bgImageDark = obj.getString("bgImageDark", null),
            bgImageBlurring = obj.getInt("bgImageBlurring", 0),
            bgImageNBlurring = obj.getInt("bgImageNBlurring", 0),
            navIconBookshelf = obj.getString("navIconBookshelf", "") ?: "",
            navIconExplore = obj.getString("navIconExplore", "") ?: "",
            navIconRss = obj.getString("navIconRss", "") ?: "",
            navIconMy = obj.getString("navIconMy", "") ?: "",
            coverTextColor = ThemeAppearanceJson.readColor(obj, "coverTextColor").takeIf { it != 0 } ?: -16777216,
            coverShadowColor = ThemeAppearanceJson.readColor(obj, "coverShadowColor").takeIf { it != 0 } ?: -16777216,
            coverTextColorN = ThemeAppearanceJson.readColor(obj, "coverTextColorN").takeIf { it != 0 } ?: -1,
            coverShadowColorN = ThemeAppearanceJson.readColor(obj, "coverShadowColorN").takeIf { it != 0 } ?: -1,
            coverShowName = obj.getBoolean("coverShowName", true),
            coverShowAuthor = obj.getBoolean("coverShowAuthor", true),
            coverShowNameN = obj.getBoolean("coverShowNameN", true),
            coverShowAuthorN = obj.getBoolean("coverShowAuthorN", true),
            coverDefaultColor = obj.getBoolean("coverDefaultColor", true),
            coverShowShadow = obj.getBoolean("coverShowShadow", false),
            coverShowStroke = obj.getBoolean("coverShowStroke", true),
            coverInfoOrientation = obj.getString("coverInfoOrientation", "0") ?: "0",
            defaultCover = obj.getString("defaultCover", "") ?: "",
            defaultCoverDark = obj.getString("defaultCoverDark", "") ?: ""
        )
    }
}
