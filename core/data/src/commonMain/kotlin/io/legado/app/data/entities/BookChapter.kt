package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import io.legado.app.core.platform.JsonCodec
import io.legado.app.data.bigdata.BigDataStoreProvider
import io.legado.app.model.analyzeRule.RuleDataInterface

/**
 * 章节实体。
 *
 * 依赖 Android/JVM 平台栈的方法已抽到 `:app` 侧 `BookChapterAndroid.kt` 同名扩展：
 * `getDisplayTitle`（ChineseUtils/appDb/appCtx/AppLog/ReplaceRule）、
 * `getAbsoluteURL`（AnalyzeUrl/NetworkUtils）、`getFileName`/`getFontName`（MD5Utils）。
 * 模式同 `BookAndroid.kt`。
 */
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"],
    indices = [(Index(value = ["bookUrl"], unique = false)),
        (Index(value = ["bookUrl", "index"], unique = true))],
    foreignKeys = [(ForeignKey(
        entity = Book::class,
        parentColumns = ["bookUrl"],
        childColumns = ["bookUrl"],
        onDelete = ForeignKey.CASCADE
    ))]
)    // 删除书籍时自动删除章节
data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    @ColumnInfo(defaultValue = "0")
    var tocLevel: Int = 0,              // 目录层级，0 为顶层
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信息
    var wordCount: String? = null,      // 本章节字数
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null,        //变量
    var reviewImg: String? = null        //段评图标
) : RuleDataInterface {

    @delegate:Transient
    @delegate:Ignore
    override val variableMap: HashMap<String, String> by lazy {
        JsonCodec.decodeStringMap(variable)?.let { HashMap(it) } ?: hashMapOf()
    }

    /** `getFileName()`/`getFontName()` 的派生缓存；不落库。 */
    @Ignore
    var titleMD5: String? = null

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = JsonCodec.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        BigDataStoreProvider.current.putChapterVariable(bookUrl, url, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return BigDataStoreProvider.current.getChapterVariable(bookUrl, url, key)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String {
        return bookUrl + url
    }
}
