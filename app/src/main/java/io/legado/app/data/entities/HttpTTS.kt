package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jayway.jsonpath.DocumentContext
import io.legado.app.utils.GSON
import io.legado.app.utils.jsonPath
import io.legado.app.utils.readBool
import io.legado.app.utils.readInt
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
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    override var header: String? = null,
    override var jsLib: String? = null,
    @ColumnInfo(defaultValue = "0")
    override var enabledCookieJar: Boolean? = false,
    var loginCheckJs: String? = null,
    /**
     * 源级语速, 0..80, 实际倍速为 (speed + 5) / 10, null 时使用默认值 5 (1 倍速)。
     * 仅当源接口支持语速参数 ({{speakSpeed}}) 时影响合成结果。
     */
    var speed: Int? = null,
    /**
     * 源自己声明的合成并发上限, 1..8。
     *
     * 外部源（含参考实现导出的 httpTTS.json）会带这个字段，导入时保留，
     * 预合成并发取它与全局设置里较小的那个。
     */
    @ColumnInfo(defaultValue = "1")
    var synthesisThreadCount: Int = 1,
    @ColumnInfo(defaultValue = "0")
    var lastUpdateTime: Long = System.currentTimeMillis()
) : BaseSource {

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "httpTts:$id"
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {

        /** 合成并发上限，与朗读设置里全局并发的取值范围一致。 */
        const val MAX_SYNTHESIS_THREAD_COUNT = 8

        fun fromJsonDoc(doc: DocumentContext): Result<HttpTTS> {
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
                    synthesisThreadCount = doc.readInt("$.synthesisThreadCount")
                        ?.coerceIn(1, MAX_SYNTHESIS_THREAD_COUNT) ?: 1,
                    lastUpdateTime = doc.readLong("$.lastUpdateTime") ?: System.currentTimeMillis()
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
