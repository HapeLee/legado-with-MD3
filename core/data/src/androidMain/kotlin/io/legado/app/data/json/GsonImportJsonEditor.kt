package io.legado.app.data.json

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.legado.app.core.platform.ImportFieldValue
import io.legado.app.core.platform.ImportJsonEditor
import io.legado.app.core.platform.ImportJsonField
import io.legado.app.utils.GSON

/**
 * [ImportJsonEditor] 的 Gson 实现。
 *
 * 四个私有辅助函数是从 `:core:ui` 的 `ImportComponents.kt` **原样搬过来**的
 * （`toImportJsonObject` / `toImportDataLike` / `toImportEditText` / `toImportJsonElement`），
 * 语义逐分支保持一致；只有 `toImportEditText` 因为 UI 改用值模型，拆成了
 * [JsonElement.toFieldValue]。
 *
 * 放在 `:core:data/androidMain` 是因为它依赖 app 配置好的 `GSON`（含自定义
 * String/Int/Map 反序列化器与 prettyPrinting/disableHtmlEscaping），而该门面在第九片已下沉到本模块。
 */
class GsonImportJsonEditor : ImportJsonEditor {

    override fun fieldsOf(data: Any?): List<ImportJsonField>? {
        val jsonObject = data.toImportJsonObject() ?: return null
        return jsonObject.entrySet().map { (name, value) ->
            ImportJsonField(name, value.toFieldValue())
        }
    }

    override fun <T : Any> withEditedText(data: T?, fieldName: String, text: String): T? {
        val jsonObject = data.toImportJsonObject() ?: return null
        val oldValue = jsonObject.get(fieldName) ?: return null
        val newValue = text.toImportJsonElement(oldValue) ?: return null
        jsonObject.add(fieldName, newValue)
        return jsonObject.toImportDataLike(data)
    }

    override fun <T : Any> withBoolean(data: T?, fieldName: String, value: Boolean): T? {
        val jsonObject = data.toImportJsonObject() ?: return null
        jsonObject.add(fieldName, JsonPrimitive(value))
        return jsonObject.toImportDataLike(data)
    }

    private fun Any?.toImportJsonObject(): JsonObject? {
        return GSON.toJsonTree(this).takeIf { it.isJsonObject }?.asJsonObject
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> JsonObject.toImportDataLike(data: T?): T? {
        val clazz = data?.let { it::class.java } ?: return null
        return runCatching { GSON.fromJson(this, clazz) as T }.getOrNull()
    }

    /** 对齐迁移前的 `JsonElement.toImportEditText()` + `BatchImportJsonField` 的布尔分支。 */
    private fun JsonElement.toFieldValue(): ImportFieldValue {
        return when {
            this is JsonNull || isJsonNull -> ImportFieldValue.Null
            isJsonObject || isJsonArray -> ImportFieldValue.Json(GSON.toJson(this))
            isJsonPrimitive -> {
                val primitive = asJsonPrimitive
                if (primitive.isBoolean) {
                    ImportFieldValue.Bool(primitive.asBoolean)
                } else {
                    ImportFieldValue.Text(primitive.asString)
                }
            }

            else -> ImportFieldValue.Text(toString())
        }
    }

    /** 原 `String.toImportJsonElement(oldValue)`：注意 `this`（未 trim）与 `text`（trim 后）的区别。 */
    private fun String.toImportJsonElement(oldValue: JsonElement): JsonElement? {
        val text = trim()
        if (oldValue.isJsonNull) {
            return if (text.isEmpty()) JsonNull.INSTANCE else JsonPrimitive(this)
        }

        if (oldValue.isJsonObject || oldValue.isJsonArray) {
            if (text.isEmpty()) return JsonNull.INSTANCE
            return runCatching { JsonParser.parseString(this) }.getOrNull()
        }

        if (!oldValue.isJsonPrimitive) return JsonPrimitive(this)

        val primitive = oldValue.asJsonPrimitive
        return when {
            primitive.isNumber -> {
                if (text.isEmpty()) {
                    JsonNull.INSTANCE
                } else {
                    text.toLongOrNull()?.let { JsonPrimitive(it) }
                        ?: text.toDoubleOrNull()?.let { JsonPrimitive(it) }
                }
            }

            else -> JsonPrimitive(this)
        }
    }
}
