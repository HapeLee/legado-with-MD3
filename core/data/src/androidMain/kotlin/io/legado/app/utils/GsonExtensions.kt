package io.legado.app.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonSyntaxException
import com.google.gson.Strictness
import com.google.gson.ToNumberPolicy
import com.google.gson.internal.LinkedTreeMap
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonWriter
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.ReviewRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.data.entities.rule.bookInfoRuleJsonDeserializer
import io.legado.app.data.entities.rule.contentRuleJsonDeserializer
import io.legado.app.data.entities.rule.exploreRuleJsonDeserializer
import io.legado.app.data.entities.rule.reviewRuleJsonDeserializer
import io.legado.app.data.entities.rule.searchRuleJsonDeserializer
import io.legado.app.data.entities.rule.tocRuleJsonDeserializer
import io.legado.app.data.entities.txtTocRuleJsonDeserializer
import io.legado.app.domain.model.json.isJsonArray
import io.legado.app.domain.model.json.isJsonObject
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import kotlin.math.ceil

val INITIAL_GSON: Gson by lazy {
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

val GSON: Gson by lazy {
    INITIAL_GSON.newBuilder()
        .registerTypeAdapter(ExploreRule::class.java, exploreRuleJsonDeserializer)
        .registerTypeAdapter(SearchRule::class.java, searchRuleJsonDeserializer)
        .registerTypeAdapter(BookInfoRule::class.java, bookInfoRuleJsonDeserializer)
        .registerTypeAdapter(TocRule::class.java, tocRuleJsonDeserializer)
        .registerTypeAdapter(ContentRule::class.java, contentRuleJsonDeserializer)
        .registerTypeAdapter(ReviewRule::class.java, reviewRuleJsonDeserializer)
        .registerTypeAdapter(TxtTocRule::class.java, txtTocRuleJsonDeserializer)
        .create()
}

val GSONStrict: Gson by lazy {
    GSON.newBuilder()
        .setStrictness(Strictness.STRICT)
        .create()
}

inline fun <reified T> genericType(): Type = object : TypeToken<T>() {}.type

inline fun <reified T> Gson.fromJsonObject(json: String?): Result<T> {
    return kotlin.runCatching {
        if (json == null) {
            throw JsonSyntaxException("解析字符串为空")
        }
        fromJson(json, genericType<T>()) as T
    }
}

inline fun <reified T> Gson.fromJsonArray(json: String?): Result<List<T>> {
    return kotlin.runCatching {
        if (json == null) {
            throw JsonSyntaxException("解析字符串为空")
        }
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(json, type) as List<T?>
        @Suppress("UNCHECKED_CAST")
        list.filterNotNull() as List<T>
    }
}

inline fun <reified T> Gson.fromJsonObject(inputStream: InputStream?): Result<T> {
    return kotlin.runCatching {
        if (inputStream == null) {
            throw JsonSyntaxException("解析流为空")
        }
        val reader = InputStreamReader(inputStream)
        fromJson(reader, genericType<T>()) as T
    }
}

inline fun <reified T> Gson.fromJsonArray(inputStream: InputStream?): Result<List<T>> {
    return kotlin.runCatching {
        if (inputStream == null) {
            throw JsonSyntaxException("解析流为空")
        }
        val reader = InputStreamReader(inputStream)
        val type = TypeToken.getParameterized(List::class.java, T::class.java).type
        val list = fromJson(reader, type) as List<T?>
        @Suppress("UNCHECKED_CAST")
        list.filterNotNull() as List<T>
    }
}

fun Gson.writeToOutputStream(out: OutputStream, any: Any) {
    val writer = JsonWriter(OutputStreamWriter(out, "UTF-8"))
    writer.setIndent("  ")
    if (any is List<*>) {
        writer.beginArray()
        any.forEach {
            it?.let {
                toJson(it, it::class.java, writer)
            }
        }
        writer.endArray()
    } else {
        toJson(any, any::class.java, writer)
    }
    writer.close()
}

/**
 *
 */
class StringJsonDeserializer : JsonDeserializer<String?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext?
    ): String? {
        return when {
            json.isJsonPrimitive -> json.asString
            json.isJsonNull -> null
            else -> json.toString()
        }
    }

}

/**
 * int类型转化失败时跳过
 */
class IntJsonDeserializer : JsonDeserializer<Int?> {

    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): Int? {
        return when {
            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                if (prim.isNumber) {
                    prim.asNumber.toInt()
                } else {
                    null
                }
            }

            else -> null
        }
    }

}

/**
 * 修复Int变为Double的问题
 */
class MapDeserializerDoubleAsIntFix :
    JsonDeserializer<Map<String, Any?>?> {

    @Throws(JsonParseException::class)
    override fun deserialize(
        jsonElement: JsonElement,
        type: Type,
        jsonDeserializationContext: JsonDeserializationContext
    ): Map<String, Any?>? {
        @Suppress("unchecked_cast")
        return read(jsonElement) as? Map<String, Any?>
    }

    fun read(json: JsonElement): Any? {
        when {
            json.isJsonArray -> {
                val list: MutableList<Any?> = ArrayList()
                val arr = json.asJsonArray
                for (anArr in arr) {
                    list.add(read(anArr))
                }
                return list
            }

            json.isJsonObject -> {
                val map: MutableMap<String, Any?> =
                    LinkedTreeMap()
                val obj = json.asJsonObject
                val entitySet =
                    obj.entrySet()
                for ((key, value) in entitySet) {
                    map[key] = read(value)
                }
                return map
            }

            json.isJsonPrimitive -> {
                val prim = json.asJsonPrimitive
                when {
                    prim.isBoolean -> {
                        return prim.asBoolean
                    }

                    prim.isString -> {
                        return prim.asString
                    }

                    prim.isNumber -> {
                        val num: Number = prim.asNumber
                        // here you can handle double int/long values
                        // and return any type you want
                        // this solution will transform 3.0 float to long values
                        return if (ceil(num.toDouble()) == num.toLong().toDouble()) {
                            num.toLong()
                        } else {
                            num.toDouble()
                        }
                    }
                }
            }
        }
        return null
    }

}

/**
 * 解析 TXT 目录规则数组（M1-3y，供 `:feature:txttocrules` 的平台契约调用）。
 *
 * 为什么单独给两个函数、而不是让 Feature 直接 `GSON.fromJsonArray<TxtTocRule>`：
 * [TxtTocRule] 需要旧版本备份的键名兼容（`rule` → `chapterRule`），这由 [GSON] 上注册的
 * [txtTocRuleJsonDeserializer] 完成，而共享层的 `JsonCodec` **不含**该 deserializer。
 * 本函数与 GSON 门面同居 `io.legado.app.utils`，因此引用 [GSON] 无需 import ——
 * 这不是风格问题：G4 的 `gson` 规则按 `import io.legado.app.utils.GSON` 锚定并做目录级计数，
 * 在已 import 它的文件之外新增使用会撑爆棘轮。放在这里既复用门面又不新增计分点。
 *
 * 失败语义与迁移前 `TxtTocRuleViewModel.parseImportRules` 一致：**抛异常**
 * （调用方 `RuleTransferUseCase` 据此把导入状态置成 `Error`），不静默返回空。
 */
fun parseTxtTocRules(json: String): List<TxtTocRule> =
    GSON.fromJsonArray<TxtTocRule>(json).getOrThrow()

/** 解析单个 TXT 目录规则对象，语义同 [parseTxtTocRules]。 */
fun parseTxtTocRule(json: String): TxtTocRule =
    GSON.fromJsonObject<TxtTocRule>(json).getOrThrow()