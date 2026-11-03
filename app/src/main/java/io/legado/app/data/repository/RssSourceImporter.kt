package io.legado.app.data.repository

import android.app.Application
import androidx.core.net.toUri
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.data.entities.RssSource
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readText
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

internal class RssSourceImporter(private val application: Application) {
    suspend fun read(input: String): List<RssSource> = withContext(IO) {
        val text = input.trim()
        when {
            text.isJsonObject() -> {
                val json = JsonParser.parseString(text).asJsonObject
                val urls = json.get("sourceUrls")
                if (urls?.isJsonArray == true) {
                    urls.asJsonArray.flatMap { readUrl(it.asString) }
                } else {
                    listOf(GSON.fromJsonObject<RssSource>(text).getOrThrow()).validate()
                }
            }
            text.isJsonArray() -> GSON.fromJsonArray<RssSource>(text).getOrThrow().validate()
            text.isAbsUrl() -> readUrl(text)
            text.isUri() -> read(text.toUri().readText(application))
            else -> throw NoStackTraceException(application.getString(R.string.wrong_format))
        }
    }

    suspend fun save(sources: List<RssSource>) = withContext(IO) {
        SourceHelp.insertRssSource(*sources.toTypedArray())
    }

    private fun List<RssSource>.validate(): List<RssSource> {
        if (firstOrNull()?.sourceUrl?.isEmpty() == true) {
            throw NoStackTraceException(application.getString(R.string.wrong_format))
        }
        return this
    }

    private suspend fun readUrl(sourceUrl: String): List<RssSource> {
        return okHttpClient.newCallResponseBody {
            if (sourceUrl.endsWith("#requestWithoutUA")) {
                url(sourceUrl.removeSuffix("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(sourceUrl)
            }
        }.decompressed().byteStream().use { body ->
            val items: List<Map<String, Any>> = jsonPath.parse(body).read("$")
            items.map { item ->
                if (!item.containsKey("sourceUrl")) {
                    throw NoStackTraceException(application.getString(R.string.wrong_format))
                }
                GSON.fromJsonObject<RssSource>(jsonPath.parse(item).jsonString()).getOrThrow()
            }
        }
    }
}
