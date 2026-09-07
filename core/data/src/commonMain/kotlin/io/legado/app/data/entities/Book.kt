package io.legado.app.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.core.platform.JsonCodec
import io.legado.app.core.platform.systemTimeMillis

/**
 * 书架书籍实体。
 *
 * 依赖 Android/JVM 平台栈的方法已抽到 `:app` 侧的 `BookAndroid.kt` 同名扩展
 * （`fileCharset` / `getFolderName` / `getUnreadChapterNum` / `toSearchBook` /
 * `migrateTo` / `save` / `delete` / `getPageAnim` / `getUseReplaceRule` /
 * `getStartDate` / `setStartDate`），模式同 `BookGroupAndroid.kt`、`DictRuleAndroid.kt`。
 */
@TypeConverters(Book.Converters::class)
@Entity(
    tableName = "books",
    indices = [
        Index(value = ["name", "author"], unique = false),
        Index(value = ["durChapterTime"], unique = false)
    ]
)
data class Book(
    // 详情页Url(本地书源存储完整文件路径)
    @PrimaryKey
    @ColumnInfo(defaultValue = "")
    override var bookUrl: String = "",
    // 目录页Url (toc=table of Contents)
    @ColumnInfo(defaultValue = "")
    var tocUrl: String = "",
    // 书源URL(默认BookType.local)
    @ColumnInfo(defaultValue = BookType.localTag)
    var origin: String = BookType.localTag,
    //书源名称 or 本地书籍文件名
    @ColumnInfo(defaultValue = "")
    var originName: String = "",
    // 书籍名称(书源获取)
    @ColumnInfo(defaultValue = "")
    override var name: String = "",
    // 作者名称(书源获取)
    @ColumnInfo(defaultValue = "")
    override var author: String = "",
    // 分类信息(书源获取)
    override var kind: String? = null,
    // 分类信息(用户修改)
    var customTag: String? = null,
    // 封面Url(书源获取)
    var coverUrl: String? = null,
    // 封面Url(用户修改)
    var customCoverUrl: String? = null,
    // 简介内容(书源获取)
    var intro: String? = null,
    // 简介内容(用户修改)
    var customIntro: String? = null,
    var remark: String? = null,
    // 自定义字符集名称(仅适用于本地书籍)
    var charset: String? = null,
    // 类型,详见BookType
    @ColumnInfo(defaultValue = "0")
    var type: Int = BookType.text,
    // 自定义分组索引号
    @ColumnInfo(defaultValue = "0")
    var group: Long = 0,
    // 最新章节标题
    var latestChapterTitle: String? = null,
    // 最新章节标题更新时间
    @ColumnInfo(defaultValue = "0")
    var latestChapterTime: Long = systemTimeMillis(),
    // 最近一次更新书籍信息的时间
    @ColumnInfo(defaultValue = "0")
    var lastCheckTime: Long = systemTimeMillis(),
    // 最近一次发现新章节的数量
    @ColumnInfo(defaultValue = "0")
    var lastCheckCount: Int = 0,
    // 书籍目录总数
    @ColumnInfo(defaultValue = "0")
    var totalChapterNum: Int = 0,
    // 当前章节名称
    var durChapterTitle: String? = null,
    // 当前章节索引
    @ColumnInfo(defaultValue = "0")
    var durChapterIndex: Int = 0,
    // 当前阅读的进度(首行字符的索引位置)
    @ColumnInfo(defaultValue = "0")
    var durChapterPos: Int = 0,
    // 最近一次阅读书籍的时间(打开正文的时间)
    @ColumnInfo(defaultValue = "0")
    var durChapterTime: Long = systemTimeMillis(),
    //字数
    override var wordCount: String? = null,
    // 刷新书架时更新书籍信息
    @ColumnInfo(defaultValue = "1")
    var canUpdate: Boolean = true,
    // 手动排序
    @ColumnInfo(defaultValue = "0")
    var order: Int = 0,
    //书源排序
    @ColumnInfo(defaultValue = "0")
    var originOrder: Int = 0,
    // 自定义书籍变量信息(用于书源规则检索书籍信息)
    override var variable: String? = null,
    //阅读设置
    var readConfig: ReadConfig? = null,
    //同步时间
    @ColumnInfo(defaultValue = "0")
    var syncTime: Long = 0L,
    // 简介内容(书源列表/发现规则获取), 与详情规则拿到的 intro 是书源作者写的两条规则,
    // 聚合类书源常把服务状态排版进详情简介, 书架等列表场景优先用这一份
    var listIntro: String? = null
) : BaseBook {

    init {
        kind = kind?.take(1000)
        intro = intro?.take(5000)
        listIntro = listIntro?.take(5000)
        customTag = customTag?.take(1000)
        customIntro = customIntro?.take(5000)
        remark = remark?.take(1000)
        latestChapterTitle = latestChapterTitle?.take(200)
        durChapterTitle = durChapterTitle?.take(200)
    }

    @delegate:Transient
    @delegate:Ignore
    override val variableMap: HashMap<String, String> by lazy {
        JsonCodec.decodeStringMap(variable)?.let { HashMap(it) } ?: hashMapOf()
    }

    @Ignore
    override var infoHtml: String? = null

    @Ignore
    override var tocHtml: String? = null

    @Ignore
    var downloadUrls: List<String>? = null

    /** `getFolderName()` 的派生缓存；不落库、不参与 equals/copy，由 Android 侧扩展写入。 */
    @Ignore
    var folderName: String? = null

    @get:Ignore
    val lastChapterIndex get() = totalChapterNum - 1

    fun getRealAuthor() = author.replace(AppPattern.authorRegex, "")

    fun getDisplayCover() = if (customCoverUrl.isNullOrEmpty()) coverUrl else customCoverUrl

    fun getDisplayIntro() = if (customIntro.isNullOrEmpty()) intro else customIntro

    //自定义简介有自动更新的需求时，可通过更新intro再调用upCustomIntro()完成
    @Suppress("unused")
    fun upCustomIntro() {
        customIntro = intro
    }

    @get:Ignore
    val config: ReadConfig
        get() {
            if (readConfig == null) {
                readConfig = ReadConfig()
            }
            return readConfig!!
        }

    // 音频片头（秒）的 setter 和 getter
    fun setOpenCredits(openCredits: Int) {
        config.openCredits = openCredits
    }

    fun getOpenCredits(): Int {
        return config.openCredits
    }

    // 音频片尾（秒）的 setter 和 getter
    fun setCloseCredits(closeCredits: Int) {
        config.closeCredits = closeCredits
    }

    fun getCloseCredits(): Int {
        return config.closeCredits
    }

    // 音频播放模式 的 setter 和 getter
    fun setPlayMode(playMode: Int) {
        config.playMode = playMode
    }

    fun getPlayMode(): Int {
        return config.playMode
    }

    // 音频增益（mB）的 setter 和 getter
    fun setAudioGain(audioGain: Int) {
        config.audioGain = audioGain
    }

    fun getAudioGain(): Int {
        return config.audioGain
    }

    fun setReverseToc(reverseToc: Boolean) {
        config.reverseToc = reverseToc
    }

    fun getReverseToc(): Boolean {
        return config.reverseToc
    }

    fun setUseReplaceRule(useReplaceRule: Boolean) {
        config.useReplaceRule = useReplaceRule
    }

    fun setReSegment(reSegment: Boolean) {
        config.reSegment = reSegment
    }

    fun getReSegment(): Boolean {
        return config.reSegment
    }

    fun setPageAnim(pageAnim: Int?) {
        config.pageAnim = pageAnim
    }

    fun setImageStyle(imageStyle: String?) {
        config.imageStyle = imageStyle
    }

    fun getImageStyle(): String? {
        return config.imageStyle
    }

    fun setTtsEngine(ttsEngine: String?) {
        config.ttsEngine = ttsEngine
    }

    fun getTtsEngine(): String? {
        return config.ttsEngine
    }

    fun setSplitLongChapter(limitLongContent: Boolean) {
        config.splitLongChapter = limitLongContent
    }

    fun getSplitLongChapter(): Boolean {
        return config.splitLongChapter
    }

    // readSimulating 的 setter 和 getter
    fun setReadSimulating(readSimulating: Boolean) {
        config.readSimulating = readSimulating
    }

    fun getReadSimulating(): Boolean {
        return config.readSimulating
    }

    // startChapter 的 setter 和 getter
    fun setStartChapter(startChapter: Int) {
        config.startChapter = startChapter
    }

    fun getStartChapter(): Int {
        if (config.readSimulating) return config.startChapter ?: 0
        return this.durChapterIndex
    }

    fun setTranslationMode(enabled: Boolean) {
        config.translationMode = enabled
    }

    fun getTranslationMode(): Boolean {
        return config.translationMode
    }

    // dailyChapters 的 setter 和 getter
    fun setDailyChapters(dailyChapters: Int) {
        config.dailyChapters = dailyChapters
    }

    fun getDailyChapters(): Int {
        return config.dailyChapters
    }

    fun getDelTag(tag: Long): Boolean {
        return config.delTag and tag == tag
    }

    fun addDelTag(tag: Long) {
        config.delTag = config.delTag and tag
    }

    fun removeDelTag(tag: Long) {
        config.delTag = config.delTag and tag.inv()
    }

    fun createBookMark(): Bookmark {
        return Bookmark(
            bookName = name,
            bookAuthor = author,
            // 源指纹：创建时的书源，跳转校验用（换源后位置可能偏移）
            bookUrl = bookUrl,
        )
    }

    @Suppress("ConstPropertyName")
    companion object {
        const val hTag = 2L
        const val rubyTag = 4L
        const val imgStyleDefault = "DEFAULT"
        const val imgStyleFull = "FULL"
        const val imgStyleText = "TEXT"
        const val imgStyleSingle = "SINGLE"
    }

    data class ReadConfig(
        var reverseToc: Boolean = false,
        var pageAnim: Int? = null,
        var reSegment: Boolean = false,
        var imageStyle: String? = null,
        var useReplaceRule: Boolean? = null,// 正文使用净化替换规则
        var delTag: Long = 0L,//去除标签
        var ttsEngine: String? = null,
        var splitLongChapter: Boolean = true,
        var readSimulating: Boolean = false,
        var startDate: String? = null,
        var startChapter: Int? = null,     // 用户设置的起始章节
        var dailyChapters: Int = 3,    // 用户设置的每日更新章节数

        val mangaColorFilter: String? = null,
        var mangaScrollMode: Int? = null,
        var webtoonSidePaddingDp: Int? = null,
        var mangaBackground: String? = null,

        var fixedType: Boolean = false, // 固定书籍类型,不随书源更新

        var translationMode: Boolean = false, // 是否启用翻译阅读模式

        var openCredits: Int = 0,    // 音频片头（秒）
        var closeCredits: Int = 0,   // 音频片尾（秒）
        var playMode: Int = 0,       // 音频播放模式
        var audioGain: Int = 0       // 音频增益（mB，-6000..6000）

    )

    class Converters {

        @TypeConverter
        fun readConfigToString(config: ReadConfig?): String = JsonCodec.toJson(config)

        @TypeConverter
        fun stringToReadConfig(json: String?) = JsonCodec.fromJsonObject(json, ReadConfig::class)
    }
}
