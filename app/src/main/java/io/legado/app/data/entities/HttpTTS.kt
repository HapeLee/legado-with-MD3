package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jayway.jsonpath.DocumentContext
import io.legado.app.utils.GSON
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readLong
import io.legado.app.utils.readString

/**
 * 在线朗读引擎
 */
@Entity(tableName = "httpTTS")
data class HttpTTS(
    @PrimaryKey
    val id: Long = System.currentTimeMillis(),
    var name: String = "",
    var url: String = "",
    var contentType: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var concurrentRate: String? = "0",
    @ColumnInfo(defaultValue = "1")
    var synthesisThreadCount: Int = 1,
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    override var header: String? = null,
    override var jsLib: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var enabledCookieJar: Boolean? = false,
    var loginCheckJs: String? = null,
    @ColumnInfo(defaultValue = "")
    var speakersJson: String = "",
    @ColumnInfo(defaultValue = "")
    var emotionsJson: String = "",
    @ColumnInfo(defaultValue = "0")
    var lastUpdateTime: Long = System.currentTimeMillis()
) : BaseSource {

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "httpTts:$id"
    }

    fun equal(source: HttpTTS): Boolean {
        return name == source.name &&
                url == source.url &&
                contentType == source.contentType &&
                concurrentRate == source.concurrentRate &&
                synthesisThreadCount == source.synthesisThreadCount &&
                loginUrl == source.loginUrl &&
                loginUi == source.loginUi &&
                header == source.header &&
                jsLib == source.jsLib &&
                enabledCookieJar == source.enabledCookieJar &&
                loginCheckJs == source.loginCheckJs &&
                speakersJson == source.speakersJson &&
                emotionsJson == source.emotionsJson
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {

        fun fromJsonDoc(doc: DocumentContext): Result<HttpTTS> {
            return kotlin.runCatching {
                val loginUi = doc.read<Any>("$.loginUi")
                HttpTTS(
                    id = doc.readLong("$.id") ?: System.currentTimeMillis(),
                    name = doc.readString("$.name")!!,
                    url = doc.readString("$.url")!!,
                    contentType = doc.readString("$.contentType"),
                    concurrentRate = doc.readString("$.concurrentRate"),
                    synthesisThreadCount = doc.readLong("$.synthesisThreadCount")?.toInt()
                        ?.coerceIn(1, 8) ?: 1,
                    loginUrl = doc.readString("$.loginUrl"),
                    loginUi = if (loginUi is List<*>) GSON.toJson(loginUi) else loginUi?.toString(),
                    header = doc.readString("$.header"),
                    loginCheckJs = doc.readString("$.loginCheckJs"),
                    speakersJson = doc.readString("$.speakersJson").orEmpty(),
                    emotionsJson = doc.readString("$.emotionsJson").orEmpty(),
                    lastUpdateTime = doc.readLong("$.lastUpdateTime") ?: System.currentTimeMillis(),
                    jsLib = doc.readString("$.jsLib")
                )
            }
        }

        fun fromJson(json: String): Result<HttpTTS> {
            return fromJsonDoc(jsonPath.parse(json))
        }

        fun fromJsonArray(jsonArray: String): Result<ArrayList<HttpTTS>> {
            return kotlin.runCatching {
                val sources = arrayListOf<HttpTTS>()
                val doc = jsonPath.parse(jsonArray).read<List<*>>("$")
                doc.forEach {
                    val jsonItem = jsonPath.parse(it)
                    fromJsonDoc(jsonItem).getOrThrow().let { source ->
                        sources.add(source)
                    }
                }
                return@runCatching sources
            }
        }

    }

}
