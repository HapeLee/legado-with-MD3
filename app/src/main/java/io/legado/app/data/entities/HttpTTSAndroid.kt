package io.legado.app.data.entities

import com.jayway.jsonpath.DocumentContext
import io.legado.app.utils.GSON
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readBool
import io.legado.app.utils.readInt
import io.legado.app.utils.readLong
import io.legado.app.utils.readString

/**
 * [HttpTTS] 的 JSON 反序列化。
 *
 * 原实体内嵌 companion 的 `fromJsonDoc/fromJson/fromJsonArray` 依赖 `com.jayway.jsonpath`
 * （纯 JVM 库）无法进 commonMain，故下沉时外置为顶层函数。
 */
@Suppress("MemberVisibilityCanBePrivate")
private fun fromHttpTTSJsonDoc(doc: DocumentContext): Result<HttpTTS> {
    return kotlin.runCatching {
        val loginUi = doc.read<Any>("$.loginUi")
        HttpTTS(
            id = doc.readLong("$.id") ?: System.currentTimeMillis(),
            name = doc.readString("$.name")!!,
            url = doc.readString("$.url")!!,
            contentType = doc.readString("$.contentType"),
            concurrentRate = doc.readString("$.concurrentRate"),
            loginUrl = doc.readString("$.loginUrl"),
            loginUi = if (loginUi is List<*>) GSON.toJson(loginUi) else loginUi?.toString(),
            header = doc.readString("$.header"),
            jsLib = doc.readString("$.jsLib"),
            enabledCookieJar = doc.readBool("$.enabledCookieJar"),
            loginCheckJs = doc.readString("$.loginCheckJs"),
            speed = doc.readInt("$.speed"),
            lastUpdateTime = doc.readLong("$.lastUpdateTime") ?: System.currentTimeMillis()
        )
    }
}

fun fromHttpTTSJson(json: String): Result<HttpTTS> {
    return fromHttpTTSJsonDoc(jsonPath.parse(json))
}

fun fromHttpTTSJsonArray(jsonArray: String): Result<ArrayList<HttpTTS>> {
    return kotlin.runCatching {
        val sources = arrayListOf<HttpTTS>()
        val doc = jsonPath.parse(jsonArray).read<List<*>>("$")
        doc.forEach {
            val jsonItem = jsonPath.parse(it)
            fromHttpTTSJsonDoc(jsonItem).getOrThrow().let { source ->
                sources.add(source)
            }
        }
        return@runCatching sources
    }
}
