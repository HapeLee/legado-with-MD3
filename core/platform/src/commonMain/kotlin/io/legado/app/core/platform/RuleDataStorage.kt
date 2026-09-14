package io.legado.app.core.platform

/** 目录条目：(名称, 是否目录)。 */
data class RuleDataEntry(val name: String, val isDirectory: Boolean)

/**
 * 书源大变量（ruleData）存储原语。
 *
 * 背景：书源规则里超过 10000 字符的变量不进数据库，而是落到以「实体标识 + key」的 MD5
 * 为路径的文件里（见 [io.legado.app.model.analyzeRule.RuleDataInterface.putVariable] 的大值分支）。
 *
 * **为什么是 expect/actual 原语，而不是「interface + Provider 注入」**：
 * 消费方是 Room entity 的实例方法（`BaseBook`/`BaseRssArticle`/`BookChapter` 的
 * `putBigVariable`），而 entity 由 Room 构造、不经过 DI；entity 又是书源 JS 的兼容面
 * （`AnalyzeRule` 里 `bindings["book"] = book`，脚本直接调 `book.putVariable`），
 * 签名与语义都不能改。这类「由框架构造、拿不到注入」的对象要访问平台能力，只有两条路：
 * 全局 service locator，或平台原语。本片选后者——模式同 [JsonCodec]（expect object，
 * 被 entity 的 `variableMap` 直接使用）与 [JvmFileSystem]/[JcaDigest]。
 *
 * **根目录由 host 设置**：Android 的应用专属外部目录（`Context.externalFiles`）只能从
 * `Context` 取得，而 `Context` 是 host 的职责；因此这里只保留「一个路径」这一项配置，
 * 由 host 在 composition root 设置一次（见 `:app` 的 `App.onCreate` 与 `host:desktop`）。
 * 未设置就访问会显式失败，不会静默写到错误位置——沿用「平台能力不可用必须显式可见」的纪律。
 *
 * **路径规则不进本契约**：哪些段做 MD5、标记文件名（`bookUrl.txt`/`origin.txt`）、
 * 目录分区（`book`/`rss`）都由共享层的 [io.legado.app.data.bigdata.RuleDataFileStore]
 * 决定；本契约只提供「根目录 + IO + MD5」这三件平台无法共享的事。
 *
 * 存储布局（与迁移前的 `:app` `help.RuleBigDataHelp` 逐字节一致，既有数据可直接读）：
 * ```
 * <rootDir>/book/<md5(bookUrl)>/<md5(key)>.txt
 * <rootDir>/book/<md5(bookUrl)>/bookUrl.txt
 * <rootDir>/book/<md5(bookUrl)>/<md5(chapterUrl)>/<md5(key)>.txt
 * <rootDir>/rss/<md5(origin)>/<md5(link)>/<md5(key)>.txt
 * <rootDir>/rss/<md5(origin)>/origin.txt
 * <rootDir>/rss/<md5(origin)>/<md5(link)>/origin.txt
 * ```
 *
 * androidMain 与 desktopMain 都是 JVM，两份 actual 实现相同（同 [JvmFileSystem] 的先例）。
 */
expect object RuleDataStorage {

    /**
     * 存储根目录的绝对路径。
     *
     * 由 host 在 composition root 设置，且必须在任何书源规则求值之前完成。
     *
     * @throws IllegalStateException 未设置时读取抛出。
     */
    var rootDir: String

    /**
     * MD5 十六进制（**小写**），用于生成文件名。
     *
     * 必须与迁移前的 `MD5Utils.md5Encode` 逐字节一致（标准 MD5、UTF-8、无盐、小写 hex），
     * 否则读不到既有数据。
     */
    fun md5(text: String): String

    /** 读取相对路径的文本；不存在或读取失败返回 null。 */
    fun readText(relativePath: String): String?

    /** 写入文本；父目录不存在则创建。 */
    fun writeText(relativePath: String, text: String)

    /** 相对路径是否存在。 */
    fun exists(relativePath: String): Boolean

    /** 删除；[recursive] 为 true 时递归删除目录（目录非空时必需）。 */
    fun delete(relativePath: String, recursive: Boolean)

    /** 列出目录条目；目录不存在返回空表。 */
    fun list(relativeDir: String): List<RuleDataEntry>
}
