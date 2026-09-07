package io.legado.app.core.platform

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.ToNumberPolicy
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import kotlin.math.ceil
import kotlin.reflect.KClass

/**
 * Gson 委托实现。配置与 app 侧 `utils.INITIAL_GSON` 严格对齐：
 * 数字修复 + prettyPrinting + disableHtmlEscaping + LONG_OR_DOUBLE，保证字节级输出一致。
 *
 * 注意：不含 app 侧 `GSON` 额外注册的 6 个 rule 类型 JsonDeserializer（那些类型尚未下沉）。
 * 待 rule 类型下沉后，`fromJsonObject(json, XxxRule::class)` 需补注册对应 deserializer。
 */
private val jsonCodecGson: Gson by lazy {
    GsonBuilder()
        .registerTypeAdapter(
            object : TypeToken<Map<String?, Any?>?>() {}.type,
            MapDeserializerDoubleAsIntFix()
        )
        .registerTypeAdapter(Int::class.java, IntJsonDeserializer())
        .registerTypeAdapter(String::class.java, StringJsonDeserializer())
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()
}

/** 严格模式实例（对齐 app 侧 `GSONStrict`）。 */
private val strictGson: Gson by lazy {
    jsonCodecGson.newBuilder()
        .setStrictness(com.google.gson.Strictness.STRICT)
        .create()
}

actual object JsonCodec {

    actual fun toJson(obj: Any?): String = jsonCodecGson.toJson(obj)

    actual fun <T : Any> fromJsonObject(json: String?, clazz: KClass<T>): T? {
        if (json == null) return null
        return try {
            jsonCodecGson.fromJson(json, clazz.java)
        } catch (e: Exception) {
            null
        }
    }

    actual fun decodeStringMap(json: String?): Map<String, String>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<HashMap<String, String>>() {}.type
            jsonCodecGson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    actual fun decodeStringMapStrict(json: String?): Map<String, String>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<HashMap<String, String>>() {}.type
            strictGson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    actual fun decodeAnyMap(json: String?): Map<String, Any>? {
        if (json == null) return null
        return try {
            val type = object : TypeToken<Map<String, Any>>() {}.type
            jsonCodecGson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    actual fun <T : Any> decodeList(json: String?, clazz: KClass<T>): List<T>? {
        if (json == null) return null
        return try {
            val type = TypeToken.getParameterized(List::class.java, clazz.java).type
            @Suppress("UNCHECKED_CAST")
            (jsonCodecGson.fromJson(json, type) as List<T?>).filterNotNull()
        } catch (e: Exception) {
            null
        }
    }
}

private class StringJsonDeserializer : JsonDeserializer<String?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext?
    ): String? = when {
        json.isJsonPrimitive -> json.asString
        json.isJsonNull -> null
        else -> json.toString()
    }
}

private class IntJsonDeserializer : JsonDeserializer<Int?> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Int? = when {
        json.isJsonPrimitive -> {
            val prim = json.asJsonPrimitive
            if (prim.isNumber) prim.asNumber.toInt() else null
        }

        else -> null
    }
}

/** 修复「Int 变 Double」问题：整数 → Long，非整数 → Double。与 app 侧实现一致。 */
private class MapDeserializerDoubleAsIntFix : JsonDeserializer<Map<String, Any?>?> {
    @Throws(JsonParseException::class)
    override fun deserialize(
        jsonElement: JsonElement,
        type: Type,
        jsonDeserializationContext: JsonDeserializationContext
    ): Map<String, Any?>? {
        @Suppress("unchecked_cast")
        return read(jsonElement) as? Map<String, Any?>
    }

    fun read(json: JsonElement): Any? = when {
        json.isJsonArray -> {
            val list: MutableList<Any?> = ArrayList()
            for (item in json.asJsonArray) {
                list.add(read(item))
            }
            list
        }

        json.isJsonObject -> {
            val map: MutableMap<String, Any?> = LinkedTreeMap()
            for ((key, value) in json.asJsonObject.entrySet()) {
                map[key] = read(value)
            }
            map
        }

        json.isJsonPrimitive -> {
            val prim = json.asJsonPrimitive
            when {
                prim.isBoolean -> prim.asBoolean
                prim.isString -> prim.asString
                prim.isNumber -> {
                    val num: Number = prim.asNumber
                    if (ceil(num.toDouble()) == num.toLong().toDouble()) {
                        num.toLong()
                    } else {
                        num.toDouble()
                    }
                }

                else -> null
            }
        }

        else -> null
    }
}
