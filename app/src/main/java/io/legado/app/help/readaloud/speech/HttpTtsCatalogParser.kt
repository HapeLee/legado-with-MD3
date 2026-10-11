package io.legado.app.help.readaloud.speech

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.domain.model.readaloud.HttpTtsCatalogDraft
import io.legado.app.domain.model.readaloud.HttpTtsEmotionDraft
import io.legado.app.domain.model.readaloud.HttpTtsSpeakerDraft

/**
 * 解析 httpTTS 源 JSON 里自带的发言人 / 情绪目录。
 *
 * 纯 Kotlin + Gson，不碰 Android，也不碰数据库，便于直接单测。
 *
 * 兼容两种写法（与参考实现一致，第三方源两种都在用）：
 *
 * - 平铺：`[{"speakerName":"晓晓","toneID":"zh-CN-XiaoxiaoNeural"}]`
 * - 分组：`[{"groupName":"女声","items":[{"speakerName":"晓晓","toneID":"..."}]}]`
 *
 * 字段别名也一并兼容：`toneID` / `toneId` / `tone_id`、`emotionTag` / `tag` / `value`。
 * `speakerName` 或 `toneID` 缺一项的条目直接丢弃——只有名字没有音色 id 的发言人是没法合成的。
 */
object HttpTtsCatalogParser {

    /** 解析单个源对象。返回顺序与源里声明的顺序一致（平铺项在前，分组项在后）。 */
    fun parse(sourceJson: String): HttpTtsCatalogDraft {
        val root = runCatching { JsonParser.parseString(sourceJson) }.getOrNull()
            ?: return HttpTtsCatalogDraft()
        if (!root.isJsonObject) return HttpTtsCatalogDraft()
        return parseCatalog(
            speakersJson = readString(root.asJsonObject, "speakersJson"),
            emotionsJson = readString(root.asJsonObject, "emotionsJson"),
        )
    }

    /**
     * 解析源数组，返回与 `HttpTTS.fromJsonArray` 同序的目录草稿，便于按下标配对。
     *
     * 顶层不是数组时退化成单对象解析。
     */
    fun parseList(sourceJsonArray: String): List<HttpTtsCatalogDraft> {
        val root = runCatching { JsonParser.parseString(sourceJsonArray) }.getOrNull()
            ?: return emptyList()
        if (!root.isJsonArray) return listOf(parse(sourceJsonArray))
        return root.asJsonArray.map { element ->
            if (!element.isJsonObject) {
                HttpTtsCatalogDraft()
            } else {
                parseCatalog(
                    speakersJson = readString(element.asJsonObject, "speakersJson"),
                    emotionsJson = readString(element.asJsonObject, "emotionsJson"),
                )
            }
        }
    }

    private fun parseCatalog(
        speakersJson: String?,
        emotionsJson: String?,
    ): HttpTtsCatalogDraft = HttpTtsCatalogDraft(
        speakers = parseSpeakers(speakersJson),
        emotions = parseEmotions(emotionsJson),
    )

    private fun parseSpeakers(json: String?): List<HttpTtsSpeakerDraft> {
        val flat = ArrayList<HttpTtsSpeakerDraft>()
        val grouped = ArrayList<HttpTtsSpeakerDraft>()
        elements(json).forEach { element ->
            if (!element.isJsonObject) return@forEach
            val obj = element.asJsonObject
            val items = obj.get("items")?.takeIf { it.isJsonArray }
            if (items != null) {
                items.asJsonArray.forEach { item ->
                    if (item.isJsonObject) {
                        speakerFrom(item.asJsonObject)?.let(grouped::add)
                    }
                }
            } else {
                speakerFrom(obj)?.let(flat::add)
            }
        }
        // 同一个 toneID 只保留首次出现的那条：重复导入不该让目录翻倍
        return (flat + grouped).distinctBy { it.toneID }
    }

    private fun parseEmotions(json: String?): List<HttpTtsEmotionDraft> {
        val flat = ArrayList<HttpTtsEmotionDraft>()
        val grouped = ArrayList<HttpTtsEmotionDraft>()
        elements(json).forEach { element ->
            if (!element.isJsonObject) return@forEach
            val obj = element.asJsonObject
            val items = obj.get("items")?.takeIf { it.isJsonArray }
            if (items != null) {
                items.asJsonArray.forEach { item ->
                    if (item.isJsonObject) {
                        emotionFrom(item.asJsonObject)?.let(grouped::add)
                    }
                }
            } else {
                emotionFrom(obj)?.let(flat::add)
            }
        }
        return (flat + grouped).distinctBy { it.emotionTag }
    }

    private fun speakerFrom(obj: JsonObject): HttpTtsSpeakerDraft? {
        val name = firstNonBlank(obj, "speakerName", "name")
        val toneId = firstNonBlank(obj, "toneID", "toneId", "tone_id")
        if (name == null || toneId == null) return null
        return HttpTtsSpeakerDraft(
            speakerName = name,
            toneID = toneId,
            tags = stringArray(obj, "tags"),
        )
    }

    private fun emotionFrom(obj: JsonObject): HttpTtsEmotionDraft? {
        val name = firstNonBlank(obj, "emotionName", "name")
        val tag = firstNonBlank(obj, "emotionTag", "tag", "value")
        if (name == null || tag == null) return null
        return HttpTtsEmotionDraft(emotionName = name, emotionTag = tag)
    }

    /** 顶层是数组就展开，是单个对象就包一层——两种写法第三方源都在用。 */
    private fun elements(json: String?): List<JsonElement> {
        if (json.isNullOrBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptyList()
        return when {
            root.isJsonArray -> root.asJsonArray.toList()
            root.isJsonObject -> listOf(root)
            else -> emptyList()
        }
    }

    private fun firstNonBlank(obj: JsonObject, vararg names: String): String? {
        names.forEach { name ->
            val element = obj.get(name) ?: return@forEach
            if (!element.isJsonPrimitive) return@forEach
            val value = element.asString
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun stringArray(obj: JsonObject, name: String): List<String> {
        val array = obj.get(name)?.takeIf { it.isJsonArray } ?: return emptyList()
        return array.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * 取字符串字段。
     *
     * 目录字段在源里也可能被写成对象/数组（用户手误），这里只认字符串，
     * 其它形态当空处理，避免把 `{"a":1}` 之类塞给下一层解析。
     */
    private fun readString(obj: JsonObject, name: String): String? {
        val element = obj.get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return element.asString
    }
}
